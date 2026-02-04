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
package com.alibaba.assistant.agent.core.model;

import com.alibaba.assistant.agent.common.enums.Language;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Records the execution of a piece of code.
 * Stores execution results, errors, and execution metadata.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ExecutionRecord implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Function name that was executed
	 */
	private String functionName;

	/**
	 * Programming language
	 */
	private Language language;

	/**
	 * Whether execution was successful
	 */
	private boolean success;

	/**
	 * Execution result (serialized as String)
	 */
	private String result;

	/**
	 * Error message if execution failed
	 */
	private String errorMessage;

	/**
	 * Stack trace if execution failed
	 */
	private String stackTrace;

	/**
	 * Execution timestamp
	 */
	private LocalDateTime executedAt;

	/**
	 * Execution duration in milliseconds
	 */
	private long durationMs;

	/**
	 * Additional metadata about the execution
	 */
	private Map<String, Object> metadata;

	/**
	 * 工具调用追踪记录，记录代码执行过程中调用的工具列表
	 */
	private List<ToolCallRecord> callTrace;

	public ExecutionRecord() {
		this.executedAt = LocalDateTime.now();
		this.callTrace = new ArrayList<>();
	}

	public ExecutionRecord(String functionName, Language language) {
		this();
		this.functionName = functionName;
		this.language = language;
	}

	// Getters and Setters

	public String getFunctionName() {
		return functionName;
	}

	public void setFunctionName(String functionName) {
		this.functionName = functionName;
	}

	public Language getLanguage() {
		return language;
	}

	public void setLanguage(Language language) {
		this.language = language;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getStackTrace() {
		return stackTrace;
	}

	public void setStackTrace(String stackTrace) {
		this.stackTrace = stackTrace;
	}

	public LocalDateTime getExecutedAt() {
		return executedAt;
	}

	public void setExecutedAt(LocalDateTime executedAt) {
		this.executedAt = executedAt;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public void setDurationMs(long durationMs) {
		this.durationMs = durationMs;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	public List<ToolCallRecord> getCallTrace() {
		return callTrace;
	}

	public void setCallTrace(List<ToolCallRecord> callTrace) {
		this.callTrace = callTrace;
	}

	/**
	 * 添加一条工具调用记录
	 * @param toolName 工具名称
	 */
	public void addToolCall(String toolName) {
		if (this.callTrace == null) {
			this.callTrace = new ArrayList<>();
		}
		this.callTrace.add(new ToolCallRecord(this.callTrace.size() + 1, toolName));
	}

	@Override
	public String toString() {
		return "ExecutionRecord{" +
				"functionName='" + functionName + '\'' +
				", language=" + language +
				", success=" + success +
				", result='" + result + '\'' +
				", errorMessage='" + errorMessage + '\'' +
				", executedAt=" + executedAt +
				", durationMs=" + durationMs +
				", callTrace=" + callTrace +
				'}';
	}
}

