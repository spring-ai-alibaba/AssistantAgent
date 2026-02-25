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
package com.alibaba.assistant.agent.start.capability.hook;

import com.alibaba.assistant.agent.common.hook.AgentPhase;
import com.alibaba.assistant.agent.common.hook.HookPhases;
import com.alibaba.assistant.agent.start.capability.config.CapabilityRegistrationProperties;
import com.alibaba.assistant.agent.start.capability.intent.CapabilityIntentClassifier;
import com.alibaba.assistant.agent.start.capability.intent.CapabilityIntentType;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Route operation intent to registered capability tool before model generation.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
@Order(40)
@HookPhases(AgentPhase.REACT)
@HookPositions(HookPosition.BEFORE_AGENT)
public class OperationIntentRouteReactHook extends AgentHook {

	private static final Logger log = LoggerFactory.getLogger(OperationIntentRouteReactHook.class);

	private static final String DRAFT_STATE_PREFIX = "capability_draft_";

	private static final String DRAFT_STATUS_SUFFIX = "_status";

	private static final Set<String> ACTIVE_DRAFT_STATUSES = Set.of("SLOT_MISSING", "WAIT_CONFIRM", "SUBMIT_FAILED");

	private final CapabilityRegistrationProperties capabilityRegistrationProperties;

	private final CapabilityIntentClassifier intentClassifier;

	public OperationIntentRouteReactHook() {
		this(new CapabilityRegistrationProperties(), new CapabilityIntentClassifier());
	}

	@Autowired
	public OperationIntentRouteReactHook(CapabilityRegistrationProperties capabilityRegistrationProperties) {
		this(capabilityRegistrationProperties, new CapabilityIntentClassifier());
	}

	OperationIntentRouteReactHook(CapabilityRegistrationProperties capabilityRegistrationProperties,
			CapabilityIntentClassifier intentClassifier) {
		this.capabilityRegistrationProperties = capabilityRegistrationProperties;
		this.intentClassifier = intentClassifier != null ? intentClassifier : new CapabilityIntentClassifier();
	}

	@Override
	public String getName() {
		return "OperationIntentRouteReactHook";
	}

	@Override
	public List<JumpTo> canJumpTo() {
		return List.of(JumpTo.tool, JumpTo.model);
	}

	@Override
	public Map<String, KeyStrategy> getKeyStrategys() {
		return Map.of("jump_to", new ReplaceStrategy());
	}

	@Override
	public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
		if (state == null) {
			return CompletableFuture.completedFuture(Map.of());
		}

		String userInput = extractUserInput(state);
		CapabilityIntentType intentType = intentClassifier.classify(userInput);
		if (intentType == CapabilityIntentType.UNKNOWN) {
			return CompletableFuture.completedFuture(Map.of());
		}

		Map<String, Object> updates = new LinkedHashMap<>();
		updates.put("capability_intent_type", intentType.label());
		log.info("OperationIntentRouteReactHook#beforeAgent - reason=intent classified, type={}, input={}",
				intentType.label(), userInput);

		if (intentType != CapabilityIntentType.OPERATION || !StringUtils.hasText(userInput)) {
			return CompletableFuture.completedFuture(updates);
		}
		if (hasActiveDraft(state.data())) {
			return CompletableFuture.completedFuture(updates);
		}

		JumpTo currentJumpTo = resolveJumpTo(state.value("jump_to").orElse(null));
		if (currentJumpTo == JumpTo.tool) {
			return CompletableFuture.completedFuture(updates);
		}

		MatchedCapability matchedCapability = matchOperationCapability(userInput);
		if (matchedCapability == null) {
			log.debug("OperationIntentRouteReactHook#beforeAgent - reason=operation intent but no capability matched");
			return CompletableFuture.completedFuture(updates);
		}

		String toolCallId = "operation_intent_" + UUID.randomUUID().toString().substring(0, 8);
		AssistantMessage assistantMessage = AssistantMessage.builder()
				.content("识别为操作类型请求，调用能力工具处理")
				.toolCalls(List.of(new AssistantMessage.ToolCall(
						toolCallId, "function", matchedCapability.toolName(), "{}")))
				.build();

		updates.put("capability_intent_route_tool", matchedCapability.toolName());
		updates.put("messages", List.of(assistantMessage));
		updates.put("jump_to", JumpTo.tool);
		log.info(
				"OperationIntentRouteReactHook#beforeAgent - reason=route operation intent to capability tool, toolName={}, score={}",
				matchedCapability.toolName(), matchedCapability.score());
		return CompletableFuture.completedFuture(updates);
	}

	private MatchedCapability matchOperationCapability(String userInput) {
		if (!StringUtils.hasText(userInput) || capabilityRegistrationProperties == null
				|| capabilityRegistrationProperties.getRegistrations() == null
				|| capabilityRegistrationProperties.getRegistrations().isEmpty()) {
			return null;
		}

		String normalizedInput = normalize(userInput);
		MatchedCapability best = null;
		for (CapabilityRegistrationProperties.HttpFormCapability capability : capabilityRegistrationProperties.getRegistrations()) {
			if (capability == null || !capability.isEnabled() || !StringUtils.hasText(capability.getToolName())) {
				continue;
			}

			int score = 0;
			score += scoreByKeywords(normalizedInput, capability.getOperationIntentKeywords());
			score += scoreByPatterns(userInput, capability.getOperationIntentPatterns());
			if (score <= 0) {
				continue;
			}

			if (best == null || score > best.score()) {
				best = new MatchedCapability(capability.getToolName(), score);
			}
		}
		return best;
	}

	private int scoreByKeywords(String normalizedInput, List<String> keywords) {
		if (!StringUtils.hasText(normalizedInput) || keywords == null || keywords.isEmpty()) {
			return 0;
		}
		int score = 0;
		for (String keyword : keywords) {
			if (!StringUtils.hasText(keyword)) {
				continue;
			}
			if (normalizedInput.contains(keyword.trim().toLowerCase())) {
				score += 3;
			}
		}
		return score;
	}

	private int scoreByPatterns(String userInput, List<String> patterns) {
		if (!StringUtils.hasText(userInput) || patterns == null || patterns.isEmpty()) {
			return 0;
		}
		int score = 0;
		for (String patternText : patterns) {
			if (!StringUtils.hasText(patternText)) {
				continue;
			}
			try {
				Pattern pattern = Pattern.compile(patternText);
				if (pattern.matcher(userInput).find()) {
					score += 5;
				}
			}
			catch (PatternSyntaxException ex) {
				log.warn("OperationIntentRouteReactHook#scoreByPatterns - reason=invalid regex pattern, pattern={}",
						patternText, ex);
			}
		}
		return score;
	}

	private boolean hasActiveDraft(Map<String, Object> stateData) {
		if (stateData == null || stateData.isEmpty()) {
			return false;
		}
		for (Map.Entry<String, Object> entry : stateData.entrySet()) {
			String key = entry.getKey();
			if (!StringUtils.hasText(key)
					|| !key.startsWith(DRAFT_STATE_PREFIX)
					|| !key.endsWith(DRAFT_STATUS_SUFFIX)) {
				continue;
			}
			String status = entry.getValue() != null ? String.valueOf(entry.getValue()) : null;
			if (ACTIVE_DRAFT_STATUSES.contains(status)) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private String extractUserInput(OverAllState state) {
		String input = state.value("input", String.class).orElse(null);
		if (StringUtils.hasText(input)) {
			return input.trim();
		}

		Optional<Object> messagesOpt = state.value("messages");
		if (messagesOpt == null || messagesOpt.isEmpty() || !(messagesOpt.get() instanceof List<?> rawMessages)) {
			return null;
		}
		for (int i = rawMessages.size() - 1; i >= 0; i--) {
			Object msg = rawMessages.get(i);
			if (msg instanceof UserMessage userMessage) {
				return userMessage.getText();
			}
			if (msg instanceof Message message && "USER".equalsIgnoreCase(String.valueOf(message.getMessageType()))
					&& StringUtils.hasText(message.getText())) {
				return message.getText();
			}
		}
		return null;
	}

	private JumpTo resolveJumpTo(Object jumpToValue) {
		if (jumpToValue instanceof JumpTo jumpTo) {
			return jumpTo;
		}
		if (jumpToValue instanceof String text) {
			return JumpTo.fromStringOrNull(text);
		}
		return null;
	}

	private String normalize(String text) {
		return text == null ? "" : text.trim().toLowerCase();
	}

	private record MatchedCapability(String toolName, int score) {
	}

}
