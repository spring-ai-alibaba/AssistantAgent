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
package com.alibaba.assistant.agent.autoconfigure.subagent;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.BaseAgent;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * BaseAgentTaskTool - 支持 BaseAgent 的任务工具（对标 TaskTool）
 *
 * <p>与原 TaskTool 的区别：
 * <ul>
 * <li>支持 Map&lt;String, BaseAgent&gt; 而不是 Map&lt;String, ReactAgent&gt;</li>
 * <li>通过 CompiledGraph.invoke() 调用而不是 agent.call()</li>
 * <li>从 OverAllState 中提取结果</li>
 * <li>支持配置 stateKeysToPropagate，将父 agent 的指定 state 传递给子 agent</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class BaseAgentTaskTool implements BiFunction<BaseAgentTaskTool.TaskRequest, ToolContext, String> {

	private static final Logger logger = LoggerFactory.getLogger(BaseAgentTaskTool.class);

	private final Map<String, BaseAgent> subAgents;

	/**
	 * 需要从父 agent 的 OverAllState 传递给子 agent 的 state keys 列表。
	 * <p>
	 * 这是一个通用扩展点，允许业务方配置需要跨 agent 传递的状态。
	 */
	private final List<String> stateKeysToPropagate;

	public BaseAgentTaskTool(Map<String, BaseAgent> subAgents) {
		this(subAgents, Collections.emptyList());
	}

	public BaseAgentTaskTool(Map<String, BaseAgent> subAgents, List<String> stateKeysToPropagate) {
		this.subAgents = subAgents;
		this.stateKeysToPropagate = stateKeysToPropagate != null
				? new ArrayList<>(stateKeysToPropagate)
				: Collections.emptyList();
	}

	@Override
	public String apply(TaskRequest request, ToolContext toolContext) {
		logger.info("BaseAgentTaskTool#apply 调用子Agent: subagentType={}", request.subagentType);

		// 1. Validate subagent type
		if (!subAgents.containsKey(request.subagentType)) {
			String error = "Error: invoked agent of type " + request.subagentType +
					", the only allowed types are " + subAgents.keySet();
			logger.error("BaseAgentTaskTool#apply {}", error);
			return error;
		}

		// 2. Get the subagent
		BaseAgent subAgent = subAgents.get(request.subagentType);

		try {
			// 3. Build inputs from description or structured inputs
			Map<String, Object> inputs;
			if (request.structuredInputs != null && !request.structuredInputs.isEmpty()) {
				inputs = new HashMap<>(request.structuredInputs);
				logger.info("BaseAgentTaskTool#apply 使用结构化输入: {}", inputs.keySet());
			} else {
				inputs = buildInputsFromDescription(request.description);
				logger.info("BaseAgentTaskTool#apply 从描述解析输入: {}", inputs.keySet());
			}

			// 4. Propagate configured state keys from parent agent
			propagateParentStateKeys(toolContext, inputs);

			// 5. Invoke the subagent through CompiledGraph
			CompiledGraph compiledGraph = subAgent.getAndCompileGraph();
			Optional<OverAllState> resultOpt = compiledGraph.invoke(inputs);

			// 6. Extract result from state
			OverAllState resultState = resultOpt.orElseThrow(() ->
					new IllegalStateException("SubAgent returned empty result"));

			// 7. Try to get generated_code or any string result
			String result = resultState.value("generated_code", String.class)
					.orElseGet(() -> extractAnyStringResult(resultState));

			logger.info("BaseAgentTaskTool#apply 子Agent调用成功: subagentType={}", request.subagentType);
			return result;

		} catch (Exception e) {
			String error = "Error executing subagent task: " + e.getMessage();
			logger.error("BaseAgentTaskTool#apply " + error, e);
			return error;
		}
	}

	/**
	 * 从父 agent 的 OverAllState 中提取配置的 keys，传递给子 agent。
	 * <p>
	 * 这是一个通用扩展点，允许业务方将父 agent 的状态（如评估结果）传递给子 agent。
	 *
	 * @param toolContext 工具上下文，包含父 agent 的 OverAllState
	 * @param inputs 子 agent 的输入 Map，将被追加父 agent 的状态
	 */
	private void propagateParentStateKeys(ToolContext toolContext, Map<String, Object> inputs) {
		if (stateKeysToPropagate.isEmpty()) {
			return;
		}

		if (toolContext == null || toolContext.getContext() == null) {
			logger.debug("BaseAgentTaskTool#propagateParentStateKeys - toolContext 为空，跳过状态传递");
			return;
		}

		Object stateObj = toolContext.getContext().get(ToolContextConstants.AGENT_STATE_CONTEXT_KEY);
		if (!(stateObj instanceof OverAllState)) {
			logger.debug("BaseAgentTaskTool#propagateParentStateKeys - 父 agent state 不存在或类型不匹配，跳过状态传递");
			return;
		}

		OverAllState parentState = (OverAllState) stateObj;
		int propagatedCount = 0;

		for (String key : stateKeysToPropagate) {
			Optional<Object> valueOpt = parentState.value(key);
			if (valueOpt.isPresent()) {
				Object value = valueOpt.get();
				inputs.put(key, value);
				propagatedCount++;
				logger.debug("BaseAgentTaskTool#propagateParentStateKeys - 传递状态: key={}, valueType={}",
						key, value.getClass().getSimpleName());
			} else {
				logger.debug("BaseAgentTaskTool#propagateParentStateKeys - 状态 key={} 在父 agent 中不存在，跳过", key);
			}
		}

		if (propagatedCount > 0) {
			logger.info("BaseAgentTaskTool#propagateParentStateKeys - 完成状态传递: propagatedCount={}, keys={}",
					propagatedCount, stateKeysToPropagate);
		}
	}

	/**
	 * 从任务描述构建输入参数
	 * 尝试解析描述中的结构化信息
	 */
	private Map<String, Object> buildInputsFromDescription(String description) {
		Map<String, Object> inputs = new HashMap<>();

		// 简单的解析逻辑：查找常见的键值对模式
		String[] lines = description.split("\n");
		for (String line : lines) {
			if (line.contains(":")) {
				String[] parts = line.split(":", 2);
				if (parts.length == 2) {
					String key = parts[0].trim().toLowerCase().replace(" ", "_");
					String value = parts[1].trim();
					inputs.put(key, value);
				}
			}
		}

		// 如果没有解析出任何内容，将整个描述作为 requirement
		if (inputs.isEmpty()) {
			inputs.put("requirement", description);
		}

		return inputs;
	}

	/**
	 * 从 OverAllState 提取任何字符串类型的结果
	 */
	private String extractAnyStringResult(OverAllState state) {
		// 尝试常见的输出键
		return state.value("output", String.class)
				.or(() -> state.value("result", String.class))
				.or(() -> state.value("message", String.class))
				.orElse("SubAgent completed successfully");
	}

	/**
	 * Request structure for the task tool (对标 TaskTool.TaskRequest)
	 */
	public static class TaskRequest {

		@JsonProperty(required = true)
		@JsonPropertyDescription("Detailed description of the task to be performed by the subagent")
		public String description;

		@JsonProperty(required = true, value = "subagent_type")
		@JsonPropertyDescription("The type of subagent to use for this task")
		public String subagentType;

		@JsonProperty(value = "structured_inputs")
		@JsonPropertyDescription("Structured input parameters for the subagent (optional, preferred over description parsing)")
		public Map<String, Object> structuredInputs;

		public TaskRequest() {
		}

		public TaskRequest(String description, String subagentType) {
			this.description = description;
			this.subagentType = subagentType;
		}

		public TaskRequest(String description, String subagentType, Map<String, Object> structuredInputs) {
			this.description = description;
			this.subagentType = subagentType;
			this.structuredInputs = structuredInputs;
		}
	}
}

