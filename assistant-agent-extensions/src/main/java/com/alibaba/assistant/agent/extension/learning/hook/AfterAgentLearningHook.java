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
import com.alibaba.assistant.agent.common.hook.AgentPhase;
import com.alibaba.assistant.agent.common.hook.HookPhases;
import com.alibaba.assistant.agent.core.observation.HookObservationHelper;
import com.alibaba.assistant.agent.core.observation.ObservationState;
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

			// 注册自定义观测数据到 ObservationState
			registerObservationData(state, "hook.input.learningType", learningType);
			registerObservationData(state, "hook.input.triggerSource", LearningTriggerSource.AFTER_AGENT.name());

			// 1. 从state中提取对话历史
			List<Object> conversationHistory = extractConversationHistory(state);
			registerObservationData(state, "hook.input.conversationHistorySize", conversationHistory.size());

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
				registerObservationData(state, "hook.output.status", "NOT_TRIGGERED");
				registerObservationData(state, "hook.output.triggered", false);
				return CompletableFuture.completedFuture(Map.of());
			}

			// 注册学习触发信息
			registerObservationData(state, "hook.output.triggered", true);

			// 5. 构建学习任务
			LearningTask task = DefaultLearningTask.builder()
				.learningType(learningType)
				.triggerSource(LearningTriggerSource.AFTER_AGENT)
				.context(context)
				.build();

			registerObservationData(state, "hook.output.taskId", task.getId());

			// 6. 执行学习（异步或同步）
			if (learningStrategy.shouldExecuteAsync(task)) {
				log.info(
						"AfterAgentLearningHook#afterAgent - reason=executing learning asynchronously, taskId={}",
						task.getId());
				registerObservationData(state, "hook.output.executionMode", "ASYNC");

				learningExecutor.executeAsync(task).exceptionally(ex -> {
					log.error(
							"AfterAgentLearningHook#afterAgent - reason=async learning execution failed, taskId={}",
							task.getId(), ex);
					return null;
				});
				registerObservationData(state, "hook.output.status", "ASYNC_SUBMITTED");
			}
			else {
				log.debug("AfterAgentLearningHook#afterAgent - reason=executing learning synchronously, taskId={}",
						task.getId());
				registerObservationData(state, "hook.output.executionMode", "SYNC");

				LearningResult result = learningExecutor.execute(task);
				if (!result.isSuccess()) {
					log.warn(
							"AfterAgentLearningHook#afterAgent - reason=learning execution failed, taskId={}, failureReason={}",
							task.getId(), result.getFailureReason());
					registerObservationData(state, "hook.output.status", "FAILED");
					registerObservationData(state, "hook.output.failureReason", truncate(result.getFailureReason(), 200));
				} else {
					registerObservationData(state, "hook.output.status", "SUCCESS");
				}
			}

		}
		catch (Exception e) {
			// 学习失败不影响主流程
			log.error("AfterAgentLearningHook#afterAgent - reason=learning hook failed", e);
			registerObservationData(state, "hook.output.status", "ERROR");
			registerObservationData(state, "hook.output.errorType", e.getClass().getSimpleName());
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

	/**
	 * 注册自定义观测数据到 ObservationState
	 * <p>
	 * 这些数据会被 CodeactAgentObservationLifecycleListener 收集并记录到 Observation 中。
	 *
	 * @param state OverAllState
	 * @param key   数据键，建议使用 "hook." 前缀
	 * @param value 数据值
	 */
	private void registerObservationData(OverAllState state, String key, Object value) {
		try {
			// 尝试获取 ObservationState
			Object obsStateObj = state.value("_observation_state_").orElse(null);
			if (obsStateObj != null) {
				// 使用反射调用 put 方法，避免直接依赖 ObservationState 类
				java.lang.reflect.Method putMethod = obsStateObj.getClass().getMethod("put", String.class, Object.class);
				putMethod.invoke(obsStateObj, key, value);
				log.debug("AfterAgentLearningHook#registerObservationData - reason=注册观测数据成功, key={}", key);
			}
		} catch (Exception e) {
			// 静默失败，不影响主流程
			log.debug("AfterAgentLearningHook#registerObservationData - reason=注册观测数据失败, key={}, error={}", key, e.getMessage());
		}
	}

	/**
	 * 截断字符串
	 */
	private String truncate(String str, int maxLength) {
		if (str == null) {
			return "null";
		}
		if (str.length() <= maxLength) {
			return str;
		}
		return str.substring(0, maxLength) + "...";
	}

}
