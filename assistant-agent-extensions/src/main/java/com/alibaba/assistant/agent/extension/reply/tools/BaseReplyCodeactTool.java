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
package com.alibaba.assistant.agent.extension.reply.tools;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeExample;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.DefaultCodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.ReplyCodeactTool;
import com.alibaba.assistant.agent.common.tools.ToolContextHelper;
import com.alibaba.assistant.agent.common.tools.definition.CodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.DefaultCodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.ParameterNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.common.tools.definition.ParameterType;
import com.alibaba.assistant.agent.extension.reply.config.ReplyToolConfig;
import com.alibaba.assistant.agent.extension.reply.model.ChannelExecutionContext;
import com.alibaba.assistant.agent.extension.reply.model.ParameterSchema;
import com.alibaba.assistant.agent.extension.reply.model.ReplyResult;
import com.alibaba.assistant.agent.extension.reply.spi.ReplyChannelDefinition;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.*;

/**
 * Reply 工具的 CodeactTool 基础实现。
 *
 * <p>将原有的 ReplyTool 改造为实现 CodeactTool 接口，支持 React 和 CodeAct 两个阶段。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class BaseReplyCodeactTool implements ReplyCodeactTool {

	private static final Logger log = LoggerFactory.getLogger(BaseReplyCodeactTool.class);

	private final String toolName;

	private final String description;

	@JsonIgnore
	private final ReplyChannelDefinition channel;

	@JsonIgnore
	private final ReplyToolConfig config;

	private final ParameterSchema parameterSchema;

	private final CodeactToolMetadata codeactMetadata;

	private final ToolDefinition toolDefinition;

	private final CodeactToolDefinition codeactDefinition;

	@JsonIgnore
	private final ObjectMapper objectMapper;

	private final ReplyChannelType channelType;

	/**
	 * 构造 Reply CodeactTool 实例。
	 * @param toolName 工具名称
	 * @param description 工具描述
	 * @param channel 渠道定义
	 * @param config 工具配置
	 * @param parameterSchema 参数模式
	 * @param channelType 渠道类型
	 */
	public BaseReplyCodeactTool(String toolName, String description, ReplyChannelDefinition channel,
			ReplyToolConfig config, ParameterSchema parameterSchema, ReplyChannelType channelType) {
		this.toolName = toolName;
		this.description = description;
		this.channel = channel;
		this.config = config;
		this.parameterSchema = parameterSchema;
		this.channelType = channelType;
		this.objectMapper = new ObjectMapper();

		// 构建 ToolDefinition
		this.toolDefinition = buildToolDefinition();

		// 构建 CodeactToolDefinition（包含 ParameterTree）
		this.codeactDefinition = buildCodeactDefinition();

		// 构建 CodeactToolMetadata
		this.codeactMetadata = buildCodeactMetadata();
	}

	@Override
	public String call(String toolInput) {
		return call(toolInput, null);
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		log.debug("BaseReplyCodeactTool#call - reason=开始执行回复工具, toolName={}, toolInput={}", toolName,
				toolInput);

		try {
			// 解析 JSON 输入参数
			Map<String, Object> params = objectMapper.readValue(toolInput, Map.class);

			// 执行回复逻辑
			ReplyResult result = execute(params, toolContext);

			// 将结果序列化为 JSON
			String resultJson = objectMapper.writeValueAsString(result);

			log.info("BaseReplyCodeactTool#call - reason=回复工具执行成功, toolName={}, success={}", toolName,
					result.isSuccess());

			return resultJson;
		}
		catch (Exception e) {
			log.error("BaseReplyCodeactTool#call - reason=回复工具执行失败, toolName={}, error={}", toolName,
					e.getMessage(), e);

			// 返回失败结果
			try {
				ReplyResult errorResult = ReplyResult.failure("Execution failed: " + e.getMessage());
				return objectMapper.writeValueAsString(errorResult);
			}
			catch (Exception ex) {
				return "{\"success\":false,\"message\":\"Failed to serialize error result\"}";
			}
		}
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
	public ReplyChannelType getChannelType() {
		return channelType;
	}

	/**
	 * 执行回复工具。
	 *
	 * @param params 参数
	 * @param toolContext 工具上下文
	 * @return 执行结果
	 */
	protected ReplyResult execute(Map<String, Object> params, ToolContext toolContext) {
		try {
			// 应用参数映射
			Map<String, Object> mappedParams = applyParameterMapping(params);

			// 应用默认参数
			applyDefaults(mappedParams);

			// 验证参数
			if (parameterSchema != null) {
				parameterSchema.validate(mappedParams);
			}

			// 构建执行上下文
			ChannelExecutionContext context = buildExecutionContext(toolContext);

			// 执行渠道逻辑
			ReplyResult result = channel.execute(context, mappedParams);

			log.info("BaseReplyCodeactTool#execute - reason=渠道执行成功, toolName={}, channelCode={}", toolName,
					channel.getChannelCode());

			return result;
		}
		catch (Exception e) {
			log.error("BaseReplyCodeactTool#execute - reason=渠道执行失败, toolName={}, error={}", toolName,
					e.getMessage(), e);
			return ReplyResult.failure("Execution failed: " + e.getMessage());
		}
	}

	/**
	 * 构建 ToolDefinition。
	 */
	private ToolDefinition buildToolDefinition() {
		// 从 ParameterSchema 构建 inputSchema
		String inputSchema = buildInputSchemaFromParameterSchema();

		return ToolDefinition.builder().name(toolName).description(description).inputSchema(inputSchema).build();
	}

	/**
	 * 构建 CodeactToolDefinition（包含结构化 ParameterTree）。
	 */
	private CodeactToolDefinition buildCodeactDefinition() {
		String inputSchema = toolDefinition.inputSchema();

		// 从 parameterSchema 构建 ParameterTree
		ParameterTree.Builder treeBuilder = ParameterTree.builder().rawInputSchema(inputSchema);

		if (parameterSchema != null && !parameterSchema.getParameters().isEmpty()) {
			for (ParameterSchema.ParameterDef param : parameterSchema.getParameters()) {
				ParameterType type = convertToParameterType(param.getType());

				ParameterNode node = ParameterNode.builder()
					.name(param.getName())
					.type(type)
					.description(param.getDescription())
					.required(param.isRequired())
					.defaultValue(param.getDefaultValue())
					.build();

				treeBuilder.addParameter(node);
				if (param.isRequired()) {
					treeBuilder.addRequiredName(param.getName());
				}
			}
		}

		return DefaultCodeactToolDefinition.builder()
			.name(toolName)
			.description(description)
			.inputSchema(inputSchema)
			.parameterTree(treeBuilder.build())
			.returnDescription("回复操作结果，包含成功状态和消息")
			.returnTypeHint("Dict[str, Any]")
			.build();
	}

	/**
	 * 将 ParameterSchema.ParameterType 转换为 ParameterType。
	 */
	private ParameterType convertToParameterType(ParameterSchema.ParameterType type) {
		if (type == null) {
			return ParameterType.STRING;
		}
		switch (type) {
			case INTEGER:
				return ParameterType.INTEGER;
			case BOOLEAN:
				return ParameterType.BOOLEAN;
			case ARRAY:
				return ParameterType.ARRAY;
			case OBJECT:
				return ParameterType.OBJECT;
			case STRING:
			default:
				return ParameterType.STRING;
		}
	}

	/**
	 * 构建 CodeactToolMetadata。
	 */
	private CodeactToolMetadata buildCodeactMetadata() {
		List<CodeExample> fewShots = new ArrayList<>();

		// 添加基本的 few-shot 示例
		if (parameterSchema != null && !parameterSchema.getParameters().isEmpty()) {
			StringBuilder exampleCode = new StringBuilder();
			exampleCode.append("# 向用户回复消息\n");
			exampleCode.append(toolName).append("(");

			List<ParameterSchema.ParameterDef> params = parameterSchema.getParameters();
			for (int i = 0; i < params.size(); i++) {
				if (i > 0) {
					exampleCode.append(", ");
				}
				ParameterSchema.ParameterDef param = params.get(i);
				exampleCode.append(param.getName()).append("=\"示例值\"");
			}

			exampleCode.append(")");

			fewShots.add(new CodeExample("向用户发送回复消息", exampleCode.toString(), "成功发送消息到指定渠道"));
		}

		return DefaultCodeactToolMetadata.builder()
			.addSupportedLanguage(Language.PYTHON)
			.targetClassName("reply_tools")
			.targetClassDescription("用户回复相关工具集合")
			.fewShots(fewShots)
			.displayName("回复工具")
			.returnDirect(true)
			.build();
	}

	/**
	 * 从 ParameterSchema 构建 inputSchema JSON。
	 */
	private String buildInputSchemaFromParameterSchema() {
		if (parameterSchema == null || parameterSchema.getParameters().isEmpty()) {
			return "{}";
		}

		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");

		Map<String, Object> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();

		for (ParameterSchema.ParameterDef param : parameterSchema.getParameters()) {
			Map<String, Object> propDef = new LinkedHashMap<>();
			propDef.put("type", param.getType().getJsonType());
			propDef.put("description", param.getDescription());

			if (param.getDefaultValue() != null) {
				propDef.put("default", param.getDefaultValue());
			}

			properties.put(param.getName(), propDef);

			if (param.isRequired()) {
				required.add(param.getName());
			}
		}

		schema.put("properties", properties);
		if (!required.isEmpty()) {
			schema.put("required", required);
		}

		try {
			return objectMapper.writeValueAsString(schema);
		}
		catch (Exception e) {
			log.error("BaseReplyCodeactTool#buildInputSchemaFromParameterSchema - reason=构建inputSchema失败, error={}",
					e.getMessage());
			return "{}";
		}
	}

	/**
	 * 转换参数类型为 JSON Schema 类型。
	 */
	private String convertTypeToJsonSchemaType(String type) {
		if (type == null) {
			return "string";
		}

		String lowerType = type.toLowerCase();
		if (lowerType.contains("int") || lowerType.contains("long")) {
			return "integer";
		}
		else if (lowerType.contains("double") || lowerType.contains("float") || lowerType.contains("number")) {
			return "number";
		}
		else if (lowerType.contains("bool")) {
			return "boolean";
		}
		else if (lowerType.contains("array") || lowerType.contains("list")) {
			return "array";
		}
		else if (lowerType.contains("object") || lowerType.contains("map")) {
			return "object";
		}

		return "string";
	}

	/**
	 * 构建代码调用模板。
	 */
	private String buildCodeInvocationTemplate() {
		if (parameterSchema == null || parameterSchema.getParameters().isEmpty()) {
			return toolName + "()";
		}

		StringBuilder template = new StringBuilder();
		template.append(toolName).append("(");

		List<ParameterSchema.ParameterDef> params = parameterSchema.getParameters();
		for (int i = 0; i < params.size(); i++) {
			if (i > 0) {
				template.append(", ");
			}
			ParameterSchema.ParameterDef param = params.get(i);
			template.append(param.getName()).append(": ");

			String type = param.getType() != null ? param.getType().getJsonType() : "string";
			if (type.equals("string")) {
				template.append("str");
			}
			else if (type.equals("integer")) {
				template.append("int");
			}
			else if (type.equals("boolean")) {
				template.append("bool");
			}
			else {
				template.append("str");
			}
		}

		template.append(")");
		return template.toString();
	}

	/**
	 * 应用参数映射。
	 */
	private Map<String, Object> applyParameterMapping(Map<String, Object> params) {
		if (config == null || config.getParameterMapping() == null || config.getParameterMapping().isEmpty()) {
			return new HashMap<>(params);
		}

		Map<String, Object> mappedParams = new HashMap<>();
		for (Map.Entry<String, Object> entry : params.entrySet()) {
			String mappedKey = config.getParameterMapping().getOrDefault(entry.getKey(), entry.getKey());
			mappedParams.put(mappedKey, entry.getValue());
		}

		return mappedParams;
	}

	/**
	 * 应用默认参数值。
	 */
	private void applyDefaults(Map<String, Object> params) {
		if (config == null || config.getDefaultParameters() == null) {
			return;
		}

		for (Map.Entry<String, Object> entry : config.getDefaultParameters().entrySet()) {
			params.putIfAbsent(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * 构建执行上下文。
	 * 从 ToolContext 中提取 threadId (即 sessionId)、userId 等信息。
	 */
	private ChannelExecutionContext buildExecutionContext(ToolContext toolContext) {
		log.debug("BaseReplyCodeactTool#buildExecutionContext - reason=开始构建上下文, toolName={}, hasToolContext={}",
				toolName, toolContext != null);

		ChannelExecutionContext.Builder builder = ChannelExecutionContext.builder()
				.toolName(toolName)
				.source(ChannelExecutionContext.ExecutionSource.CODEACT);

		if (toolContext == null) {
			log.warn("BaseReplyCodeactTool#buildExecutionContext - reason=toolContext为空, toolName={}", toolName);
			return builder.build();
		}

		// 从 ToolContext 获取 threadId (即 sessionId)
		Optional<String> threadIdOpt = ToolContextHelper.getThreadId(toolContext);
		if (threadIdOpt.isPresent()) {
			String sessionId = threadIdOpt.get();
			builder.sessionId(sessionId);
			log.debug("BaseReplyCodeactTool#buildExecutionContext - reason=获取sessionId成功, sessionId={}", sessionId);
		} else {
			log.warn("BaseReplyCodeactTool#buildExecutionContext - reason=未能获取sessionId(threadId), toolName={}", toolName);
		}

		// 从 metadata 获取 userId 和 traceId
		ToolContextHelper.getFromMetadata(toolContext, "user_id").ifPresent(builder::userId);
		ToolContextHelper.getFromMetadata(toolContext, "trace_id").ifPresent(builder::traceId);

		// 从 metadata 获取所有扩展字段，放入 extensions
		// 这样业务方可以通过 extensions 传递自定义数据
		ToolContextHelper.getAllMetadata(toolContext).ifPresent(metadata -> {
			for (Map.Entry<String, Object> entry : metadata.entrySet()) {
				String key = entry.getKey();
				builder.extension(key, entry.getValue());
			}
		});

		return builder.build();
	}

}

