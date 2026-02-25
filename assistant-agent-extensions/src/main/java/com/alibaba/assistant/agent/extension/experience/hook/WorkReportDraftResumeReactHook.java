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
package com.alibaba.assistant.agent.extension.experience.hook;

import com.alibaba.assistant.agent.common.hook.AgentPhase;
import com.alibaba.assistant.agent.common.hook.HookPhases;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 通用能力草稿续填 Hook。
 *
 * <p>当 capability_draft_{toolName}_status 处于未完成状态时，
 * 本 Hook 会继续调用对应 toolName，避免模型走自由生成导致的重复追问。
 */
@HookPhases(AgentPhase.REACT)
@HookPositions(HookPosition.BEFORE_AGENT)
public class WorkReportDraftResumeReactHook extends AgentHook {

	private static final Logger log = LoggerFactory.getLogger(WorkReportDraftResumeReactHook.class);

	private static final String DRAFT_STATE_PREFIX = "capability_draft_";
	private static final String DRAFT_STATUS_SUFFIX = "_status";
	private static final Set<String> ACTIVE_DRAFT_STATUSES = Set.of("SLOT_MISSING", "WAIT_CONFIRM", "SUBMIT_FAILED");
	private static final String SLOT_EXTRACT_BASE_PROMPT = """
			你是能力槽位提取器。请从用户输入中提取字段并输出 JSON 对象。
			
			规则：
			1. 只输出一个 JSON 对象，不要输出解释文本。
			2. 只提取输入中明确给出的值，不要猜测。
			3. 支持“字段 值”与“字段：值”两种表达。
			4. 如果没有可提取字段，输出 {}。
			""";

	private final ChatModel chatModel;

	public WorkReportDraftResumeReactHook() {
		this(null);
	}

	public WorkReportDraftResumeReactHook(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	@Override
	public String getName() {
		return "WorkReportDraftResumeReactHook";
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

		ActiveDraftContext activeDraft = resolveActiveDraftContext(state);
		if (activeDraft == null) {
			return CompletableFuture.completedFuture(Map.of());
		}

		String userInput = extractUserInput(state);
		Map<String, Object> arguments = new LinkedHashMap<>();
		arguments.putAll(activeDraft.collectedSlots());
		arguments.putAll(extractToolArguments(userInput, activeDraft));
		if ("WAIT_CONFIRM".equals(activeDraft.status()) && isConfirmText(userInput)) {
			arguments.put("confirmed", true);
		}

		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
				"work_report_resume_" + UUID.randomUUID().toString().substring(0, 8),
				"function",
				activeDraft.toolName(),
				JSON.toJSONString(arguments)
		);
		AssistantMessage assistantMessage = AssistantMessage.builder()
				.content("继续处理能力槽位收集")
				.toolCalls(List.of(toolCall))
				.build();

		log.info(
				"WorkReportDraftResumeReactHook#beforeAgent - reason=resume draft by tool call, toolName={}, status={}, argsKeys={}",
				activeDraft.toolName(),
				activeDraft.status(),
				arguments.keySet());

		return CompletableFuture.completedFuture(Map.of(
				"messages", List.of(assistantMessage),
				"jump_to", JumpTo.tool
		));
	}

	private ActiveDraftContext resolveActiveDraftContext(OverAllState state) {
		Map<String, Object> stateData = state.data();
		Set<String> activeTools = detectActiveToolsFromState(stateData);
		ToolSnapshot snapshotFromHistory = extractLatestToolSnapshot(state, activeTools);

		if (snapshotFromHistory != null) {
			String status = resolveStatusFromState(stateData, snapshotFromHistory.toolName(), snapshotFromHistory.status());
			if (ACTIVE_DRAFT_STATUSES.contains(status)) {
				Map<String, Object> mergedSlots = new LinkedHashMap<>();
				mergedSlots.putAll(snapshotFromHistory.collectedSlots());
				mergedSlots.putAll(loadDraftSlotsFromState(stateData, snapshotFromHistory.toolName()));
				return new ActiveDraftContext(
						snapshotFromHistory.toolName(),
						status,
						mergedSlots,
						snapshotFromHistory.missingFields(),
						snapshotFromHistory.fieldLabels()
				);
			}
		}

		for (String toolName : activeTools) {
			String status = resolveStatusFromState(stateData, toolName, null);
			if (!ACTIVE_DRAFT_STATUSES.contains(status)) {
				continue;
			}
			Map<String, Object> draftSlots = loadDraftSlotsFromState(stateData, toolName);
			return new ActiveDraftContext(toolName, status, draftSlots, List.of(), Map.of());
		}
		return null;
	}

	private Set<String> detectActiveToolsFromState(Map<String, Object> stateData) {
		if (stateData == null || stateData.isEmpty()) {
			return Set.of();
		}
		Set<String> tools = new LinkedHashSet<>();
		for (Map.Entry<String, Object> entry : stateData.entrySet()) {
			String key = entry.getKey();
			if (!StringUtils.hasText(key)
					|| !key.startsWith(DRAFT_STATE_PREFIX)
					|| !key.endsWith(DRAFT_STATUS_SUFFIX)) {
				continue;
			}
			String status = entry.getValue() != null ? String.valueOf(entry.getValue()) : null;
			if (!ACTIVE_DRAFT_STATUSES.contains(status)) {
				continue;
			}
			String toolName = key.substring(DRAFT_STATE_PREFIX.length(), key.length() - DRAFT_STATUS_SUFFIX.length());
			if (StringUtils.hasText(toolName)) {
				tools.add(toolName);
			}
		}
		return tools;
	}

	private String resolveStatusFromState(Map<String, Object> stateData, String toolName, String defaultStatus) {
		if (!StringUtils.hasText(toolName) || stateData == null || stateData.isEmpty()) {
			return defaultStatus;
		}
		Object statusObj = stateData.get(statusKey(toolName));
		if (statusObj == null) {
			return defaultStatus;
		}
		return String.valueOf(statusObj);
	}

	@SuppressWarnings("unchecked")
	private ToolSnapshot extractLatestToolSnapshot(OverAllState state, Set<String> activeTools) {
		Optional<Object> messagesOpt = state.value("messages");
		if (messagesOpt == null || messagesOpt.isEmpty() || !(messagesOpt.get() instanceof List<?> rawMessages)) {
			return null;
		}
		for (int i = rawMessages.size() - 1; i >= 0; i--) {
			Object msg = rawMessages.get(i);
			if (!(msg instanceof ToolResponseMessage toolResponseMessage)
					|| toolResponseMessage.getResponses() == null) {
				continue;
			}
			for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
				if (response == null || !StringUtils.hasText(response.responseData())) {
					continue;
				}
				try {
					JSONObject jsonObject = JSON.parseObject(response.responseData());
					String status = jsonObject.getString("status");
					if (!ACTIVE_DRAFT_STATUSES.contains(status)) {
						continue;
					}
					String toolName = resolveToolName(response, jsonObject);
					if (!StringUtils.hasText(toolName)) {
						continue;
					}
					if (activeTools != null && !activeTools.isEmpty() && !activeTools.contains(toolName)) {
						continue;
					}
					Map<String, Object> collectedSlots = toSlotMap(jsonObject.get("collected_slots"));
					if (collectedSlots.isEmpty()) {
						collectedSlots = toSlotMap(jsonObject.get("preview"));
					}
					List<String> missingFields = toStringList(jsonObject.get("missing_fields"));
					Map<String, String> fieldLabels = toStringMap(jsonObject.get("missing_field_labels"));
					return new ToolSnapshot(toolName, status, collectedSlots, missingFields, fieldLabels);
				}
				catch (Exception ignore) {
					continue;
				}
			}
		}
		return null;
	}

	private String resolveToolName(ToolResponseMessage.ToolResponse response, JSONObject jsonObject) {
		if (jsonObject != null && StringUtils.hasText(jsonObject.getString("tool_name"))) {
			return jsonObject.getString("tool_name");
		}
		if (response != null && StringUtils.hasText(response.name())) {
			return response.name();
		}
		return null;
	}

	private Map<String, Object> loadDraftSlotsFromState(Map<String, Object> stateData, String toolName) {
		if (!StringUtils.hasText(toolName) || stateData == null || stateData.isEmpty()) {
			return Map.of();
		}
		return toSlotMap(stateData.get(draftKey(toolName)));
	}

	private String draftKey(String toolName) {
		return DRAFT_STATE_PREFIX + toolName;
	}

	private String statusKey(String toolName) {
		return draftKey(toolName) + DRAFT_STATUS_SUFFIX;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> toSlotMap(Object value) {
		if (!(value instanceof Map<?, ?> source)) {
			return Map.of();
		}
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : source.entrySet()) {
			if (entry.getKey() == null || isEmptyValue(entry.getValue())) {
				continue;
			}
			result.put(String.valueOf(entry.getKey()), entry.getValue());
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private String extractUserInput(OverAllState state) {
		String input = state.value("input", String.class).orElse(null);
		if (StringUtils.hasText(input)) {
			return input;
		}

		Optional<Object> messagesOpt = state.value("messages");
		if (messagesOpt == null || messagesOpt.isEmpty() || !(messagesOpt.get() instanceof List<?> messages)) {
			return null;
		}
		for (int i = messages.size() - 1; i >= 0; i--) {
			Object msg = messages.get(i);
			if (msg instanceof UserMessage userMessage) {
				return userMessage.getText();
			}
			if (msg instanceof Message message && StringUtils.hasText(message.getText())
					&& "USER".equalsIgnoreCase(String.valueOf(message.getMessageType()))) {
				return message.getText();
			}
		}
		return null;
	}

	private Map<String, Object> extractToolArguments(String userInput, ActiveDraftContext activeDraft) {
		Map<String, Object> args = new LinkedHashMap<>();
		if (!StringUtils.hasText(userInput)) {
			return args;
		}
		if (chatModel == null) {
			log.warn("WorkReportDraftResumeReactHook#extractToolArguments - reason=chatModel unavailable, skip llm extraction");
			return args;
		}

		try {
			Map<String, Object> llmArgs = extractToolArgumentsByLlm(userInput.trim(), activeDraft);
			args.putAll(llmArgs);
			log.debug("WorkReportDraftResumeReactHook#extractToolArguments - reason=llm extraction completed, keys={}",
					llmArgs.keySet());
		}
		catch (Exception e) {
			log.warn("WorkReportDraftResumeReactHook#extractToolArguments - reason=llm extraction failed", e);
		}
		return args;
	}

	private Map<String, Object> extractToolArgumentsByLlm(String userInput, ActiveDraftContext activeDraft) {
		Set<String> candidateSlots = new LinkedHashSet<>();
		if (activeDraft != null) {
			candidateSlots.addAll(activeDraft.missingFields());
			candidateSlots.addAll(activeDraft.collectedSlots().keySet());
			candidateSlots.addAll(activeDraft.fieldLabels().keySet());
		}

		String systemPrompt = buildSlotExtractPrompt(activeDraft, candidateSlots);
		String userPrompt = """
				用户输入：
				%s
				
				请仅输出 JSON 对象。
				""".formatted(userInput);
		Prompt prompt = new Prompt(List.of(
				new SystemMessage(systemPrompt),
				new UserMessage(userPrompt)
		));

		ChatResponse response = chatModel.call(prompt);
		if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
			return Map.of();
		}

		String output = response.getResult().getOutput().getText();
		JSONObject jsonObject = parseJsonObject(output);
		if (jsonObject == null || jsonObject.isEmpty()) {
			return Map.of();
		}
		return filterExtractedArguments(jsonObject, candidateSlots);
	}

	private String buildSlotExtractPrompt(ActiveDraftContext activeDraft, Set<String> candidateSlots) {
		StringBuilder sb = new StringBuilder(SLOT_EXTRACT_BASE_PROMPT);
		if (activeDraft != null && StringUtils.hasText(activeDraft.toolName())) {
			sb.append("\n当前能力工具: ").append(activeDraft.toolName()).append("\n");
		}
		if (candidateSlots == null || candidateSlots.isEmpty()) {
			sb.append("\n未提供候选字段，请仅在输入中能明确识别字段名和值时提取。\n");
			return sb.toString();
		}

		sb.append("\n候选字段（优先按这些字段输出）：\n");
		for (String key : candidateSlots) {
			String label = activeDraft != null ? activeDraft.fieldLabels().get(key) : null;
			if (StringUtils.hasText(label)) {
				sb.append("- ").append(key).append(": ").append(label).append("\n");
			}
			else {
				sb.append("- ").append(key).append("\n");
			}
		}
		sb.append("输出时字段名必须使用候选字段中的 key。");
		return sb.toString();
	}

	private Map<String, Object> filterExtractedArguments(JSONObject jsonObject, Set<String> candidateSlots) {
		if (jsonObject == null || jsonObject.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> args = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
			String key = entry.getKey();
			if (!StringUtils.hasText(key)) {
				continue;
			}
			if (candidateSlots != null && !candidateSlots.isEmpty() && !candidateSlots.contains(key)) {
				continue;
			}
			Object normalized = normalizeSlotValue(entry.getValue());
			if (!isEmptyValue(normalized)) {
				args.put(key, normalized);
			}
		}
		return args;
	}

	private Object normalizeSlotValue(Object value) {
		if (value instanceof String str) {
			String trimmed = str.trim();
			return StringUtils.hasText(trimmed) ? trimmed : null;
		}
		return value;
	}

	private JSONObject parseJsonObject(String output) {
		if (!StringUtils.hasText(output)) {
			return null;
		}
		String cleaned = trimMarkdownCodeFence(output.trim());
		JSONObject parsed = parseJsonObjectSafely(cleaned);
		if (parsed != null) {
			return parsed;
		}

		int objectStart = cleaned.indexOf('{');
		int objectEnd = cleaned.lastIndexOf('}');
		if (objectStart >= 0 && objectEnd > objectStart) {
			return parseJsonObjectSafely(cleaned.substring(objectStart, objectEnd + 1));
		}
		return null;
	}

	private JSONObject parseJsonObjectSafely(String text) {
		try {
			return JSON.parseObject(text);
		}
		catch (Exception ignore) {
			return null;
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

	private boolean isConfirmText(String text) {
		if (!StringUtils.hasText(text)) {
			return false;
		}
		String normalized = removeWhitespace(text).toLowerCase();
		return Set.of("确认", "确认提交", "提交", "是", "yes", "ok", "好的").contains(normalized);
	}

	private String removeWhitespace(String text) {
		if (!StringUtils.hasText(text)) {
			return "";
		}
		StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (!Character.isWhitespace(ch)) {
				sb.append(ch);
			}
		}
		return sb.toString();
	}

	private boolean isEmptyValue(Object value) {
		if (value == null) {
			return true;
		}
		if (value instanceof String str) {
			return !StringUtils.hasText(str);
		}
		return false;
	}

	private List<String> toStringList(Object value) {
		if (!(value instanceof List<?> list) || list.isEmpty()) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		for (Object item : list) {
			if (item == null) {
				continue;
			}
			String text = String.valueOf(item).trim();
			if (StringUtils.hasText(text)) {
				result.add(text);
			}
		}
		return result;
	}

	private Map<String, String> toStringMap(Object value) {
		if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
			return Map.of();
		}
		Map<String, String> result = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			String key = String.valueOf(entry.getKey()).trim();
			String text = String.valueOf(entry.getValue()).trim();
			if (StringUtils.hasText(key) && StringUtils.hasText(text)) {
				result.put(key, text);
			}
		}
		return result;
	}

	private record ActiveDraftContext(
			String toolName,
			String status,
			Map<String, Object> collectedSlots,
			List<String> missingFields,
			Map<String, String> fieldLabels) {
	}

	private record ToolSnapshot(
			String toolName,
			String status,
			Map<String, Object> collectedSlots,
			List<String> missingFields,
			Map<String, String> fieldLabels) {
	}
}
