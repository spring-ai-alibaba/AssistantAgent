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
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * WriteConditionCodeTool - 条件判断代码注册工具
 *
 * <p>职责：
 * <ul>
 * <li>接收 LLM 在 React 阶段直接生成的条件判断函数代码</li>
 * <li>验证代码格式（函数名、参数、返回值类型）</li>
 * <li>将代码注入到 CodeContext，供触发器使用</li>
 * </ul>
 *
 * <p>说明：
 * <ul>
 * <li>新增 code 参数，直接接收完整的 Python 条件函数代码</li>
 * <li>条件函数必须返回 boolean 值（True/False）</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class WriteConditionCodeTool implements BiFunction<WriteConditionCodeTool.Request, ToolContext, String> {

	private static final Logger logger = LoggerFactory.getLogger(WriteConditionCodeTool.class);

	private final CodeContext codeContext;
	private final RuntimeEnvironmentManager environmentManager;

	public WriteConditionCodeTool(CodeContext codeContext,
								  RuntimeEnvironmentManager environmentManager) {
		this.codeContext = codeContext;
		this.environmentManager = environmentManager;
	}

	@Override
	public String apply(Request request, ToolContext toolContext) {
		logger.info("WriteConditionCodeTool#apply 注册条件判断代码: functionName={}", request.functionName);

		try {
			// 1. 验证必填参数
			if (request.functionName == null || request.functionName.trim().isEmpty()) {
				return "Error: functionName is required";
			}
			if (request.code == null || request.code.trim().isEmpty()) {
				return "Error: code is required. Please provide the complete Python condition function code.";
			}

			// 2. 清理代码
			String cleanedCode = cleanUpCode(request.code);

			// 3. 验证代码格式
			String validationError = validateCode(request.functionName, request.parameters, cleanedCode);
			if (validationError != null) {
				logger.warn("WriteConditionCodeTool#apply 代码验证失败: {}", validationError);
				return "Error: " + validationError;
			}

			// 4. 注册代码到 CodeContext
			registerCode(request, cleanedCode, toolContext);

			logger.info("WriteConditionCodeTool#apply 条件判断代码注册成功: functionName={}", request.functionName);
			return String.format("条件函数 %s 已成功注册，可用于触发器订阅。\n```python\n%s\n```",
					request.functionName, cleanedCode);

		} catch (Exception e) {
			logger.error("WriteConditionCodeTool#apply 条件判断代码注册失败", e);
			return "Error: " + e.getMessage();
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
	 */
	private String validateCode(String expectedFunctionName, List<String> expectedParameters, String code) {
		String actualFunctionName = environmentManager.extractFunctionName(code);
		if (actualFunctionName == null) {
			return "Code does not contain a valid function definition. Expected: def " + expectedFunctionName + "(...) -> bool";
		}
		if (!actualFunctionName.equals(expectedFunctionName)) {
			return String.format("Function name mismatch. Expected: %s, Actual: %s",
					expectedFunctionName, actualFunctionName);
		}
		return null;
	}

	/**
	 * 注册代码到 Session状态 和 Store
	 *
	 * <p>代码会被保存到OverAllState的session级别存储中，而不是全局共享的CodeContext，
	 * 这样不同session的代码不会相互干扰。
	 */
	private void registerCode(Request request, String conditionCode, ToolContext toolContext) {
		// 创建 GeneratedCode 对象
		GeneratedCode code = new GeneratedCode(
				request.functionName,
				codeContext.getLanguage(),
				conditionCode,
				request.description != null ? request.description : ""
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
	 * write_condition_code 工具的详细描述
	 * 
	 * <p>包含条件函数编写规范和指导，帮助 LLM 生成正确的条件判断代码。
	 */
	private static final String WRITE_CONDITION_CODE_DESCRIPTION = """
		为触发器注册一个条件检查函数。函数必须返回布尔值（True/False）。
		
		必需参数：
		- functionName：函数名称（应以 'check_' 或 'condition_' 开头）
		- code：返回 True 或 False 的完整 Python 函数代码
		- parameters：参数名列表（可选，定时触发器可为空）
		- description：描述该函数检查什么条件的自然语言描述
		
		条件函数编写规范：
		1. 返回类型：
		   - 必须返回 True 或 False（布尔值）
		   - True 表示条件满足，触发器动作将执行
		   - False 表示条件不满足，不执行动作
		
		2. 定时/延迟触发器：
		   - 条件函数可以简单返回 True
		   - 实际时间控制由 subscribe_trigger 的 delay/cron 参数控制
		   - 示例：def check_reminder_condition(): return True
		
		3. 事件触发器：
		   - 检查实际条件（如数据变化、阈值）
		   - 示例：def check_quota_exceeded(current, limit): return current > limit
		
		4. 错误处理：
		   - 发生任何错误时返回 False（故障安全）
		   - 不要在条件函数中抛出异常
		
		示例：
		
		1. 定时触发器（始终为 True，通过 delay 控制时间）：
		   write_condition_code(
		       functionName='check_medicine_reminder',
		       code='def check_medicine_reminder():\\n    return True',
		       parameters=[],
		       description='吃药提醒的定时条件'
		   )
		
		2. 事件触发器（实际条件检查）：
		   write_condition_code(
		       functionName='check_is_working_day',
		       code='def check_is_working_day(date):\\n    return date.weekday() < 5',
		       parameters=['date'],
		       description='检查给定日期是否为工作日'
		   )
		""";

	/**
	 * 创建 ToolCallback
	 */
	public static ToolCallback createWriteConditionCodeToolCallback(
			CodeContext codeContext,
			RuntimeEnvironmentManager environmentManager) {

		WriteConditionCodeTool tool = new WriteConditionCodeTool(codeContext, environmentManager);

		return FunctionToolCallback.builder("write_condition_code", tool)
				.description(WRITE_CONDITION_CODE_DESCRIPTION)
				.inputType(Request.class)
				.build();
	}

	/**
	 * Request for writing condition code
	 */
	public static class Request {
		@JsonProperty(required = true)
		@JsonPropertyDescription("条件检查函数的名称（应以 'check_' 或 'condition_' 开头）")
		public String functionName;

		@JsonProperty(required = true)
		@JsonPropertyDescription("定义条件函数的完整 Python 代码。必须返回 True 或 False。")
		public String code;

		@JsonProperty
		@JsonPropertyDescription("条件函数需要的参数名列表")
		@JsonDeserialize(using = FlexibleStringListDeserializer.class)
		public List<String> parameters;

		@JsonProperty
		@JsonPropertyDescription("描述该函数检查什么条件的自然语言描述")
		public String description;

		public Request() {
		}

		public Request(String functionName, String code, List<String> parameters, String description) {
			this.functionName = functionName;
			this.code = code;
			this.parameters = parameters;
			this.description = description;
		}
	}
}

