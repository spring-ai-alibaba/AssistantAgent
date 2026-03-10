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
package com.alibaba.assistant.agent.extension.dynamic.http;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.DefaultCodeactToolMetadata;
import com.alibaba.assistant.agent.extension.dynamic.naming.NameNormalizer;
import com.alibaba.assistant.agent.extension.dynamic.spi.DynamicCodeactToolFactory;
import com.alibaba.assistant.agent.extension.dynamic.spi.DynamicToolFactoryContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP(OpenAPI) 动态工具工厂。
 *
 * <p>从 OpenAPI 3.1 文档解析指定的 endpoints，生成 CodeactTool。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class HttpDynamicToolFactory implements DynamicCodeactToolFactory {

	private static final Logger logger = LoggerFactory.getLogger(HttpDynamicToolFactory.class);

	private static final String FACTORY_ID = "http";

	private final OpenApiSpec openApiSpec;

	private final List<EndpointSpec> endpoints;

	private HttpDynamicToolFactory(Builder builder) {
		this.openApiSpec = builder.openApiSpec;
		this.endpoints = new ArrayList<>(builder.endpoints);
	}

	@Override
	public String factoryId() {
		return FACTORY_ID;
	}

	@Override
	public List<CodeactTool> createTools(DynamicToolFactoryContext context) {
		logger.info("HttpDynamicToolFactory#createTools - reason=开始创建HTTP动态工具, endpointCount={}",
				endpoints.size());

		List<CodeactTool> tools = new ArrayList<>();

		if (openApiSpec == null || openApiSpec.getContent() == null) {
			logger.warn("HttpDynamicToolFactory#createTools - reason=OpenAPI文档为空，无法创建工具");
			return tools;
		}

		ObjectMapper objectMapper = context.getObjectMapper();
		NameNormalizer nameNormalizer = context.getNameNormalizer();

		try {
			// 解析 OpenAPI 文档
			JsonNode openApiDoc = objectMapper.readTree(openApiSpec.getContent());

			// 获取 baseUrl
			String baseUrl = resolveBaseUrl(openApiDoc);

			// 遍历要暴露的 endpoints
			for (EndpointSpec endpoint : endpoints) {
				try {
					CodeactTool tool = createToolFromEndpoint(
							endpoint, openApiDoc, baseUrl, objectMapper, nameNormalizer);

					if (tool != null) {
						tools.add(tool);
						logger.debug("HttpDynamicToolFactory#createTools - reason=创建HTTP工具成功, endpoint={}",
								endpoint);
					}
				}
				catch (Exception e) {
					logger.error("HttpDynamicToolFactory#createTools - reason=创建HTTP工具失败, endpoint={}, error={}",
							endpoint, e.getMessage(), e);
				}
			}
		}
		catch (Exception e) {
			logger.error("HttpDynamicToolFactory#createTools - reason=解析OpenAPI文档失败, error={}",
					e.getMessage(), e);
		}

		logger.info("HttpDynamicToolFactory#createTools - reason=HTTP动态工具创建完成, totalCount={}", tools.size());

		return tools;
	}

	/**
	 * 解析 baseUrl。
	 */
	private String resolveBaseUrl(JsonNode openApiDoc) {
		// 优先使用配置的 baseUrl
		if (openApiSpec.getBaseUrl() != null && !openApiSpec.getBaseUrl().isEmpty()) {
			return openApiSpec.getBaseUrl();
		}

		// 尝试从 OpenAPI servers 中获取
		JsonNode servers = openApiDoc.get("servers");
		if (servers != null && servers.isArray() && servers.size() > 0) {
			JsonNode firstServer = servers.get(0);
			if (firstServer.has("url")) {
				return firstServer.get("url").asText();
			}
		}

		logger.warn("HttpDynamicToolFactory#resolveBaseUrl - reason=无法解析baseUrl，使用空字符串");
		return "";
	}

	/**
	 * 从 endpoint 创建工具。
	 */
	private CodeactTool createToolFromEndpoint(EndpointSpec endpoint, JsonNode openApiDoc,
			String baseUrl, ObjectMapper objectMapper, NameNormalizer nameNormalizer) {

		String method = endpoint.getMethod();
		String path = endpoint.getPath();

		// 查找对应的 operation
		JsonNode paths = openApiDoc.get("paths");
		if (paths == null || !paths.has(path)) {
			logger.warn("HttpDynamicToolFactory#createToolFromEndpoint - reason=未找到path, path={}", path);
			return null;
		}

		JsonNode pathItem = paths.get(path);
		String methodLower = method.toLowerCase();

		if (!pathItem.has(methodLower)) {
			logger.warn("HttpDynamicToolFactory#createToolFromEndpoint - reason=未找到method, path={}, method={}",
					path, method);
			return null;
		}

		JsonNode operation = pathItem.get(methodLower);

		// 解析 operation 信息
		String operationId = operation.has("operationId")
				? operation.get("operationId").asText()
				: method.toLowerCase() + "_" + path.replace("/", "_").replace("{", "").replace("}", "");

		String summary = operation.has("summary") ? operation.get("summary").asText() : "";
		String description = operation.has("description")
				? operation.get("description").asText()
				: summary;

		// 解析 tags 用于确定类名
		String targetClassName = "http_api";
		String targetClassDescription = "从 OpenAPI 生成的 HTTP API 工具";

		if (operation.has("tags") && operation.get("tags").isArray()) {
			JsonNode tags = operation.get("tags");
			if (tags.size() > 0) {
				String tag = tags.get(0).asText();
				targetClassName = "http_" + nameNormalizer.normalizeClassName(tag);

				// 尝试从 OpenAPI tags 定义获取描述
				JsonNode tagDefs = openApiDoc.get("tags");
				if (tagDefs != null && tagDefs.isArray()) {
					for (JsonNode tagDef : tagDefs) {
						if (tagDef.has("name") && tagDef.get("name").asText().equals(tag)) {
							if (tagDef.has("description")) {
								targetClassDescription = tagDef.get("description").asText();
							}
							break;
						}
					}
				}
			}
		}

		// 生成方法名
		String methodName = nameNormalizer.normalizeMethodName(operationId);

		// 生成工具名（全局唯一）
		String toolName = nameNormalizer.normalizeToolName("http", targetClassName, methodName);

		// 构建 inputSchema（嵌套结构）
		String inputSchema = buildInputSchema(operation, pathItem, path, objectMapper);

		// 构建描述（包含 method + path）
		String toolDescription = description + " [" + method + " " + path + "]";

		// 构建 ToolDefinition
		ToolDefinition toolDefinition = DefaultToolDefinition.builder()
				.name(toolName)
				.description(toolDescription)
				.inputSchema(inputSchema)
				.build();

		// 构建代码调用模板（只包含方法名和参数，类名会在输出时自动添加）
		String codeInvocationTemplate = methodName +
				"(path: dict, query: dict = None, headers: dict = None, body: dict = None) -> Any";

		// 构建 CodeactToolMetadata
		CodeactToolMetadata metadata = DefaultCodeactToolMetadata.builder()
				.addSupportedLanguage(Language.PYTHON)
				.targetClassName(targetClassName)
				.targetClassDescription(targetClassDescription)
				.codeInvocationTemplate(codeInvocationTemplate)
				.returnDirect(false)
				.build();

		// 创建工具
		return new HttpDynamicCodeactTool(
				objectMapper,
				toolDefinition,
				metadata,
				method,
				path,
				baseUrl,
				openApiSpec.getGlobalHeaders(),
				openApiSpec.getTimeoutMs()
		);
	}

	/**
	 * 构建 inputSchema（嵌套结构：path/query/headers/body）。
	 */
	private String buildInputSchema(JsonNode operation, JsonNode pathItem, String path,
			ObjectMapper objectMapper) {

		ObjectNode schema = objectMapper.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = objectMapper.createObjectNode();
		List<String> required = new ArrayList<>();

		// 解析 path 参数
		ObjectNode pathProps = objectMapper.createObjectNode();
		List<String> pathRequired = new ArrayList<>();

		// 解析 query 参数
		ObjectNode queryProps = objectMapper.createObjectNode();

		// 解析 header 参数
		ObjectNode headerProps = objectMapper.createObjectNode();

		// 合并 pathItem 级别和 operation 级别的 parameters
		List<JsonNode> allParameters = new ArrayList<>();

		if (pathItem.has("parameters")) {
			pathItem.get("parameters").forEach(allParameters::add);
		}
		if (operation.has("parameters")) {
			operation.get("parameters").forEach(allParameters::add);
		}

		for (JsonNode param : allParameters) {
			String paramName = param.has("name") ? param.get("name").asText() : "";
			String paramIn = param.has("in") ? param.get("in").asText() : "";
			boolean paramRequired = param.has("required") && param.get("required").asBoolean();
			String paramDescription = param.has("description") ? param.get("description").asText() : "";

			ObjectNode paramSchema = objectMapper.createObjectNode();
			if (param.has("schema")) {
				JsonNode schemaNode = param.get("schema");
				paramSchema.put("type", schemaNode.has("type") ? schemaNode.get("type").asText() : "string");
				if (schemaNode.has("description")) {
					paramSchema.put("description", schemaNode.get("description").asText());
				}
			}
			else {
				paramSchema.put("type", "string");
			}
			if (!paramDescription.isEmpty()) {
				paramSchema.put("description", paramDescription);
			}

			switch (paramIn) {
				case "path":
					pathProps.set(paramName, paramSchema);
					if (paramRequired) {
						pathRequired.add(paramName);
					}
					break;
				case "query":
					queryProps.set(paramName, paramSchema);
					break;
				case "header":
					headerProps.set(paramName, paramSchema);
					break;
			}
		}

		// 添加 path 参数对象
		if (pathProps.size() > 0) {
			ObjectNode pathObj = objectMapper.createObjectNode();
			pathObj.put("type", "object");
			pathObj.set("properties", pathProps);
			if (!pathRequired.isEmpty()) {
				pathObj.set("required", objectMapper.valueToTree(pathRequired));
			}
			properties.set("path", pathObj);
			required.add("path");
		}

		// 添加 query 参数对象
		if (queryProps.size() > 0) {
			ObjectNode queryObj = objectMapper.createObjectNode();
			queryObj.put("type", "object");
			queryObj.set("properties", queryProps);
			properties.set("query", queryObj);
		}

		// 添加 headers 参数对象
		if (headerProps.size() > 0) {
			ObjectNode headerObj = objectMapper.createObjectNode();
			headerObj.put("type", "object");
			headerObj.set("properties", headerProps);
			properties.set("headers", headerObj);
		}

		// 解析 requestBody
		if (operation.has("requestBody")) {
			JsonNode requestBody = operation.get("requestBody");
			boolean bodyRequired = requestBody.has("required") && requestBody.get("required").asBoolean();

			ObjectNode bodySchema = objectMapper.createObjectNode();
			bodySchema.put("type", "object");
			bodySchema.put("description", "Request body");

			if (requestBody.has("content")) {
				JsonNode content = requestBody.get("content");
				// 优先使用 application/json
				if (content.has("application/json")) {
					JsonNode jsonContent = content.get("application/json");
					if (jsonContent.has("schema")) {
						bodySchema = (ObjectNode) jsonContent.get("schema").deepCopy();
					}
				}
			}

			properties.set("body", bodySchema);
			if (bodyRequired) {
				required.add("body");
			}
		}

		schema.set("properties", properties);
		if (!required.isEmpty()) {
			schema.set("required", objectMapper.valueToTree(required));
		}

		try {
			return objectMapper.writeValueAsString(schema);
		}
		catch (Exception e) {
			logger.warn("HttpDynamicToolFactory#buildInputSchema - reason=序列化schema失败, error={}",
					e.getMessage());
			return "{}";
		}
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

		private OpenApiSpec openApiSpec;

		private final List<EndpointSpec> endpoints = new ArrayList<>();

		/**
		 * 设置 OpenAPI 规格。
		 *
		 * @param spec OpenApiSpec 实例
		 * @return 构建器
		 */
		public Builder openApiSpec(OpenApiSpec spec) {
			this.openApiSpec = spec;
			return this;
		}

		/**
		 * 添加要暴露的 endpoint。
		 *
		 * @param endpoint EndpointSpec 实例
		 * @return 构建器
		 */
		public Builder addEndpoint(EndpointSpec endpoint) {
			this.endpoints.add(endpoint);
			return this;
		}

		/**
		 * 设置要暴露的 endpoints。
		 *
		 * @param endpoints EndpointSpec 列表
		 * @return 构建器
		 */
		public Builder endpoints(List<EndpointSpec> endpoints) {
			this.endpoints.clear();
			this.endpoints.addAll(endpoints);
			return this;
		}

		/**
		 * 构建工厂实例。
		 *
		 * @return HttpDynamicToolFactory 实例
		 */
		public HttpDynamicToolFactory build() {
			return new HttpDynamicToolFactory(this);
		}

	}

}

