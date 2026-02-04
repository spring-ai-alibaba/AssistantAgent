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

import java.util.Objects;

/**
 * Configuration for conditional execution of an evaluation criterion.
 * 
 * <p>When configured, the criterion will only execute if the dependent criterion's
 * result satisfies the specified condition. Otherwise, it returns a default value
 * and is marked as SKIPPED.
 *
 * @author Assistant Agent Team
 */
public class ConditionalExecutionConfig {

	/**
	 * Name of the criterion that this condition depends on.
	 * Must be declared in the criterion's dependsOn list.
	 */
	private String dependsOnCriterion;

	/**
	 * Expected value for the condition to be satisfied.
	 * The dependent criterion's result value will be compared against this.
	 */
	private Object expectedValue;

	/**
	 * Match mode for comparing the actual value with expected value.
	 */
	private MatchMode matchMode = MatchMode.EQUALS;

	/**
	 * Default value to return when condition is NOT satisfied.
	 */
	private Object defaultValue;

	/**
	 * Reason/explanation for why the criterion was skipped.
	 */
	private String skipReason;

	/**
	 * Match modes for conditional execution
	 */
	public enum MatchMode {
		/**
		 * Actual value must equal expected value
		 */
		EQUALS,

		/**
		 * Actual value must not equal expected value
		 */
		NOT_EQUALS,

		/**
		 * Actual value must not be null
		 */
		NOT_NULL,

		/**
		 * Actual value must be Boolean.TRUE
		 */
		IS_TRUE,

		/**
		 * Actual value must be Boolean.FALSE
		 */
		IS_FALSE
	}

	// Constructors

	public ConditionalExecutionConfig() {
	}

	// Builder-style static factory methods

	/**
	 * Create a condition.
	 *
	 * <p>For {@link MatchMode#EQUALS} and {@link MatchMode#NOT_EQUALS}, expectedValue is required.
	 * For other modes, expectedValue is ignored and can be null.
	 */
	public static ConditionalExecutionConfig of(String dependsOnCriterion, MatchMode matchMode, Object expectedValue) {
		ConditionalExecutionConfig config = new ConditionalExecutionConfig();
		config.setDependsOnCriterion(dependsOnCriterion);
		config.setMatchMode(matchMode != null ? matchMode : MatchMode.EQUALS);
		config.setExpectedValue(expectedValue);
		return config;
	}

	// Fluent setters for chaining

	/**
	 * Set the default value to return when condition is not satisfied
	 */
	public ConditionalExecutionConfig withDefaultValue(Object defaultValue) {
		this.defaultValue = defaultValue;
		return this;
	}

	/**
	 * Set the reason for skipping
	 */
	public ConditionalExecutionConfig withSkipReason(String skipReason) {
		this.skipReason = skipReason;
		return this;
	}

	// Core matching logic

	/**
	 * Check if the actual value satisfies the condition
	 *
	 * @param actualValue the value from the dependent criterion's result
	 * @return true if condition is satisfied, false otherwise
	 */
	public boolean matches(Object actualValue) {
		switch (matchMode) {
			case EQUALS:
				return Objects.equals(expectedValue, actualValue);
			case NOT_EQUALS:
				return !Objects.equals(expectedValue, actualValue);
			case NOT_NULL:
				return actualValue != null;
			case IS_TRUE:
				return Boolean.TRUE.equals(actualValue);
			case IS_FALSE:
				return Boolean.FALSE.equals(actualValue);
			default:
				return true;
		}
	}

	/**
	 * Get a human-readable description of this condition
	 */
	public String getConditionDescription() {
		switch (matchMode) {
			case EQUALS:
				return String.format("%s == %s", dependsOnCriterion, expectedValue);
			case NOT_EQUALS:
				return String.format("%s != %s", dependsOnCriterion, expectedValue);
			case NOT_NULL:
				return String.format("%s is not null", dependsOnCriterion);
			case IS_TRUE:
				return String.format("%s is true", dependsOnCriterion);
			case IS_FALSE:
				return String.format("%s is false", dependsOnCriterion);
			default:
				return "unknown condition";
		}
	}

	// Standard getters and setters

	public String getDependsOnCriterion() {
		return dependsOnCriterion;
	}

	public void setDependsOnCriterion(String dependsOnCriterion) {
		this.dependsOnCriterion = dependsOnCriterion;
	}

	public Object getExpectedValue() {
		return expectedValue;
	}

	public void setExpectedValue(Object expectedValue) {
		this.expectedValue = expectedValue;
	}

	public MatchMode getMatchMode() {
		return matchMode;
	}

	public void setMatchMode(MatchMode matchMode) {
		this.matchMode = matchMode;
	}

	public Object getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(Object defaultValue) {
		this.defaultValue = defaultValue;
	}

	public String getSkipReason() {
		return skipReason;
	}

	public void setSkipReason(String skipReason) {
		this.skipReason = skipReason;
	}

	@Override
	public String toString() {
		return "ConditionalExecutionConfig{" +
			"dependsOnCriterion='" + dependsOnCriterion + '\'' +
			", matchMode=" + matchMode +
			", expectedValue=" + expectedValue +
			", defaultValue=" + defaultValue +
			", skipReason='" + skipReason + '\'' +
			'}';
	}
}
