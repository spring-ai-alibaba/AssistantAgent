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
package com.alibaba.assistant.agent.common.tools.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 结构化参数树 - 从 inputSchema 解析得到。
 *
 * <p>包含工具的所有参数信息，用于生成代码签名和文档。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ParameterTree {

	private final List<ParameterNode> parameters;

	private final Set<String> requiredNames;

	private final String rawInputSchema;

	private ParameterTree(Builder builder) {
		this.parameters = Collections.unmodifiableList(builder.parameters);
		this.requiredNames = Collections.unmodifiableSet(builder.requiredNames);
		this.rawInputSchema = builder.rawInputSchema;
	}

	/**
	 * 获取所有参数节点。
	 * @return 参数节点列表
	 */
	public List<ParameterNode> getParameters() {
		return parameters;
	}

	/**
	 * 获取必填参数名集合。
	 * @return 必填参数名集合
	 */
	public Set<String> getRequiredNames() {
		return requiredNames;
	}

	/**
	 * 获取原始 inputSchema JSON。
	 * @return 原始 inputSchema JSON
	 */
	public String getRawInputSchema() {
		return rawInputSchema;
	}

	/**
	 * 获取必填参数列表。
	 * @return 必填参数列表
	 */
	public List<ParameterNode> getRequiredParameters() {
		return parameters.stream().filter(ParameterNode::isRequired).collect(Collectors.toList());
	}

	/**
	 * 获取可选参数列表。
	 * @return 可选参数列表
	 */
	public List<ParameterNode> getOptionalParameters() {
		return parameters.stream().filter(p -> !p.isRequired()).collect(Collectors.toList());
	}

	/**
	 * 判断是否有参数。
	 * @return true 表示有参数
	 */
	public boolean hasParameters() {
		return parameters != null && !parameters.isEmpty();
	}

	/**
	 * 获取参数数量。
	 * @return 参数数量
	 */
	public int getParameterCount() {
		return parameters != null ? parameters.size() : 0;
	}

	/**
	 * 根据名称获取参数。
	 * @param name 参数名称
	 * @return 参数节点，未找到返回 null
	 */
	public ParameterNode getParameter(String name) {
		if (name == null || parameters == null) {
			return null;
		}
		return parameters.stream().filter(p -> name.equals(p.getName())).findFirst().orElse(null);
	}

	/**
	 * 生成 Python 函数签名的参数列表。
	 *
	 * <p>必填参数在前，可选参数在后（带默认值）。
	 * @return Python 参数签名字符串
	 */
	public String toPythonSignature() {
		if (!hasParameters()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		List<ParameterNode> required = getRequiredParameters();
		List<ParameterNode> optional = getOptionalParameters();

		// 必填参数在前
		for (int i = 0; i < required.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			ParameterNode param = required.get(i);
			sb.append(param.getName()).append(": ").append(param.getPythonTypeHint());
		}

		// 可选参数在后（带默认值）
		for (int i = 0; i < optional.size(); i++) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			ParameterNode param = optional.get(i);
			sb.append(param.getName()).append(": ").append(param.getPythonTypeHint());
			sb.append(" = ").append(formatDefaultValue(param));
		}

		return sb.toString();
	}

	private String formatDefaultValue(ParameterNode param) {
		Object defaultValue = param.getDefaultValue();
		if (defaultValue == null) {
			return "None";
		}
		if (defaultValue instanceof String) {
			// 转义字符串中的特殊字符
			String escaped = escapeStringForPython((String) defaultValue);
			return "\"" + escaped + "\"";
		}
		if (defaultValue instanceof Boolean) {
			return (Boolean) defaultValue ? "True" : "False";
		}
		return String.valueOf(defaultValue);
	}

	/**
	 * 转义字符串以便在 Python 代码中安全使用。
	 *
	 * @param str 原始字符串
	 * @return 转义后的字符串
	 */
	private String escapeStringForPython(String str) {
		if (str == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (char c : str.toCharArray()) {
			switch (c) {
				case '\\':
					sb.append("\\\\");
					break;
				case '"':
					sb.append("\\\"");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				default:
					if (c < 32) {
						// 控制字符用 Unicode 转义
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
			}
		}
		return sb.toString();
	}

	/**
	 * 创建构建器实例。
	 * @return 构建器
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * 创建空的参数树。
	 * @return 空的参数树
	 */
	public static ParameterTree empty() {
		return builder().build();
	}

	@Override
	public String toString() {
		return "ParameterTree{" + "parameterCount=" + getParameterCount() + ", requiredNames=" + requiredNames + '}';
	}

	/**
	 * ParameterTree 构建器。
	 */
	public static class Builder {

		private List<ParameterNode> parameters = new ArrayList<>();

		private Set<String> requiredNames = new HashSet<>();

		private String rawInputSchema;

		public Builder parameters(List<ParameterNode> parameters) {
			this.parameters = new ArrayList<>(parameters);
			return this;
		}

		public Builder addParameter(ParameterNode parameter) {
			this.parameters.add(parameter);
			if (parameter.isRequired()) {
				this.requiredNames.add(parameter.getName());
			}
			return this;
		}

		public Builder requiredNames(Set<String> requiredNames) {
			this.requiredNames = new HashSet<>(requiredNames);
			return this;
		}

		public Builder addRequiredName(String name) {
			this.requiredNames.add(name);
			return this;
		}

		public Builder rawInputSchema(String rawInputSchema) {
			this.rawInputSchema = rawInputSchema;
			return this;
		}

		public ParameterTree build() {
			return new ParameterTree(this);
		}

	}

}

