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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single evaluation criterion
 * Each criterion is an independent evaluation dimension with its own configuration
 *
 * @author Assistant Agent Team
 */
public class EvaluationCriterion {

	/**
	 * Unique identifier for this criterion
	 */
	private String name;

	/**
	 * Natural language description of this criterion
	 */
	private String description;

	/**
	 * Type of result this criterion produces
	 */
	private ResultType resultType = ResultType.TEXT;

	/**
	 * Valid options when resultType is ENUM
	 */
	private List<String> options = new ArrayList<>();

	/**
	 * Names of criteria that must be evaluated before this one
	 */
	private List<String> dependsOn = new ArrayList<>();

	/**
	 * Reference to evaluator to use
	 */
	private String evaluatorRef;

	/**
	 * Configuration specific to this criterion
	 */
	private Map<String, Object> config = new HashMap<>();

	/**
	 * Working mechanism description for LLM
	 * Explains HOW the LLM should approach this evaluation
	 */
	private String workingMechanism;

	/**
	 * Few-shot examples for LLM guidance
	 */
	private List<FewShotExample> fewShots = new ArrayList<>();

	/**
	 * Policy for reasoning output
	 */
	private ReasoningPolicy reasoningPolicy = ReasoningPolicy.NONE;

	/**
	 * Custom prompt (overrides default prompt assembly)
	 */
	private String customPrompt;

	/**
	 * Context field bindings for automatic prompt assembly
	 */
	private List<String> contextBindings = new ArrayList<>();

	/**
	 * Batching configuration for this criterion
	 * Enables batch processing with concurrency control
	 */
	private CriterionBatchingConfig batchingConfig;

	/**
	 * Conditional execution configuration.
	 * When set, the criterion will only execute if the condition is satisfied.
	 * Otherwise, it returns the configured default value and is marked as SKIPPED.
	 */
	private ConditionalExecutionConfig conditionalExecution;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public ResultType getResultType() {
		return resultType;
	}

	public void setResultType(ResultType resultType) {
		this.resultType = resultType;
	}

	public List<String> getOptions() {
		return options;
	}

	public void setOptions(List<String> options) {
		this.options = options;
	}

	public List<String> getDependsOn() {
		return dependsOn;
	}

	public void setDependsOn(List<String> dependsOn) {
		this.dependsOn = dependsOn;
	}

	public String getEvaluatorRef() {
		return evaluatorRef;
	}

	public void setEvaluatorRef(String evaluatorRef) {
		this.evaluatorRef = evaluatorRef;
	}

	/**
	 * Set evaluator using built-in EvaluatorType enum
	 * @param evaluatorType the built-in evaluator type
	 */
	public void setEvaluatorType(EvaluatorType evaluatorType) {
		this.evaluatorRef = evaluatorType != null ? evaluatorType.getValue() : null;
	}

	public Map<String, Object> getConfig() {
		return config;
	}

	public void setConfig(Map<String, Object> config) {
		this.config = config;
	}

	public String getWorkingMechanism() {
		return workingMechanism;
	}

	public void setWorkingMechanism(String workingMechanism) {
		this.workingMechanism = workingMechanism;
	}

	public List<FewShotExample> getFewShots() {
		return fewShots;
	}

	public void setFewShots(List<FewShotExample> fewShots) {
		this.fewShots = fewShots;
	}

	public ReasoningPolicy getReasoningPolicy() {
		return reasoningPolicy;
	}

	public void setReasoningPolicy(ReasoningPolicy reasoningPolicy) {
		this.reasoningPolicy = reasoningPolicy;
	}

	public String getCustomPrompt() {
		return customPrompt;
	}

	public void setCustomPrompt(String customPrompt) {
		this.customPrompt = customPrompt;
	}

	public List<String> getContextBindings() {
		return contextBindings;
	}

	public void setContextBindings(List<String> contextBindings) {
		this.contextBindings = contextBindings;
	}

	public CriterionBatchingConfig getBatchingConfig() {
		return batchingConfig;
	}

	public void setBatchingConfig(CriterionBatchingConfig batchingConfig) {
		this.batchingConfig = batchingConfig;
	}

	public ConditionalExecutionConfig getConditionalExecution() {
		return conditionalExecution;
	}

	public void setConditionalExecution(ConditionalExecutionConfig conditionalExecution) {
		this.conditionalExecution = conditionalExecution;
	}

	/**
	 * Few-shot example for LLM guidance
	 */
	public static class FewShotExample {
		private String input;
		private String context;
		private String expectedOutput;

		public FewShotExample() {
		}

		public FewShotExample(String input, String context, String expectedOutput) {
			this.input = input;
			this.context = context;
			this.expectedOutput = expectedOutput;
		}

		public String getInput() {
			return input;
		}

		public void setInput(String input) {
			this.input = input;
		}

		public String getContext() {
			return context;
		}

		public void setContext(String context) {
			this.context = context;
		}

		public String getExpectedOutput() {
			return expectedOutput;
		}

		public void setExpectedOutput(String expectedOutput) {
			this.expectedOutput = expectedOutput;
		}
	}
}

