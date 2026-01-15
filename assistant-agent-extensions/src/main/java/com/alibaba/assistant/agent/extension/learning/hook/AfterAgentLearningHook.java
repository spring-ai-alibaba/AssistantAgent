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

package com.alibaba.assistant.agent.extension.learning.hook;

import com.alibaba.assistant.agent.common.hook.AgentPhase;
import com.alibaba.assistant.agent.common.hook.HookPhases;
import com.alibaba.assistant.agent.extension.learning.internal.DefaultLearningContext;
import com.alibaba.assistant.agent.extension.learning.internal.DefaultLearningTask;
import com.alibaba.assistant.agent.extension.learning.model.LearningContext;
import com.alibaba.assistant.agent.extension.learning.model.LearningResult;
import com.alibaba.assistant.agent.extension.learning.model.LearningTask;
import com.alibaba.assistant.agent.extension.learning.model.LearningTriggerContext;
import com.alibaba.assistant.agent.extension.learning.model.LearningTriggerSource;
import com.alibaba.assistant.agent.extension.learning.spi.LearningExecutor;
import com.alibaba.assistant.agent.extension.learning.spi.LearningStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Agent执行完成后学习Hook
 * 在Agent执行完成后触发学习，收集完整的执行信息
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@HookPhases(AgentPhase.REACT)
@HookPositions(HookPosition.AFTER_AGENT)
public class AfterAgentLearningHook extends AgentHook {

	private static final Logger log = LoggerFactory.getLogger(AfterAgentLearningHook.class);

	private final LearningExecutor learningExecutor;

	private final LearningStrategy learningStrategy;

	private final String learningType;

	public AfterAgentLearningHook(LearningExecutor learningExecutor, LearningStrategy learningStrategy,
			String learningType) {
		this.learningExecutor = learningExecutor;
		this.learningStrategy = learningStrategy;
		this.learningType = learningType != null ? learningType : "experience";
	}

	@Override
	public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
		try {
			log.info("AfterAgentLearningHook#afterAgent - reason=agent execution completed, starting learning process");

			// 1. 从state中提取对话历史
			List<Object> conversationHistory = extractConversationHistory(state);

			// 2. 构建学习上下文
			LearningContext context = DefaultLearningContext.builder()
				.overAllState(state)
				.conversationHistory(conversationHistory)
				.toolCallRecords(new ArrayList<>()) // TODO: 从state中提取工具调用记录
				.modelCallRecords(new ArrayList<>()) // TODO: 从state中提取模型调用记录
				.triggerSource(LearningTriggerSource.AFTER_AGENT)
				.build();

			// 3. 构建触发上下文
			LearningTriggerContext triggerContext = LearningTriggerContext.builder()
				.source(LearningTriggerSource.AFTER_AGENT)
				.context(context)
				.build();

			// 4. 判断是否应该触发学习
			if (!learningStrategy.shouldTriggerLearning(triggerContext)) {
				log.info("AfterAgentLearningHook#afterAgent - reason=strategy decided not to trigger learning");
				return CompletableFuture.completedFuture(Map.of());
			}

			// 4. 构建学习任务
			LearningTask task = DefaultLearningTask.builder()
				.learningType(learningType)
				.triggerSource(LearningTriggerSource.AFTER_AGENT)
				.context(context)
				.build();

			// 5. 执行学习（异步或同步）
			if (learningStrategy.shouldExecuteAsync(task)) {
				log.info(
						"AfterAgentLearningHook#afterAgent - reason=executing learning asynchronously, taskId={}",
						task.getId());
				learningExecutor.executeAsync(task).exceptionally(ex -> {
					log.error(
							"AfterAgentLearningHook#afterAgent - reason=async learning execution failed, taskId={}",
							task.getId(), ex);
					return null;
				});
			}
			else {
				log.debug("AfterAgentLearningHook#afterAgent - reason=executing learning synchronously, taskId={}",
						task.getId());
				LearningResult result = learningExecutor.execute(task);
				if (!result.isSuccess()) {
					log.warn(
							"AfterAgentLearningHook#afterAgent - reason=learning execution failed, taskId={}, failureReason={}",
							task.getId(), result.getFailureReason());
				}
			}

		}
		catch (Exception e) {
			// 学习失败不影响主流程
			log.error("AfterAgentLearningHook#afterAgent - reason=learning hook failed", e);
		}

		return CompletableFuture.completedFuture(Map.of());
	}

	@Override
	public String getName() {
		return "AfterAgentLearningHook";
	}

	/**
	 * 从OverAllState中提取对话历史
	 * @param state Agent执行状态
	 * @return 对话历史列表
	 */
	private List<Object> extractConversationHistory(OverAllState state) {
		try {
			// 尝试从state中获取messages
			return state.value("messages", List.class).orElse(new ArrayList<>());
		} catch (Exception e) {
			log.warn("AfterAgentLearningHook#extractConversationHistory - reason=failed to extract conversation history", e);
			return new ArrayList<>();
		}
	}

}

