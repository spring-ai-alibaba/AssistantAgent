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

import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.assistant.agent.evaluation.model.EvaluationResult;
import com.alibaba.assistant.agent.evaluation.model.EvaluationCriterion;
import com.alibaba.assistant.agent.evaluation.model.EvaluationSuite;
import com.alibaba.assistant.agent.evaluation.observation.EvaluationObservationLifecycleListener;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Graph-based evaluation executor that uses spring-ai-alibaba-graph-core
 * Compiles evaluation suite into StateGraph and executes with dependency management
 *
 * @author Assistant Agent Team
 */
public class GraphBasedEvaluationExecutor {

	private static final Logger logger = LoggerFactory.getLogger(GraphBasedEvaluationExecutor.class);

	private final ExecutorService executorService;
	private final boolean shouldShutdownExecutor;

	public GraphBasedEvaluationExecutor() {
		this(null);
	}

	public GraphBasedEvaluationExecutor(ExecutorService executorService) {
		if (executorService == null) {
			// Create default thread pool for batching
			this.executorService = Executors.newCachedThreadPool();
			this.shouldShutdownExecutor = true;
			logger.debug("Created default ExecutorService for batch processing");
		} else {
			this.executorService = executorService;
			this.shouldShutdownExecutor = false;
			logger.debug("Using provided ExecutorService for batch processing");
		}
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}

	/**
	 * Execute evaluation for a suite using its compiled graph.
	 */
	public EvaluationResult execute(EvaluationSuite suite, EvaluationContext context) {
		return execute(suite, context, null);
	}

	/**
	 * Execute evaluation for a suite using its compiled graph with parent Span.
	 *
	 * @param suite Evaluation suite to execute
	 * @param context Evaluation context
	 * @param parentSpan Parent span for establishing span hierarchy
	 * @return Evaluation result
	 */
	public EvaluationResult execute(EvaluationSuite suite, EvaluationContext context,
									Span parentSpan) {
		EvaluationResult result = new EvaluationResult();
		result.setSuiteId(suite.getId());
		result.setSuiteName(suite.getName());
		result.setStartTimeMillis(System.currentTimeMillis());

		try {
			CompiledGraph compiledGraph = suite.getCompiledGraph();
			if (compiledGraph == null) {
				throw new IllegalStateException(
					"EvaluationSuite.compiledGraph is null; make sure it was built via EvaluationSuiteBuilder with graph-core");
			}

			// Initialize input data - no longer need a results map
			Map<String, Object> initialData = new HashMap<>();
			initialData.put("suite", suite);
			initialData.put("evaluationContext", context);

			// 将 parent Span 存入 ThreadLocal，而不是 initialData
			// 因为 Span 对象不能被序列化
			if (parentSpan != null) {
				EvaluationObservationLifecycleListener.setParentSpan(parentSpan);
				logger.debug("GraphBasedEvaluationExecutor#execute - reason=设置parentSpan到ThreadLocal");
			}

			// Use CompiledGraph.invokeAndGetOutput to execute the graph
			// This follows graph-core best practices and ensures we get the final state
			// Important: graph-core uses ParallelNode for fan-out edges (e.g. multiple START children,
			// or a criterion that multiple other criteria depend on). ParallelNode defaults to an
			// internal static executor unless a per-node Executor is provided via RunnableConfig metadata.
			// We bind our executorService to all potential parallel-node IDs to keep concurrency under
			// the Evaluation module's control.
			RunnableConfig.Builder configBuilder = RunnableConfig.builder();
			Set<String> parallelNodeIds = findParallelNodeIds(suite);
			for (String nodeId : parallelNodeIds) {
				configBuilder.addParallelNodeExecutor(nodeId, executorService);
			}

			RunnableConfig config = configBuilder.build();
			Optional<NodeOutput> outputOpt = compiledGraph.invokeAndGetOutput(initialData, config);

			// Extract final state data from NodeOutput
			Map<String, Object> finalStateData =
				outputOpt.map(NodeOutput::state)
					.map(overAllState -> overAllState.data())
					.orElseGet(HashMap::new);

			// Collect results from individual <criterionName>_result keys
			Map<String, CriterionResult> criteriaResults = new HashMap<>();
			for (EvaluationCriterion criterion : suite.getCriteria()) {
				String resultKey = criterion.getName() + "_result";
				CriterionResult criterionResult = (CriterionResult) finalStateData.get(resultKey);

				if (criterionResult != null) {
					criteriaResults.put(criterion.getName(), criterionResult);
					logger.debug("Collected result for criterion '{}': status={}, value={}",
						criterion.getName(), criterionResult.getStatus(), criterionResult.getValue());
				} else {
					logger.warn("No result found for criterion '{}' in final state", criterion.getName());
				}
			}

			result.setCriteriaResults(criteriaResults);
			result.setStatistics(calculateStatistics(criteriaResults));

			logger.info("Collected {} criterion results from final state", criteriaResults.size());
		}
		catch (Exception e) {
			logger.error("Error executing evaluation graph: {}", e.getMessage(), e);
		}
		finally {
			result.setEndTimeMillis(System.currentTimeMillis());
			// 清理 ThreadLocal，避免内存泄漏
			EvaluationObservationLifecycleListener.clearParentSpan();
		}

		return result;
	}

	/**
	 * Identify node IDs that may become graph-core ParallelNodes (i.e. have multiple outgoing edges).
	 * <p>
	 * In our Evaluation graph, outgoing edges are derived from:
	 * <ul>
	 *   <li>START -&gt; root criteria (criteria without dependsOn)</li>
	 *   <li>dep -&gt; criterion (for each dependsOn relationship)</li>
	 * </ul>
	 * Graph-core will create a ParallelNode when a source has &gt; 1 target.
	 */
	private Set<String> findParallelNodeIds(EvaluationSuite suite) {
		if (suite == null || suite.getCriteria() == null || suite.getCriteria().isEmpty()) {
			return Set.of();
		}

		Map<String, Long> outDegrees = suite.getCriteria()
			.stream()
			.flatMap(criterion -> {
				if (criterion.getDependsOn() == null || criterion.getDependsOn().isEmpty()) {
					return java.util.stream.Stream.of(StateGraph.START);
				}
				return criterion.getDependsOn().stream();
			})
			.collect(Collectors.groupingBy(sourceId -> sourceId, Collectors.counting()));

		return outDegrees.entrySet()
			.stream()
			.filter(e -> e.getValue() != null && e.getValue() > 1)
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
	}

	/**
	 * Calculate statistics from results
	 */
	private EvaluationResult.EvaluationStatistics calculateStatistics(
			Map<String, CriterionResult> results) {

		EvaluationResult.EvaluationStatistics stats = new EvaluationResult.EvaluationStatistics();
		stats.setTotalCriteria(results.size());

		int success = 0, failed = 0, skipped = 0, timeout = 0, error = 0;

		for (CriterionResult result : results.values()) {
			switch (result.getStatus()) {
				case SUCCESS:
					success++;
					break;
				case FAILED:
					failed++;
					break;
				case SKIPPED:
					skipped++;
					break;
				case TIMEOUT:
					timeout++;
					break;
				case ERROR:
					error++;
					break;
			}
		}

		stats.setSuccessCount(success);
		stats.setFailedCount(failed);
		stats.setSkippedCount(skipped);
		stats.setTimeoutCount(timeout);
		stats.setErrorCount(error);

		return stats;
	}

	/**
	 * Shutdown the executor service
	 */
	public void shutdown() {
		if (shouldShutdownExecutor && executorService != null) {
			logger.info("GraphBasedEvaluationExecutor#shutdown - reason=关闭ExecutorService");
			executorService.shutdown();
		}
	}
}
