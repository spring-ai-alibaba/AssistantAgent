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
package com.alibaba.assistant.agent.extension.dynamic.naming;

import com.github.promeg.pinyinhelper.Pinyin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 命名归一化器。
 *
 * <p>将含特殊字符/中文的名称转换为可作为类名/方法名的标识符。
 * 支持中文转拼音，特殊字符统一转为下划线。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class NameNormalizer {

	private static final Logger logger = LoggerFactory.getLogger(NameNormalizer.class);

	private static final Pattern LEADING_DIGIT = Pattern.compile("^[0-9]");

	private static final Pattern MULTI_UNDERSCORE = Pattern.compile("_+");


	/**
	 * 用于跟踪命名冲突的计数器。
	 */
	private final Map<String, AtomicInteger> nameCounters = new ConcurrentHashMap<>();

	/**
	 * 获取默认实例。
	 *
	 * @return 默认的 NameNormalizer 实例
	 */
	public static NameNormalizer defaultInstance() {
		return new NameNormalizer();
	}

	/**
	 * 将原始名称归一化为类名（snake_case，用于 Python 模块/类）。
	 *
	 * @param raw 原始名称
	 * @return 归一化后的类名
	 */
	public String normalizeClassName(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "default_class";
		}

		String normalized = normalizeToIdentifier(raw);
		return normalized.toLowerCase();
	}

	/**
	 * 将原始名称归一化为方法名。
	 *
	 * <p>只处理中文转拼音和特殊字符，保留原始大小写。
	 *
	 * @param raw 原始名称
	 * @return 归一化后的方法名
	 */
	public String normalizeMethodName(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "default_method";
		}

		// 只处理中文和特殊字符，保留原始大小写
		return normalizeToIdentifier(raw);
	}

	/**
	 * 生成全局唯一的工具名。
	 *
	 * @param prefix 前缀（如 "mcp" 或 "http"）
	 * @param className 类名部分
	 * @param methodName 方法名部分
	 * @return 全局唯一的工具名
	 */
	public String normalizeToolName(String prefix, String className, String methodName) {
		String base = prefix + "__" + className + "__" + methodName;
		return ensureUnique(base);
	}

	/**
	 * 将原始字符串转换为合法标识符。
	 * 中文字符会转换为拼音，特殊字符转为下划线。
	 *
	 * @param raw 原始字符串
	 * @return 合法的标识符
	 */
	private String normalizeToIdentifier(String raw) {
		StringBuilder sb = new StringBuilder();

		for (char c : raw.toCharArray()) {
			if (Character.isLetterOrDigit(c) && c < 128) {
				// ASCII 字母数字直接保留
				sb.append(c);
			}
			else if (c == '_' || c == '-' || c == ' ' || c == '.' || c == '·') {
				// 常见分隔符转为下划线
				sb.append('_');
			}
			else if (Pinyin.isChinese(c)) {
				// 中文字符转为拼音（优先使用 Pinyin 库的判断，更准确）
				String pinyin = Pinyin.toPinyin(c).toLowerCase();
				sb.append(pinyin);
				logger.debug("NameNormalizer#normalizeToIdentifier - reason=中文转拼音, char={}, pinyin={}", c, pinyin);
			}
			else if (isChinese(c)) {
				// 其他 CJK 相关字符（标点等）转为下划线
				sb.append('_');
			}
			else {
				// 其他特殊字符转为下划线
				sb.append('_');
			}
		}

		String result = sb.toString();

		// 合并多个连续下划线
		result = MULTI_UNDERSCORE.matcher(result).replaceAll("_");

		// 去除首尾下划线
		result = result.replaceAll("^_+|_+$", "");

		// 如果首字符是数字，添加前缀
		if (!result.isEmpty() && LEADING_DIGIT.matcher(result).find()) {
			result = "n" + result;
		}

		// 如果为空，返回默认值
		if (result.isEmpty()) {
			result = "unnamed";
		}

		return result;
	}

	/**
	 * 判断字符是否为中文。
	 */
	private boolean isChinese(char c) {
		Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
		return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
				|| ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
				|| ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
				|| ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
				|| ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
				|| ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
				|| ub == Character.UnicodeBlock.GENERAL_PUNCTUATION;
	}


	/**
	 * 确保名称唯一。
	 */
	private String ensureUnique(String baseName) {
		AtomicInteger counter = nameCounters.computeIfAbsent(baseName, k -> new AtomicInteger(0));
		int count = counter.getAndIncrement();

		if (count == 0) {
			return baseName;
		}
		else {
			return baseName + "_" + count;
		}
	}

	/**
	 * 重置命名计数器（通常用于测试）。
	 */
	public void reset() {
		nameCounters.clear();
	}

}

