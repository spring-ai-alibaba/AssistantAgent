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

/**
 * SearchProvider 的扩展参数描述。
 *
 * <p>SearchProvider 实现类可以通过 {@link SearchProvider#getExtendedParameters()} 声明额外的工具参数，
 * 框架会自动将这些参数注册到 Tool 的 inputSchema / ParameterTree 中，
 * 调用时传入的值会被放入 {@link com.alibaba.assistant.agent.extension.search.model.SearchRequest#getFilters()} 中。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SearchExtendedParameter {

	/**
	 * 参数名称（对应 JSON Schema property name，同时也是 filters 中的 key）
	 */
	private final String name;

	/**
	 * JSON Schema 类型，如 "string", "integer", "boolean" 等
	 */
	private final String type;

	/**
	 * 参数描述
	 */
	private final String description;

	/**
	 * 是否必填
	 */
	private final boolean required;

	/**
	 * 默认值，可为 null
	 */
	private final Object defaultValue;

	private SearchExtendedParameter(Builder builder) {
		this.name = builder.name;
		this.type = builder.type;
		this.description = builder.description;
		this.required = builder.required;
		this.defaultValue = builder.defaultValue;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public String getDescription() {
		return description;
	}

	public boolean isRequired() {
		return required;
	}

	public Object getDefaultValue() {
		return defaultValue;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String name;

		private String type = "string";

		private String description = "";

		private boolean required = false;

		private Object defaultValue;

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder type(String type) {
			this.type = type;
			return this;
		}

		public Builder description(String description) {
			this.description = description;
			return this;
		}

		public Builder required(boolean required) {
			this.required = required;
			return this;
		}

		public Builder defaultValue(Object defaultValue) {
			this.defaultValue = defaultValue;
			return this;
		}

		public SearchExtendedParameter build() {
			if (name == null || name.isEmpty()) {
				throw new IllegalArgumentException("SearchExtendedParameter name must not be empty");
			}
			return new SearchExtendedParameter(this);
		}

	}

}

