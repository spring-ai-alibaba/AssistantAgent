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

import com.alibaba.assistant.agent.common.tools.SearchCodeactTool;
import com.alibaba.assistant.agent.extension.search.model.SearchSourceType;
import com.alibaba.assistant.agent.extension.search.spi.SearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Search CodeactTool 工厂类。
 *
 * <p>负责根据 SearchProvider 创建对应的 SearchCodeactTool 实例。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SearchCodeactToolFactory {

	private static final Logger log = LoggerFactory.getLogger(SearchCodeactToolFactory.class);

	private final List<SearchProvider> searchProviders;

	/**
	 * 构造工厂实例。
	 *
	 * @param searchProviders 搜索提供者列表
	 */
	public SearchCodeactToolFactory(List<SearchProvider> searchProviders) {
		this.searchProviders = searchProviders != null ? searchProviders : new ArrayList<>();
	}

	/**
	 * 根据 SearchProvider 列表创建 SearchCodeactTool 列表。
	 *
	 * @return SearchCodeactTool 列表
	 */
	public List<SearchCodeactTool> createTools() {
		List<SearchCodeactTool> tools = new ArrayList<>();

		for (SearchProvider provider : searchProviders) {
			try {
				// 为每个 provider 创建对应的工具
				SearchCodeactTool tool = createToolFromProvider(provider);
				if (tool != null) {
					tools.add(tool);
					log.info(
							"SearchCodeactToolFactory#createTools - reason=创建搜索工具成功, providerName={}, toolName={}",
							provider.getName(), tool.getToolDefinition().name());
				}
			}
			catch (Exception e) {
				log.error("SearchCodeactToolFactory#createTools - reason=创建搜索工具失败, providerName={}, error={}",
						provider.getName(), e.getMessage(), e);
			}
		}

		log.info("SearchCodeactToolFactory#createTools - reason=搜索工具创建完成, totalCount={}", tools.size());

		return tools;
	}

	/**
	 * 从 SearchProvider 创建 SearchCodeactTool。
	 *
	 * @param provider 搜索提供者
	 * @return SearchCodeactTool 实例
	 */
	private SearchCodeactTool createToolFromProvider(SearchProvider provider) {
		// 根据 provider 支持的类型确定工具名称和描述
		String toolName = deriveToolName(provider);
		String description = deriveDescription(provider);
		SearchSourceType sourceType = deriveSourceType(provider);
		SearchCodeactTool.SearchScope scope = deriveSearchScope(sourceType);

		return new BaseSearchCodeactTool(toolName, description, provider, sourceType, scope);
	}

	/**
	 * 推导工具名称。
	 */
	private String deriveToolName(SearchProvider provider) {
		String name = provider.getName();

		String snakeName = toSnakeCase(name.replace("Provider", "").replace("Search", ""));
		return snakeName.startsWith("search_") ? snakeName : "search_" + snakeName;
	}

	/**
	 * 推导工具描述。
	 */
	private String deriveDescription(SearchProvider provider) {
		String name = provider.getName();

		if (name.contains("ProjectContext")) {
			return "搜索项目上下文信息，包括代码、配置、文档等";
		}
		else if (name.contains("Knowledge")) {
			return "搜索团队知识库，包括FAQ、规范、最佳实践等";
		}
		else if (name.contains("Web")) {
			return "执行Web搜索，获取公开互联网信息";
		}
		else if (name.contains("Experience")) {
			return "搜索经验池，查找相关的历史经验和案例";
		}
		else {
			return "执行搜索查询: " + provider.getName();
		}
	}

	/**
	 * 推导搜索源类型。
	 */
	private SearchSourceType deriveSourceType(SearchProvider provider) {
		String name = provider.getName();

		if (name.contains("ProjectContext")) {
			return SearchSourceType.PROJECT;
		}
		else if (name.contains("Knowledge")) {
			return SearchSourceType.KNOWLEDGE;
		}
		else if (name.contains("Web")) {
			return SearchSourceType.WEB;
		}
		else if (name.contains("Experience")) {
			return SearchSourceType.EXPERIENCE;
		}
		else {
			return SearchSourceType.CUSTOM;
		}
	}

	/**
	 * 推导搜索范围。
	 */
	private SearchCodeactTool.SearchScope deriveSearchScope(SearchSourceType sourceType) {
		switch (sourceType) {
			case PROJECT:
				return SearchCodeactTool.SearchScope.PROJECT;
			case KNOWLEDGE:
				return SearchCodeactTool.SearchScope.KNOWLEDGE;
			case WEB:
				return SearchCodeactTool.SearchScope.WEB;
			default:
				return SearchCodeactTool.SearchScope.UNIFIED;
		}
	}

	/**
	 * 转换为 snake_case。
	 */
	private String toSnakeCase(String str) {
		if (str == null || str.isEmpty()) {
			return "search";
		}

		return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
	}

}

