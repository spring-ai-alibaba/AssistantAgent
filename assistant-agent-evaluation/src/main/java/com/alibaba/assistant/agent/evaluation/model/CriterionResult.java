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

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Result of evaluating a single criterion
 *
 * @author Assistant Agent Team
 */
public class CriterionResult {

	/**
	 * ID of the criterion that was evaluated
	 */
	private String criterionName;

	/**
	 * Status of the evaluation
	 */
	private CriterionStatus status;

	/**
	 * The evaluation result value (structure depends on resultType)
	 */
	private Object value;

	/**
	 * Reasoning or explanation (optional, based on reasoningPolicy)
	 */
	private String reason;

	/**
	 * Raw response from evaluator (LLM output, external service response, etc.)
	 */
	private String rawResponse;

	/**
	 * Raw prompt sent to LLM (for observability)
	 */
	private String rawPrompt;

	/**
	 * Error message if status is ERROR
	 */
	private String errorMessage;

	/**
	 * Timestamp when evaluation started
	 */
	private Long startTimeMillis;

	/**
	 * Timestamp when evaluation completed
	 */
	private Long endTimeMillis;

	/**
	 * Additional metadata for the result
	 */
	private java.util.Map<String, Object> metadata = new java.util.HashMap<>();

	// Getters and Setters

	public String getCriterionName() {
		return criterionName;
	}

	public void setCriterionName(String criterionName) {
		this.criterionName = criterionName;
	}

	public CriterionStatus getStatus() {
		return status;
	}

	public void setStatus(CriterionStatus status) {
		this.status = status;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getRawResponse() {
		return rawResponse;
	}

	public void setRawResponse(String rawResponse) {
		this.rawResponse = rawResponse;
	}

	public String getRawPrompt() {
		return rawPrompt;
	}

	public void setRawPrompt(String rawPrompt) {
		this.rawPrompt = rawPrompt;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public Long getStartTimeMillis() {
		return startTimeMillis;
	}

	public void setStartTimeMillis(Long startTimeMillis) {
		this.startTimeMillis = startTimeMillis;
	}

	public Long getEndTimeMillis() {
		return endTimeMillis;
	}

	public void setEndTimeMillis(Long endTimeMillis) {
		this.endTimeMillis = endTimeMillis;
	}

	public java.util.Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(java.util.Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	/**
	 * Get execution duration in milliseconds
	 */
	@JsonIgnore
	public Long getDurationMillis() {
		if (startTimeMillis != null && endTimeMillis != null) {
			return endTimeMillis - startTimeMillis;
		}
		return null;
	}
}

