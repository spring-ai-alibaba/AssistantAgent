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
package com.alibaba.assistant.agent.autoconfigure.tools;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.core.context.CodeContext;
import com.alibaba.assistant.agent.core.executor.GraalCodeExecutor;
import com.alibaba.assistant.agent.core.model.ExecutionRecord;
import com.alibaba.assistant.agent.core.model.GeneratedCode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Tool for executing generated code.
 * Uses GraalCodeExecutor to run Python code in a sandboxed environment.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ExecuteCodeTool implements BiFunction<ExecuteCodeTool.Request, ToolContext, ExecuteCodeTool.Response> {

	private static final Logger logger = LoggerFactory.getLogger(ExecuteCodeTool.class);

	private final GraalCodeExecutor executor;
	private final CodeContext codeContext;

	public ExecuteCodeTool(GraalCodeExecutor executor, CodeContext codeContext) {
		this.executor = executor;
		this.codeContext = codeContext;
		logger.info("ExecuteCodeTool#<init> 初始化完成");
	}

	// Backward compatibility constructor
	public ExecuteCodeTool(GraalCodeExecutor executor) {
		this.executor = executor;
		this.codeContext = null;
		logger.info("ExecuteCodeTool#<init> 初始化完成（无CodeContext）");
	}

	@Override
	public Response apply(Request request, ToolContext toolContext) {
		logger.info("ExecuteCodeTool#apply 执行代码请求: functionName={}, args={}",
			request.functionName, request.args);

		try {
			// Get state from context
			OverAllState state = (OverAllState) toolContext.getContext()
				.get(ToolContextConstants.AGENT_STATE_CONTEXT_KEY);

			if (state == null) {
				throw new IllegalStateException("Agent state not found in tool context");
			}

			// Validate and log function information
			if (codeContext != null) {
				java.util.Optional<GeneratedCode> codeOpt = codeContext.getFunction(request.functionName);
				if (codeOpt.isPresent()) {
					GeneratedCode code = codeOpt.get();
					logger.info("ExecuteCodeTool#apply 找到函数定义: functionName={}, signature={}, parameters={}",
						code.getFunctionName(), code.getFunctionSignature(), code.getParameters());

					// Validate parameters if available
					if (code.getParameters() != null && !code.getParameters().isEmpty()) {
						// Check if provided args match the expected parameters
						if (request.args != null) {
							for (String expectedParam : code.getParameters()) {
								if (!request.args.containsKey(expectedParam)) {
									logger.warn("ExecuteCodeTool#apply 缺少预期参数: {}, 提供的参数: {}",
										expectedParam, request.args.keySet());
								}
							}
							// Check for unexpected parameters
							for (String providedParam : request.args.keySet()) {
								if (!code.getParameters().contains(providedParam)) {
									logger.warn("ExecuteCodeTool#apply 提供了未预期的参数: {}, 预期参数: {}",
										providedParam, code.getParameters());
								}
							}
						} else {
							logger.warn("ExecuteCodeTool#apply 函数需要参数 {} 但未提供任何参数",
								code.getParameters());
						}
					} else {
						logger.info("ExecuteCodeTool#apply 函数使用灵活参数(**kwargs)，可接受任意参数");
					}
				} else {
					logger.warn("ExecuteCodeTool#apply 在CodeContext中未找到函数: {}", request.functionName);
				}
			}

			// Execute code with toolContext for CodeactTools
			ExecutionRecord record = executor.execute(request.functionName, request.args, toolContext);

			// Update state
			updateState(state, record);

			if (record.isSuccess()) {
				logger.info("ExecuteCodeTool#apply 代码执行成功: functionName={}, result={}",
					request.functionName, record.getResult());
				return new Response(true, record.getResult(), null, record.getDurationMs());
			} else {
				logger.error("ExecuteCodeTool#apply 代码执行失败: functionName={}, error={}",
					request.functionName, record.getErrorMessage());
				return new Response(false, null, record.getErrorMessage(), record.getDurationMs());
			}

		} catch (Exception e) {
			logger.error("ExecuteCodeTool#apply 代码执行异常", e);
			return new Response(false, null, "Execution error: " + e.getMessage(), 0);
		}
	}

	@SuppressWarnings("unchecked")
	private void updateState(OverAllState state, ExecutionRecord record) {
		// Get or create execution_history list
		List<ExecutionRecord> history = state.value(CodeactStateKeys.EXECUTION_HISTORY, List.class)
			.map(list -> new ArrayList<>((List<ExecutionRecord>) list))
			.orElse(new ArrayList<>());

		// Add record
		history.add(record);

		// Update state
		Map<String, Object> updates = Map.of(
			CodeactStateKeys.EXECUTION_HISTORY, history,
			CodeactStateKeys.CURRENT_EXECUTION, record
		);
		state.updateState(updates);

		logger.debug("ExecuteCodeTool#updateState 执行历史已更新: count={}", history.size());
	}

	/**
	 * Request for executing code
	 */
	public static class Request {
		@JsonProperty(required = true)
		@JsonPropertyDescription("The exact name of the function to execute. " +
			"This MUST be the same function name used when generating the code with write_code tool. " +
			"You can list available functions by checking the generated_codes in state.")
		public String functionName;

		@JsonProperty
		@JsonPropertyDescription("Function arguments as a map of parameter names to values. " +
			"The parameter names MUST exactly match the parameters specified when the function was generated. " +
			"Example: If function was generated with parameters ['a', 'b'], then use {\"a\": value1, \"b\": value2}. " +
			"If the function was generated without specific parameters (uses **kwargs), you can pass any parameters. " +
			"Value types: String, Number (int/float), Boolean, List, Map/Object")
		public Map<String, Object> args;

		public Request() {
		}

		public Request(String functionName, Map<String, Object> args) {
			this.functionName = functionName;
			this.args = args;
		}
	}

	/**
	 * Response from executing code
	 */
	public static class Response {
		public boolean success;
		public String result;
		public String error;
		public long durationMs;

		public Response() {
		}

		public Response(boolean success, String result, String error, long durationMs) {
			this.success = success;
			this.result = result;
			this.error = error;
			this.durationMs = durationMs;
		}
	}
}

