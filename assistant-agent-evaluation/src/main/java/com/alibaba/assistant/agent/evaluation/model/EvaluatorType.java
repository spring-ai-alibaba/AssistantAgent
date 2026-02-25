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
package com.alibaba.assistant.agent.evaluation.model;

/**
 * Built-in evaluator type constants
 * This enum provides type-safe references to built-in evaluators that are actually implemented.
 * Custom evaluators can still be registered using arbitrary String IDs via evaluatorRef field.
 *
 * @author Assistant Agent Team
 */
public enum EvaluatorType {

	/**
	 * LLM-based evaluator (default)
	 * Suitable for classification, scoring, and text generation tasks.
	 * The specific behavior can be configured via criterion settings:
	 * - Classification: use resultType=ENUM with options
	 * - Scoring: use resultType=SCORE
	 * - Text generation: use resultType=TEXT
	 */
	LLM_BASED("llm-based"),

	/**
	 * Rule-based evaluator
	 * Uses Java code logic for evaluation.
	 * Suitable for threshold checks, format validation, and custom business logic.
	 */
	RULE_BASED("rule-based"),

	/**
	 * Multimodal LLM-based evaluator
	 * Supports processing multimodal inputs like images.
	 * Extends LLM_BASED with additional capability to handle Media content.
	 */
	MULTIMODAL_LLM_BASED("multimodal-llm-based");

	private final String value;

	EvaluatorType(String value) {
		this.value = value;
	}

	/**
	 * Get the evaluator value string
	 * @return evaluator value
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Get EvaluatorType from value string
	 * @param value evaluator value
	 * @return EvaluatorType or null if not found
	 */
	public static EvaluatorType fromValue(String value) {
		if (value == null) {
			return null;
		}
		for (EvaluatorType type : values()) {
			if (type.value.equals(value)) {
				return type;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return value;
	}
}

