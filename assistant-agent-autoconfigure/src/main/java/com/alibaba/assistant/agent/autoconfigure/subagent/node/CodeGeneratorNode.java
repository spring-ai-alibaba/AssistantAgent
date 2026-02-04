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
package com.alibaba.assistant.agent.autoconfigure.subagent.node;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.definition.ParameterNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.common.tools.definition.ReturnSchema;
import com.alibaba.assistant.agent.autoconfigure.subagent.filter.CodeactToolFilter;
import com.alibaba.assistant.agent.core.tool.schema.ReturnSchemaRegistry;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.InterceptorChain;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CodeGeneratorNode - 代码生成节点（专门用于CodeGeneratorSubAgent）
 *
 * <p>参考AgentLlmNode的设计，但专注于代码生成场景
 * <p>从CodeactTool的元数据构建系统提示，通过拦截器链调用模型
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class CodeGeneratorNode implements NodeActionWithConfig {

	private static final Logger logger = LoggerFactory.getLogger(CodeGeneratorNode.class);

	private final ChatModel chatModel;
	private final Language language;
	private final List<CodeactTool> codeactTools;
	private List<ModelInterceptor> modelInterceptors;
	private final String outputKey;
	private final boolean isCondition;
	private final String customSystemPrompt;
	private final ReturnSchemaRegistry returnSchemaRegistry;

	public CodeGeneratorNode(
			ChatModel chatModel,
			Language language,
			List<CodeactTool> codeactTools,
			List<ModelInterceptor> modelInterceptors,
			String outputKey,
			boolean isCondition,
			String customSystemPrompt) {
		this(chatModel, language, codeactTools, modelInterceptors, outputKey, isCondition, customSystemPrompt, null);
	}

	public CodeGeneratorNode(
			ChatModel chatModel,
			Language language,
			List<CodeactTool> codeactTools,
			List<ModelInterceptor> modelInterceptors,
			String outputKey,
			boolean isCondition,
			String customSystemPrompt,
			ReturnSchemaRegistry returnSchemaRegistry) {
		this.chatModel = chatModel;
		this.language = language;
		this.codeactTools = codeactTools;
		this.modelInterceptors = modelInterceptors;
		this.outputKey = outputKey;
		this.isCondition = isCondition;
		this.customSystemPrompt = customSystemPrompt;
		this.returnSchemaRegistry = returnSchemaRegistry;
	}

	/**
	 * Set model interceptors（用于在构建后设置拦截器）
	 */
	public void setModelInterceptors(List<ModelInterceptor> modelInterceptors) {
		this.modelInterceptors = modelInterceptors;
	}

	@Override
	public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
		logger.info("CodeGeneratorNode#apply 开始生成代码");

		try {
			// 1. 从state提取用户输入（需求、函数名、参数等）
			String requirement = extractRequirement(state);
			String functionName = extractFunctionName(state);
			List<String> parameters = extractParameters(state);
			List<String> historyCode = extractHistoryCode(state);

			logger.debug("CodeGeneratorNode#apply 提取输入: functionName={}, requirement={}, parameters={}, historyCodeCount={}",
					functionName, requirement, parameters, historyCode.size());

			// 2. 【新增】根据 state 中的白名单配置筛选工具
			List<CodeactTool> effectiveTools = filterTools(state);

			// 3. 构建系统提示（使用筛选后的工具）
			String systemPrompt = buildSystemPrompt(language, effectiveTools, isCondition, customSystemPrompt, historyCode);

			// 4. 构建用户消息
			String userMessage = buildUserMessage(requirement, functionName, parameters, isCondition);

            logger.info("CodeGeneratorNode#apply 构建消息: systemPrompt={}, userMessage={}",
                    systemPrompt, userMessage);
			// 5. 构造ModelRequest
			List<Message> messages = List.of(
					new SystemMessage(systemPrompt),
					new UserMessage(userMessage)
			);

			ModelRequest modelRequest = ModelRequest.builder()
					.messages(messages)
					.build();

			// 6. 通过拦截器链调用模型
			ModelResponse modelResponse = executeWithInterceptors(modelRequest);

			// 7. 提取生成的代码
			String generatedCode = extractCodeFromResponse(modelResponse);

			logger.info("CodeGeneratorNode#apply 代码生成成功: functionName={}, codeLength={}",
					functionName, generatedCode.length());

			// 8. 返回结果（放入outputKey）
			Map<String, Object> result = new HashMap<>();
			result.put(outputKey, generatedCode);
			// 可选：将筛选后的工具列表写回 state，用于调试和审计
			result.put(CodeactStateKeys.FILTERED_CODEACT_TOOLS, effectiveTools);
			return result;

		} catch (Exception e) {
			logger.error("CodeGeneratorNode#apply 代码生成失败", e);
			Map<String, Object> errorResult = new HashMap<>();
			errorResult.put(outputKey, "Error: " + e.getMessage());
			return errorResult;
		}
	}

	/**
	 * 根据 state 中的白名单配置筛选工具
	 * 
	 * <p>使用 {@link CodeactToolFilter} 根据 state 中配置的白名单筛选可用工具。
	 * 如果没有配置白名单，则返回全部工具（保持向后兼容）。
	 * 
	 * @param state OverAllState 实例
	 * @return 筛选后的工具列表
	 */
	private List<CodeactTool> filterTools(OverAllState state) {
		List<CodeactTool> filtered = CodeactToolFilter.filter(codeactTools, state, language);
		
		logger.info("CodeGeneratorNode#filterTools - 工具筛选完成: total={}, filtered={}",
				codeactTools != null ? codeactTools.size() : 0, filtered.size());
		
		return filtered;
	}

	/**
	 * 从state提取需求
	 */
	private String extractRequirement(OverAllState state) {
		return state.value("requirement", String.class)
				.or(() -> state.value("task_description", String.class))
				.orElse("");
	}

	/**
	 * 从state提取函数名
	 */
	private String extractFunctionName(OverAllState state) {
		return state.value("function_name", String.class)
				.orElse("generated_function");
	}

	/**
	 * 从state提取参数列表
	 */
	@SuppressWarnings("unchecked")
	private List<String> extractParameters(OverAllState state) {
		return state.value("parameters", List.class)
				.orElse(new ArrayList<>());
	}

	/**
	 * 从state提取历史生成的代码
	 */
	@SuppressWarnings("unchecked")
	private List<String> extractHistoryCode(OverAllState state) {
		// 尝试从多个可能的 key 中获取历史代码
		return state.value("history_code", List.class)
				.or(() -> state.value("generated_functions", List.class))
				.or(() -> state.value("code_history", List.class))
				.orElse(new ArrayList<>());
	}

	/**
	 * 获取 codeactTools（供 CodeGeneratorSubAgent 在 init_context 节点中注入到 state）
	 */
	public List<CodeactTool> getCodeactTools() {
		return codeactTools;
	}


	/**
	 * 获取 language（供 CodeGeneratorSubAgent 在 init_context 节点中注入到 state）
	 */
	public Language getLanguage() {
		return language;
	}

	/**
	 * 构建系统提示（伪装成一个 Python 文件结构）
	 *
	 * <p>将工具信息组织成完整的 Python 文件格式，让 LLM 像在文件中补充代码一样生成函数。
	 */
	private String buildSystemPrompt(Language language, List<CodeactTool> codeactTools,
									  boolean isCondition, String customPrompt, List<String> historyCode) {
		StringBuilder sb = new StringBuilder();

		// 角色说明
		if (customPrompt != null && !customPrompt.trim().isEmpty()) {
			sb.append(customPrompt).append("\n\n");
		} else {
			sb.append("你是一个专业的 Python 代码生成助手。你的任务是在已有的 Python 文件中补充新的函数。\n\n");
		}

		// 开始构建 Python 文件
		sb.append("```python\n");
		sb.append("# -*- coding: utf-8 -*-\n");
		sb.append("\"\"\"\n");
		sb.append("CodeAct 工具集成模块\n");
		sb.append("\n");
		sb.append("【代码生成规则】\n");
		sb.append("1. 只生成函数定义，不要重复 import 和 class\n");
		sb.append("2. 函数名和参数必须与要求完全一致\n");
		sb.append("3. 调用工具使用 实例名.方法名() 格式\n");
		sb.append("4. 只返回纯代码，不要 ```python 标记\n");
		if (isCondition) {
			sb.append("5. 条件函数必须返回 True 或 False\n");
		} else {
			sb.append("5. 【重要】每个函数必须有 return 语句返回结果\n");
			sb.append("   - 查询/搜索类：return 搜索结果\n");
			sb.append("   - 处理/计算类：return 处理结果\n");
			sb.append("   - 通知/回复类：先执行操作，再 return 操作结果或状态\n");
		}
		sb.append("\"\"\"\n\n");

		// === import 区域 ===
		sb.append("# === Import ===\n");
		sb.append("from typing import Any, List, Dict, Optional\n");
		sb.append("import json\n");
		sb.append("import re\n\n");

		// 按照是否有 class 分组
		Map<String, List<CodeactTool>> toolsByClass = new LinkedHashMap<>();
		List<CodeactTool> globalTools = new ArrayList<>();

		if (codeactTools != null) {
			for (CodeactTool tool : codeactTools) {
				CodeactToolMetadata meta = tool.getCodeactMetadata();
				if (meta.supportedLanguages().contains(language)) {
					String className = meta.targetClassName();
					if (className != null && !className.isEmpty()) {
						toolsByClass.computeIfAbsent(className, k -> new ArrayList<>()).add(tool);
					} else {
						globalTools.add(tool);
					}
				}
			}
		}

		// === Class 定义区域 ===
		sb.append("# === 工具类定义 ===\n\n");

		for (Map.Entry<String, List<CodeactTool>> entry : toolsByClass.entrySet()) {
			String className = entry.getKey();
			List<CodeactTool> tools = entry.getValue();

			// 获取类描述
			String classDescription = tools.get(0).getCodeactMetadata().targetClassDescription();
			if (classDescription == null) {
				classDescription = className + " 工具集";
			}

			// 生成类定义
			sb.append("class ").append(toPascalCase(className)).append(":\n");
			sb.append("    \"\"\"").append(classDescription).append("\"\"\"\n\n");

			// 生成方法定义
			for (CodeactTool tool : tools) {
				appendMethodDefinition(sb, tool);
			}
			sb.append("\n");
		}

		// === 全局函数定义 ===
		if (!globalTools.isEmpty()) {
			sb.append("# === 全局函数 ===\n\n");
			for (CodeactTool tool : globalTools) {
				appendGlobalFunctionDefinition(sb, tool);
			}
			sb.append("\n");
		}

		// === 实例化区域 ===
		sb.append("# === 工具实例（可直接使用）===\n");
		for (String className : toolsByClass.keySet()) {
			sb.append(className).append(" = ").append(toPascalCase(className)).append("()\n");
		}
		sb.append("\n");

		// === 历史代码区（拼接之前生成的函数）===
		sb.append("# === 历史代码 ===\n");
		if (historyCode != null && !historyCode.isEmpty()) {
			for (String code : historyCode) {
				sb.append(code).append("\n\n");
			}
		} else {
			sb.append("# （暂无历史函数）\n");
		}
		sb.append("\n");

		// === 待生成函数区（占位符，在 userMessage 中具体指定）===
		sb.append("# === 待生成函数 ===\n");
		sb.append("# 在此处补充新函数（见下方要求）\n\n");

		sb.append("```\n");

		return sb.toString();
	}

	/**
	 * 将 snake_case 转为 PascalCase
	 */
	private String toPascalCase(String snakeCase) {
		StringBuilder sb = new StringBuilder();
		boolean capitalizeNext = true;
		for (char c : snakeCase.toCharArray()) {
			if (c == '_') {
				capitalizeNext = true;
			} else {
				if (capitalizeNext) {
					sb.append(Character.toUpperCase(c));
					capitalizeNext = false;
				} else {
					sb.append(c);
				}
			}
		}
		return sb.toString();
	}

	/**
	 * 添加类方法定义（带完整的 Google style docstring）
	 */
	private void appendMethodDefinition(StringBuilder sb, CodeactTool tool) {
		String methodName = tool.getName();
		String description = tool.getDescription();
		ParameterTree parameterTree = tool.getParameterTree();

		// 获取 ReturnSchema（优先从 registry 获取观测到的）
		ReturnSchema returnSchema = getReturnSchema(tool);

		logger.debug("CodeGeneratorNode#appendMethodDefinition - reason=生成方法定义, methodName={}, hasReturnSchema={}, hasSuccessShape={}",
				methodName, returnSchema != null,
				returnSchema != null ? returnSchema.getSuccessShape() != null : false);

		// 生成参数签名
		String paramSignature = "";
		if (parameterTree != null && parameterTree.hasParameters()) {
			paramSignature = parameterTree.toPythonSignature();
		} else {
			// 兼容旧方式
			CodeactToolMetadata meta = tool.getCodeactMetadata();
			String signature = meta.codeInvocationTemplate();
			if (signature != null && signature.contains("(")) {
				String params = signature.substring(signature.indexOf('(') + 1);
				if (params.contains(")")) {
					paramSignature = params.substring(0, params.indexOf(')'));
				}
			}
		}

		// 方法定义
		sb.append("    def ").append(methodName).append("(self");
		if (!paramSignature.isEmpty()) {
			sb.append(", ").append(paramSignature);
		}
		sb.append(")");

		// 返回类型
		String returnType = "Any";
		if (returnSchema != null) {
			returnType = returnSchema.getPythonTypeHint();
		}
		sb.append(" -> ").append(returnType);
		sb.append(":\n");

		// 生成完整的 Google style docstring
		sb.append("        \"\"\"").append(description != null ? description : methodName).append("\n");

		// Args 部分
		if (parameterTree != null && parameterTree.hasParameters()) {
			sb.append("\n        Args:\n");
			for (ParameterNode param : parameterTree.getParameters()) {
				sb.append("            ").append(param.getName());
				sb.append(" (").append(param.getPythonTypeHint());
				if (!param.isRequired()) {
					sb.append(", optional");
				}
				sb.append(")");
				if (param.getDescription() != null && !param.getDescription().isEmpty()) {
					sb.append(": ").append(param.getDescription());
				}
				if (param.hasDefaultValue()) {
					sb.append(" 默认值: ").append(param.getDefaultValue());
				}
				sb.append("\n");
			}
		}

		// Returns 部分
		sb.append("\n        Returns:\n");
		sb.append("            ").append(returnType).append(": ");
		if (returnSchema != null && returnSchema.getDescription() != null) {
			sb.append(returnSchema.getDescription());
		} else {
			sb.append("操作结果");
		}
		sb.append("\n");

		// 如果有观测到的 shape，展开字段
		if (returnSchema != null && returnSchema.getSuccessShape() != null) {
			appendShapeDoc(sb, returnSchema.getSuccessShape(), 4);
		}

		sb.append("        \"\"\"\n");
		sb.append("        ...\n\n");
	}

	/**
	 * 获取工具的 ReturnSchema（优先从 registry 获取观测到的）
	 */
	private ReturnSchema getReturnSchema(CodeactTool tool) {
		String toolName = tool.getName();

		// 优先从 registry 获取（包含观测到的 schema）
		if (returnSchemaRegistry != null) {
			logger.info("CodeGeneratorNode#getReturnSchema - reason=开始查询schema, registryHashCode={}, toolName={}, allToolsWithSchema={}",
					System.identityHashCode(returnSchemaRegistry), toolName, returnSchemaRegistry.getToolsWithSchema());
			ReturnSchema observed = returnSchemaRegistry.getSchema(toolName).orElse(null);
			if (observed != null) {
				logger.info("CodeGeneratorNode#getReturnSchema - reason=从registry获取到schema, toolName={}, sampleCount={}, hasSuccessShape={}",
						toolName, observed.getSampleCount(), observed.getSuccessShape() != null);
				return observed;
			} else {
				logger.info("CodeGeneratorNode#getReturnSchema - reason=registry中未找到schema, toolName={}", toolName);
			}
		} else {
			logger.warn("CodeGeneratorNode#getReturnSchema - reason=returnSchemaRegistry为null, toolName={}", toolName);
		}

		// 其次使用工具声明的 schema
		ReturnSchema declared = tool.getDeclaredReturnSchema();
		logger.debug("CodeGeneratorNode#getReturnSchema - reason=使用声明的schema, toolName={}, hasDeclared={}",
				toolName, declared != null);
		return declared;
	}

	/**
	 * 生成 shape 文档（递归展开嵌套结构）
	 */
	private void appendShapeDoc(StringBuilder sb, com.alibaba.assistant.agent.common.tools.definition.ShapeNode shape, int indentLevel) {
		// 限制最大递归深度，避免无限展开
		if (indentLevel > 8) {
			return;
		}

		String indent = "    ".repeat(indentLevel);

		if (shape instanceof com.alibaba.assistant.agent.common.tools.definition.ObjectShapeNode objShape) {
			for (Map.Entry<String, com.alibaba.assistant.agent.common.tools.definition.ShapeNode> entry : objShape.getFields().entrySet()) {
				String fieldName = entry.getKey();
				com.alibaba.assistant.agent.common.tools.definition.ShapeNode fieldShape = entry.getValue();
				sb.append(indent).append("- ").append(fieldName);
				sb.append(" (").append(fieldShape.getPythonTypeHint());
				if (fieldShape.isOptional()) {
					sb.append(", optional");
				}
				sb.append(")");
				if (fieldShape.getDescription() != null) {
					sb.append(": ").append(fieldShape.getDescription());
				}
				sb.append("\n");

				// 递归展开嵌套对象
				if (fieldShape instanceof com.alibaba.assistant.agent.common.tools.definition.ObjectShapeNode nestedObj) {
					if (!nestedObj.getFields().isEmpty()) {
						appendShapeDoc(sb, nestedObj, indentLevel + 1);
					}
				}
				// 递归展开数组中的对象
				else if (fieldShape instanceof com.alibaba.assistant.agent.common.tools.definition.ArrayShapeNode arrShape) {
					com.alibaba.assistant.agent.common.tools.definition.ShapeNode itemShape = arrShape.getItemShape();
					if (itemShape instanceof com.alibaba.assistant.agent.common.tools.definition.ObjectShapeNode itemObj) {
						if (!itemObj.getFields().isEmpty()) {
							sb.append(indent).append("    ").append("每项包含:\n");
							appendShapeDoc(sb, itemObj, indentLevel + 2);
						}
					}
				}
			}
		} else if (shape instanceof com.alibaba.assistant.agent.common.tools.definition.ArrayShapeNode arrShape) {
			com.alibaba.assistant.agent.common.tools.definition.ShapeNode itemShape = arrShape.getItemShape();
			sb.append(indent).append("列表，每项为 ").append(itemShape.getPythonTypeHint()).append("\n");
			if (itemShape instanceof com.alibaba.assistant.agent.common.tools.definition.ObjectShapeNode itemObj) {
				if (!itemObj.getFields().isEmpty()) {
					appendShapeDoc(sb, itemObj, indentLevel + 1);
				}
			}
		}
	}

	/**
	 * 添加全局函数定义（带完整的 Google style docstring）
	 */
	private void appendGlobalFunctionDefinition(StringBuilder sb, CodeactTool tool) {
		String functionName = tool.getName();
		String description = tool.getDescription();
		ParameterTree parameterTree = tool.getParameterTree();

		// 获取 ReturnSchema
		ReturnSchema returnSchema = getReturnSchema(tool);

		// 生成函数签名
		String paramSignature = "";
		if (parameterTree != null && parameterTree.hasParameters()) {
			paramSignature = parameterTree.toPythonSignature();
		} else {
			// 兼容旧方式
			CodeactToolMetadata meta = tool.getCodeactMetadata();
			String signature = meta.codeInvocationTemplate();
			if (signature != null && signature.contains("(")) {
				String params = signature.substring(signature.indexOf('(') + 1);
				if (params.contains(")")) {
					paramSignature = params.substring(0, params.indexOf(')'));
				}
			}
		}

		sb.append("def ").append(functionName).append("(");
		sb.append(paramSignature);
		sb.append(")");

		// 返回类型
		String returnType = "Any";
		if (returnSchema != null) {
			returnType = returnSchema.getPythonTypeHint();
		}
		sb.append(" -> ").append(returnType);
		sb.append(":\n");

		// 生成完整的 Google style docstring
		sb.append("    \"\"\"").append(description != null ? description : "全局函数").append("\n");

		// Args 部分
		if (parameterTree != null && parameterTree.hasParameters()) {
			sb.append("\n    Args:\n");
			for (ParameterNode param : parameterTree.getParameters()) {
				sb.append("        ").append(param.getName());
				sb.append(" (").append(param.getPythonTypeHint());
				if (!param.isRequired()) {
					sb.append(", optional");
				}
				sb.append(")");
				if (param.getDescription() != null && !param.getDescription().isEmpty()) {
					sb.append(": ").append(param.getDescription());
				}
				if (param.hasDefaultValue()) {
					sb.append(" 默认值: ").append(param.getDefaultValue());
				}
				sb.append("\n");
			}
		}

		// Returns 部分
		sb.append("\n    Returns:\n");
		sb.append("        ").append(returnType).append(": ");
		if (returnSchema != null && returnSchema.getDescription() != null) {
			sb.append(returnSchema.getDescription());
		} else {
			sb.append("操作结果");
		}
		sb.append("\n");

		// 如果有观测到的 shape，展开字段
		if (returnSchema != null && returnSchema.getSuccessShape() != null) {
			appendShapeDoc(sb, returnSchema.getSuccessShape(), 3);
		}

		sb.append("    \"\"\"\n");
		sb.append("    ...\n\n");
	}

	/**
	 * 构建用户消息（明确指定函数签名，营造"补充代码"的感觉）
	 */
	private String buildUserMessage(String requirement, String functionName,
									 List<String> parameters, boolean isCondition) {
		StringBuilder sb = new StringBuilder();

		sb.append("请在上面的 Python 文件中补充以下函数：\n\n");

		// 构建函数签名
		sb.append("```python\n");
		sb.append("def ").append(functionName).append("(");

		if (parameters != null && !parameters.isEmpty()) {
			sb.append(String.join(", ", parameters));
		}
		// 不使用 **kwargs，让 LLM 根据需求自行决定参数

		sb.append(")");
		if (isCondition) {
			sb.append(" -> bool");
		}
		sb.append(":\n");
		sb.append("    \"\"\"\n");
		sb.append("    ").append(requirement).append("\n");
		if (isCondition) {
			sb.append("    返回: True 或 False\n");
		}
		sb.append("    \"\"\"\n");
		sb.append("    # TODO: 在此实现函数逻辑\n");
		sb.append("    ...\n");
		sb.append("```\n\n");

		sb.append("请根据需求描述，实现上述函数的完整代码。\n");
		sb.append("直接输出函数代码，不要包含 ```python 标记。");

		return sb.toString();
	}

	/**
	 * 通过拦截器链执行模型调用
	 */
	private ModelResponse executeWithInterceptors(ModelRequest request) {
		// 构建拦截器链（参考AgentLlmNode的实现）
		ModelCallHandler handler = (req) -> {
			ChatResponse chatResponse = chatModel.call(new Prompt(req.getMessages()));
			AssistantMessage assistantMessage = (AssistantMessage) chatResponse.getResult().getOutput();
			return ModelResponse.of(assistantMessage, chatResponse);
		};

		if (modelInterceptors != null && !modelInterceptors.isEmpty()) {
			ModelCallHandler chainedHandler = InterceptorChain.chainModelInterceptors(modelInterceptors, handler);
			return chainedHandler.call(request);
		} else {
			return handler.call(request);
		}
	}

	/**
	 * 从响应中提取代码
	 */
	private String extractCodeFromResponse(ModelResponse response) {
		if (response == null || response.getMessage() == null) {
			throw new IllegalStateException("Model response is null");
		}

		Object message = response.getMessage();
		String content;
		if (message instanceof AssistantMessage) {
			content = ((AssistantMessage) message).getText();
		} else {
			content = message.toString();
		}

		if (content == null || content.trim().isEmpty()) {
			throw new IllegalStateException("Generated code is empty");
		}

		// 移除markdown标记（如果有）
		String code = content.trim();
		if (code.startsWith("```")) {
			// 去掉 ```python 或 ```java 等标记
			int firstNewLine = code.indexOf('\n');
			if (firstNewLine > 0) {
				code = code.substring(firstNewLine + 1);
			}
			// 去掉末尾的 ```
			if (code.endsWith("```")) {
				code = code.substring(0, code.length() - 3);
			}
			code = code.trim();
		}

		return code;
	}
}

