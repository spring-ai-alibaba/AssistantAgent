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
import com.alibaba.assistant.agent.extension.experience.fastintent.CodeFastIntentSupport;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.FastIntentConfig;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * WriteCodeTool - 代码注册工具
 *
 * <p>职责：
 * <ul>
 * <li>接收 LLM 在 React 阶段直接生成的完整 Python 代码</li>
 * <li>验证代码格式（函数名、参数是否匹配）</li>
 * <li>将代码注入到 CodeContext，供后续 execute_code 调用</li>
 * </ul>
 *
 * <p>说明：
 * <ul>
 * <li>新增 code 参数，直接接收完整的 Python 函数代码</li>
 * <li>保留 FastIntent 快速匹配能力</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class WriteCodeTool implements BiFunction<WriteCodeTool.Request, ToolContext, String> {

	private static final Logger logger = LoggerFactory.getLogger(WriteCodeTool.class);

	private final CodeContext codeContext;
	private final RuntimeEnvironmentManager environmentManager;

	private final CodeFastIntentSupport codeFastIntentSupport;

	public WriteCodeTool(CodeContext codeContext,
						 RuntimeEnvironmentManager environmentManager,
						 CodeFastIntentSupport codeFastIntentSupport) {
		this.codeContext = codeContext;
		this.environmentManager = environmentManager;
		this.codeFastIntentSupport = codeFastIntentSupport;
	}

	public WriteCodeTool(CodeContext codeContext, RuntimeEnvironmentManager environmentManager) {
		this(codeContext, environmentManager, null);
	}

	@Override
	public String apply(Request request, ToolContext toolContext) {
		logger.info("WriteCodeTool#apply 注册代码: functionName={}", request.functionName);

		try {
			// 0. FastPath Intent (CODE): hit => skip code validation
			String fastIntentResult = tryFastIntent(request, toolContext);
			if (fastIntentResult != null) {
				return fastIntentResult;
			}

			// 1. 验证必填参数
			if (request.functionName == null || request.functionName.trim().isEmpty()) {
				return "Error: functionName is required";
			}
			if (request.code == null || request.code.trim().isEmpty()) {
				return "Error: code is required. Please provide the complete Python function code.";
			}

			// 2. 清理代码（移除可能的 markdown 标记）
			String cleanedCode = cleanUpCode(request.code);

			// 3. 验证代码格式
			String validationError = validateCode(request.functionName, request.parameters, cleanedCode);
			if (validationError != null) {
				logger.warn("WriteCodeTool#apply 代码验证失败: {}", validationError);
				return "Error: " + validationError;
			}

			// 4. 注册代码到 CodeContext
			registerCode(request, cleanedCode, toolContext);

			logger.info("WriteCodeTool#apply 代码注册成功: functionName={}", request.functionName);
			return String.format("函数 %s 已成功注册，可以通过 execute_code 执行。\n```python\n%s\n```",
					request.functionName, cleanedCode);

		} catch (Exception e) {
			logger.error("WriteCodeTool#apply 代码注册失败", e);
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
			Map<String, Object> toolReq = CodeFastIntentSupport.toolReqOf(request.description, request.functionName, request.parameters);
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
				return null;
			}

			logger.info("WriteCodeTool#tryFastIntent - reason=fast-intent HIT (skip codegen), expId={}", best.getId());
			return "FastIntent(Code) hit: " + best.getTitle() + "\n```python\n" + code + "\n```";

		} catch (Exception e) {
			logger.warn("WriteCodeTool#tryFastIntent - reason=fast-intent failed, fallback to normal flow, error={}", e.getMessage());
			return null;
		}
	}

	/**
	 * 清理代码（移除 markdown 代码块标记）
	 */
	private String cleanUpCode(String code) {
		if (code == null) {
			return null;
		}
		String cleaned = code.trim();
		if (cleaned.startsWith("```python")) {
			cleaned = cleaned.substring(9);
		} else if (cleaned.startsWith("```py")) {
			cleaned = cleaned.substring(5);
		} else if (cleaned.startsWith("```")) {
			cleaned = cleaned.substring(3);
		}
		if (cleaned.endsWith("```")) {
			cleaned = cleaned.substring(0, cleaned.length() - 3);
		}
		return cleaned.trim();
	}

	/**
	 * 验证代码格式
	 *
	 * @param expectedFunctionName 期望的函数名
	 * @param expectedParameters 期望的参数列表
	 * @param code 代码内容
	 * @return 错误信息，如果验证通过返回 null
	 */
	private String validateCode(String expectedFunctionName, List<String> expectedParameters, String code) {
		String actualFunctionName = environmentManager.extractFunctionName(code);
		if (actualFunctionName == null) {
			return "Code does not contain a valid function definition. Expected: def " + expectedFunctionName + "(...)";
		}
		if (!actualFunctionName.equals(expectedFunctionName)) {
			return String.format("Function name mismatch. Expected: %s, Actual: %s",
					expectedFunctionName, actualFunctionName);
		}
		return null;
	}

	/**
	 * 注册代码到 Session状态 和 Store
	 */
	private void registerCode(Request request, String code, ToolContext toolContext) {
		GeneratedCode generatedCode = new GeneratedCode(
				request.functionName,
				codeContext.getLanguage(),
				code,
				request.description != null ? request.description : ""
		);
		generatedCode.setParameters(request.parameters != null ? new ArrayList<>(request.parameters) : new ArrayList<>());

		OverAllState state = getOverAllState(toolContext);

		if (state != null) {
			SessionCodeManager.registerSessionFunction(state, generatedCode);
			logger.info("WriteCodeTool#registerCode - reason=代码已注册到session, functionName={}",
					request.functionName);
		} else {
			codeContext.registerFunction(generatedCode);
			logger.warn("WriteCodeTool#registerCode - reason=无法获取state降级到全局CodeContext, functionName={}",
					request.functionName);
		}
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
	@SuppressWarnings("unused")
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
	 * write_code 工具的详细描述
	 * 
	 * <p>包含代码编写规范和指导，帮助 LLM 生成正确的代码。
	 * <p>注意：此描述应保持通用，不预设具体的工具方法名，具体可用工具由运行时注入的 CodeactTool 决定。
	 */
	private static final String WRITE_CODE_DESCRIPTION = """
		使用指定的名称和完整代码注册一个 Python 函数。
		
		必需参数：
		- functionName：函数名称（snake_case 格式，例如 'calculate_sum'）
		- description：函数功能的简要描述
		- parameters：函数接受的参数名列表
		- code：完整的 Python 函数代码，包含 'def' 语句和完整实现
		
		代码编写规范：
		1. 函数结构：
		   - 必须以 'def function_name(params):' 开头，与 functionName 参数匹配
		   - 包含描述函数用途的文档字符串
		   - 使用 try/except 块优雅地处理错误
		   - 返回有意义的结果，格式为字典
		
		2. 纯计算函数：
		   - 对于简单计算或数据处理，直接返回结果
		   - Agent 会在 execute_code 执行后处理向用户展示结果
		   - 示例：return {"success": True, "result": computed_value}
		
		3. 工具使用（当工具可用时）：
		   - 工具在运行时作为 Python 对象注入
		   - 使用 'tool_class.method_name(args)' 格式调用工具
		   - 使用前先在系统提示或指导中检查可用工具
		   - 常见工具类：search_tools、reply_tools、trigger_tools 等
		   - 不要假设特定方法存在，先验证！
		
		4. 错误处理：
		   - 将风险操作包装在 try/except 块中
		   - 操作失败时返回错误信息
		   - 示例：except Exception as e: return {"success": False, "error": str(e)}
		
		重要说明：
		- 代码在 GraalVM Python 沙箱环境中运行
		- 只使用当前会话中明确可用的工具
		- 不确定可用工具时，编写返回结果的纯 Python 代码
		- Agent 可以在代码执行后向用户展示结果
		
		示例（纯计算）：
		write_code(
		    functionName='calculate_sum',
		    description='计算两个数的和',
		    parameters=['a', 'b'],
		    code='''def calculate_sum(a, b):
		    \"\"\"计算两个数的和\"\"\"
		    try:
		        result = a + b
		        return {"success": True, "sum": result, "message": f"{a} + {b} = {result}"}
		    except Exception as e:
		        return {"success": False, "error": str(e)}
		'''
		)
		""";

	/**
	 * 创建 ToolCallback
	 */
	public static ToolCallback createWriteCodeToolCallback(
			CodeContext codeContext,
			RuntimeEnvironmentManager environmentManager,
			CodeFastIntentSupport codeFastIntentSupport) {

		WriteCodeTool tool = new WriteCodeTool(codeContext, environmentManager, codeFastIntentSupport);

		return FunctionToolCallback.builder("write_code", tool)
				.description(WRITE_CODE_DESCRIPTION)
				.inputType(Request.class)
				.build();
	}

	public static ToolCallback createWriteCodeToolCallback(
			CodeContext codeContext,
			RuntimeEnvironmentManager environmentManager) {
		return createWriteCodeToolCallback(codeContext, environmentManager, null);
	}

	/**
	 * Request for writing code
	 */
	public static class Request {
		@JsonProperty(required = true)
		@JsonPropertyDescription("要注册的函数名称（snake_case 格式）")
		public String functionName;

		@JsonProperty(required = true)
		@JsonPropertyDescription("函数功能的描述")
		public String description;

		@JsonProperty
		@JsonPropertyDescription("函数参数名的字符串数组")
		@JsonDeserialize(using = FlexibleStringListDeserializer.class)
		public List<String> parameters;

		@JsonProperty(required = true)
		@JsonPropertyDescription("完整的 Python 函数代码，包含 'def' 语句和实现")
		public String code;

		public Request() {
		}

		public Request(String functionName, String description, List<String> parameters, String code) {
			this.functionName = functionName;
			this.description = description;
			this.parameters = parameters;
			this.code = code;
		}

	}
}

