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
package com.alibaba.assistant.agent.extension.search.tools;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeExample;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.DefaultCodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.SearchCodeactTool;
import com.alibaba.assistant.agent.common.tools.definition.CodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.DefaultCodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.ParameterNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.common.tools.definition.ParameterType;
import com.alibaba.assistant.agent.extension.search.model.SearchRequest;
import com.alibaba.assistant.agent.extension.search.model.SearchResultItem;
import com.alibaba.assistant.agent.extension.search.model.SearchResultSet;
import com.alibaba.assistant.agent.extension.search.model.SearchSourceType;
import com.alibaba.assistant.agent.extension.search.spi.SearchProvider;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.*;

/**
 * Search 工具的 CodeactTool 基础实现。
 *
 * <p>将 SearchProvider 适配为实现 SearchCodeactTool 接口，支持 React 和 CodeAct 两个阶段。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class BaseSearchCodeactTool implements SearchCodeactTool {

	private static final Logger log = LoggerFactory.getLogger(BaseSearchCodeactTool.class);

	private final String toolName;

	private final String description;

	@JsonIgnore
	private final SearchProvider searchProvider;

	private final SearchSourceType sourceType;

	private final SearchScope defaultScope;

	private final CodeactToolMetadata codeactMetadata;

	private final ToolDefinition toolDefinition;

	private final CodeactToolDefinition codeactDefinition;

	@JsonIgnore
	private final ObjectMapper objectMapper;

	/**
	 * 构造 Search CodeactTool 实例。
	 * @param toolName 工具名称
	 * @param description 工具描述
	 * @param searchProvider 搜索提供者
	 * @param sourceType 搜索源类型
	 * @param defaultScope 默认搜索范围
	 */
	public BaseSearchCodeactTool(String toolName, String description, SearchProvider searchProvider,
			SearchSourceType sourceType, SearchScope defaultScope) {
		this.toolName = toolName;
		this.description = description;
		this.searchProvider = searchProvider;
		this.sourceType = sourceType;
		this.defaultScope = defaultScope;
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
		log.debug("BaseSearchCodeactTool#call - reason=开始执行搜索工具, toolName={}, toolInput={}", toolName,
				toolInput);

		try {
			// 解析 JSON 输入参数
			Map<String, Object> params = objectMapper.readValue(toolInput, Map.class);

			// 执行搜索逻辑
			SearchResultSet resultSet = executeSearch(params, toolContext);

			// 将结果序列化为 JSON
			String resultJson = objectMapper.writeValueAsString(resultSet);

			log.info("BaseSearchCodeactTool#call - reason=搜索工具执行成功, toolName={}, resultCount={}", toolName,
					resultSet.getItems().size());

			return resultJson;
		}
		catch (Exception e) {
			log.error("BaseSearchCodeactTool#call - reason=搜索工具执行失败, toolName={}, error={}", toolName,
					e.getMessage(), e);

			// 返回失败结果
			try {
				SearchResultSet errorResult = SearchResultSet.empty("Search failed: " + e.getMessage());
				return objectMapper.writeValueAsString(errorResult);
			}
			catch (Exception ex) {
				return "{\"success\":false,\"message\":\"Failed to serialize error result\",\"items\":[]}";
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
	public SearchScope getDefaultScope() {
		return defaultScope;
	}

	/**
	 * 构建 CodeactToolDefinition（包含结构化 ParameterTree）。
	 */
	private CodeactToolDefinition buildCodeactDefinition() {
		String inputSchema = toolDefinition.inputSchema();

		// 构建 ParameterTree
		ParameterTree parameterTree = ParameterTree.builder()
			.rawInputSchema(inputSchema)
			.addParameter(ParameterNode.builder()
				.name("query")
				.type(ParameterType.STRING)
				.description("搜索查询关键词")
				.required(true)
				.build())
			.addParameter(ParameterNode.builder()
				.name("limit")
				.type(ParameterType.INTEGER)
				.description("返回结果数量限制")
				.required(false)
				.defaultValue(10)
				.build())
			.addRequiredName("query")
			.build();

		return DefaultCodeactToolDefinition.builder()
			.name(toolName)
			.description(description)
			.inputSchema(inputSchema)
			.parameterTree(parameterTree)
			.returnDescription("搜索结果列表，包含匹配的条目")
			.returnTypeHint("Dict[str, Any]")
			.build();
	}

	/**
	 * 执行搜索。
	 *
	 * @param params 参数
	 * @param toolContext 工具上下文
	 * @return 搜索结果集
	 */
	protected SearchResultSet executeSearch(Map<String, Object> params, ToolContext toolContext) {
		try {
			// 构建搜索请求
			SearchRequest request = buildSearchRequest(params);

			// 执行搜索
			List<SearchResultItem> items = searchProvider.search(request);

			log.info("BaseSearchCodeactTool#executeSearch - reason=搜索执行成功, toolName={}, sourceType={}, resultCount={}",
					toolName, sourceType, items.size());

			// 构建结果集
			return SearchResultSet.success(items);
		}
		catch (Exception e) {
			log.error("BaseSearchCodeactTool#executeSearch - reason=搜索执行失败, toolName={}, error={}", toolName,
					e.getMessage(), e);
			return SearchResultSet.empty("Search execution failed: " + e.getMessage());
		}
	}

	/**
	 * 构建 ToolDefinition。
	 */
	private ToolDefinition buildToolDefinition() {
		// 构建 inputSchema
		String inputSchema = buildInputSchema();

		return ToolDefinition.builder().name(toolName).description(description).inputSchema(inputSchema).build();
	}

	/**
	 * 构建 CodeactToolMetadata。
	 */
	private CodeactToolMetadata buildCodeactMetadata() {
		List<CodeExample> fewShots = new ArrayList<>();

		// 添加基本的 few-shot 示例
		StringBuilder exampleCode = new StringBuilder();
		exampleCode.append("# 搜索相关信息\n");
		exampleCode.append("result = ").append(toolName).append("(query=\"搜索关键词\")\n");
		exampleCode.append("print(f\"找到 {len(result['items'])} 条结果\")");

		fewShots.add(new CodeExample("执行搜索并获取结果", exampleCode.toString(), "返回搜索结果列表"));

		return DefaultCodeactToolMetadata.builder()
			.addSupportedLanguage(Language.PYTHON)
			.targetClassName("search_tools")
			.targetClassDescription("搜索相关工具集合")
			.fewShots(fewShots)
			.displayName("搜索工具")
			.returnDirect(false)
			.build();
	}

	/**
	 * 构建 inputSchema JSON。
	 */
	private String buildInputSchema() {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");

		Map<String, Object> properties = new LinkedHashMap<>();

		// query 参数
		Map<String, Object> queryProp = new LinkedHashMap<>();
		queryProp.put("type", "string");
		queryProp.put("description", "搜索查询关键词");
		properties.put("query", queryProp);

		// limit 参数（可选）
		Map<String, Object> limitProp = new LinkedHashMap<>();
		limitProp.put("type", "integer");
		limitProp.put("description", "返回结果数量限制");
		limitProp.put("default", 10);
		properties.put("limit", limitProp);

		schema.put("properties", properties);
		schema.put("required", Arrays.asList("query"));

		try {
			return objectMapper.writeValueAsString(schema);
		}
		catch (Exception e) {
			log.error("BaseSearchCodeactTool#buildInputSchema - reason=构建inputSchema失败, error={}",
					e.getMessage());
			return "{}";
		}
	}


	/**
	 * 构建搜索请求。
	 */
	private SearchRequest buildSearchRequest(Map<String, Object> params) {
		String query = (String) params.get("query");
		Integer limit = params.containsKey("limit") ? ((Number) params.get("limit")).intValue() : 10;

		SearchRequest request = new SearchRequest();
		request.setQuery(query);
		request.getSourceTypes().add(sourceType);
		request.setTopK(limit);

		return request;
	}

}

