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

import com.alibaba.assistant.agent.core.context.CodeContext;
import com.alibaba.assistant.agent.core.context.SessionCodeManager;
import com.alibaba.assistant.agent.core.executor.RuntimeEnvironmentManager;
import com.alibaba.assistant.agent.core.model.GeneratedCode;
import com.alibaba.assistant.agent.autoconfigure.subagent.BaseAgentTaskTool;
import com.alibaba.assistant.agent.extension.experience.fastintent.CodeFastIntentSupport;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.FastIntentConfig;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * WriteConditionCodeTool - 条件判断代码生成工具（委托给 BaseAgentTaskTool）
 *
 * <p>职责：
 * <ul>
 * <li>参数适配：将 Request 转换为 TaskRequest，强调返回 boolean</li>
 * <li>委托调用：通过 BaseAgentTaskTool 调用 condition-code-generator 子 Agent</li>
 * <li>额外处理：注册到 CodeContext 和持久化到 Store</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class WriteConditionCodeTool implements BiFunction<WriteConditionCodeTool.Request, ToolContext, String> {

	private static final Logger logger = LoggerFactory.getLogger(WriteConditionCodeTool.class);

	private final BaseAgentTaskTool taskTool;
	private final CodeContext codeContext;
	private final RuntimeEnvironmentManager environmentManager;

	// Experience / fast-intent (optional)
	private final CodeFastIntentSupport codeFastIntentSupport;

	public WriteConditionCodeTool(BaseAgentTaskTool taskTool,
								  CodeContext codeContext,
								  RuntimeEnvironmentManager environmentManager,
								  CodeFastIntentSupport codeFastIntentSupport) {
		this.taskTool = taskTool;
		this.codeContext = codeContext;
		this.environmentManager = environmentManager;
		this.codeFastIntentSupport = codeFastIntentSupport;
	}

	// Backward compatibility constructor
	public WriteConditionCodeTool(BaseAgentTaskTool taskTool, CodeContext codeContext, RuntimeEnvironmentManager environmentManager) {
		this(taskTool, codeContext, environmentManager, (CodeFastIntentSupport) null);
	}

	@Override
	public String apply(Request request, ToolContext toolContext) {
		logger.info("WriteConditionCodeTool#apply 调用条件判断代码生成子Agent: functionName={}", request.functionName);

		try {
			// 0. FastPath Intent (CODE): hit => skip condition-code-generator
			String fastIntentResult = tryFastIntent(request, toolContext);
			if (fastIntentResult != null) {
				return fastIntentResult;
			}

			// 1. 参数适配：构建任务描述，强调返回 boolean
			String taskDescription = buildTaskDescription(request);
			BaseAgentTaskTool.TaskRequest taskRequest = new BaseAgentTaskTool.TaskRequest(
					taskDescription,
					"condition-code-generator"
			);

			// 2. 委托给 TaskTool 调用子 Agent
			String generatedCode = taskTool.apply(taskRequest, toolContext);

			// 3. 检查错误
			if (generatedCode.startsWith("Error:")) {
				return generatedCode;
			}

			// 4. 额外处理：注册到 CodeContext
			registerCode(request, generatedCode, toolContext);

			logger.info("WriteConditionCodeTool#apply 条件判断代码生成成功: functionName={}", request.functionName);
			return "Condition code generated successfully: " + request.functionName;

		} catch (Exception e) {
			logger.error("WriteConditionCodeTool#apply 条件判断代码生成失败", e);
			return "Error: " + e.getMessage();
		}
	}

	private String tryFastIntent(Request request, ToolContext toolContext) {
		try {
			if (codeFastIntentSupport == null || toolContext == null) {
				return null;
			}

			String language = (codeContext != null && codeContext.getLanguage() != null) ? codeContext.getLanguage().name() : null;
			var toolReq = CodeFastIntentSupport.toolReqOf(request.requirement, request.functionName, request.parameters);

			var hitOpt = codeFastIntentSupport.tryHit(toolContext, toolReq, language);
			if (hitOpt.isEmpty()) {
				return null;
			}

			Experience best = hitOpt.get().experience();
			String code = hitOpt.get().code();
			if (code == null) {
				return null;
			}

			try {
				registerCode(request, code, toolContext);
			} catch (Exception e) {
				String err = e.getMessage();
				logger.warn("WriteConditionCodeTool#tryFastIntent - reason=fast-intent register failed, expId={}, error={}",
						best != null ? best.getId() : "unknown", err);

				FastIntentConfig.FastIntentFallback fb = CodeFastIntentSupport.getOnRegisterFallback(best);
				if (fb == FastIntentConfig.FastIntentFallback.FAIL_FAST) {
					return "Error: FastIntent(Code) register failed: " + err;
				}
				return null; // fallback to normal LLM path
			}

			logger.info("WriteConditionCodeTool#tryFastIntent - reason=fast-intent HIT (skip codegen), expId={}",
					best != null ? best.getId() : "unknown");
			return "FastIntent(Code) hit: " + (best != null ? best.getTitle() : "matched") + "\n```python\n" + code + "\n```";

		} catch (Exception e) {
			logger.warn("WriteConditionCodeTool#tryFastIntent - reason=fast-intent failed, fallback to normal flow, error={}", e.getMessage());
			return null;
		}
	}

	/**
	 * 构建任务描述（强调返回 boolean）
	 */
	private String buildTaskDescription(Request request) {
		StringBuilder desc = new StringBuilder();
		desc.append("生成条件判断函数代码（必须返回 boolean 值）\n");
		desc.append("需求: ").append(request.requirement).append("\n");
		desc.append("函数名: ").append(request.functionName).append("\n");

		if (request.parameters != null && !request.parameters.isEmpty()) {
			desc.append("参数列表: ").append(String.join(", ", request.parameters)).append("\n");
		} else {
			desc.append("参数: 使用 **kwargs 接收灵活参数\n");
		}

		desc.append("\n重要: 函数必须返回 True 或 False");

		return desc.toString();
	}

	/**
	 * 注册代码到 Session状态 和 Store
	 *
	 * <p>代码会被保存到OverAllState的session级别存储中，而不是全局共享的CodeContext，
	 * 这样不同session的代码不会相互干扰。
	 */
	private void registerCode(Request request, String generatedCode, ToolContext toolContext) {
		// 验证函数名
		String actualFunctionName = environmentManager.extractFunctionName(generatedCode);
		if (actualFunctionName != null && !actualFunctionName.equals(request.functionName)) {
			logger.warn("WriteConditionCodeTool#registerCode - reason=生成的函数名不匹配, expected={}, actual={}",
					request.functionName, actualFunctionName);
		}

		// 创建 GeneratedCode 对象
		GeneratedCode code = new GeneratedCode(
				request.functionName,
				codeContext.getLanguage(),
				generatedCode,
				request.requirement
		);
		code.setParameters(request.parameters != null ? new ArrayList<>(request.parameters) : new ArrayList<>());

		// 获取 OverAllState
		OverAllState state = getOverAllState(toolContext);

		// 注册到 Session 级别存储（优先）
		if (state != null) {
			SessionCodeManager.registerSessionFunction(state, code);
			logger.info("WriteConditionCodeTool#registerCode - reason=条件代码已注册到session, functionName={}",
					request.functionName);
		} else {
			// 降级：如果无法获取state，仍然注册到共享的CodeContext
			codeContext.registerFunction(code);
			logger.warn("WriteConditionCodeTool#registerCode - reason=无法获取state降级到全局CodeContext, functionName={}",
					request.functionName);
		}

		// 持久化到 Store（用于跨session的长期存储，默认暂时为空不做任何操作）
		// saveToStore(toolContext, code);
	}

	/**
	 * 从ToolContext获取OverAllState
	 */
	private OverAllState getOverAllState(ToolContext toolContext) {
		if (toolContext == null || toolContext.getContext() == null) {
			return null;
		}
		Object state = toolContext.getContext().get(ToolContextConstants.AGENT_STATE_CONTEXT_KEY);
		if (state instanceof OverAllState) {
			return (OverAllState) state;
		}
		return null;
	}

	/**
	 * 保存到 Store
	 */
	private void saveToStore(ToolContext toolContext, GeneratedCode code) {
		try {
			OverAllState state = (OverAllState) toolContext.getContext()
					.get(ToolContextConstants.AGENT_STATE_CONTEXT_KEY);
			if (state == null) {
				return;
			}

			Store store = state.getStore();
			if (store == null) {
				logger.warn("WriteConditionCodeTool#saveToStore Store未初始化");
				return;
			}

			List<String> namespace = List.of("codeact", "condition_code_generation");
			Map<String, Object> data = new HashMap<>();
			data.put("functionName", code.getFunctionName());
			data.put("language", code.getLanguage().name());
			data.put("code", code.getCode());
			data.put("parameters", code.getParameters());
			data.put("requirement", code.getOriginalQuery());
			data.put("isCondition", true);

			StoreItem item = StoreItem.of(namespace, code.getFunctionName(), data);
			store.putItem(item);

			logger.debug("WriteConditionCodeTool#saveToStore 条件代码已保存到Store: functionName={}", code.getFunctionName());

		} catch (Exception e) {
			logger.error("WriteConditionCodeTool#saveToStore 保存失败", e);
		}
	}

	/**
	 * 创建 ToolCallback（供 CodeactSubAgentInterceptor 使用）
	 */
	public static ToolCallback createWriteConditionCodeToolCallback(
			BaseAgentTaskTool taskTool,
			CodeContext codeContext,
			RuntimeEnvironmentManager environmentManager) {

		WriteConditionCodeTool tool = new WriteConditionCodeTool(taskTool, codeContext, environmentManager);

		return FunctionToolCallback.builder("write_condition_code", tool)
				.description("Generate and register a condition check function for triggers. " +
						"This function MUST return a boolean value (True/False). " +
						"Example: write_condition_code(requirement='check if it is a working day', " +
						"functionName='check_is_working_day', parameters=['date'])")
				.inputType(Request.class)
				.build();
	}

	public static ToolCallback createWriteConditionCodeToolCallback(
			BaseAgentTaskTool taskTool,
			CodeContext codeContext,
			RuntimeEnvironmentManager environmentManager,
			CodeFastIntentSupport codeFastIntentSupport) {

		WriteConditionCodeTool tool = new WriteConditionCodeTool(taskTool, codeContext, environmentManager, codeFastIntentSupport);

		return FunctionToolCallback.builder("write_condition_code", tool)
				.description("Generate and register a condition check function for triggers. " +
						"This function MUST return a boolean value (True/False). " +
						"Example: write_condition_code(requirement='check if it is a working day', " +
						"functionName='check_is_working_day', parameters=['date'])")
				.inputType(Request.class)
				.build();
	}

	/**
	 * Request for writing condition code
	 */
	public static class Request {
		@JsonProperty(required = true)
		@JsonPropertyDescription("Natural language description of the condition to check")
		public String requirement;

		@JsonProperty(required = true)
		@JsonPropertyDescription("Function name for the condition check (should start with 'check_' or 'condition_')")
		public String functionName;

		@JsonProperty
		@JsonPropertyDescription("List of parameter names the condition function needs")
		public List<String> parameters;

		public Request() {
		}

		public Request(String requirement, String functionName, List<String> parameters) {
			this.requirement = requirement;
			this.functionName = functionName;
			this.parameters = parameters;
		}
	}
}

