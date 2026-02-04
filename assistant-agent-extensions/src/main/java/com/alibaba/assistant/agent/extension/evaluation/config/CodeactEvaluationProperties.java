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
package com.alibaba.assistant.agent.extension.evaluation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Codeact Evaluation 配置属性
 * 控制各评估点的启用开关与 Suite ID
 *
 * @author Assistant Agent Team
 */
@ConfigurationProperties(prefix = "spring.ai.alibaba.codeact.extension.evaluation")
public class CodeactEvaluationProperties {

	/**
	 * 是否启用 Evaluation 集成（顶层总开关）
	 */
	private boolean enabled = true;

	/**
	 * 是否异步执行评估
	 */
	private boolean async = false;

	/**
	 * 评估调用超时时间（毫秒），超时后跳过评估
	 */
	private long timeoutMs = 5000;

	/**
	 * 输入路由评估配置
	 */
	private EvaluationPointConfig inputRouting = new EvaluationPointConfig(true, "react-phase-suite");

	/**
	 * 模型输出评估配置
	 */
	private EvaluationPointConfig modelOutput = new EvaluationPointConfig(false, "model_output_quality_suite");

	/**
	 * 代码生成输入评估配置（新增）
	 */
	private EvaluationPointConfig codeGenerationInput = new EvaluationPointConfig(false, "codeact-phase-suite");

	/**
	 * 代码执行评估配置
	 */
	private EvaluationPointConfig codeExecution = new EvaluationPointConfig(false, "code_execution_suite");

	/**
	 * 会话总结评估配置
	 */
	private EvaluationPointConfig sessionSummary = new EvaluationPointConfig(false, "session_summary_suite");

	// Getters and Setters

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isAsync() {
		return async;
	}

	public void setAsync(boolean async) {
		this.async = async;
	}

	public long getTimeoutMs() {
		return timeoutMs;
	}

	public void setTimeoutMs(long timeoutMs) {
		this.timeoutMs = timeoutMs;
	}

	public EvaluationPointConfig getInputRouting() {
		return inputRouting;
	}

	public void setInputRouting(EvaluationPointConfig inputRouting) {
		this.inputRouting = inputRouting;
	}

	public EvaluationPointConfig getModelOutput() {
		return modelOutput;
	}

	public void setModelOutput(EvaluationPointConfig modelOutput) {
		this.modelOutput = modelOutput;
	}

	public EvaluationPointConfig getCodeGenerationInput() {
		return codeGenerationInput;
	}

	public void setCodeGenerationInput(EvaluationPointConfig codeGenerationInput) {
		this.codeGenerationInput = codeGenerationInput;
	}

	public EvaluationPointConfig getCodeExecution() {
		return codeExecution;
	}

	public void setCodeExecution(EvaluationPointConfig codeExecution) {
		this.codeExecution = codeExecution;
	}

	public EvaluationPointConfig getSessionSummary() {
		return sessionSummary;
	}

	public void setSessionSummary(EvaluationPointConfig sessionSummary) {
		this.sessionSummary = sessionSummary;
	}

	/**
	 * 单个评估点的配置
	 */
	public static class EvaluationPointConfig {
		/**
		 * 是否启用此评估点
		 */
		private boolean enabled;

		/**
		 * 使用的 EvaluationSuite ID
		 */
		private String suiteId;

		public EvaluationPointConfig() {
		}

		public EvaluationPointConfig(boolean enabled, String suiteId) {
			this.enabled = enabled;
			this.suiteId = suiteId;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getSuiteId() {
			return suiteId;
		}

		public void setSuiteId(String suiteId) {
			this.suiteId = suiteId;
		}
	}
}

