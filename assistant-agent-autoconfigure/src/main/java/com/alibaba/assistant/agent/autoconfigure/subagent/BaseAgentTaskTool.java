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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import java.util.HashMap;
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
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class BaseAgentTaskTool implements BiFunction<BaseAgentTaskTool.TaskRequest, ToolContext, String> {

	private static final Logger logger = LoggerFactory.getLogger(BaseAgentTaskTool.class);

	private final Map<String, BaseAgent> subAgents;

	public BaseAgentTaskTool(Map<String, BaseAgent> subAgents) {
		this.subAgents = subAgents;
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
				inputs = request.structuredInputs;
				logger.info("BaseAgentTaskTool#apply 使用结构化输入: {}", inputs.keySet());
			} else {
				inputs = buildInputsFromDescription(request.description);
				logger.info("BaseAgentTaskTool#apply 从描述解析输入: {}", inputs.keySet());
			}

			// 4. Invoke the subagent through CompiledGraph
			CompiledGraph compiledGraph = subAgent.getAndCompileGraph();
			Optional<OverAllState> resultOpt = compiledGraph.invoke(inputs);

			// 5. Extract result from state
			OverAllState resultState = resultOpt.orElseThrow(() ->
					new IllegalStateException("SubAgent returned empty result"));

			// 6. Try to get generated_code or any string result
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

