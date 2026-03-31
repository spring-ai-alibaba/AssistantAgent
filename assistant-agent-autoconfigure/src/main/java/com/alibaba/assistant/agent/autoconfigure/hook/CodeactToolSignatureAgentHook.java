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
package com.alibaba.assistant.agent.autoconfigure.hook;

import com.alibaba.assistant.agent.common.constant.HookPriorityConstants;
import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.core.tool.CodeactToolRegistry;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * CodeactTool 签名注入 Hook
 *
 * <p>在 BEFORE_AGENT 阶段执行，将 {@link CodeactToolRegistry} 中所有注册工具的
 * Python 签名（class stub / function stub）注入到 messages 中，使 React 阶段的
 * LLM 在调用 {@code write_code} 编写 Python 代码时，能够正确地使用这些 CodeactTool。
 *
 * <p> React 阶段的 LLM 需要同时具备工具选择和代码编写的能力，因此需要将工具的完整签名信息提前注入到 Prompt 中。
 *
 * <p>注入方式采用 {@link AssistantMessage} + {@link ToolResponseMessage} 配对，
 * 与 {@link CodeactToolsStateInitHook} 类似，由 {@code CodeactAgent.Builder} 自动注册。
 *
 * <p>优先级 {@link HookPriorityConstants#CODEACT_TOOL_SIGNATURE_HOOK}（8），
 * 在 {@link CodeactToolsStateInitHook}（5）之后、经验注入（20）之前执行。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 * @see CodeactToolsStateInitHook
 */
@HookPositions(HookPosition.BEFORE_AGENT)
public class CodeactToolSignatureAgentHook extends AgentHook {

	private static final Logger log = LoggerFactory.getLogger(CodeactToolSignatureAgentHook.class);

	private static final String INJECTION_TOOL_NAME = "codeact_tool_signature_injection";

	private final CodeactToolRegistry codeactToolRegistry;

	private final Language language;

	public CodeactToolSignatureAgentHook(CodeactToolRegistry codeactToolRegistry, Language language) {
		this.codeactToolRegistry = codeactToolRegistry;
		this.language = language;
	}

	@Override
	public String getName() {
		return "CodeactToolSignatureAgentHook";
	}

	@Override
	public int getOrder() {
		return HookPriorityConstants.CODEACT_TOOL_SIGNATURE_HOOK;
	}

	@Override
	public List<JumpTo> canJumpTo() {
		return List.of();
	}

	@Override
	@SuppressWarnings("unchecked")
	public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
		log.info("CodeactToolSignatureAgentHook#beforeAgent - reason=开始注入CodeactTool签名到Prompt");

		try {
			if (codeactToolRegistry == null || codeactToolRegistry.getAllTools().isEmpty()) {
				log.info("CodeactToolSignatureAgentHook#beforeAgent - reason=无已注册的CodeactTool，跳过签名注入");
				return CompletableFuture.completedFuture(Map.of());
			}

			// 检查是否已经注入过（防止多轮对话重复注入）
			Optional<Object> messagesOpt = state.value("messages");
			if (messagesOpt.isPresent()) {
				List<Message> messages = (List<Message>) messagesOpt.get();
				for (Message msg : messages) {
					if (msg instanceof ToolResponseMessage toolMsg) {
						for (ToolResponseMessage.ToolResponse response : toolMsg.getResponses()) {
							if (INJECTION_TOOL_NAME.equals(response.name())) {
								log.info("CodeactToolSignatureAgentHook#beforeAgent - reason=检测到已注入工具签名，跳过");
								return CompletableFuture.completedFuture(Map.of());
							}
						}
					}
				}
			}

			String structuredPrompt = codeactToolRegistry.generateStructuredToolPrompt(language);
			if (structuredPrompt == null || structuredPrompt.isBlank()) {
				log.info("CodeactToolSignatureAgentHook#beforeAgent - reason=生成的工具签名为空，跳过");
				return CompletableFuture.completedFuture(Map.of());
			}

			String content = buildInjectionContent(structuredPrompt);

			String toolCallId = "tool_sig_" + UUID.randomUUID().toString().substring(0, 8);

			AssistantMessage assistantMessage = AssistantMessage.builder()
				.toolCalls(List.of(new AssistantMessage.ToolCall(toolCallId, "function", INJECTION_TOOL_NAME, "{}")))
				.build();

			ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
				.responses(
						List.of(new ToolResponseMessage.ToolResponse(toolCallId, INJECTION_TOOL_NAME, content)))
				.build();

			log.info(
					"CodeactToolSignatureAgentHook#beforeAgent - reason=工具签名注入成功, toolCount={}, contentLength={}",
					codeactToolRegistry.getAllTools().size(), content.length());

			return CompletableFuture.completedFuture(Map.of("messages", List.of(assistantMessage, toolResponseMessage)));

		}
		catch (Exception e) {
			log.error("CodeactToolSignatureAgentHook#beforeAgent - reason=注入工具签名失败", e);
			return CompletableFuture.completedFuture(Map.of());
		}
	}

	private String buildInjectionContent(String structuredToolPrompt) {
		StringBuilder sb = new StringBuilder();

		sb.append("=== CodeactTool Definitions ===\n\n");
		sb.append("The following tool definitions are ONLY available inside the `code` parameter of `write_code`.\n");
		sb.append("Do NOT call them as React-phase function calls. ");
		sb.append("Use them in your Python code via `instance.method(args)` or as global functions.\n\n");

		sb.append(structuredToolPrompt);

		sb.append("\n=== Usage Guidelines ===\n");
		sb.append("- Write Python code calling these tools inside `write_code`, then run it with `execute_code`.\n");
		sb.append("- Use `try/except` to handle errors and return a result dict.\n");
		sb.append("- If the return type is unspecified (Any), avoid accessing fields directly; ");
		sb.append("use `llm_tools.call_llm(source_data=result, ...)` to extract data if available.\n");

		return sb.toString();
	}

}
