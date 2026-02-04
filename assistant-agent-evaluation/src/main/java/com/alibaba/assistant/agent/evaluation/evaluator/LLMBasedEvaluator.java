/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.evaluation.evaluator;

import com.alibaba.assistant.agent.evaluation.executor.SourcePathResolver;
import com.alibaba.assistant.agent.evaluation.model.CriterionExecutionContext;
import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.assistant.agent.evaluation.model.CriterionStatus;
import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.assistant.agent.evaluation.model.EvaluationCriterion;
import com.alibaba.assistant.agent.evaluation.model.ReasoningPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-based evaluator that uses ChatModel to evaluate criteria
 * Provides default prompt assembly logic and supports custom prompts
 *
 * @author Assistant Agent Team
 */
public class LLMBasedEvaluator implements Evaluator {

	private static final Logger logger = LoggerFactory.getLogger(LLMBasedEvaluator.class);

	private final ChatModel chatModel;
	private final String evaluatorId;
	private final ObjectMapper objectMapper;

	public LLMBasedEvaluator(ChatModel chatModel, String evaluatorId) {
		this.chatModel = chatModel;
		this.evaluatorId = evaluatorId;
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public CriterionResult evaluate(CriterionExecutionContext executionContext) {
		CriterionResult result = new CriterionResult();
		result.setCriterionName(executionContext.getCriterion().getName());
		result.setStartTimeMillis(System.currentTimeMillis());

		try {
			// Build prompt
			String promptText = buildPrompt(executionContext);

			logger.debug("Evaluating criterion {} with LLM, prompt: {}",
				executionContext.getCriterion().getName(), promptText);

			// Call LLM
			Prompt prompt = new Prompt(promptText);
			ChatResponse chatResponse = chatModel.call(prompt);
			String response = chatResponse.getResult().getOutput().getText();

            if (response == null || response.trim().isEmpty()) {
				logger.warn("ChatModel returned empty response for criterion {}", executionContext.getCriterion().getName());
				result.setStatus(CriterionStatus.ERROR);
				result.setErrorMessage("LLM response was empty");
				return result;
			}

			result.setRawResponse(response);

			// Parse response based on result type and reasoning policy
			ParsedResponse parsedResponse = parseStructuredResponse(response, executionContext.getCriterion());
			result.setValue(parsedResponse.getValue());
			if (parsedResponse.getReasoning() != null) {
				result.setReason(parsedResponse.getReasoning());
			}

			result.setStatus(CriterionStatus.SUCCESS);

		} catch (Exception e) {
			logger.error("Error evaluating criterion {}: {}",
				executionContext.getCriterion().getName(), e.getMessage(), e);
			result.setStatus(CriterionStatus.ERROR);
			result.setErrorMessage(e.getMessage());
		} finally {
			result.setEndTimeMillis(System.currentTimeMillis());
		}

		return result;
	}

	/**
	 * Build prompt for LLM evaluation
	 */
	protected String buildPrompt(CriterionExecutionContext executionContext) {
		EvaluationCriterion criterion = executionContext.getCriterion();

		// If custom prompt template is provided, use it
		if (criterion.getCustomPrompt() != null && !criterion.getCustomPrompt().isEmpty()) {
			return interpolateTemplate(criterion.getCustomPrompt(), executionContext);
		}

		// Otherwise, use default prompt assembly logic
		StringBuilder prompt = new StringBuilder();

		// Instruction
		prompt.append("You are an evaluator performing the following evaluation task:\n\n");

		// Description
		prompt.append("Evaluation Criterion Description: ").append(criterion.getDescription()).append("\n\n");

		// Working mechanism
		if (criterion.getWorkingMechanism() != null && !criterion.getWorkingMechanism().isEmpty()) {
			prompt.append("Working Mechanism:\n");
			// Interpolate working mechanism to support placeholders like {{common_sense_data}}
			String interpolatedMechanism = interpolateTemplate(criterion.getWorkingMechanism(), executionContext);
			prompt.append(interpolatedMechanism).append("\n\n");
		}

		// Unified output format with RESULT: prefix
		prompt.append("Output your response in the following format:\n\n");
		prompt.append("RESULT: ");
		switch (criterion.getResultType()) {
			case BOOLEAN:
				prompt.append("(true or false)\n");
				break;
			case ENUM:
				if (!criterion.getOptions().isEmpty()) {
					prompt.append("(one of: ").append(String.join(", ", criterion.getOptions())).append(")\n");
				}
				break;
			case SCORE:
				prompt.append("(a numeric score between 0 and 1)\n");
				break;
			case JSON:
				prompt.append("(a valid JSON object)\n");
				break;
			case LIST:
				prompt.append("(a JSON array, e.g., [\"item1\", \"item2\"] or [0, 1, 2])\n");
				break;
			default:
				prompt.append("(your evaluation result)\n");
		}

		// Add reasoning section only when needed
		if (criterion.getReasoningPolicy() != ReasoningPolicy.NONE) {
			prompt.append("REASONING:\n");
			if (criterion.getReasoningPolicy() == ReasoningPolicy.BRIEF) {
				prompt.append("(brief explanation in 1-2 sentences)\n");
			} else if (criterion.getReasoningPolicy() == ReasoningPolicy.FULL) {
				prompt.append("(detailed reasoning process)\n");
			}
		} else {
			prompt.append("(No reasoning required for this evaluation)\n");
		}

		// Few-shot examples
		if (!criterion.getFewShots().isEmpty()) {
			prompt.append("\nExamples:\n");
			for (int i = 0; i < criterion.getFewShots().size(); i++) {
				EvaluationCriterion.FewShotExample example = criterion.getFewShots().get(i);
				prompt.append("Example ").append(i + 1).append(":\n");
				prompt.append("[Input] ").append(example.getInput()).append("\n");
				if (example.getContext() != null && !example.getContext().isEmpty()) {
					prompt.append("[Context] ").append(example.getContext()).append("\n");
				}
				prompt.append("[Expected Output] ").append("RESULT: ").append(example.getExpectedOutput()).append("\n\n");
			}
		}

		// Context information
		prompt.append("\nContext:\n");
		if (!criterion.getContextBindings().isEmpty()) {
			for (String binding : criterion.getContextBindings()) {
				Object value = resolveContextBinding(binding, executionContext);
				prompt.append("- ").append(binding).append(": ").append(formatValue(value)).append("\n");
			}
		}

		// Previous results if any dependencies
		if (!criterion.getDependsOn().isEmpty() && !executionContext.getDependencyResults().isEmpty()) {
			prompt.append("Previous Evaluation Results:\n");
			for (String depName : criterion.getDependsOn()) {
				CriterionResult prevResult = executionContext.getDependencyResult(depName);
				if (prevResult != null) {
					prompt.append("- ").append(depName).append(": ").append(prevResult.getValue()).append("\n");
				}
			}
			prompt.append("\n");
		}

		prompt.append("\nYour evaluation result:\n");

		return prompt.toString();
	}

	/**
	 * Resolve a context binding path (e.g., "context.input.userInput", "dependencies.criterionName.value")
	 * Now uses SourcePathResolver for consistent path resolution with batching sourcePath logic
	 */
	protected Object resolveContextBinding(String binding, CriterionExecutionContext executionContext) {
		// This enables LLM-based evaluators to access the current batched list in their prompt.
		if (binding != null && binding.startsWith("context.") && executionContext != null
				&& executionContext.getExtraBindings() != null && !executionContext.getExtraBindings().isEmpty()) {
			String path = binding.substring("context.".length());
			Object fromExtra = resolveFromExtraBindings(path, executionContext.getExtraBindings());
			if (fromExtra != null) {
				return fromExtra;
			}
		}
		return SourcePathResolver.resolve(
			binding,
			executionContext.getInputContext(),
			executionContext.getDependencyResults()
		);
	}

	/**
	 * Resolve a dotted path from the per-batch extraBindings map.
	 */
	@SuppressWarnings("unchecked")
	private Object resolveFromExtraBindings(String path, Map<String, Object> extraBindings) {
		if (path == null || path.isEmpty() || extraBindings == null || extraBindings.isEmpty()) {
			return null;
		}

		String[] parts = path.split("\\.", 2);
		String key = parts[0];
		if (!extraBindings.containsKey(key)) {
			return null;
		}

		Object value = extraBindings.get(key);
		if (parts.length == 1) {
			return value;
		}

		// Navigate nested fields if the value is a map
		String remaining = parts[1];
		if (value instanceof Map) {
			return navigateExtraMap((Map<?, ?>) value, remaining);
		}

		return null;
	}

	private Object navigateExtraMap(Map<?, ?> map, String path) {
		if (map == null || path == null || path.isEmpty()) {
			return null;
		}
		String[] parts = path.split("\\.", 2);
		Object value = map.get(parts[0]);
		if (value == null) {
			return null;
		}
		if (parts.length == 1) {
			return value;
		}
		if (value instanceof Map) {
			return navigateExtraMap((Map<?, ?>) value, parts[1]);
		}
		return null;
	}

	/**
	 * Format a value for inclusion in prompt
	 */
	protected String formatValue(Object value) {
		if (value == null) {
			return "null";
		}
		if (value instanceof String) {
			return (String) value;
		}
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception e) {
			return value.toString();
		}
	}

	/**
	 * Interpolate template with context values
	 */
	protected String interpolateTemplate(String template, CriterionExecutionContext executionContext) {
		if (template == null || template.isEmpty()) {
			return template;
		}

		// Build context map only once
		Map<String, Object> allValues = new HashMap<>();
		EvaluationContext inputContext = executionContext.getInputContext();
		if (inputContext != null) {
			if (inputContext.getInput() != null) {
				allValues.putAll(inputContext.getInput());
			}
			if (inputContext.getExecutionResult() != null) {
				allValues.putAll(inputContext.getExecutionResult());
			}
			if (inputContext.getEnvironment() != null) {
				allValues.putAll(inputContext.getEnvironment());
			}
		}

		// Add dependency results to interpolation context
		if (executionContext.getDependencyResults() != null) {
			for (Map.Entry<String, CriterionResult> entry : executionContext.getDependencyResults().entrySet()) {
				if (entry.getValue() != null) {
					allValues.put(entry.getKey(), entry.getValue().getValue());
				}
			}
		}

		// Use regex for single-pass replacement
		Pattern pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");
		Matcher matcher = pattern.matcher(template);
		StringBuilder sb = new StringBuilder();

		while (matcher.find()) {
			String key = matcher.group(1);
			Object value = allValues.get(key);
			String replacement = formatValue(value);
			// Escape special characters in replacement string
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);

		return sb.toString();
	}

	/**
	 * Parse LLM response, handling unified RESULT: format with optional REASONING:
	 */
	protected ParsedResponse parseStructuredResponse(String response, EvaluationCriterion criterion) {
		String trimmed = response.trim();

		// Look for RESULT: prefix (now used in all cases)
		if (trimmed.contains("RESULT:")) {
			int resultIndex = trimmed.indexOf("RESULT:");
			String afterResult = trimmed.substring(resultIndex + "RESULT:".length());

			// Check if response also has REASONING: section
			if (criterion.getReasoningPolicy() != ReasoningPolicy.NONE && afterResult.contains("REASONING:")) {
				int reasoningIndex = afterResult.indexOf("REASONING:");

				// Extract result and reasoning sections
				String resultSection = afterResult.substring(0, reasoningIndex).trim();
				String reasoningSection = afterResult.substring(reasoningIndex + "REASONING:".length()).trim();

				Object value = parseResponseValue(resultSection, criterion);
				return new ParsedResponse(value, reasoningSection);
			} else {
				// Only RESULT: section (NONE reasoning policy)
				String resultSection = afterResult.trim();
				Object value = parseResponseValue(resultSection, criterion);
				return new ParsedResponse(value, null);
			}
		}

		// Fallback to simple parsing (for compatibility with old responses)
		Object value = parseResponseValue(trimmed, criterion);
		return new ParsedResponse(value, null);
	}

	/**
	 * Parse LLM response value based on result type
	 */
	protected Object parseResponseValue(String response, EvaluationCriterion criterion) {
		String trimmed = response.trim();

		switch (criterion.getResultType()) {
			case BOOLEAN:
				return Boolean.parseBoolean(trimmed) || trimmed.equalsIgnoreCase("yes") || trimmed.equalsIgnoreCase("true");
			case ENUM:
				// Check if response matches one of the options
				for (String option : criterion.getOptions()) {
					if (trimmed.equalsIgnoreCase(option)) {
						return option;
					}
				}
				return trimmed; // Return as-is if no match
			case SCORE:
				try {
					return Double.parseDouble(trimmed);
				} catch (NumberFormatException e) {
					logger.warn("Failed to parse score from response: {}", trimmed);
					return 0.0;
				}
			case JSON:
				try {
					return objectMapper.readValue(trimmed, Object.class);
				} catch (Exception e) {
					logger.warn("Failed to parse JSON from response: {}", trimmed);
					return trimmed;
				}
			case LIST:
				try {
					// Extract JSON array from response if it contains extra text
					String jsonArray = extractJsonArray(trimmed);
					return objectMapper.readValue(jsonArray, List.class);
				} catch (Exception e) {
					logger.warn("Failed to parse LIST from response: {}", trimmed);
					// Return empty list on parse failure
					return java.util.Collections.emptyList();
				}
			default:
				return trimmed;
		}
	}

	/**
	 * Extract JSON array from response text.
	 * Handles cases where the response contains extra text around the JSON array.
	 */
	private String extractJsonArray(String text) {
		if (text == null || text.isEmpty()) {
			return "[]";
		}

		// Find the first '[' and last ']' to extract the JSON array
		int start = text.indexOf('[');
		int end = text.lastIndexOf(']');

		if (start != -1 && end != -1 && end > start) {
			return text.substring(start, end + 1);
		}

		// If no brackets found, return the original text (let objectMapper handle the error)
		return text;
	}

	/**
	 * Internal class to hold parsed response with value and reasoning
	 */
	protected static class ParsedResponse {
		private final Object value;
		private final String reasoning;

		public ParsedResponse(Object value, String reasoning) {
			this.value = value;
			this.reasoning = reasoning;
		}

		public Object getValue() {
			return value;
		}

		public String getReasoning() {
			return reasoning;
		}
	}

	/**
	 * Parse LLM response based on result type
	 * @deprecated Use parseStructuredResponse instead
	 */
	@Deprecated
	protected Object parseResponse(String response, EvaluationCriterion criterion) {
		return parseResponseValue(response, criterion);
	}

	@Override
	public String getEvaluatorId() {
		return evaluatorId;
	}
}
