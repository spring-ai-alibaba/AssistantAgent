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
package com.alibaba.assistant.agent.extension.dynamic.tool;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.DefaultCodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.definition.CodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.DefaultCodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.common.tools.definition.ParameterNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态 CodeAct 工具的抽象基类。
 *
 * <p>提供 required 参数校验、JSON 解析等通用能力。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public abstract class AbstractDynamicCodeactTool implements CodeactTool {

	private static final Logger logger = LoggerFactory.getLogger(AbstractDynamicCodeactTool.class);

	@JsonIgnore
	protected final ObjectMapper objectMapper;

	protected final ToolDefinition toolDefinition;

	protected final CodeactToolMetadata codeactMetadata;

	protected final CodeactToolDefinition codeactDefinition;

	/**
	 * 从 inputSchema 中解析出的 required 字段列表。
	 */
	protected final Set<String> requiredFields;

	protected AbstractDynamicCodeactTool(ObjectMapper objectMapper, ToolDefinition toolDefinition,
			CodeactToolMetadata codeactMetadata) {
		this.objectMapper = objectMapper;
		this.toolDefinition = toolDefinition;
		this.codeactMetadata = codeactMetadata;
		this.requiredFields = parseRequiredFields(toolDefinition.inputSchema());
		this.codeactDefinition = buildCodeactDefinition(toolDefinition);
	}

	/**
	 * 构建 CodeactToolDefinition。
	 */
	private CodeactToolDefinition buildCodeactDefinition(ToolDefinition toolDef) {
		String inputSchema = toolDef.inputSchema();
		ParameterTree parameterTree = parseParameterTree(inputSchema);
        String desc = toolDef.description();
        if (desc.contains("\nReturn schema: \n")) {
            desc = desc.substring(0, desc.indexOf("\nReturn schema: \n"));
        }
		return DefaultCodeactToolDefinition.builder()
			.name(toolDef.name())
			.description(desc)
			.inputSchema(inputSchema)
			.parameterTree(parameterTree)
			.returnDescription("工具执行结果")
			.returnTypeHint("Dict[str, Any]")
			.build();
	}

	/**
	 * 解析 inputSchema 为 ParameterTree。
	 */
	private ParameterTree parseParameterTree(String inputSchema) {
		if (inputSchema == null || inputSchema.isEmpty()) {
			return ParameterTree.empty();
		}

		try {
			JsonNode root = objectMapper.readTree(inputSchema);
			ParameterTree.Builder builder = ParameterTree.builder().rawInputSchema(inputSchema);

			// 解析 required 字段
			Set<String> requiredNames = new HashSet<>();
			JsonNode requiredNode = root.get("required");
			if (requiredNode != null && requiredNode.isArray()) {
				for (JsonNode reqName : requiredNode) {
					requiredNames.add(reqName.asText());
				}
			}
			builder.requiredNames(requiredNames);

			// 解析 properties
			JsonNode propertiesNode = root.get("properties");
			if (propertiesNode != null && propertiesNode.isObject()) {
				Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
				while (fields.hasNext()) {
					Map.Entry<String, JsonNode> field = fields.next();
					String paramName = field.getKey();
					JsonNode paramNode = field.getValue();

					ParameterNode parameterNode = parseParameterNode(paramName, paramNode,
							requiredNames.contains(paramName));
					builder.addParameter(parameterNode);
				}
			}

			logger.debug("AbstractDynamicCodeactTool#parseParameterTree - reason=解析ParameterTree成功, parameterCount={}",
					builder.build().getParameterCount());
			return builder.build();
		}
		catch (Exception e) {
			logger.warn("AbstractDynamicCodeactTool#parseParameterTree - reason=解析ParameterTree失败, error={}",
					e.getMessage());
			return ParameterTree.builder().rawInputSchema(inputSchema).build();
		}
	}

	/**
	 * 解析单个参数节点。
	 */
	private ParameterNode parseParameterNode(String name, JsonNode node, boolean required) {
		ParameterNode.Builder builder = ParameterNode.builder().name(name).required(required);

		// 解析类型
		String typeStr = node.has("type") ? node.get("type").asText() : null;
		ParameterType type = ParameterType.fromJsonSchemaType(typeStr);
		builder.type(type);

		// 解析描述
		if (node.has("description")) {
			builder.description(node.get("description").asText());
		}

		// 解析默认值
		if (node.has("default") && !node.get("default").isNull()) {
			JsonNode defaultNode = node.get("default");
			if (defaultNode.isTextual()) {
				builder.defaultValue(defaultNode.asText());
			}
			else if (defaultNode.isInt()) {
				builder.defaultValue(defaultNode.asInt());
			}
			else if (defaultNode.isBoolean()) {
				builder.defaultValue(defaultNode.asBoolean());
			}
		}

		// 解析枚举值
		if (node.has("enum") && node.get("enum").isArray()) {
			List<Object> enumValues = new ArrayList<>();
			for (JsonNode enumVal : node.get("enum")) {
				if (enumVal.isTextual()) {
					enumValues.add(enumVal.asText());
				}
				else {
					enumValues.add(enumVal.toString());
				}
			}
			builder.enumValues(enumValues);
		}

		return builder.build();
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return toolDefinition;
	}

	@Override
	public CodeactToolDefinition getCodeactDefinition() {
		return codeactDefinition;
	}

	@Override
	public CodeactToolMetadata getCodeactMetadata() {
		return codeactMetadata;
	}

	@Override
	public String call(String toolInput) {
		return call(toolInput, null);
	}

	@Override
	public String call(String toolInput, @Nullable ToolContext toolContext) {
		String toolName = toolDefinition.name();
		logger.info("AbstractDynamicCodeactTool#call - reason=开始调用动态工具, toolName={}, inputLength={}",
				toolName, toolInput != null ? toolInput.length() : 0);

		try {
			// 解析输入参数
			Map<String, Object> args = parseInput(toolInput);

			// 校验 required 字段
			validateRequired(args);

			// 执行实际调用
			long startTime = System.currentTimeMillis();
			String result = doCall(args, toolContext);
			long costMs = System.currentTimeMillis() - startTime;

			logger.info("AbstractDynamicCodeactTool#call - reason=动态工具调用成功, toolName={}, costMs={}, resultLength={}",
					toolName, costMs, result != null ? result.length() : 0);

			return result;
		}
		catch (Exception e) {
			logger.error("AbstractDynamicCodeactTool#call - reason=动态工具调用失败, toolName={}, error={}",
					toolName, e.getMessage(), e);
			return buildErrorResponse(e.getMessage());
		}
	}

	/**
	 * 实际执行调用的抽象方法，由子类实现。
	 *
	 * @param args 解析后的参数
	 * @param toolContext 工具上下文
	 * @return 调用结果
	 * @throws Exception 调用异常
	 */
	protected abstract String doCall(Map<String, Object> args, @Nullable ToolContext toolContext) throws Exception;

	/**
	 * 解析输入 JSON 为 Map。
	 *
	 * @param toolInput 输入 JSON 字符串
	 * @return 解析后的 Map
	 * @throws com.fasterxml.jackson.core.JsonProcessingException JSON 解析异常
	 */
	protected Map<String, Object> parseInput(String toolInput) throws JsonProcessingException {
		if (toolInput == null || toolInput.trim().isEmpty()) {
			return Map.of();
		}
		return objectMapper.readValue(toolInput, new TypeReference<Map<String, Object>>() {});
	}

	/**
	 * 校验 required 字段是否存在。
	 *
	 * @param args 参数 Map
	 * @throws IllegalArgumentException 缺少必填字段
	 */
	protected void validateRequired(Map<String, Object> args) {
		List<String> missingFields = new ArrayList<>();

		for (String field : requiredFields) {
			if (!args.containsKey(field) || args.get(field) == null) {
				missingFields.add(field);
			}
		}

		if (!missingFields.isEmpty()) {
			throw new IllegalArgumentException("Missing required fields: " + missingFields);
		}
	}

	/**
	 * 从 inputSchema 解析 required 字段列表。
	 *
	 * @param inputSchema JSON Schema 字符串
	 * @return required 字段集合
	 */
	protected Set<String> parseRequiredFields(String inputSchema) {
		Set<String> required = new HashSet<>();

		if (inputSchema == null || inputSchema.isEmpty()) {
			return required;
		}

		try {
			JsonNode root = objectMapper.readTree(inputSchema);
			JsonNode requiredNode = root.get("required");

			if (requiredNode != null && requiredNode.isArray()) {
				for (JsonNode field : requiredNode) {
					required.add(field.asText());
				}
			}
		}
		catch (Exception e) {
			logger.warn("AbstractDynamicCodeactTool#parseRequiredFields - reason=解析inputSchema失败, error={}",
					e.getMessage());
		}

		return required;
	}

	/**
	 * 构建错误响应 JSON。
	 *
	 * @param message 错误信息
	 * @return 错误响应 JSON 字符串
	 */
	protected String buildErrorResponse(String message) {
		return "{\"error\": \"" + escapeJson(message) + "\"}";
	}

	/**
	 * 转义 JSON 字符串中的特殊字符。
	 *
	 * @param str 原始字符串
	 * @return 转义后的字符串
	 */
	protected String escapeJson(String str) {
		if (str == null) {
			return "";
		}
		return str.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	/**
	 * 构建 CodeactToolMetadata 的辅助方法。
	 *
	 * @param targetClassName 目标类名
	 * @param targetClassDescription 目标类描述
	 * @param codeInvocationTemplate 代码调用模板
	 * @return CodeactToolMetadata 实例
	 */
	protected static CodeactToolMetadata buildMetadata(String targetClassName, String targetClassDescription,
			String codeInvocationTemplate) {
		return DefaultCodeactToolMetadata.builder()
				.addSupportedLanguage(Language.PYTHON)
				.targetClassName(targetClassName)
				.targetClassDescription(targetClassDescription)
				.codeInvocationTemplate(codeInvocationTemplate)
				.fewShots(new ArrayList<>())
				.returnDirect(false)
				.build();
	}

}

