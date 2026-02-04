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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 统一搜索工具 - 聚合多种搜索源的 CodeactTool 实现。
 *
 * <p>该工具可以同时搜索多个数据源（项目、知识库、Web等），并聚合结果。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class UnifiedSearchCodeactTool implements SearchCodeactTool {

	private static final Logger log = LoggerFactory.getLogger(UnifiedSearchCodeactTool.class);

	private static final String TOOL_NAME = "unified_search";

	private static final String DESCRIPTION = "统一搜索工具，可同时搜索项目、知识库、Web等多个数据源";

	@JsonIgnore
	private final List<SearchProvider> searchProviders;

	private final CodeactToolMetadata codeactMetadata;

	private final ToolDefinition toolDefinition;

	private final CodeactToolDefinition codeactDefinition;

	@JsonIgnore
	private final ObjectMapper objectMapper;

	/**
	 * 构造统一搜索工具。
	 * @param searchProviders 所有可用的搜索提供者
	 */
	public UnifiedSearchCodeactTool(List<SearchProvider> searchProviders) {
		this.searchProviders = searchProviders != null ? searchProviders : new ArrayList<>();
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
		log.debug("UnifiedSearchCodeactTool#call - reason=开始执行统一搜索, toolInput={}", toolInput);

		try {
			// 解析 JSON 输入参数
			Map<String, Object> params = objectMapper.readValue(toolInput, Map.class);

			// 执行统一搜索
			SearchResultSet resultSet = executeUnifiedSearch(params, toolContext);

			// 将结果序列化为 JSON
			String resultJson = objectMapper.writeValueAsString(resultSet);

			log.info("UnifiedSearchCodeactTool#call - reason=统一搜索执行成功, resultCount={}, sourceCount={}",
					resultSet.getItems().size(), resultSet.getMetadata().get("source_count"));

			return resultJson;
		}
		catch (Exception e) {
			log.error("UnifiedSearchCodeactTool#call - reason=统一搜索执行失败, error={}", e.getMessage(), e);

			// 返回失败结果
			try {
				SearchResultSet errorResult = SearchResultSet.empty("Unified search failed: " + e.getMessage());
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
		return SearchScope.UNIFIED;
	}

	/**
	 * 执行统一搜索 - 聚合多个搜索源的结果。
	 */
	private SearchResultSet executeUnifiedSearch(Map<String, Object> params, ToolContext toolContext) {
		String query = (String) params.get("query");
		Integer limit = params.containsKey("limit") ? ((Number) params.get("limit")).intValue() : 10;

		// 解析要搜索的源 keys (可以是类型名，也可以是 provider 名)
		List<String> sourceKeys = parseSourceKeys(params);

		log.info("UnifiedSearchCodeactTool#executeUnifiedSearch - reason=开始统一搜索, query={}, sources={}, limit={}",
				query, sourceKeys, limit);

		// 并行搜索多个数据源
		List<SearchResultItem> allItems = new ArrayList<>();
		int successCount = 0;
		int failureCount = 0;

		for (String key : sourceKeys) {
			try {
				// 1. 尝试作为 Provider Name 匹配
				Optional<SearchProvider> namedProvider = searchProviders.stream()
						.filter(p -> p.getName().equalsIgnoreCase(key))
						.findFirst();

				if (namedProvider.isPresent()) {
					// 按指定 Provider 名称搜索
					SearchProvider provider = namedProvider.get();
					// 当指定具体 Provider 时，尝试使用 CUSTOM 类型，或者该 Provider 支持的第一个类型
					// 这里为了简单，我们传递 CUSTOM 类型，如果 Provider 不处理 SourceType 也没关系
					SearchRequest request = buildSearchRequest(query, SearchSourceType.CUSTOM, limit);

					List<SearchResultItem> items = provider.search(request);
					if (items != null) {
                        allItems.addAll(items);
                    }
					successCount++;
					log.debug("UnifiedSearchCodeactTool#executeUnifiedSearch - reason=指定Provider搜索成功, provider={}, resultCount={}",
							provider.getName(), items.size());
					continue;
				}

				// 2. 尝试作为 SourceType 匹配
				SearchSourceType sourceType;
				try {
					sourceType = SearchSourceType.valueOf(key.toUpperCase());
				} catch (IllegalArgumentException e) {
					log.warn("UnifiedSearchCodeactTool#executeUnifiedSearch - reason=无效的源类型或Provider名称, key={}", key);
					continue;
				}

				// 查找支持该源类型的 provider
				List<SearchProvider> providers = searchProviders.stream()
						.filter(p -> p.supports(sourceType))
						.collect(Collectors.toList());

				if (providers.isEmpty()) {
					log.warn("UnifiedSearchCodeactTool#executeUnifiedSearch - reason=未找到支持的provider, sourceType={}",
							sourceType);
					continue;
				}

				// 使用第一个支持的 provider 进行搜索
				SearchProvider provider = providers.get(0);
				SearchRequest request = buildSearchRequest(query, sourceType, limit);

				List<SearchResultItem> items = provider.search(request);
				allItems.addAll(items);
				successCount++;

				log.debug("UnifiedSearchCodeactTool#executeUnifiedSearch - reason=单源搜索成功, sourceType={}, resultCount={}",
						sourceType, items.size());
			}
			catch (Exception e) {
				failureCount++;
				log.error("UnifiedSearchCodeactTool#executeUnifiedSearch - reason=搜索执行失败, sourceKey={}, error={}",
						key, e.getMessage(), e);
			}
		}

		// 按相关度排序并限制数量
		List<SearchResultItem> sortedItems = allItems.stream()
				.sorted(Comparator.comparing(SearchResultItem::getScore).reversed())
				.limit(limit)
				.collect(Collectors.toList());

		log.info("UnifiedSearchCodeactTool#executeUnifiedSearch - reason=统一搜索完成, totalResults={}, successSources={}, failedSources={}",
				sortedItems.size(), successCount, failureCount);

		// 构建结果集
		SearchResultSet resultSet = SearchResultSet.success(sortedItems);
		resultSet.getMetadata().put("source_count", successCount);
		resultSet.getMetadata().put("failed_sources", failureCount);

		return resultSet;
	}

	/**
	 * 解析要搜索的源列表（字符串）。
	 */
	private List<String> parseSourceKeys(Map<String, Object> params) {
		List<String> keys = new ArrayList<>();

		// 如果指定了 sources 参数
		if (params.containsKey("sources")) {
			Object sourcesObj = params.get("sources");
			if (sourcesObj instanceof List) {
				List<?> sourcesList = (List<?>) sourcesObj;
				for (Object source : sourcesList) {
					if (source != null) {
						keys.add(source.toString());
					}
				}
			}
		}

		// 如果没有指定，使用默认的源类型
		if (keys.isEmpty()) {
			keys.add(SearchSourceType.PROJECT.name());
			keys.add(SearchSourceType.KNOWLEDGE.name());
		}

		return keys;
	}

	/**
	 * 获取所有可用的源名称（包括标准类型和Provider名称）。
	 */
	private List<Object> getAvailableSourceNames() {
		List<Object> names = new ArrayList<>();
		// 添加标准类型
		for (SearchSourceType type : SearchSourceType.values()) {
			names.add(type.name().toLowerCase());
		}
		// 添加 Provider 名称
		if (searchProviders != null) {
			for (SearchProvider p : searchProviders) {
				names.add(p.getName());
			}
		}
		return names;
	}

	/**
	 * 构建 ToolDefinition。
	 */
	private ToolDefinition buildToolDefinition() {
		String inputSchema = buildInputSchema();
		return ToolDefinition.builder().name(TOOL_NAME).description(DESCRIPTION).inputSchema(inputSchema).build();
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
				.name("sources")
				.type(ParameterType.ARRAY)
				.description("要搜索的数据源列表，支持标准类型(project, knowledge, web等)或指定Provider名称")
				.required(false)
				.enumValues(getAvailableSourceNames())
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
			.name(TOOL_NAME)
			.description(DESCRIPTION)
			.inputSchema(inputSchema)
			.parameterTree(parameterTree)
			.returnDescription("统一搜索结果，包含多个数据源的匹配条目")
			.returnTypeHint("Dict[str, Any]")
			.build();
	}

	/**
	 * 构建 CodeactToolMetadata。
	 */
	private CodeactToolMetadata buildCodeactMetadata() {
		List<CodeExample> fewShots = new ArrayList<>();

		// 示例1：基本搜索
		fewShots.add(new CodeExample("执行统一搜索",
				"result = unified_search(query=\"Spring Boot配置\")\nprint(f\"找到 {len(result['items'])} 条结果\")",
				"搜索项目和知识库，返回相关结果"));

		// 示例2：指定搜索源
		fewShots.add(new CodeExample("指定搜索源",
				"result = unified_search(query=\"API文档\", sources=[\"project\", \"knowledge\", \"web\"])",
				"在多个指定的数据源中搜索"));

		return DefaultCodeactToolMetadata.builder()
			.addSupportedLanguage(Language.PYTHON)
			.targetClassName("search_tools")
			.targetClassDescription("搜索相关工具集合")
			.fewShots(fewShots)
			.displayName("统一搜索")
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

		// sources 参数（可选）
		Map<String, Object> sourcesProp = new LinkedHashMap<>();
		sourcesProp.put("type", "array");
		Map<String, Object> itemsProp = new LinkedHashMap<>();
		itemsProp.put("type", "string");
		itemsProp.put("enum", getAvailableSourceNames());
		sourcesProp.put("items", itemsProp);
		sourcesProp.put("description", "要搜索的数据源列表，支持标准类型或Provider名称");
		properties.put("sources", sourcesProp);

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
			log.error("UnifiedSearchCodeactTool#buildInputSchema - reason=构建inputSchema失败, error={}",
					e.getMessage());
			return "{}";
		}
	}

	/**
	 * 构建搜索请求。
	 */
	private SearchRequest buildSearchRequest(String query, SearchSourceType sourceType, int limit) {
		SearchRequest request = new SearchRequest();
		request.setQuery(query);
		request.getSourceTypes().add(sourceType);
		request.setTopK(limit);
		return request;
	}

}

