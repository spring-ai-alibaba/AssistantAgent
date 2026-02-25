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
package com.alibaba.assistant.agent.start.capability.inference;

import com.alibaba.assistant.agent.start.capability.config.CapabilityRegistrationProperties;
import com.alibaba.assistant.agent.start.capability.provider.CapabilityProviderOrchestrator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generic LLM slot inference for capability tools.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class CapabilitySlotInferenceService {

	private static final Logger logger = LoggerFactory.getLogger(CapabilitySlotInferenceService.class);

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private static final String SLOT_INFER_BASE_PROMPT = """
			你是一个能力槽位提取器，负责从用户输入中提取字段值并输出 JSON。
			规则：
			1. 只输出一个 JSON 对象，不输出解释。
			2. 只提取用户明确表达的信息，不要臆测。
			3. 字段 key 必须来自候选字段列表。
			4. 若字段有 options，优先输出 option.value；若无法确定，输出最接近的原文值。
			5. 若没有可提取内容，输出 {}。
			""";

	private final ObjectMapper objectMapper;

	@Nullable
	private final ChatModel chatModel;

	@Autowired
	public CapabilitySlotInferenceService(ObjectMapper objectMapper, ObjectProvider<ChatModel> chatModelProvider) {
		this(objectMapper, chatModelProvider.getIfAvailable());
	}

	CapabilitySlotInferenceService(ObjectMapper objectMapper, @Nullable ChatModel chatModel) {
		this.objectMapper = objectMapper;
		this.chatModel = chatModel;
	}

	/**
	 * Infer missing slots from latest user input with LLM.
	 *
	 * @param capability capability definition
	 * @param missingFields fields that still need values
	 * @param currentSlots collected slots
	 * @param userInput latest user input text
	 * @param providerHints provider option/default hints
	 * @return inferred slots map, empty when none inferred
	 */
	public Map<String, Object> inferMissingSlots(
			CapabilityRegistrationProperties.HttpFormCapability capability,
			List<String> missingFields,
			Map<String, Object> currentSlots,
			@Nullable String userInput,
			@Nullable Map<String, CapabilityProviderOrchestrator.FieldHint> providerHints) {
		if (capability == null || missingFields == null || missingFields.isEmpty()) {
			return Map.of();
		}
		if (chatModel == null || !StringUtils.hasText(userInput)) {
			return Map.of();
		}

		List<String> inferableFields = resolveInferableFields(capability, missingFields);
		if (inferableFields.isEmpty()) {
			return Map.of();
		}

		String systemPrompt = buildSystemPrompt(capability, inferableFields, providerHints);
		String userPrompt = buildUserPrompt(userInput, currentSlots);
		Prompt prompt = new Prompt(List.of(
				new SystemMessage(systemPrompt),
				new UserMessage(userPrompt)));

		try {
			ChatResponse response = chatModel.call(prompt);
			String output = extractOutputText(response);
			if (!StringUtils.hasText(output)) {
				return Map.of();
			}
			Map<String, Object> parsed = parseJsonObject(output);
			if (parsed.isEmpty()) {
				return Map.of();
			}
			return normalizeInferredSlots(capability, inferableFields, parsed, providerHints);
		}
		catch (Exception e) {
			logger.warn("CapabilitySlotInferenceService#inferMissingSlots - reason=llm inference failed, toolName={}, error={}",
					capability.getToolName(), e.getMessage());
			return Map.of();
		}
	}

	private List<String> resolveInferableFields(CapabilityRegistrationProperties.HttpFormCapability capability,
			List<String> missingFields) {
		Map<String, CapabilityRegistrationProperties.FieldSpec> fieldSpecByName = indexFieldSpecByName(capability);
		List<String> inferable = new ArrayList<>();
		for (String fieldName : missingFields) {
			CapabilityRegistrationProperties.FieldSpec fieldSpec = fieldSpecByName.get(fieldName);
			if (canInfer(fieldSpec)) {
				inferable.add(fieldName);
			}
		}
		return inferable;
	}

	private boolean canInfer(@Nullable CapabilityRegistrationProperties.FieldSpec fieldSpec) {
		if (fieldSpec == null || !StringUtils.hasText(fieldSpec.getInferMode())) {
			return true;
		}
		String inferMode = fieldSpec.getInferMode().trim().toUpperCase();
		return !Set.of("OFF", "NONE", "DISABLED", "MANUAL").contains(inferMode);
	}

	private String buildSystemPrompt(CapabilityRegistrationProperties.HttpFormCapability capability,
			List<String> inferableFields,
			@Nullable Map<String, CapabilityProviderOrchestrator.FieldHint> providerHints) {
		StringBuilder sb = new StringBuilder(SLOT_INFER_BASE_PROMPT);
		sb.append("\n当前工具: ").append(capability.getToolName()).append("\n");
		sb.append("\n候选字段定义：\n");

		Map<String, CapabilityRegistrationProperties.FieldSpec> fieldSpecByName = indexFieldSpecByName(capability);
		for (String fieldName : inferableFields) {
			CapabilityRegistrationProperties.FieldSpec fieldSpec = fieldSpecByName.get(fieldName);
			sb.append("- key=").append(fieldName);
			if (fieldSpec != null && StringUtils.hasText(fieldSpec.getDescription())) {
				sb.append("; description=").append(fieldSpec.getDescription());
			}
			if (fieldSpec != null && StringUtils.hasText(fieldSpec.getInputMode())) {
				sb.append("; input_mode=").append(fieldSpec.getInputMode());
			}
			String optionHint = buildOptionHint(fieldSpec, providerHints != null ? providerHints.get(fieldName) : null);
			if (StringUtils.hasText(optionHint)) {
				sb.append("; options=").append(optionHint);
			}
			if (fieldSpec != null && StringUtils.hasText(fieldSpec.getInferPrompt())) {
				sb.append("; infer_hint=").append(fieldSpec.getInferPrompt());
			}
			sb.append("\n");
		}
		sb.append("输出字段只能使用以上 key。\n");
		return sb.toString();
	}

	private String buildOptionHint(@Nullable CapabilityRegistrationProperties.FieldSpec fieldSpec,
			@Nullable CapabilityProviderOrchestrator.FieldHint providerHint) {
		List<String> optionTexts = new ArrayList<>();
		if (providerHint != null && providerHint.options() != null) {
			for (CapabilityProviderOrchestrator.OptionItem option : providerHint.options()) {
				if (option == null || !StringUtils.hasText(option.value())) {
					continue;
				}
				String label = StringUtils.hasText(option.label()) ? option.label() : option.value();
				optionTexts.add(label + "(" + option.value() + ")");
			}
		}
		if (optionTexts.isEmpty() && fieldSpec != null && fieldSpec.getOptions() != null) {
			for (CapabilityRegistrationProperties.FieldOption option : fieldSpec.getOptions()) {
				if (option == null || !StringUtils.hasText(option.getValue())) {
					continue;
				}
				String label = StringUtils.hasText(option.getLabel()) ? option.getLabel() : option.getValue();
				optionTexts.add(label + "(" + option.getValue() + ")");
			}
		}
		if (optionTexts.isEmpty()) {
			return "";
		}
		return optionTexts.stream().collect(Collectors.joining(", "));
	}

	private String buildUserPrompt(@Nullable String userInput, Map<String, Object> currentSlots) {
		Map<String, Object> safeCurrentSlots = currentSlots != null ? currentSlots : Map.of();
		String currentSlotsJson;
		try {
			currentSlotsJson = objectMapper.writeValueAsString(safeCurrentSlots);
		}
		catch (Exception e) {
			currentSlotsJson = "{}";
		}
		return """
				用户输入：
				%s

				已收集字段：
				%s

				请仅输出 JSON 对象。
				""".formatted(userInput != null ? userInput.trim() : "", currentSlotsJson);
	}

	private String extractOutputText(@Nullable ChatResponse response) {
		if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
			return null;
		}
		return response.getResult().getOutput().getText();
	}

	private Map<String, Object> parseJsonObject(String output) {
		if (!StringUtils.hasText(output)) {
			return Map.of();
		}
		String cleaned = trimMarkdownCodeFence(output.trim());
		Map<String, Object> parsed = parseJsonObjectSafely(cleaned);
		if (!parsed.isEmpty()) {
			return parsed;
		}

		int objectStart = cleaned.indexOf('{');
		int objectEnd = cleaned.lastIndexOf('}');
		if (objectStart >= 0 && objectEnd > objectStart) {
			return parseJsonObjectSafely(cleaned.substring(objectStart, objectEnd + 1));
		}
		return Map.of();
	}

	private Map<String, Object> parseJsonObjectSafely(String text) {
		if (!StringUtils.hasText(text)) {
			return Map.of();
		}
		try {
			Map<String, Object> parsed = objectMapper.readValue(text, MAP_TYPE);
			return parsed != null ? parsed : Map.of();
		}
		catch (Exception ignore) {
			return Map.of();
		}
	}

	private String trimMarkdownCodeFence(String text) {
		if (!StringUtils.hasText(text)) {
			return text;
		}
		String cleaned = text;
		if (cleaned.startsWith("```json")) {
			cleaned = cleaned.substring(7).trim();
		}
		else if (cleaned.startsWith("```")) {
			cleaned = cleaned.substring(3).trim();
		}
		if (cleaned.endsWith("```")) {
			cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
		}
		return cleaned;
	}

	private Map<String, Object> normalizeInferredSlots(
			CapabilityRegistrationProperties.HttpFormCapability capability,
			List<String> inferableFields,
			Map<String, Object> parsed,
			@Nullable Map<String, CapabilityProviderOrchestrator.FieldHint> providerHints) {
		if (parsed == null || parsed.isEmpty() || inferableFields == null || inferableFields.isEmpty()) {
			return Map.of();
		}
		Map<String, CapabilityRegistrationProperties.FieldSpec> fieldSpecByName = indexFieldSpecByName(capability);
		Map<String, Object> normalized = new LinkedHashMap<>();
		Set<String> candidateSet = new LinkedHashSet<>(inferableFields);
		for (Map.Entry<String, Object> entry : parsed.entrySet()) {
			String key = entry.getKey();
			if (!StringUtils.hasText(key) || !candidateSet.contains(key)) {
				continue;
			}
			CapabilityRegistrationProperties.FieldSpec fieldSpec = fieldSpecByName.get(key);
			CapabilityProviderOrchestrator.FieldHint providerHint = providerHints != null ? providerHints.get(key) : null;
			Object value = normalizeFieldValue(key, entry.getValue(), fieldSpec, providerHint);
			if (!isEmptyValue(value)) {
				normalized.put(key, value);
			}
		}
		return normalized;
	}

	private Object normalizeFieldValue(String fieldName, Object rawValue,
			@Nullable CapabilityRegistrationProperties.FieldSpec fieldSpec,
			@Nullable CapabilityProviderOrchestrator.FieldHint providerHint) {
		Object normalizedRaw = unwrapValue(rawValue);
		if (normalizedRaw == null) {
			return null;
		}
		boolean multiSelect = fieldSpec != null && "SELECT_MULTI".equalsIgnoreCase(fieldSpec.getInputMode());
		if (multiSelect) {
			List<String> values = normalizeAsMultiValue(fieldName, normalizedRaw, fieldSpec, providerHint);
			return values.isEmpty() ? null : String.join(",", values);
		}
		String text = normalizeText(normalizedRaw);
		if (!StringUtils.hasText(text)) {
			return null;
		}
		String mapped = mapToOptionValue(fieldName, text, fieldSpec, providerHint);
		return StringUtils.hasText(mapped) ? mapped : text;
	}

	private Object unwrapValue(Object value) {
		if (value instanceof Map<?, ?> map) {
			Object rawValue = map.get("value");
			if (rawValue != null) {
				return rawValue;
			}
		}
		return value;
	}

	private List<String> normalizeAsMultiValue(String fieldName, Object value,
			@Nullable CapabilityRegistrationProperties.FieldSpec fieldSpec,
			@Nullable CapabilityProviderOrchestrator.FieldHint providerHint) {
		List<String> sourceValues = new ArrayList<>();
		if (value instanceof Collection<?> collection) {
			for (Object item : collection) {
				String text = normalizeText(item);
				if (StringUtils.hasText(text)) {
					sourceValues.add(text);
				}
			}
		}
		else {
			String text = normalizeText(value);
			if (StringUtils.hasText(text)) {
				for (String part : text.split("[,，、;；\\n\\r]+")) {
					String trimmed = part != null ? part.trim() : "";
					if (StringUtils.hasText(trimmed)) {
						sourceValues.add(trimmed);
					}
				}
			}
		}
		if (sourceValues.isEmpty()) {
			return List.of();
		}

		List<String> normalized = new ArrayList<>();
		for (String source : sourceValues) {
			String mapped = mapToOptionValue(fieldName, source, fieldSpec, providerHint);
			String finalValue = StringUtils.hasText(mapped) ? mapped : source;
			if (StringUtils.hasText(finalValue) && !normalized.contains(finalValue)) {
				normalized.add(finalValue);
			}
		}
		return normalized;
	}

	private String mapToOptionValue(String fieldName, String rawValue,
			@Nullable CapabilityRegistrationProperties.FieldSpec fieldSpec,
			@Nullable CapabilityProviderOrchestrator.FieldHint providerHint) {
		if (!StringUtils.hasText(rawValue)) {
			return null;
		}
		Map<String, String> aliasToValue = buildOptionAlias(fieldName, fieldSpec, providerHint);
		if (aliasToValue.isEmpty()) {
			return null;
		}
		String normalized = normalizeAlias(rawValue);
		return aliasToValue.get(normalized);
	}

	private Map<String, String> buildOptionAlias(String fieldName,
			@Nullable CapabilityRegistrationProperties.FieldSpec fieldSpec,
			@Nullable CapabilityProviderOrchestrator.FieldHint providerHint) {
		Map<String, String> aliasToValue = new LinkedHashMap<>();
		if (providerHint != null && providerHint.options() != null) {
			for (CapabilityProviderOrchestrator.OptionItem option : providerHint.options()) {
				if (option == null || !StringUtils.hasText(option.value())) {
					continue;
				}
				String optionValue = option.value();
				putAlias(aliasToValue, optionValue, optionValue);
				putAlias(aliasToValue, option.label(), optionValue);
			}
		}
		if (fieldSpec != null && fieldSpec.getOptions() != null) {
			for (CapabilityRegistrationProperties.FieldOption option : fieldSpec.getOptions()) {
				if (option == null || !StringUtils.hasText(option.getValue())) {
					continue;
				}
				String optionValue = option.getValue();
				putAlias(aliasToValue, optionValue, optionValue);
				putAlias(aliasToValue, option.getLabel(), optionValue);
			}
		}
		if (!aliasToValue.containsKey(normalizeAlias(fieldName))) {
			putAlias(aliasToValue, fieldName, fieldName);
		}
		return aliasToValue;
	}

	private void putAlias(Map<String, String> aliasToValue, @Nullable String alias, String value) {
		if (!StringUtils.hasText(alias) || !StringUtils.hasText(value)) {
			return;
		}
		aliasToValue.putIfAbsent(normalizeAlias(alias), value);
	}

	private String normalizeAlias(String text) {
		if (!StringUtils.hasText(text)) {
			return "";
		}
		StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (!Character.isWhitespace(ch)) {
				sb.append(Character.toLowerCase(ch));
			}
		}
		return sb.toString();
	}

	private String normalizeText(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		return StringUtils.hasText(text) ? text : null;
	}

	private boolean isEmptyValue(@Nullable Object value) {
		if (value == null) {
			return true;
		}
		if (value instanceof String text) {
			return !StringUtils.hasText(text);
		}
		return false;
	}

	private Map<String, CapabilityRegistrationProperties.FieldSpec> indexFieldSpecByName(
			CapabilityRegistrationProperties.HttpFormCapability capability) {
		if (capability == null || capability.getFields() == null || capability.getFields().isEmpty()) {
			return Map.of();
		}
		Map<String, CapabilityRegistrationProperties.FieldSpec> fieldSpecByName = new LinkedHashMap<>();
		for (CapabilityRegistrationProperties.FieldSpec fieldSpec : capability.getFields()) {
			if (fieldSpec != null && StringUtils.hasText(fieldSpec.getName())) {
				fieldSpecByName.put(fieldSpec.getName(), fieldSpec);
			}
		}
		return fieldSpecByName;
	}

}
