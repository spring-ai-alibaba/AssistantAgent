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
package com.alibaba.assistant.agent.evaluation.executor;

import com.alibaba.assistant.agent.evaluation.aggregation.BatchAggregationStrategy;
import com.alibaba.assistant.agent.evaluation.aggregation.BatchAggregationStrategyRegistry;
import com.alibaba.assistant.agent.evaluation.evaluator.Evaluator;
import com.alibaba.assistant.agent.evaluation.evaluator.EvaluatorRegistry;
import com.alibaba.assistant.agent.evaluation.model.ConditionalExecutionConfig;
import com.alibaba.assistant.agent.evaluation.model.CriterionBatchingConfig;
import com.alibaba.assistant.agent.evaluation.model.CriterionExecutionContext;
import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.assistant.agent.evaluation.model.CriterionStatus;
import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.assistant.agent.evaluation.model.EvaluationCriterion;
import com.alibaba.assistant.agent.evaluation.model.EvaluationSuite;
import com.alibaba.assistant.agent.evaluation.model.ExecutionContextFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Graph node action that executes a single evaluation criterion.
 * This class bridges the graph-core execution model with the evaluation framework.
 *
 * Returns a Map containing the updated "results" to be merged into the main state
 * via graph-core's updateState mechanism.
 *
 * @author Assistant Agent Team
 */
public class CriterionEvaluationAction implements Function<OverAllState, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(CriterionEvaluationAction.class);

    private final EvaluationCriterion criterion;
    private final EvaluatorRegistry evaluatorRegistry;
    private final BatchAggregationStrategyRegistry aggregationStrategyRegistry;
    private final ExecutorService executorService;

    public CriterionEvaluationAction(EvaluationCriterion criterion, EvaluatorRegistry evaluatorRegistry) {
        this(criterion, evaluatorRegistry, new BatchAggregationStrategyRegistry(), null);
    }

    public CriterionEvaluationAction(EvaluationCriterion criterion,
                                     EvaluatorRegistry evaluatorRegistry,
                                     BatchAggregationStrategyRegistry aggregationStrategyRegistry,
                                     ExecutorService executorService) {
        this.criterion = criterion;
        this.evaluatorRegistry = evaluatorRegistry;
        this.aggregationStrategyRegistry = aggregationStrategyRegistry;
        this.executorService = executorService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> updates = new HashMap<>();

        try {
            // Extract essential components directly from state
            EvaluationSuite suite = (EvaluationSuite) state.data().get("suite");
            EvaluationContext evaluationContext = (EvaluationContext) state.data().get("evaluationContext");

            if (suite == null || evaluationContext == null) {
                throw new IllegalStateException("Required components not found in OverAllState");
            }

            logger.info("Executing criterion: {}", criterion.getName());

            // Build dependency results map from individual state keys
            Map<String, CriterionResult> dependencyResults = buildDependencyResults(state);

            // Check conditional execution configuration
            ConditionalExecutionConfig conditionalConfig = criterion.getConditionalExecution();
            if (conditionalConfig != null) {
                CriterionResult skipResult = checkConditionalExecution(conditionalConfig, dependencyResults);
                if (skipResult != null) {
                    // Condition not met, return skipped result
                    logger.info("Criterion '{}' skipped: {}", criterion.getName(), conditionalConfig.getSkipReason());
                    return buildSkippedResultUpdates(skipResult);
                }
                logger.debug("Conditional execution check passed for criterion: {}", criterion.getName());
            }

            // Check if batching is enabled
            CriterionBatchingConfig batchingConfig = criterion.getBatchingConfig();
            boolean batchingEnabled = batchingConfig != null && batchingConfig.isEnabled();

            CriterionResult result;
            if (batchingEnabled) {
                // Execute with batching
                logger.debug("Batching enabled for criterion: {}", criterion.getName());
                result = executeWithBatching(evaluationContext, dependencyResults, batchingConfig);
            } else {
                // Execute without batching (original logic)
                logger.debug("Batching not enabled for criterion: {}", criterion.getName());
                result = executeWithoutBatching(evaluationContext, dependencyResults);
            }

            // Store result as an independent state key
            updates.put(criterion.getName() + "_result", result);
            updates.put(criterion.getName() + "_status", result.getStatus().toString());
            updates.put(criterion.getName() + "_completed", true);
            updates.put("lastExecuted", criterion.getName());
            updates.put("timestamp", System.currentTimeMillis());

            if (result.getValue() != null) {
                updates.put(criterion.getName() + "_value", result.getValue());
            }

            logger.info("Criterion {} completed with result: {}", criterion.getName(), result.getValue());

        } catch (Exception e) {
            logger.error("Error executing criterion {}: {}", criterion.getName(), e.getMessage(), e);

            // Create error result
            CriterionResult errorResult = new CriterionResult();
            errorResult.setCriterionName(criterion.getName());
            errorResult.setStatus(CriterionStatus.ERROR);
            errorResult.setErrorMessage(e.getMessage());
            errorResult.setStartTimeMillis(System.currentTimeMillis());
            errorResult.setEndTimeMillis(System.currentTimeMillis());

            // Store error result as an independent state key
            updates.put(criterion.getName() + "_result", errorResult);
            updates.put(criterion.getName() + "_status", "ERROR");
            updates.put(criterion.getName() + "_error", e.getMessage());
            updates.put("timestamp", System.currentTimeMillis());
        }

        return updates;
    }

    /**
     * Build dependency results map from individual state keys
     * This reads <criterionName>_result keys from state for all dependencies
     */
    private Map<String, CriterionResult> buildDependencyResults(OverAllState state) {
        Map<String, CriterionResult> dependencyResults = new HashMap<>();

        if (criterion.getDependsOn() != null && !criterion.getDependsOn().isEmpty()) {
            for (String depName : criterion.getDependsOn()) {
                CriterionResult depResult = (CriterionResult) state.data().get(depName + "_result");
                if (depResult != null) {
                    dependencyResults.put(depName, depResult);
                }
            }
        }

        return dependencyResults;
    }

    /**
     * Execute criterion without batching (original logic).
     */
    private CriterionResult executeWithoutBatching(EvaluationContext evaluationContext,
                                                    Map<String, CriterionResult> dependencyResults) {
        // Use factory to create criterion execution context with dependencies
        CriterionExecutionContext executionContext = ExecutionContextFactory.createCriterionContext(
            criterion, evaluationContext, dependencyResults
        );

        // Find and execute evaluator
        Evaluator evaluator = findEvaluator(criterion);
        if (evaluator == null) {
            throw new IllegalStateException("No evaluator found for criterion: " + criterion.getName());
        }

        // Execute evaluation
        return evaluator.evaluate(executionContext);
    }

    /**
     * Execute criterion with batching and concurrency.
     */
    private CriterionResult executeWithBatching(EvaluationContext evaluationContext,
                                                Map<String, CriterionResult> dependencyResults,
                                                CriterionBatchingConfig batchingConfig) {
        try {
            // Validate batching config
            batchingConfig.validate();

            // Resolve source collection
            Object sourceObject = SourcePathResolver.resolve(
                batchingConfig.getSourcePath(),
                evaluationContext,
                dependencyResults
            );

            // Check if source is a collection
            if (!SourcePathResolver.isCollection(sourceObject)) {
                logger.warn("Source path '{}' did not resolve to a collection for criterion '{}', falling back to non-batching execution",
                    batchingConfig.getSourcePath(), criterion.getName());
                return executeWithoutBatching(evaluationContext, dependencyResults);
            }

            Collection<?> sourceCollection = SourcePathResolver.toCollection(sourceObject);
            if (sourceCollection == null || sourceCollection.isEmpty()) {
                logger.debug("Source collection is empty for criterion '{}', returning empty result", criterion.getName());
                return createEmptyCollectionResult();
            }

            logger.info("Criterion '{}': processing {} items with batchSize={}, maxConcurrentBatches={}",
                criterion.getName(), sourceCollection.size(), batchingConfig.getBatchSize(), batchingConfig.getMaxConcurrentBatches());

            // Split into batches
            List<List<Object>> batches = splitIntoBatches(sourceCollection, batchingConfig.getBatchSize());
            logger.debug("Split {} items into {} batches", sourceCollection.size(), batches.size());

            // Process batches
            List<CriterionResult> allBatchResults = processBatches(
                batches,
                evaluationContext,
                dependencyResults,
                batchingConfig
            );

            // Aggregate results
            return aggregateResults(evaluationContext, dependencyResults, batchingConfig, allBatchResults);

        } catch (Exception e) {
            logger.error("Error during batching execution for criterion '{}': {}", criterion.getName(), e.getMessage(), e);
            CriterionResult errorResult = new CriterionResult();
            errorResult.setCriterionName(criterion.getName());
            errorResult.setStatus(CriterionStatus.ERROR);
            errorResult.setErrorMessage("Batching execution failed: " + e.getMessage());
            errorResult.setStartTimeMillis(System.currentTimeMillis());
            errorResult.setEndTimeMillis(System.currentTimeMillis());
            return errorResult;
        }
    }

    /**
     * Split collection into batches.
     */
    private List<List<Object>> splitIntoBatches(Collection<?> collection, int batchSize) {
        List<List<Object>> batches = new ArrayList<>();
        List<Object> currentBatch = new ArrayList<>();

        for (Object item : collection) {
            currentBatch.add(item);
            if (currentBatch.size() >= batchSize) {
                batches.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
            }
        }

        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
    }

    /**
     * Process batches with concurrency control.
     */
    private List<CriterionResult> processBatches(List<List<Object>> batches,
                                                  EvaluationContext evaluationContext,
                                                  Map<String, CriterionResult> dependencyResults,
                                                  CriterionBatchingConfig batchingConfig) {
        int maxConcurrent = batchingConfig.getMaxConcurrentBatches();

        if (maxConcurrent <= 1 || executorService == null) {
            // Sequential execution
            return processBatchesSequentially(batches, evaluationContext, dependencyResults, batchingConfig);
        } else {
            // Concurrent execution with semaphore for concurrency control
            return processBatchesConcurrently(batches, evaluationContext, dependencyResults, batchingConfig, maxConcurrent);
        }
    }

    /**
     * Process batches sequentially.
     */
    private List<CriterionResult> processBatchesSequentially(List<List<Object>> batches,
                                                              EvaluationContext evaluationContext,
                                                              Map<String, CriterionResult> dependencyResults,
                                                              CriterionBatchingConfig batchingConfig) {
        List<CriterionResult> allResults = new ArrayList<>();

        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            List<Object> batch = batches.get(batchIndex);
            logger.debug("Processing batch {}/{} with {} items", batchIndex + 1, batches.size(), batch.size());

            List<CriterionResult> batchResults = processSingleBatch(
                batch,
                evaluationContext,
                dependencyResults,
                batchingConfig
            );
            allResults.addAll(batchResults);
        }

        return allResults;
    }

    /**
     * Process batches concurrently.
     */
    private List<CriterionResult> processBatchesConcurrently(List<List<Object>> batches,
                                                              EvaluationContext evaluationContext,
                                                              Map<String, CriterionResult> dependencyResults,
                                                              CriterionBatchingConfig batchingConfig,
                                                              int maxConcurrent) {
        Semaphore semaphore = new Semaphore(maxConcurrent);
        List<CompletableFuture<List<CriterionResult>>> futures = new ArrayList<>();

        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            final int index = batchIndex;
            final List<Object> batch = batches.get(batchIndex);

            CompletableFuture<List<CriterionResult>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire();
                    logger.debug("Processing batch {}/{} with {} items (concurrent)", index + 1, batches.size(), batch.size());
                    return processSingleBatch(batch, evaluationContext, dependencyResults, batchingConfig);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Batch processing interrupted", e);
                    return Collections.emptyList();
                } finally {
                    semaphore.release();
                }
            }, executorService);

            futures.add(future);
        }

        // Wait for all batches to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allOf.join();

        // Collect results in order
        return futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    /**
     * Process a single batch
     */
    private List<CriterionResult> processSingleBatch(List<Object> batch,
                                                      EvaluationContext evaluationContext,
                                                      Map<String, CriterionResult> dependencyResults,
                                                      CriterionBatchingConfig batchingConfig) {
        List<CriterionResult> batchResults = new ArrayList<>();
        String batchBindingKey = batchingConfig.getBatchBindingKey();

        Evaluator evaluator = findEvaluator(criterion);
        if (evaluator == null) {
            throw new IllegalStateException("No evaluator found for criterion: " + criterion.getName());
        }

        Map<String, Object> extraBindings = new HashMap<>();
        extraBindings.put(batchBindingKey, batch);

        CriterionExecutionContext batchContext = ExecutionContextFactory.createCriterionContext(
            criterion,
            evaluationContext,
            dependencyResults,
            extraBindings
        );

        // Evaluate this batch
        CriterionResult batchResult = evaluator.evaluate(batchContext);
        batchResults.add(batchResult);

        return batchResults;
    }

    /**
     * Aggregate batch results using the configured strategy.
     */
    private CriterionResult aggregateResults(EvaluationContext evaluationContext,
                                             Map<String, CriterionResult> dependencyResults,
                                             CriterionBatchingConfig batchingConfig,
                                             List<CriterionResult> batchResults) {
        String strategyId = batchingConfig.getAggregationStrategy();
        BatchAggregationStrategy strategy = aggregationStrategyRegistry.getStrategy(strategyId);

        if (strategy == null) {
            logger.warn("Aggregation strategy '{}' not found, using ANY_TRUE as default", strategyId);
            strategy = aggregationStrategyRegistry.getStrategy("ANY_TRUE");
        }

        if (strategy == null) {
            throw new IllegalStateException("No aggregation strategy available");
        }

        // Create base context for aggregation
        CriterionExecutionContext baseContext = ExecutionContextFactory.createCriterionContext(
            criterion,
            evaluationContext,
            dependencyResults
        );

        return strategy.aggregate(baseContext, batchResults);
    }

    /**
     * Create result for empty collection scenario.
     */
    private CriterionResult createEmptyCollectionResult() {
        CriterionResult result = new CriterionResult();
        result.setCriterionName(criterion.getName());
        result.setStatus(CriterionStatus.SUCCESS);
        result.setValue(false);
        result.setReason("Empty collection - no batches to evaluate");
        result.setStartTimeMillis(System.currentTimeMillis());
        result.setEndTimeMillis(System.currentTimeMillis());
        return result;
    }

    /**
     * Find the appropriate evaluator for this criterion
     */
    private Evaluator findEvaluator(EvaluationCriterion criterion) {
        // Use effective evaluator ref which provides intelligent defaults
        String evaluatorRef = criterion.getEvaluatorRef();

        // Try to get the specified evaluator
        Evaluator evaluator = evaluatorRegistry.getEvaluator(evaluatorRef);
        if (evaluator != null) {
            return evaluator;
        }

        // Fallback to default evaluator if specified ref not found
        Evaluator defaultEvaluator = evaluatorRegistry.getDefaultEvaluator();
        if (defaultEvaluator != null) {
            logger.warn("Evaluator '{}' not found for criterion '{}', using default evaluator '{}'",
                       evaluatorRef, criterion.getName(), defaultEvaluator.getEvaluatorId());
            return defaultEvaluator;
        }

        // No evaluator found
        return null;
    }

    // ========== Conditional Execution Support ==========

    /**
     * Check if the conditional execution condition is satisfied.
     * 
     * @param config the conditional execution configuration
     * @param dependencyResults results from dependent criteria
     * @return null if condition is satisfied (should continue execution),
     *         or a CriterionResult with SKIPPED status if condition is not met
     */
    private CriterionResult checkConditionalExecution(ConditionalExecutionConfig config,
                                                       Map<String, CriterionResult> dependencyResults) {
        String depCriterion = config.getDependsOnCriterion();
        
        if (depCriterion == null || depCriterion.isEmpty()) {
            logger.warn("Conditional execution configured for '{}' but dependsOnCriterion is not set",
                criterion.getName());
            return null; // Proceed with execution if misconfigured
        }

        CriterionResult depResult = dependencyResults.get(depCriterion);
        
        if (depResult == null) {
            logger.warn("Dependency criterion '{}' not found for conditional execution of '{}'",
                depCriterion, criterion.getName());
            // Dependency not found - return skipped result
            return buildSkippedResult(config, "Dependency criterion not found: " + depCriterion);
        }

        // Check if dependency completed successfully
        if (depResult.getStatus() == CriterionStatus.ERROR || 
            depResult.getStatus() == CriterionStatus.TIMEOUT) {
            logger.warn("Dependency criterion '{}' did not complete successfully (status: {}), skipping '{}'",
                depCriterion, depResult.getStatus(), criterion.getName());
            return buildSkippedResult(config, 
                "Dependency criterion " + depCriterion + " did not complete successfully");
        }

        // Get the actual value from dependency result
        Object actualValue = depResult.getValue();
        
        // Check if condition is satisfied
        if (config.matches(actualValue)) {
            // Condition satisfied, return null to continue execution
            logger.debug("Conditional execution satisfied for '{}': {} (actual: {})",
                criterion.getName(), config.getConditionDescription(), actualValue);
            return null;
        }

        // Condition not satisfied, return skipped result
        String skipReason = config.getSkipReason();
        if (skipReason == null || skipReason.isEmpty()) {
            skipReason = String.format("Condition not met: %s (actual value: %s)",
                config.getConditionDescription(), actualValue);
        }
        
        logger.debug("Conditional execution not satisfied for '{}': expected {}, actual: {}",
            criterion.getName(), config.getConditionDescription(), actualValue);
        
        return buildSkippedResult(config, skipReason);
    }

    /**
     * Build a skipped result with the configured default value
     */
    private CriterionResult buildSkippedResult(ConditionalExecutionConfig config, String reason) {
        CriterionResult result = new CriterionResult();
        result.setCriterionName(criterion.getName());
        result.setStatus(CriterionStatus.SKIPPED);
        result.setValue(config.getDefaultValue());
        result.setReason(reason);
        result.setStartTimeMillis(System.currentTimeMillis());
        result.setEndTimeMillis(System.currentTimeMillis());
        
        // Add metadata about the skip
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("skippedByCondition", true);
        metadata.put("conditionCriterion", config.getDependsOnCriterion());
        metadata.put("conditionDescription", config.getConditionDescription());
        result.setMetadata(metadata);
        
        return result;
    }

    /**
     * Build state updates map for a skipped result
     */
    private Map<String, Object> buildSkippedResultUpdates(CriterionResult result) {
        Map<String, Object> updates = new HashMap<>();
        
        updates.put(criterion.getName() + "_result", result);
        updates.put(criterion.getName() + "_status", result.getStatus().toString());
        updates.put(criterion.getName() + "_completed", true);
        updates.put(criterion.getName() + "_skipped", true);
        updates.put("lastExecuted", criterion.getName());
        updates.put("timestamp", System.currentTimeMillis());
        
        if (result.getValue() != null) {
            updates.put(criterion.getName() + "_value", result.getValue());
        }
        
        return updates;
    }
}
