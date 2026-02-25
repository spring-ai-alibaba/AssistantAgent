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
package com.alibaba.assistant.agent.start.capability.intent;

import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Rule-based classifier for high-level capability intent type.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class CapabilityIntentClassifier {

	private static final Set<String> OPERATION_KEYWORDS = Set.of(
			"发起", "提交", "创建", "新增", "修改", "更新", "删除", "申请", "预约", "安排", "办理",
			"上报", "审批");

	private static final Set<String> QUERY_KEYWORDS = Set.of(
			"查询", "查一下", "查找", "检索", "是什么", "多少", "查看", "获取", "列出", "展示");

	private static final Set<String> ANALYSIS_KEYWORDS = Set.of(
			"分析", "评估", "诊断", "复盘", "对比", "趋势", "原因", "建议", "洞察");

	/**
	 * Classify user input into operation/query/analysis.
	 *
	 * @param userInput user input text
	 * @return classified intent type
	 */
	public CapabilityIntentType classify(String userInput) {
		if (!StringUtils.hasText(userInput)) {
			return CapabilityIntentType.UNKNOWN;
		}

		String normalized = normalize(userInput);
		int operationScore = score(normalized, OPERATION_KEYWORDS);
		int queryScore = score(normalized, QUERY_KEYWORDS);
		int analysisScore = score(normalized, ANALYSIS_KEYWORDS);

		if (operationScore <= 0 && queryScore <= 0 && analysisScore <= 0) {
			return CapabilityIntentType.UNKNOWN;
		}

		int maxScore = Math.max(operationScore, Math.max(queryScore, analysisScore));
		if (operationScore == maxScore) {
			return CapabilityIntentType.OPERATION;
		}
		if (analysisScore == maxScore) {
			return CapabilityIntentType.ANALYSIS;
		}
		return CapabilityIntentType.QUERY;
	}

	private int score(String normalizedInput, Set<String> keywords) {
		if (!StringUtils.hasText(normalizedInput) || keywords == null || keywords.isEmpty()) {
			return 0;
		}
		int score = 0;
		for (String keyword : keywords) {
			if (!StringUtils.hasText(keyword)) {
				continue;
			}
			if (normalizedInput.contains(keyword.toLowerCase())) {
				score += 2;
			}
		}
		return score;
	}

	private String normalize(String userInput) {
		return userInput == null ? "" : userInput.trim().toLowerCase();
	}

}
