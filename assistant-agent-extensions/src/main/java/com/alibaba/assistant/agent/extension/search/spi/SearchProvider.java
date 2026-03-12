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
package com.alibaba.assistant.agent.extension.search.spi;

import com.alibaba.assistant.agent.extension.search.model.SearchRequest;
import com.alibaba.assistant.agent.extension.search.model.SearchResultItem;
import com.alibaba.assistant.agent.extension.search.model.SearchSourceType;

import java.util.Collections;
import java.util.List;

/**
 * 搜索数据源提供者SPI接口
 * 代表某一类数据源（项目上下文、知识库、Web搜索等）的搜索能力
 *
 * @author Assistant Agent Team
 */
public interface SearchProvider {
	/**
	 * 判断是否支持指定的数据源类型
	 *
	 * @param type 数据源类型
	 * @return 是否支持
	 */
	boolean supports(SearchSourceType type);

	/**
	 * 执行搜索
	 *
	 * @param request 搜索请求
	 * @return 搜索结果列表
	 */
	List<SearchResultItem> search(SearchRequest request);

	/**
	 * 获取Provider名称
	 *
	 * @return Provider名称
	 */
	default String getName() {
		return this.getClass().getSimpleName();
	}

	/**
	 * 获取该Provider声明的扩展参数列表。
	 *
	 * <p>框架会将这些参数动态注册到搜索工具的 inputSchema / ParameterTree 中，
	 * 调用时传入的值会被放入 {@link SearchRequest#getFilters()} 供 Provider 在 search 中读取。
	 *
	 * <p>默认返回空列表，即不声明任何扩展参数。
	 *
	 * @return 扩展参数列表
	 */
	default List<SearchExtendedParameter> getExtendedParameters() {
		return Collections.emptyList();
	}
}

