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
package com.alibaba.assistant.agent.extension.dynamic.mcp;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.DefaultCodeactToolMetadata;
import com.alibaba.assistant.agent.extension.dynamic.naming.NameNormalizer;
import com.alibaba.assistant.agent.extension.dynamic.spi.DynamicCodeactToolFactory;
import com.alibaba.assistant.agent.extension.dynamic.spi.DynamicToolFactoryContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 动态工具工厂。
 *
 * <p>复用 MCP Client Boot Starter 提供的 ToolCallbackProvider，
 * 将其中的 ToolCallback 适配为 CodeactTool，仅注入 CodeAct 阶段。
 *
 * <p>工厂会自动从 ToolCallback 中推断类名和方法名，无需手动配置 McpServerSpec。
 * 如果需要自定义类名/描述，可以通过 serverSpecs 传入额外的映射信息。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class McpDynamicToolFactory implements DynamicCodeactToolFactory {

	private static final Logger logger = LoggerFactory.getLogger(McpDynamicToolFactory.class);

	private static final String FACTORY_ID = "mcp";

	/**
	 * MCP Client Boot Starter 提供的 ToolCallbackProvider。
	 */
	private final ToolCallbackProvider toolCallbackProvider;

	/**
	 * MCP Server 元数据映射：connectionName -> McpServerSpec（可选，用于自定义命名）。
	 */
	private final Map<String, McpServerSpec> serverSpecs;

	/**
	 * 默认的 target class name 前缀。
	 */
	private final String defaultTargetClassNamePrefix;

	/**
	 * 默认的 target class description。
	 */
	private final String defaultTargetClassDescription;

	private McpDynamicToolFactory(Builder builder) {
		this.toolCallbackProvider = builder.toolCallbackProvider;
		this.serverSpecs = new HashMap<>(builder.serverSpecs);
		this.defaultTargetClassNamePrefix = builder.defaultTargetClassNamePrefix != null
				? builder.defaultTargetClassNamePrefix : "mcp";
		this.defaultTargetClassDescription = builder.defaultTargetClassDescription != null
				? builder.defaultTargetClassDescription : "MCP 动态工具";
	}

	@Override
	public String factoryId() {
		return FACTORY_ID;
	}

	@Override
	public List<CodeactTool> createTools(DynamicToolFactoryContext context) {
		logger.info("McpDynamicToolFactory#createTools - reason=开始创建MCP动态工具");

		List<CodeactTool> tools = new ArrayList<>();

		if (toolCallbackProvider == null) {
			logger.warn("McpDynamicToolFactory#createTools - reason=ToolCallbackProvider为空，无法创建工具");
			return tools;
		}

		ObjectMapper objectMapper = context.getObjectMapper();
		NameNormalizer nameNormalizer = context.getNameNormalizer();

		// 获取所有 MCP tool callbacks（MCP Client Boot Starter 已自动装配）
		ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();

		if (callbacks == null || callbacks.length == 0) {
			logger.info("McpDynamicToolFactory#createTools - reason=无可用的MCP工具");
			return tools;
		}

		logger.info("McpDynamicToolFactory#createTools - reason=获取到MCP工具, count={}", callbacks.length);

		for (ToolCallback callback : callbacks) {
			try {
				CodeactTool tool = createToolFromCallback(callback, objectMapper, nameNormalizer);
				tools.add(tool);

				logger.debug("McpDynamicToolFactory#createTools - reason=创建MCP工具成功, toolName={}",
						callback.getToolDefinition().name());
			}
			catch (Exception e) {
				logger.error("McpDynamicToolFactory#createTools - reason=创建MCP工具失败, toolName={}, error={}",
						callback.getToolDefinition().name(), e.getMessage(), e);
			}
		}

		logger.info("McpDynamicToolFactory#createTools - reason=MCP动态工具创建完成, totalCount={}", tools.size());

		return tools;
	}

	/**
	 * 从 ToolCallback 创建 CodeactTool。
	 *
	 * <p>类名和方法名的确定逻辑：
	 * <ol>
	 *   <li>如果 callback 实现了 {@link McpServerAwareToolCallback}，使用其提供的 serverName 和 toolName</li>
	 *   <li>否则使用配置的 serverSpecs 或默认前缀</li>
	 * </ol>
	 */
	private CodeactTool createToolFromCallback(ToolCallback callback, ObjectMapper objectMapper,
			NameNormalizer nameNormalizer) {

		ToolDefinition definition = callback.getToolDefinition();
		String toolName = definition.name();
		String inputSchema = definition.inputSchema();

		// 类名：使用配置的 server 名称或默认前缀
		// 归一化后的类名（如 my-mcp-server -> my_mcp_server_tools）
		String targetClassName = nameNormalizer.normalizeClassName(defaultTargetClassNamePrefix) + "_tools";
		String targetClassDescription = defaultTargetClassDescription;

		// 优先检查是否实现了 McpServerAwareToolCallback 接口
		if (callback instanceof McpServerAwareToolCallback serverAwareCallback) {
			String serverName = serverAwareCallback.getServerName();
			String displayName = serverAwareCallback.getServerDisplayName();

			if (serverName != null && !serverName.isEmpty()) {
				targetClassName = nameNormalizer.normalizeClassName(serverName) + "_tools";
				targetClassDescription = displayName != null ? displayName : serverName;
				// 使用原始工具名而不是 ToolDefinition 中的名称
				toolName = serverAwareCallback.getToolName();

				logger.debug("McpDynamicToolFactory#createToolFromCallback - reason=从McpServerAwareToolCallback获取服务器信息, " +
						"serverName={}, toolName={}", serverName, toolName);
			}
		}
		// 如果有自定义的 serverSpecs，尝试匹配（作为后备方案）
		else if (!serverSpecs.isEmpty()) {
			for (Map.Entry<String, McpServerSpec> entry : serverSpecs.entrySet()) {
				McpServerSpec spec = entry.getValue();
				// 使用 serverSpec 中的名称
				targetClassName = nameNormalizer.normalizeClassName(spec.getServerName()) + "_tools";
				targetClassDescription = spec.getDescription();
				break;  // 目前只支持单个 server，使用第一个
			}
		}

		// 方法名：使用工具原始名称归一化后的结果
		// 将驼峰式或其他格式转为 snake_case
		String methodName = normalizeToolNameToMethod(toolName, nameNormalizer);

		// 生成代码调用模板（只包含方法名和参数）
		String codeInvocationTemplate = buildCodeInvocationTemplate(
				methodName, inputSchema, objectMapper);

		// 构建 CodeactToolMetadata
		CodeactToolMetadata metadata = DefaultCodeactToolMetadata.builder()
				.addSupportedLanguage(Language.PYTHON)
				.targetClassName(targetClassName)
				.targetClassDescription(targetClassDescription)
				.codeInvocationTemplate(codeInvocationTemplate)
				.returnDirect(false)
				.build();

		logger.debug("McpDynamicToolFactory#createToolFromCallback - reason=创建工具, toolName={}, className={}, methodName={}",
				toolName, targetClassName, methodName);

		// 创建适配器
		return new McpToolCallbackAdapter(objectMapper, callback, metadata);
	}

	/**
	 * 将工具名转换为方法名。
	 *
	 * <p>只处理中文转拼音和特殊字符，保留原始大小写（不做驼峰转下划线）。
	 */
	private String normalizeToolNameToMethod(String toolName, NameNormalizer nameNormalizer) {
		// 通过 nameNormalizer 只处理中文和特殊字符
		return nameNormalizer.normalizeMethodName(toolName);
	}

	/**
	 * 构建代码调用模板（只包含方法名和参数，不含类名）。
	 * 类名会在输出时由代码生成器自动添加。
	 */
	private String buildCodeInvocationTemplate(String methodName,
			String inputSchema, ObjectMapper objectMapper) {

		StringBuilder template = new StringBuilder();
		template.append(methodName).append("(");

		// 解析参数
		try {
			if (inputSchema != null && !inputSchema.isEmpty()) {
				JsonNode root = objectMapper.readTree(inputSchema);
				JsonNode properties = root.get("properties");

				if (properties != null && properties.isObject()) {
					List<String> params = new ArrayList<>();

					properties.fieldNames().forEachRemaining(fieldName -> {
						JsonNode prop = properties.get(fieldName);
						String type = prop.has("type") ? prop.get("type").asText() : "Any";
						String pythonType = mapToPythonType(type);
						params.add(fieldName + ": " + pythonType);
					});

					template.append(String.join(", ", params));
				}
			}
		}
		catch (Exception e) {
			logger.warn("McpDynamicToolFactory#buildCodeInvocationTemplate - reason=解析inputSchema失败, error={}",
					e.getMessage());
		}

		template.append(") -> Any");

		return template.toString();
	}

	/**
	 * 将 JSON Schema 类型映射为 Python 类型。
	 */
	private String mapToPythonType(String jsonType) {
		return switch (jsonType.toLowerCase()) {
			case "string" -> "str";
			case "integer" -> "int";
			case "number" -> "float";
			case "boolean" -> "bool";
			case "array" -> "list";
			case "object" -> "dict";
			default -> "Any";
		};
	}

	/**
	 * 创建构建器。
	 *
	 * @return 构建器实例
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * 构建器类。
	 */
	public static class Builder {

		private ToolCallbackProvider toolCallbackProvider;

		private final Map<String, McpServerSpec> serverSpecs = new HashMap<>();

		private String defaultTargetClassNamePrefix;

		private String defaultTargetClassDescription;

		/**
		 * 设置 ToolCallbackProvider。
		 *
		 * @param provider ToolCallbackProvider 实例
		 * @return 构建器
		 */
		public Builder toolCallbackProvider(ToolCallbackProvider provider) {
			this.toolCallbackProvider = provider;
			return this;
		}

		/**
		 * 添加 MCP Server 元数据规格。
		 *
		 * @param spec McpServerSpec 实例
		 * @return 构建器
		 */
		public Builder addServerSpec(McpServerSpec spec) {
			this.serverSpecs.put(spec.getConnectionName(), spec);
			return this;
		}

		/**
		 * 添加多个 MCP Server 元数据规格。
		 *
		 * @param specs McpServerSpec 列表
		 * @return 构建器
		 */
		public Builder serverSpecs(List<McpServerSpec> specs) {
			for (McpServerSpec spec : specs) {
				this.serverSpecs.put(spec.getConnectionName(), spec);
			}
			return this;
		}

		/**
		 * 设置默认的 target class name 前缀。
		 *
		 * @param prefix 默认类名前缀，如 "mcp"
		 * @return 构建器
		 */
		public Builder defaultTargetClassNamePrefix(String prefix) {
			this.defaultTargetClassNamePrefix = prefix;
			return this;
		}

		/**
		 * 设置默认的 target class description。
		 *
		 * @param description 默认描述
		 * @return 构建器
		 */
		public Builder defaultTargetClassDescription(String description) {
			this.defaultTargetClassDescription = description;
			return this;
		}

		/**
		 * 构建工厂实例。
		 *
		 * @return McpDynamicToolFactory 实例
		 */
		public McpDynamicToolFactory build() {
			return new McpDynamicToolFactory(this);
		}

	}

}

