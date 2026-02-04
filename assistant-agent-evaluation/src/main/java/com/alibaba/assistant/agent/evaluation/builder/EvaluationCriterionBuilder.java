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
package com.alibaba.assistant.agent.evaluation.builder;

import com.alibaba.assistant.agent.evaluation.model.ConditionalExecutionConfig;
import com.alibaba.assistant.agent.evaluation.model.CriterionBatchingConfig;
import com.alibaba.assistant.agent.evaluation.model.EvaluationCriterion;
import com.alibaba.assistant.agent.evaluation.model.EvaluatorType;
import com.alibaba.assistant.agent.evaluation.model.ReasoningPolicy;
import com.alibaba.assistant.agent.evaluation.model.ResultType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Builder for creating EvaluationCriterion instances
 *
 * @author Assistant Agent Team
 */
public class EvaluationCriterionBuilder {

	private final EvaluationCriterion criterion;

	public EvaluationCriterionBuilder(String name) {
		this.criterion = new EvaluationCriterion();
		this.criterion.setName(name);
	}

	public static EvaluationCriterionBuilder create(String name) {
		return new EvaluationCriterionBuilder(name);
	}

	public EvaluationCriterionBuilder description(String description) {
		criterion.setDescription(description);
		return this;
	}

	public EvaluationCriterionBuilder resultType(ResultType resultType) {
		criterion.setResultType(resultType);
		return this;
	}

	public EvaluationCriterionBuilder options(String... options) {
		criterion.setOptions(Arrays.asList(options));
		return this;
	}

	public EvaluationCriterionBuilder options(List<String> options) {
		criterion.setOptions(options);
		return this;
	}

	public EvaluationCriterionBuilder dependsOn(String... dependencyNames) {
		criterion.setDependsOn(Arrays.asList(dependencyNames));
		return this;
	}

	public EvaluationCriterionBuilder dependsOn(List<String> dependencyNames) {
		criterion.setDependsOn(dependencyNames);
		return this;
	}

	public EvaluationCriterionBuilder evaluatorRef(String evaluatorRef) {
		criterion.setEvaluatorRef(evaluatorRef);
		return this;
	}

	public EvaluationCriterionBuilder evaluatorType(EvaluatorType evaluatorType) {
		criterion.setEvaluatorType(evaluatorType);
		return this;
	}

	public EvaluationCriterionBuilder config(Map<String, Object> config) {
		criterion.setConfig(config);
		return this;
	}

	public EvaluationCriterionBuilder workingMechanism(String workingMechanism) {
		criterion.setWorkingMechanism(workingMechanism);
		return this;
	}

	public EvaluationCriterionBuilder fewShots(List<EvaluationCriterion.FewShotExample> fewShots) {
		criterion.setFewShots(fewShots);
		return this;
	}

	public EvaluationCriterionBuilder addFewShot(String input, String context, String expectedOutput) {
		if (criterion.getFewShots() == null) {
			criterion.setFewShots(new ArrayList<>());
		}
		criterion.getFewShots().add(new EvaluationCriterion.FewShotExample(input, context, expectedOutput));
		return this;
	}

	public EvaluationCriterionBuilder reasoningPolicy(ReasoningPolicy reasoningPolicy) {
		criterion.setReasoningPolicy(reasoningPolicy);
		return this;
	}

	public EvaluationCriterionBuilder promptTemplate(String promptTemplate) {
		criterion.setCustomPrompt(promptTemplate);
		return this;
	}

	public EvaluationCriterionBuilder contextBindings(String... bindings) {
		criterion.setContextBindings(Arrays.asList(bindings));
		return this;
	}

	public EvaluationCriterionBuilder contextBindings(List<String> bindings) {
		criterion.setContextBindings(bindings);
		return this;
	}

	/**
	 * Set the batching configuration for this criterion.
	 * Enables batch processing of collections with controlled concurrency.
	 *
	 * @param config The batching configuration
	 * @return this builder
	 */
	public EvaluationCriterionBuilder batchingConfig(CriterionBatchingConfig config) {
		criterion.setBatchingConfig(config);
		return this;
	}

	/**
	 * Configure batching for this criterion with all parameters.
	 *
	 * @param sourcePath Path to the source collection (e.g., "context.input.toolList")
	 * @param batchSize Maximum number of items per batch
	 * @param maxConcurrentBatches Maximum concurrent batches (1 for sequential)
	 * @param batchBindingKey Key name for binding current batch in context
	 * @param aggregationStrategy Strategy for aggregating results (e.g., "ANY_TRUE", "ALL_TRUE", "MERGE_LISTS")
	 * @return this builder
	 */
	public EvaluationCriterionBuilder batching(String sourcePath, int batchSize, int maxConcurrentBatches,
	                                           String batchBindingKey, String aggregationStrategy) {
		CriterionBatchingConfig config = new CriterionBatchingConfig();
		config.setEnabled(true);
		config.setSourcePath(sourcePath);
		config.setBatchSize(batchSize);
		config.setMaxConcurrentBatches(maxConcurrentBatches);
		config.setBatchBindingKey(batchBindingKey);
		config.setAggregationStrategy(aggregationStrategy);
		criterion.setBatchingConfig(config);
		return this;
	}

	public EvaluationCriterionBuilder conditionalExecution(ConditionalExecutionConfig config) {
		criterion.setConditionalExecution(config);
		ensureDependsOn(config.getDependsOnCriterion());
		return this;
	}

	/**
	 * General-purpose conditional execution.
	 */
	public EvaluationCriterionBuilder conditionalOn(String dependsOnCriterion,
	                                                ConditionalExecutionConfig.MatchMode matchMode,
	                                                Object expectedValue,
	                                                Object defaultValue,
	                                                String skipReason) {
		ConditionalExecutionConfig cfgForMsg = ConditionalExecutionConfig.of(dependsOnCriterion, matchMode, expectedValue);
		ConditionalExecutionConfig config = cfgForMsg
			.withDefaultValue(defaultValue)
			.withSkipReason(skipReason != null ? skipReason : "Condition not met: " + cfgForMsg.getConditionDescription());
		criterion.setConditionalExecution(config);
		ensureDependsOn(dependsOnCriterion);
		return this;
	}


	/**
	 * Ensure the dependent criterion is in the dependsOn list
	 */
	private void ensureDependsOn(String dependsOnCriterion) {
		if (dependsOnCriterion == null) {
			return;
		}
		List<String> currentDeps = criterion.getDependsOn();
		if (currentDeps == null) {
			currentDeps = new ArrayList<>();
			criterion.setDependsOn(currentDeps);
		}
		if (!currentDeps.contains(dependsOnCriterion)) {
			List<String> newDeps = new ArrayList<>(currentDeps);
			newDeps.add(dependsOnCriterion);
			criterion.setDependsOn(newDeps);
		}
	}

	public EvaluationCriterion build() {
		// Validate required fields
		if (criterion.getName() == null || criterion.getName().isEmpty()) {
			throw new IllegalStateException("Criterion name is required");
		}
		return criterion;
	}
}
