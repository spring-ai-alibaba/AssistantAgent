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
package com.alibaba.assistant.agent.core.executor.python;

import com.alibaba.assistant.agent.core.context.CodeContext;
import com.alibaba.assistant.agent.core.executor.RuntimeEnvironmentManager;
import com.alibaba.assistant.agent.common.enums.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python runtime environment manager.
 * Handles Python-specific code wrapping, imports, and environment setup.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class PythonEnvironmentManager implements RuntimeEnvironmentManager {

	private static final Logger logger = LoggerFactory.getLogger(PythonEnvironmentManager.class);

	// Common imports that are often needed
	private static final Set<String> COMMON_IMPORTS = Set.of(
		"import json",
		"import sys",
		"import os",
		"import re",
		"import math",
		"import base64",
		"from typing import Any, List, Dict, Optional"
	);

	// Pattern to extract function name from Python function definition
	private static final Pattern FUNCTION_NAME_PATTERN =
		Pattern.compile("def\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");

	@Override
	public Language getSupportedLanguage() {
		return Language.PYTHON;
	}

	@Override
	public String generateImports(CodeContext context) {
		logger.debug("PythonEnvironmentManager#generateImports 生成Python导入语句");

		Set<String> allImports = new HashSet<>(COMMON_IMPORTS);
		allImports.addAll(context.getRequiredImports());

		StringBuilder imports = new StringBuilder();
		for (String importStmt : allImports) {
			imports.append(importStmt).append("\n");
		}

		return imports.toString();
	}

	@Override
	public String wrapFunctionCode(String functionCode, CodeContext context) {
		logger.debug("PythonEnvironmentManager#wrapFunctionCode 包装Python函数代码");

		// For Python, just return the function code as-is
		// The executor will handle imports and other functions
		return functionCode;
	}

	@Override
	public String generateFunctionCall(String functionName, Map<String, Object> args) {
		logger.info("PythonEnvironmentManager#generateFunctionCall 生成函数调用: functionName={}, args={}", functionName, args);

		if (args == null || args.isEmpty()) {
			return functionName + "()";
		}

		// Generate Python kwargs from Map
		StringBuilder kwargs = new StringBuilder();
		int i = 0;
		for (Map.Entry<String, Object> entry : args.entrySet()) {
			if (i > 0) {
				kwargs.append(", ");
			}

			String key = entry.getKey();
			Object value = entry.getValue();

			// 参数名不需要引号
			kwargs.append(key).append("=");

			// 根据值的类型决定是否需要引号
			if (value == null) {
				kwargs.append("None");
			} else if (value instanceof String) {
				// 字符串值使用 Base64 编码，避免所有转义问题
				kwargs.append(encodeStringForPython(value.toString()));
			} else if (value instanceof Boolean) {
				// Python 的布尔值首字母大写
				kwargs.append(((Boolean) value) ? "True" : "False");
			} else if (value instanceof Number) {
				// 数字不需要引号
				kwargs.append(value);
			} else if (value instanceof java.util.List) {
				// List 转换为 Python list
				kwargs.append(convertListToPython((java.util.List<?>) value));
			} else if (value instanceof Map) {
				// Map 转换为 Python dict
				kwargs.append(convertMapToPython((Map<?, ?>) value));
			} else {
				// 其他类型转为字符串，使用 Base64 编码
				kwargs.append(encodeStringForPython(value.toString()));
			}

			i++;
		}

		String result = functionName + "(" + kwargs + ")";
		logger.info("PythonEnvironmentManager#generateFunctionCall 生成的函数调用: {}", result);
		return result;
	}

	/**
	 * Convert Java List to Python list string
	 */
	private String convertListToPython(java.util.List<?> list) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) sb.append(", ");
			Object item = list.get(i);
			sb.append(convertValueToPython(item));
		}
		sb.append("]");
		return sb.toString();
	}

	/**
	 * Convert Java Map to Python dict string
	 */
	private String convertMapToPython(Map<?, ?> map) {
		StringBuilder sb = new StringBuilder("{");
		int i = 0;
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (i > 0) sb.append(", ");
			sb.append(encodeStringForPython(entry.getKey().toString())).append(": ");
			sb.append(convertValueToPython(entry.getValue()));
			i++;
		}
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Encode string to Base64 and generate Python decode expression.
	 * This avoids all escape issues by encoding the string as Base64.
	 * Example: "哈哈" -> base64.b64decode('5ZOI5ZOI').decode('utf-8')
	 */
	private String encodeStringForPython(String value) {
		if (value == null) {
			return "None";
		}
		String base64Encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
		return "base64.b64decode('" + base64Encoded + "').decode('utf-8')";
	}

	/**
	 * Convert Java value to Python value string
	 */
	private String convertValueToPython(Object value) {
		if (value == null) {
			return "None";
		} else if (value instanceof String) {
			return encodeStringForPython(value.toString());
		} else if (value instanceof Boolean) {
			return ((Boolean) value) ? "True" : "False";
		} else if (value instanceof Number) {
			return value.toString();
		} else if (value instanceof java.util.List) {
			return convertListToPython((java.util.List<?>) value);
		} else if (value instanceof Map) {
			return convertMapToPython((Map<?, ?>) value);
		} else {
			return encodeStringForPython(value.toString());
		}
	}

	@Override
	public String extractFunctionName(String code) {
		logger.info("PythonEnvironmentManager#extractFunctionName 开始提取函数名");
		logger.debug("PythonEnvironmentManager#extractFunctionName 代码内容:\n{}", code);

		Matcher matcher = FUNCTION_NAME_PATTERN.matcher(code);
		if (matcher.find()) {
			String functionName = matcher.group(1);
			logger.info("PythonEnvironmentManager#extractFunctionName 提取到函数名: {}", functionName);
			return functionName;
		}

		logger.error("PythonEnvironmentManager#extractFunctionName 无法提取函数名，代码:\n{}", code);
		return null;
	}
}

