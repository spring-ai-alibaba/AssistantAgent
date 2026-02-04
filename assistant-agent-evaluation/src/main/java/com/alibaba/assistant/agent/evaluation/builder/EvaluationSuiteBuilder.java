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

import com.alibaba.assistant.agent.evaluation.aggregation.BatchAggregationStrategyRegistry;
import com.alibaba.assistant.agent.evaluation.evaluator.EvaluatorRegistry;
import com.alibaba.assistant.agent.evaluation.executor.CriterionEvaluationAction;
import com.alibaba.assistant.agent.evaluation.model.EvaluationCriterion;
import com.alibaba.assistant.agent.evaluation.model.EvaluationSuite;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Builder for creating EvaluationSuite instances
 *
 * @author Assistant Agent Team
 */
public class EvaluationSuiteBuilder {

    private final EvaluationSuite suite;
    private final List<EvaluationCriterion> criteria = new ArrayList<>();
    private final EvaluatorRegistry evaluatorRegistry;
    private final BatchAggregationStrategyRegistry aggregationStrategyRegistry;
    private ExecutorService executorService;
    private final List<GraphLifecycleListener> lifecycleListeners = new ArrayList<>();
    private Tracer tracer;
    private Span parentSpan;

    public EvaluationSuiteBuilder(String id, EvaluatorRegistry evaluatorRegistry) {
        this(id, evaluatorRegistry, new BatchAggregationStrategyRegistry(), null);
    }

    public EvaluationSuiteBuilder(String id,
                                  EvaluatorRegistry evaluatorRegistry,
                                  BatchAggregationStrategyRegistry aggregationStrategyRegistry,
                                  ExecutorService executorService) {
        this.suite = new EvaluationSuite();
        this.suite.setId(id);
        this.evaluatorRegistry = evaluatorRegistry;
        this.aggregationStrategyRegistry = aggregationStrategyRegistry;
        this.executorService = executorService;
    }


    public static EvaluationSuiteBuilder create(String id, EvaluatorRegistry evaluatorRegistry) {
        return new EvaluationSuiteBuilder(id, evaluatorRegistry);
    }

    public static EvaluationSuiteBuilder create(String id,
                                                EvaluatorRegistry evaluatorRegistry,
                                                BatchAggregationStrategyRegistry aggregationStrategyRegistry,
                                                ExecutorService executorService) {
        return new EvaluationSuiteBuilder(id, evaluatorRegistry, aggregationStrategyRegistry, executorService);
    }

    public EvaluationSuiteBuilder executorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    /**
     * 添加 GraphLifecycleListener 用于可观测性
     *
     * @param listener GraphLifecycleListener
     * @return this
     */
    public EvaluationSuiteBuilder lifecycleListener(GraphLifecycleListener listener) {
        if (listener != null) {
            this.lifecycleListeners.add(listener);
        }
        return this;
    }

    /**
     * 添加多个 GraphLifecycleListeners
     *
     * @param listeners GraphLifecycleListeners
     * @return this
     */
    public EvaluationSuiteBuilder lifecycleListeners(List<? extends GraphLifecycleListener> listeners) {
        if (listeners != null) {
            this.lifecycleListeners.addAll(listeners);
        }
        return this;
    }

    /**
     * 设置 Tracer 用于可观测性
     *
     * @param tracer Tracer
     * @return this
     */
    public EvaluationSuiteBuilder tracer(Tracer tracer) {
        this.tracer = tracer;
        return this;
    }

    /**
     * 设置 parent Span，所有评估项的 span 都会继承此父 span
     *
     * @param parentSpan 父 Span
     * @return this
     */
    public EvaluationSuiteBuilder parentSpan(Span parentSpan) {
        this.parentSpan = parentSpan;
        return this;
    }


    public EvaluationSuiteBuilder name(String name) {
        suite.setName(name);
        return this;
    }

    public EvaluationSuiteBuilder description(String description) {
        suite.setDescription(description);
        return this;
    }

    public EvaluationSuiteBuilder defaultEvaluator(String defaultEvaluator) {
        suite.setDefaultEvaluator(defaultEvaluator);
        return this;
    }

    public EvaluationSuiteBuilder defaultModelConfig(Map<String, Object> config) {
        suite.setDefaultModelConfig(config);
        return this;
    }

    public EvaluationSuiteBuilder addCriterion(EvaluationCriterion criterion) {
        criteria.add(criterion);
        return this;
    }

    public EvaluationSuiteBuilder addCriteria(List<EvaluationCriterion> criteria) {
        this.criteria.addAll(criteria);
        return this;
    }

    public EvaluationSuiteBuilder addCriteria(EvaluationCriterion... criteria) {
        Collections.addAll(this.criteria, criteria);
        return this;
    }

    public EvaluationSuite build() {
        // Validate required fields
        if (suite.getId() == null || suite.getId().isEmpty()) {
            throw new IllegalStateException("Suite ID is required");
        }
        if (suite.getName() == null || suite.getName().isEmpty()) {
            suite.setName(suite.getId());
        }

        // Validate that all evaluator references exist in the registry
        validateEvaluatorReferences();

        // Attach criteria list
        suite.setCriteria(criteria);

        try {
            // Build evaluation graph using graph-core StateGraph based on dependsOn
            // Configure KeyStrategyFactory to properly manage state keys
            StateGraph stateGraph = new StateGraph(() -> {
                Map<String, KeyStrategy> strategies = new HashMap<>();
                // suite and evaluationContext are read-only, use ReplaceStrategy
                strategies.put("suite", new ReplaceStrategy());
                strategies.put("evaluationContext", new ReplaceStrategy());

                // Each criterion result gets its own state key with ReplaceStrategy
                for (EvaluationCriterion criterion : criteria) {
                    strategies.put(criterion.getName() + "_result", new ReplaceStrategy());
                    strategies.put(criterion.getName() + "_status", new ReplaceStrategy());
                    strategies.put(criterion.getName() + "_completed", new ReplaceStrategy());
                    strategies.put(criterion.getName() + "_value", new ReplaceStrategy());
                    strategies.put(criterion.getName() + "_error", new ReplaceStrategy());
                }

                strategies.put("lastExecuted", new ReplaceStrategy());
                strategies.put("timestamp", new ReplaceStrategy());

                // 汇聚节点的状态（使用不以 __ 开头的命名）
                for (int i = 0; i < 100; i++) {
                    strategies.put("join_level_" + i, new ReplaceStrategy());
                }

                return strategies;
            });

            // Add a node for each criterion with actual evaluation delegation
            for (EvaluationCriterion criterion : criteria) {
                // Create local final references to avoid closure reference issues
                final EvaluationCriterion criterionCopy = criterion;
                final EvaluatorRegistry registryCopy = evaluatorRegistry;
                final BatchAggregationStrategyRegistry strategyCopy = aggregationStrategyRegistry;
                final ExecutorService executorCopy = executorService;

                stateGraph.addNode(criterion.getName(), (overAllState, config) -> {
                    // Use actual evaluation delegation via CriterionEvaluationAction
                    // Pass executor service and aggregation strategy registry for batching support
                    CriterionEvaluationAction action = new CriterionEvaluationAction(
                            criterionCopy,
                            registryCopy,
                            strategyCopy,
                            executorCopy
                    );
                    Map<String, Object> updates = action.apply(overAllState);

                    // No aggregation needed - just return the updates directly
                    // Each criterion's result is stored under <name>_result key
                    return completedFuture(updates);
                });
            }

            // Wire edges based on dependsOn; criteria without deps go from START
            // 分析依赖关系，构建并行执行图
            // 框架约束：并行节点必须汇聚到同一个目标节点
            // 解决方案：使用中间汇聚节点

            if (criteria.isEmpty()) {
                // 空 criteria，创建一个空节点连接 START 和 END
                stateGraph.addNode("empty_placeholder", (overAllState, config) ->
                        completedFuture(Map.of()));
                stateGraph.addEdge(StateGraph.START, "empty_placeholder");
                stateGraph.addEdge("empty_placeholder", StateGraph.END);
            } else {
                // 分析依赖层级，将节点按层级分组
                List<List<String>> levels = buildExecutionLevels(criteria);

                // 为每个层级之间添加汇聚节点
                // 结构: START -> level0_nodes -> join0 -> level1_nodes -> join1 -> ... -> END
                String previousJoinNode = StateGraph.START;

                for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
                    List<String> levelNodes = levels.get(levelIndex);
                    // 使用不以 __ 开头的节点名，避免与框架保留前缀冲突
                    String joinNodeName = "join_level_" + levelIndex;

                    if (levelNodes.size() == 1) {
                        // 单节点层级，直接连接
                        stateGraph.addEdge(previousJoinNode, levelNodes.get(0));
                        previousJoinNode = levelNodes.get(0);
                    } else {
                        // 多节点层级，需要汇聚节点
                        // 添加汇聚节点
                        stateGraph.addNode(joinNodeName, (overAllState, config) ->
                                completedFuture(Map.of()));

                        // 从上一个汇聚点并行分发到当前层级的所有节点
                        for (String nodeName : levelNodes) {
                            stateGraph.addEdge(previousJoinNode, nodeName);
                        }

                        // 所有当前层级节点汇聚到 joinNode
                        for (String nodeName : levelNodes) {
                            stateGraph.addEdge(nodeName, joinNodeName);
                        }

                        previousJoinNode = joinNodeName;
                    }
                }

                // 最后连接到 END
                stateGraph.addEdge(previousJoinNode, StateGraph.END);
            }

            // Build CompileConfig with lifecycleListeners for observability
            CompileConfig.Builder configBuilder = CompileConfig.builder();

            // Add ObservationRegistry if available

            // Add all lifecycleListeners
            for (GraphLifecycleListener listener : lifecycleListeners) {
                configBuilder.withLifecycleListener(listener);
            }

            CompileConfig compileConfig = configBuilder.build();

            // Compile graph with config
            CompiledGraph compiled = stateGraph.compile(compileConfig);
            suite.setCompiledGraph(compiled);
        }
        catch (GraphStateException e) {
            throw new IllegalStateException("Failed to build or compile evaluation graph", e);
        }

        return suite;
    }

    /**
     * Validate that all evaluator references exist in the registry
     */
    private void validateEvaluatorReferences() {
        for (EvaluationCriterion criterion : criteria) {
            String evaluatorRef = criterion.getEvaluatorRef();
            if (evaluatorRef != null && !evaluatorRef.isEmpty()) {
                if (!evaluatorRegistry.hasEvaluator(evaluatorRef)) {
                    throw new IllegalStateException(
                            String.format("Criterion '%s' references unknown evaluator '%s'. " +
                                            "Available evaluators: %s",
                                    criterion.getName(),
                                    evaluatorRef,
                                    evaluatorRegistry.getEvaluatorIds()));
                }
            }
        }
    }

    /**
     * 根据依赖关系构建执行层级
     * 同一层级的节点可以并行执行，不同层级按顺序执行
     * 层级0：无依赖的节点
     * 层级N：依赖于层级0~N-1的节点
     *
     * @param criteria 评估标准列表
     * @return 按层级分组的节点名称列表
     */
    private List<List<String>> buildExecutionLevels(List<EvaluationCriterion> criteria) {
        // 构建节点名称到节点的映射
        Map<String, EvaluationCriterion> nameToNode = new HashMap<>();
        for (EvaluationCriterion criterion : criteria) {
            nameToNode.put(criterion.getName(), criterion);
        }

        // 记录每个节点的层级
        Map<String, Integer> nodeLevel = new HashMap<>();

        // 计算每个节点的层级（递归计算）
        for (EvaluationCriterion criterion : criteria) {
            computeNodeLevel(criterion.getName(), nameToNode, nodeLevel);
        }

        // 找出最大层级
        int maxLevel = nodeLevel.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        // 按层级分组
        List<List<String>> levels = new ArrayList<>();
        for (int i = 0; i <= maxLevel; i++) {
            levels.add(new ArrayList<>());
        }

        for (EvaluationCriterion criterion : criteria) {
            int level = nodeLevel.get(criterion.getName());
            levels.get(level).add(criterion.getName());
        }

        // 移除空层级
        levels.removeIf(List::isEmpty);

        return levels;
    }

    /**
     * 递归计算节点的层级
     * 节点层级 = max(所有依赖节点的层级) + 1
     * 无依赖节点的层级为 0
     */
    private int computeNodeLevel(String nodeName,
                                 Map<String, EvaluationCriterion> nameToNode,
                                 Map<String, Integer> nodeLevel) {
        // 如果已计算过，直接返回
        if (nodeLevel.containsKey(nodeName)) {
            return nodeLevel.get(nodeName);
        }

        EvaluationCriterion criterion = nameToNode.get(nodeName);
        if (criterion == null) {
            // 节点不存在，返回-1（作为外部依赖）
            return -1;
        }

        List<String> deps = criterion.getDependsOn();
        if (deps == null || deps.isEmpty()) {
            // 无依赖，层级为0
            nodeLevel.put(nodeName, 0);
            return 0;
        }

        // 计算所有依赖的最大层级
        int maxDepLevel = -1;
        for (String dep : deps) {
            if (nameToNode.containsKey(dep)) {
                int depLevel = computeNodeLevel(dep, nameToNode, nodeLevel);
                maxDepLevel = Math.max(maxDepLevel, depLevel);
            }
        }

        // 当前节点的层级 = 最大依赖层级 + 1
        int level = maxDepLevel + 1;
        nodeLevel.put(nodeName, level);
        return level;
    }
}
