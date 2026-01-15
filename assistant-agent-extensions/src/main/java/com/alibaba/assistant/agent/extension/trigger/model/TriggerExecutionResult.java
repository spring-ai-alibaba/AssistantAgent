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

package com.alibaba.assistant.agent.extension.trigger.model;

/**
 * 触发器执行结果
 *
 * @author canfeng
 * @since 1.0.0
 */
public class TriggerExecutionResult {

	/**
	 * 触发器ID
	 */
	private String triggerId;

	/**
	 * 条件是否满足
	 */
	private Boolean conditionPassed;

	/**
	 * 执行是否成功
	 */
	private Boolean executionSuccess;

	/**
	 * 是否应该放弃（停止监听）
	 */
	private Boolean shouldAbandon;

	/**
	 * 执行结果
	 */
	private Object executionResult;

	/**
	 * 错误信息
	 */
	private String errorMessage;

	/**
	 * 执行耗时（毫秒）
	 */
	private Long executionTime;

	public TriggerExecutionResult() {
	}

	// Static factory methods

	/**
	 * 创建条件未满足的结果
	 */
	public static TriggerExecutionResult conditionNotMet(String triggerId) {
		TriggerExecutionResult result = new TriggerExecutionResult();
		result.setTriggerId(triggerId);
		result.setConditionPassed(false);
		result.setExecutionSuccess(true);
		return result;
	}

	/**
	 * 创建成功结果
	 */
	public static TriggerExecutionResult success(String triggerId, Object executionResult) {
		TriggerExecutionResult result = new TriggerExecutionResult();
		result.setTriggerId(triggerId);
		result.setConditionPassed(true);
		result.setExecutionSuccess(true);
		result.setExecutionResult(executionResult);
		return result;
	}

	/**
	 * 创建失败结果
	 */
	public static TriggerExecutionResult failure(String triggerId, String errorMessage) {
		TriggerExecutionResult result = new TriggerExecutionResult();
		result.setTriggerId(triggerId);
		result.setExecutionSuccess(false);
		result.setErrorMessage(errorMessage);
		return result;
	}

	// Getters and Setters

	public String getTriggerId() {
		return triggerId;
	}

	public void setTriggerId(String triggerId) {
		this.triggerId = triggerId;
	}

	public Boolean getConditionPassed() {
		return conditionPassed;
	}

	public void setConditionPassed(Boolean conditionPassed) {
		this.conditionPassed = conditionPassed;
	}

	public Boolean getExecutionSuccess() {
		return executionSuccess;
	}

	public void setExecutionSuccess(Boolean executionSuccess) {
		this.executionSuccess = executionSuccess;
	}

	public Boolean getShouldAbandon() {
		return shouldAbandon;
	}

	public void setShouldAbandon(Boolean shouldAbandon) {
		this.shouldAbandon = shouldAbandon;
	}

	public Object getExecutionResult() {
		return executionResult;
	}

	public void setExecutionResult(Object executionResult) {
		this.executionResult = executionResult;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public Long getExecutionTime() {
		return executionTime;
	}

	public void setExecutionTime(Long executionTime) {
		this.executionTime = executionTime;
	}

	@Override
	public String toString() {
		return "TriggerExecutionResult{" +
				"triggerId='" + triggerId + '\'' +
				", conditionPassed=" + conditionPassed +
				", executionSuccess=" + executionSuccess +
				", shouldAbandon=" + shouldAbandon +
				", executionTime=" + executionTime +
				", errorMessage='" + errorMessage + '\'' +
				'}';
	}

}

