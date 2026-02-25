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
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 清理 FastIntent 写入的 jump_to 路由状态，并支持能力工具结果直出。
 */
@HookPhases(AgentPhase.REACT)
@HookPositions(HookPosition.BEFORE_MODEL)
public class FastIntentJumpCleanupModelHook extends ModelHook {

	private static final Logger log = LoggerFactory.getLogger(FastIntentJumpCleanupModelHook.class);
	private static final Set<String> CAPABILITY_TERMINAL_STATUSES = Set.of(
			"SLOT_MISSING", "WAIT_CONFIRM", "SUBMITTED", "SUBMIT_FAILED");

	@Override
	public String getName() {
		return "FastIntentJumpCleanupModelHook";
	}

	@Override
	public List<JumpTo> canJumpTo() {
		return List.of(JumpTo.end);
	}

	@Override
	public Map<String, KeyStrategy> getKeyStrategys() {
		return Map.of("jump_to", new ReplaceStrategy());
	}

	@Override
	@SuppressWarnings("unchecked")
	public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
		if (state == null) {
			return CompletableFuture.completedFuture(Map.of());
		}

		Map<String, Object> directCapabilityReply = buildDirectCapabilityReply(state);
		if (!directCapabilityReply.isEmpty()) {
			return CompletableFuture.completedFuture(directCapabilityReply);
		}

		Optional<Object> fastIntentOpt = state.value("fast_intent");
		if (fastIntentOpt == null) {
			fastIntentOpt = Optional.empty();
		}
		if (fastIntentOpt.isEmpty() || !(fastIntentOpt.get() instanceof Map<?, ?> fastIntentMap)) {
			return CompletableFuture.completedFuture(Map.of());
		}
		Object hitValue = fastIntentMap.get("hit");
		if (!(hitValue instanceof Boolean) || !Boolean.TRUE.equals(hitValue)) {
			return CompletableFuture.completedFuture(Map.of());
		}

		Optional<Object> jumpToOpt = state.value("jump_to");
		if (jumpToOpt == null) {
			jumpToOpt = Optional.empty();
		}
		if (jumpToOpt.isEmpty()) {
			return CompletableFuture.completedFuture(Map.of());
		}

		JumpTo jumpTo = resolveJumpTo(jumpToOpt.get());
		if (jumpTo != JumpTo.tool) {
			return CompletableFuture.completedFuture(Map.of());
		}

		Map<String, Object> updates = new HashMap<>();
		updates.put("jump_to", null);
		log.debug("FastIntentJumpCleanupModelHook#beforeModel - reason=清理 FastIntent 残留 jump_to=tool");
		return CompletableFuture.completedFuture(updates);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> buildDirectCapabilityReply(OverAllState state) {
		Optional<Object> messagesOpt = state.value("messages");
		if (messagesOpt == null) {
			messagesOpt = Optional.empty();
		}
		if (messagesOpt.isEmpty() || !(messagesOpt.get() instanceof List<?> rawMessages) || rawMessages.isEmpty()) {
			return Map.of();
		}

		Object last = rawMessages.get(rawMessages.size() - 1);
		if (!(last instanceof ToolResponseMessage toolResponseMessage)) {
			return Map.of();
		}
		if (toolResponseMessage.getResponses() == null || toolResponseMessage.getResponses().isEmpty()) {
			return Map.of();
		}

		for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
			if (response == null || !StringUtils.hasText(response.responseData())) {
				continue;
			}

			JSONObject obj;
			try {
				obj = JSON.parseObject(response.responseData());
			}
			catch (Exception ex) {
				log.warn("FastIntentJumpCleanupModelHook#buildDirectCapabilityReply - reason=解析工具返回失败", ex);
				continue;
			}

			String status = obj.getString("status");
			if (!CAPABILITY_TERMINAL_STATUSES.contains(status)) {
				continue;
			}
			if (!isCapabilityLifecyclePayload(obj)) {
				continue;
			}

			String message = obj.getString("message");
			if (!StringUtils.hasText(message)) {
				continue;
			}

			String toolName = resolveToolName(response, obj);
			AssistantMessage assistantMessage = AssistantMessage.builder().content(message).build();
			Map<String, Object> updates = new HashMap<>();
			updates.put("messages", List.of(assistantMessage));
			updates.put("jump_to", JumpTo.end);
			log.info("FastIntentJumpCleanupModelHook#buildDirectCapabilityReply - reason=能力工具返回直出, toolName={}, status={}",
					toolName, status);
			return updates;
		}

		return Map.of();
	}

	private boolean isCapabilityLifecyclePayload(JSONObject obj) {
		if (obj == null || obj.isEmpty()) {
			return false;
		}
		return obj.containsKey("tool_name")
				|| obj.containsKey("missing_fields")
				|| obj.containsKey("confirmation_required")
				|| obj.containsKey("preview")
				|| obj.containsKey("collected_slots")
				|| obj.containsKey("success")
				|| obj.containsKey("http_status");
	}

	private String resolveToolName(ToolResponseMessage.ToolResponse response, JSONObject obj) {
		if (obj != null && StringUtils.hasText(obj.getString("tool_name"))) {
			return obj.getString("tool_name");
		}
		if (response != null && StringUtils.hasText(response.name())) {
			return response.name();
		}
		return "unknown";
	}

	private JumpTo resolveJumpTo(Object value) {
		if (value instanceof JumpTo jumpTo) {
			return jumpTo;
		}
		if (value instanceof String str) {
			return JumpTo.fromStringOrNull(str);
		}
		return null;
	}
}
