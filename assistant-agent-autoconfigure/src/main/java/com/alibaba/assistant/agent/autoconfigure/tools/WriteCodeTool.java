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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * WriteCodeTool - 代码生成工具（委托给 BaseAgentTaskTool）
 *
 * <p>职责：
 * <ul>
 * <li>参数适配：将 WriteCodeRequest 转换为 TaskRequest</li>
 * <li>委托调用：通过 BaseAgentTaskTool 调用 code-generator 子 Agent</li>
 * <li>额外处理：注册到 CodeContext 和持久化到 Store</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class WriteCodeTool implements BiFunction<WriteCodeTool.Request, ToolContext, String> {

	private static final Logger logger = LoggerFactory.getLogger(WriteCodeTool.class);

	private final BaseAgentTaskTool taskTool;
	private final CodeContext codeContext;
	private final RuntimeEnvironmentManager environmentManager;

	// fast-intent (optional)
	private final CodeFastIntentSupport codeFastIntentSupport;

	public WriteCodeTool(BaseAgentTaskTool taskTool,
						 CodeContext codeContext,
						 RuntimeEnvironmentManager environmentManager,
						 CodeFastIntentSupport codeFastIntentSupport) {
		this.taskTool = taskTool;
		this.codeContext = codeContext;
		this.environmentManager = environmentManager;
		this.codeFastIntentSupport = codeFastIntentSupport;
	}

	// Backward compatibility constructor
	public WriteCodeTool(BaseAgentTaskTool taskTool, CodeContext codeContext, RuntimeEnvironmentManager environmentManager) {
		this(taskTool, codeContext, environmentManager, null);
	}

	@Override
	public String apply(Request request, ToolContext toolContext) {
		logger.info("WriteCodeTool#apply 调用代码生成子Agent: functionName={}", request.functionName);

		try {
			// 0. FastPath Intent (CODE): hit => skip code-generator
			String fastIntentResult = tryFastIntent(request, toolContext);
			if (fastIntentResult != null) {
				return fastIntentResult;
			}

			// 1. 参数适配：构建结构化输入
			Map<String, Object> structuredInputs = new HashMap<>();
			structuredInputs.put("requirement", request.requirement);
			structuredInputs.put("function_name", request.functionName);
			structuredInputs.put("parameters", request.parameters != null ? request.parameters : new ArrayList<>());

			// 添加历史代码（合并全局和session维度）
			List<String> historyCode = getHistoryCode(toolContext);
			structuredInputs.put("history_code", historyCode);

			String taskDescription = buildTaskDescription(request);
			BaseAgentTaskTool.TaskRequest taskRequest = new BaseAgentTaskTool.TaskRequest(
					taskDescription,
					"code-generator",
					structuredInputs
			);

			logger.debug("WriteCodeTool#apply 构建结构化输入: requirement={}, function_name={}, parameters={}, historyCodeCount={}",
					request.requirement, request.functionName, request.parameters, historyCode.size());

			// 2. 委托给 TaskTool 调用子 Agent
			String generatedCode = taskTool.apply(taskRequest, toolContext);

			// 3. 检查错误
			if (generatedCode.startsWith("Error:")) {
				return generatedCode;
			}

			// 4. 额外处理：注册到 CodeContext
			registerCode(request, generatedCode, toolContext);

			logger.info("WriteCodeTool#apply 代码生成成功: functionName={}", request.functionName);
			return "Code generated successfully: " + request.functionName+ "\n```python\n" + generatedCode + "\n```";

		} catch (Exception e) {
			logger.error("WriteCodeTool#apply 代码生成失败", e);
			return "Error: " + e.getMessage();
		}
	}

	@SuppressWarnings("unchecked")
	private String tryFastIntent(Request request, ToolContext toolContext) {
		try {
			if (codeFastIntentSupport == null || toolContext == null) {
				return null;
			}

			String language = (codeContext != null && codeContext.getLanguage() != null) ? codeContext.getLanguage().name() : null;
			Map<String, Object> toolReq = CodeFastIntentSupport.toolReqOf(request.requirement, request.functionName, request.parameters);
			Optional<CodeFastIntentSupport.Hit> hitOpt = codeFastIntentSupport.tryHit(toolContext, toolReq, language);
			if (hitOpt.isEmpty()) {
				return null;
			}

			Experience best = hitOpt.get().experience();
			String code = hitOpt.get().code();
			try {
				registerCode(request, code, toolContext);
			} catch (Exception e) {
				String err = e.getMessage();
				logger.warn("WriteCodeTool#tryFastIntent - reason=fast-intent register failed, expId={}, error={}",
						best.getId(), err);

				FastIntentConfig.FastIntentFallback fb = CodeFastIntentSupport.getOnRegisterFallback(best);
				if (fb == FastIntentConfig.FastIntentFallback.FAIL_FAST) {
					return "Error: FastIntent(Code) register failed: " + err;
				}
				return null; // fallback to normal LLM path
			}

			logger.info("WriteCodeTool#tryFastIntent - reason=fast-intent HIT (skip codegen), expId={}", best.getId());
			return "FastIntent(Code) hit: " + best.getTitle() + "\n```python\n" + code + "\n```";

		} catch (Exception e) {
			logger.warn("WriteCodeTool#tryFastIntent - reason=fast-intent failed, fallback to normal flow, error={}", e.getMessage());
			return null;
		}
	}

	/**
	 * 构建任务描述
	 */
	private String buildTaskDescription(Request request) {
		StringBuilder desc = new StringBuilder();
		desc.append("需求: ").append(request.requirement).append("\n");
		desc.append("函数名: ").append(request.functionName).append("\n");

		if (request.parameters != null && !request.parameters.isEmpty()) {
			desc.append("参数列表: ").append(String.join(", ", request.parameters)).append("\n");
		} else {
			desc.append("参数: 使用 **kwargs 接收灵活参数\n");
		}

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
			logger.warn("WriteCodeTool#registerCode - reason=生成的函数名不匹配, expected={}, actual={}",
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
			logger.info("WriteCodeTool#registerCode - reason=代码已注册到session, functionName={}",
					request.functionName);
		} else {
			// 降级：如果无法获取state，仍然注册到共享的CodeContext
			codeContext.registerFunction(code);
			logger.warn("WriteCodeTool#registerCode - reason=无法获取state降级到全局CodeContext, functionName={}",
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
				logger.warn("WriteCodeTool#saveToStore Store未初始化");
				return;
			}

			List<String> namespace = List.of("codeact", "code_generation");
			Map<String, Object> data = new HashMap<>();
			data.put("functionName", code.getFunctionName());
			data.put("language", code.getLanguage().name());
			data.put("code", code.getCode());
			data.put("parameters", code.getParameters());
			data.put("requirement", code.getOriginalQuery());

			StoreItem item = StoreItem.of(namespace, code.getFunctionName(), data);
			store.putItem(item);

			logger.debug("WriteCodeTool#saveToStore 代码已保存到Store: functionName={}", code.getFunctionName());

		} catch (Exception e) {
			logger.error("WriteCodeTool#saveToStore 保存失败", e);
		}
	}

	/**
	 * 获取历史生成的代码（合并全局和session维度）
	 *
	 * <p>使用SessionCodeManager获取合并后的代码，session维度的代码优先级高于全局代码。
	 *
	 * @param toolContext 工具上下文（用于获取session状态）
	 * @return 历史代码列表
	 */
	private List<String> getHistoryCode(ToolContext toolContext) {
		List<String> historyCode = new ArrayList<>();

		OverAllState state = getOverAllState(toolContext);

		// 使用SessionCodeManager获取合并后的代码
		Collection<GeneratedCode> mergedFunctions = SessionCodeManager.getMergedFunctions(state, codeContext);
		for (GeneratedCode code : mergedFunctions) {
			historyCode.add(code.getCode());
		}

		logger.debug("WriteCodeTool#getHistoryCode - reason=获取历史代码, totalCount={}, sessionCount={}",
				historyCode.size(), SessionCodeManager.getSessionFunctionCount(state));

		return historyCode;
	}

	/**
	 * 创建 ToolCallback（供 CodeactSubAgentInterceptor 使用）
	 */
	public static ToolCallback createWriteCodeToolCallback(
			BaseAgentTaskTool taskTool,
			CodeContext codeContext,
			RuntimeEnvironmentManager environmentManager,
			CodeFastIntentSupport codeFastIntentSupport) {

		WriteCodeTool tool = new WriteCodeTool(taskTool, codeContext, environmentManager, codeFastIntentSupport);

		return FunctionToolCallback.builder("write_code", tool)
				.description("Generate and register a new function with specified name and parameters. " +
						"IMPORTANT: You MUST provide both 'functionName' and 'parameters' fields. " +
						"Example: write_code(requirement='calculate sum', functionName='calculate_sum', parameters=['a', 'b'])")
				.inputType(Request.class)
				.build();
	}

	// Backward-compatible factory
	public static ToolCallback createWriteCodeToolCallback(
			BaseAgentTaskTool taskTool,
			CodeContext codeContext,
			RuntimeEnvironmentManager environmentManager) {
		return createWriteCodeToolCallback(taskTool, codeContext, environmentManager, (CodeFastIntentSupport) null);
	}

	/**
	 * Request for writing code
	 */
	public static class Request {
		@JsonProperty(required = true)
		@JsonPropertyDescription("Natural language description of what the function should do")
		public String requirement;

		@JsonProperty(required = true)
		@JsonPropertyDescription("The exact name of the function to generate")
		public String functionName;

		@JsonProperty
		@JsonPropertyDescription("List of parameter names the function should accept")
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

