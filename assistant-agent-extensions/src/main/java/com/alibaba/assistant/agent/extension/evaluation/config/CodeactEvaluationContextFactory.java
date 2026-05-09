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
package com.alibaba.assistant.agent.extension.evaluation.config;

import com.alibaba.assistant.agent.extension.evaluation.model.CodeactEvaluationTag;
import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Codeact Evaluation Context 工厂类
 * 负责将 Codeact 的 OverAllState、模型请求/响应、工具上下文等信息，
 * 转换为统一的 EvaluationContext
 *
 * @author Assistant Agent Team
 */
public class CodeactEvaluationContextFactory {

	private static final Logger log = LoggerFactory.getLogger(CodeactEvaluationContextFactory.class);

	/**
	 * 构造输入路由评估的上下文
	 *
	 * @param state Agent 状态
	 * @param config 运行配置
	 * @return EvaluationContext
	 */
	@SuppressWarnings("unchecked")
	public EvaluationContext createInputRoutingContext(OverAllState state, RunnableConfig config) {
		log.debug("CodeactEvaluationContextFactory#createInputRoutingContext - reason=开始构造输入路由评估上下文");

		Map<String, Object> input = new HashMap<>();
		Map<String, Object> executionResult = new HashMap<>();

		// 提取用户输入 - 从 state 的 messages key 中获取
		// 注意：由于其他 Hook（如 ExperiencePrefetchHook）可能已经向 messages 中注入了
		// AssistantMessage 和 ToolResponseMessage，这里需要遍历查找最后一条 UserMessage
		Optional<List<Message>> messagesOpt = state.value("messages");
		if (messagesOpt.isPresent()) {
			List<Message> messages = messagesOpt.get();
			if (!messages.isEmpty()) {
				// 遍历查找最后一条 UserMessage
				String userInput = findLastUserInput(messages);
				if (userInput != null) {
					input.put(CodeactEvaluationTag.INPUT_USER_INPUT, userInput);
					log.debug("CodeactEvaluationContextFactory#createInputRoutingContext - reason=找到用户输入, length={}", userInput.length());
				} else {
					log.warn("CodeactEvaluationContextFactory#createInputRoutingContext - reason=未找到UserMessage, messagesSize={}", messages.size());
				}

				// 会话历史
				input.put(CodeactEvaluationTag.INPUT_CONVERSATION_HISTORY, messages);
			}
		}

		// 提取 Agent 名称（从 config 或 state 中）
		String agentName = state.value("agentName", String.class).orElse(null);
		if (agentName != null) {
			input.put(CodeactEvaluationTag.INPUT_AGENT_NAME, agentName);
		}

		// 可选：检查是否有知识检索结果
		Optional<Object> knowledgeHitsOpt = state.value("knowledgeSearchHits");
		knowledgeHitsOpt.ifPresent(hits ->
			executionResult.put(CodeactEvaluationTag.EXEC_KNOWLEDGE_SEARCH_HITS, hits));

		// 注入外部参数（如果存在）
		Optional<Map<String, Object>> externalParamsOpt = state.value("evaluationExternalParams");
		externalParamsOpt.ifPresent(params -> input.putAll(params));

		return new EvaluationContext(input, executionResult);
	}

	/**
	 * 构造模型输出评估的上下文
	 *
	 * @param state Agent 状态
	 * @param modelOutput 当前轮的模型输出内容
	 * @return EvaluationContext
	 */
	@SuppressWarnings("unchecked")
	public EvaluationContext createModelOutputContext(OverAllState state, String modelOutput) {
		log.debug("CodeactEvaluationContextFactory#createModelOutputContext - reason=开始构造模型输出评估上下文");

		Map<String, Object> input = new HashMap<>();
		Map<String, Object> executionResult = new HashMap<>();

		// 提取用户输入与会话历史
		Optional<List<Message>> messagesOpt = state.value("messages");
		if (messagesOpt.isPresent()) {
			List<Message> messages = messagesOpt.get();
			if (!messages.isEmpty()) {
				// 遍历查找最后一条 UserMessage
				String userInput = findLastUserInput(messages);
				if (userInput != null) {
					input.put(CodeactEvaluationTag.INPUT_USER_INPUT, userInput);
				}
				input.put(CodeactEvaluationTag.INPUT_CONVERSATION_HISTORY, messages);
			}
		}

		// 当前模型输出
		executionResult.put(CodeactEvaluationTag.EXEC_MODEL_OUTPUT, modelOutput);

		// 注入外部参数（如果存在）
		Optional<Map<String, Object>> externalParamsOpt = state.value("evaluationExternalParams");
		externalParamsOpt.ifPresent(params -> input.putAll(params));

		return new EvaluationContext(input, executionResult);
	}

	/**
	 * 构造代码生成输入评估的上下文（新增）
	 *
	 * @param state Agent 状态
	 * @param codeTaskDescription 代码任务描述
	 * @param targetLanguage 目标语言
	 * @param environmentConstraints 环境约束
	 * @return EvaluationContext
	 */
	@SuppressWarnings("unchecked")
	public EvaluationContext createCodeGenerationInputContext(
			OverAllState state,
			String codeTaskDescription,
			String targetLanguage,
			Map<String, Object> environmentConstraints) {

		log.debug("CodeactEvaluationContextFactory#createCodeGenerationInputContext - reason=开始构造代码生成输入评估上下文");

		Map<String, Object> input = new HashMap<>();
		Map<String, Object> executionResult = new HashMap<>();

		// 代码生成任务相关参数
		input.put(CodeactEvaluationTag.INPUT_CODE_TASK_DESCRIPTION, codeTaskDescription);
		input.put(CodeactEvaluationTag.INPUT_TARGET_LANGUAGE, targetLanguage);
		input.put(CodeactEvaluationTag.INPUT_ENVIRONMENT_CONSTRAINTS, environmentConstraints);

		// 会话上下文
		Optional<List<Message>> messagesOpt = state.value("messages");
		messagesOpt.ifPresent(messages ->
			input.put(CodeactEvaluationTag.INPUT_CONVERSATION_HISTORY, messages));

		// 注入外部参数（如果存在）
		Optional<Map<String, Object>> externalParamsOpt = state.value("evaluationExternalParams");
		externalParamsOpt.ifPresent(params -> input.putAll(params));

		return new EvaluationContext(input, executionResult);
	}

	/**
	 * 构造代码执行评估的上下文
	 *
	 * @param state Agent 状态
	 * @param codeSnippet 待执行代码
	 * @param execResult 执行结果（可为 null，表示执行前评估）
	 * @param execError 执行异常（可为 null）
	 * @param durationMs 执行时长（可为 null）
	 * @return EvaluationContext
	 */
	@SuppressWarnings("unchecked")
	public EvaluationContext createCodeExecutionContext(
			OverAllState state,
			String codeSnippet,
			Object execResult,
			Throwable execError,
			Long durationMs) {

		log.debug("CodeactEvaluationContextFactory#createCodeExecutionContext - reason=开始构造代码执行评估上下文");

		Map<String, Object> input = new HashMap<>();
		Map<String, Object> executionResult = new HashMap<>();

		// 会话上下文
		Optional<List<Message>> messagesOpt = state.value("messages");
		messagesOpt.ifPresent(messages ->
			input.put(CodeactEvaluationTag.INPUT_CONVERSATION_HISTORY, messages));

		// 代码内容
		executionResult.put(CodeactEvaluationTag.EXEC_CODE_SNIPPET, codeSnippet);

		// 执行结果（执行后才有）
		if (execResult != null) {
			executionResult.put(CodeactEvaluationTag.EXEC_CODE_EXEC_RESULT, execResult);
		}

		// 执行异常
		if (execError != null) {
			executionResult.put(CodeactEvaluationTag.EXEC_CODE_EXEC_ERROR, execError.toString());
		}

		// 执行时长
		if (durationMs != null) {
			executionResult.put(CodeactEvaluationTag.EXEC_CODE_EXEC_DURATION_MS, durationMs);
		}

		// 注入外部参数（如果存在）
		Optional<Map<String, Object>> externalParamsOpt = state.value("evaluationExternalParams");
		externalParamsOpt.ifPresent(params -> input.putAll(params));

		return new EvaluationContext(input, executionResult);
	}

	/**
	 * 构造会话总结评估的上下文
	 *
	 * @param state Agent 状态
	 * @param conversationTrace 完整对话轨迹
	 * @param toolTrace 工具调用轨迹
	 * @return EvaluationContext
	 */
	@SuppressWarnings("unchecked")
	public EvaluationContext createSessionSummaryContext(
			OverAllState state,
			Object conversationTrace,
			Object toolTrace) {

		log.debug("CodeactEvaluationContextFactory#createSessionSummaryContext - reason=开始构造会话总结评估上下文");

		Map<String, Object> input = new HashMap<>();
		Map<String, Object> executionResult = new HashMap<>();

		// 会话历史
		Optional<List<Message>> messagesOpt = state.value("messages");
		messagesOpt.ifPresent(messages ->
			input.put(CodeactEvaluationTag.INPUT_CONVERSATION_HISTORY, messages));

		// 完整轨迹
		if (conversationTrace != null) {
			executionResult.put(CodeactEvaluationTag.EXEC_CONVERSATION_TRACE, conversationTrace);
		}

		if (toolTrace != null) {
			executionResult.put(CodeactEvaluationTag.EXEC_TOOL_EXECUTION_TRACE, toolTrace);
		}

		// 注入外部参数（如果存在）
		Optional<Map<String, Object>> externalParamsOpt = state.value("evaluationExternalParams");
		externalParamsOpt.ifPresent(params -> input.putAll(params));

		return new EvaluationContext(input, executionResult);
	}

	/**
	 * 从消息列表中查找最后一条 UserMessage 的内容
	 *
	 * <p>由于其他 Hook（如 ExperiencePrefetchHook）可能在 beforeAgent 阶段向 messages 中
	 * 注入 AssistantMessage 和 ToolResponseMessage，直接取最后一条消息可能不是用户输入。
	 * 此方法从后往前遍历，找到最后一条 UserMessage。
	 *
	 * @param messages 消息列表
	 * @return 最后一条 UserMessage 的文本内容，如果没有找到则返回 null
	 */
	private String findLastUserInput(List<Message> messages) {
		for (int i = messages.size() - 1; i >= 0; i--) {
			Message message = messages.get(i);
			if (message instanceof UserMessage) {
				return message.getText();
			}
		}
		return null;
	}
}

