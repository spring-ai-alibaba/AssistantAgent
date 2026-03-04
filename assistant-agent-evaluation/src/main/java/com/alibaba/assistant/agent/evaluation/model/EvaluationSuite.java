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

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A collection of evaluation criteria for a specific target
 *
 * @author Assistant Agent Team
 */
public class EvaluationSuite {

	/**
	 * Unique identifier for this suite
	 */
	private String id;

	/**
	 * Name of this evaluation suite
	 */
	private String name;

	/**
	 * Description of what this suite evaluates
	 */
	private String description;

	/**
	 * Default evaluator reference for criteria that don't specify one
	 */
	private String defaultEvaluator;

	/**
	 * Default model configuration
	 */
	private Map<String, Object> defaultModelConfig = new HashMap<>();

	/**
	 * List of evaluation criteria in this suite
	 */
	private List<EvaluationCriterion> criteria = new ArrayList<>();

	/**
	 * Default timeout for individual criterion execution (in milliseconds).
	 * This is used when a criterion does not specify its own timeoutMs.
	 * Default is 10000ms (10 seconds).
	 */
	private long defaultCriterionTimeoutMs = 10000;

	/**
	 * Compiled evaluation graph representation based on criteria dependencies.
	 * This is built by EvaluationSuiteBuilder using graph-core and treated as an internal implementation detail for execution.
	 */
	@JsonIgnore
	private transient CompiledGraph compiledGraph;

	// Getters and Setters

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

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

	public String getDefaultEvaluator() {
		return defaultEvaluator;
	}

	public void setDefaultEvaluator(String defaultEvaluator) {
		this.defaultEvaluator = defaultEvaluator;
	}

	public Map<String, Object> getDefaultModelConfig() {
		return defaultModelConfig;
	}

	public void setDefaultModelConfig(Map<String, Object> defaultModelConfig) {
		this.defaultModelConfig = defaultModelConfig;
	}

	public List<EvaluationCriterion> getCriteria() {
		return criteria;
	}

	public void setCriteria(List<EvaluationCriterion> criteria) {
		this.criteria = criteria;
	}

	public CompiledGraph getCompiledGraph() {
		return compiledGraph;
	}

	public void setCompiledGraph(CompiledGraph compiledGraph) {
		this.compiledGraph = compiledGraph;
	}

	public long getDefaultCriterionTimeoutMs() {
		return defaultCriterionTimeoutMs;
	}

	public void setDefaultCriterionTimeoutMs(long defaultCriterionTimeoutMs) {
		this.defaultCriterionTimeoutMs = defaultCriterionTimeoutMs;
	}
}
