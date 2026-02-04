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
package com.alibaba.assistant.agent.extension.evaluation.hook;

import com.alibaba.assistant.agent.evaluation.EvaluationService;
import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.assistant.agent.evaluation.model.EvaluationResult;
import com.alibaba.assistant.agent.evaluation.model.EvaluationSuite;
import com.alibaba.assistant.agent.extension.evaluation.store.OverAllStateEvaluationResultStore;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Prioritized;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

/**
 * BEFORE_AGENT 阶段的评估 Hook
 * 负责在 Agent 执行前进行评估，并将结果存储到 OverAllState
 * <p>
 * 支持功能：
 * - 同步/异步执行模式
 * - 超时控制
 * - 优先级排序
 * - 上下文构建支持 RunnableConfig
 *
 * @author Assistant Agent Team
 */
@HookPositions(HookPosition.BEFORE_AGENT)
public class BeforeAgentEvaluationHook extends AgentHook implements Prioritized {

    private static final Logger log = LoggerFactory.getLogger(BeforeAgentEvaluationHook.class);

    /**
     * 默认超时时间（毫秒）
     */
    private static final long DEFAULT_TIMEOUT_MS = 5000;

    /**
     * 默认优先级
     */
    private static final int DEFAULT_ORDER = 100;

    private final EvaluationService evaluationService;
    private final String suiteId;
    private final BiFunction<OverAllState, RunnableConfig, EvaluationContext> contextBuilder;
    private final boolean enabled;
    private final boolean async;
    private final long timeoutMs;
    private final int order;

    /**
     * 简化构造函数（默认异步、默认超时、默认优先级）
     *
     * @param evaluationService 评估服务
     * @param suiteId 评估套件 ID
     * @param contextBuilder 上下文构建函数
     * @param enabled 是否启用
     */
    public BeforeAgentEvaluationHook(EvaluationService evaluationService,
                                      String suiteId,
                                      BiFunction<OverAllState, RunnableConfig, EvaluationContext> contextBuilder,
                                      boolean enabled) {
        this(evaluationService, suiteId, contextBuilder, enabled, true, DEFAULT_TIMEOUT_MS, DEFAULT_ORDER);
    }

    /**
     * 带优先级的构造函数（默认异步、默认超时）
     *
     * @param evaluationService 评估服务
     * @param suiteId 评估套件 ID
     * @param contextBuilder 上下文构建函数
     * @param enabled 是否启用
     * @param order 优先级
     */
    public BeforeAgentEvaluationHook(EvaluationService evaluationService,
                                      String suiteId,
                                      BiFunction<OverAllState, RunnableConfig, EvaluationContext> contextBuilder,
                                      boolean enabled,
                                      int order) {
        this(evaluationService, suiteId, contextBuilder, enabled, true, DEFAULT_TIMEOUT_MS, order);
    }

    /**
     * 完整构造函数
     *
     * @param evaluationService 评估服务
     * @param suiteId 评估套件 ID
     * @param contextBuilder 上下文构建函数（支持 state 和 config）
     * @param enabled 是否启用
     * @param async 是否异步执行（false 时同步执行）
     * @param timeoutMs 超时时间（毫秒），仅异步模式下生效
     * @param order 优先级（越小越先执行）
     */
    public BeforeAgentEvaluationHook(EvaluationService evaluationService,
                                      String suiteId,
                                      BiFunction<OverAllState, RunnableConfig, EvaluationContext> contextBuilder,
                                      boolean enabled,
                                      boolean async,
                                      long timeoutMs,
                                      int order) {
        this.evaluationService = evaluationService;
        this.suiteId = suiteId;
        this.contextBuilder = contextBuilder;
        this.enabled = enabled;
        this.async = async;
        this.timeoutMs = timeoutMs;
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public String getName() {
        return "BeforeAgentEvaluationHook-" + suiteId;
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return List.of();
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        if (!enabled) {
            return CompletableFuture.completedFuture(Map.of());
        }

        log.info("BeforeAgentEvaluationHook#beforeAgent - reason=开始执行评估, suiteId={}, async={}", suiteId, async);

        try {
            // 1. 构造评估上下文（支持 config）
            EvaluationContext context = contextBuilder.apply(state, config);

            // 2. 加载评估套件
            EvaluationSuite suite = loadSuite();
            if (suite == null) {
                return CompletableFuture.completedFuture(Map.of());
            }

            // 3. 根据配置选择同步或异步执行
            if (async) {
                return executeAsync(state, suite, context);
            } else {
                return executeSync(state, suite, context);
            }

        } catch (Exception e) {
            log.error("BeforeAgentEvaluationHook#beforeAgent - reason=评估执行失败, suiteId=" + suiteId, e);
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    /**
     * 加载评估套件
     *
     * @return 评估套件，如果不存在返回 null
     */
    private EvaluationSuite loadSuite() {
        try {
            return evaluationService.loadSuite(suiteId);
        } catch (IllegalArgumentException e) {
            log.warn("BeforeAgentEvaluationHook#beforeAgent - reason=未找到评估套件, suiteId={}", suiteId);
            return null;
        }
    }

    /**
     * 同步执行评估
     */
    private CompletableFuture<Map<String, Object>> executeSync(OverAllState state,
                                                                EvaluationSuite suite,
                                                                EvaluationContext context) {
        EvaluationResult result = evaluationService.evaluate(suite, context);
        return CompletableFuture.completedFuture(buildResultMap(state, result));
    }

    /**
     * 异步执行评估（带超时控制）
     */
    private CompletableFuture<Map<String, Object>> executeAsync(OverAllState state,
                                                                 EvaluationSuite suite,
                                                                 EvaluationContext context) {
        return evaluationService.evaluateAsync(suite, context)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .thenApply(result -> buildResultMap(state, result))
                .exceptionally(e -> {
                    if (e.getCause() instanceof TimeoutException) {
                        log.warn("BeforeAgentEvaluationHook#beforeAgent - reason=评估超时, suiteId={}, timeoutMs={}",
                                suiteId, timeoutMs);
                    } else {
                        log.error("BeforeAgentEvaluationHook#beforeAgent - reason=异步评估失败, suiteId=" + suiteId, e);
                    }
                    return Map.of();
                });
    }

    /**
     * 构建结果 Map 并记录日志
     */
    private Map<String, Object> buildResultMap(OverAllState state, EvaluationResult result) {
        log.info("BeforeAgentEvaluationHook#beforeAgent - reason=评估完成, suiteId={}, statistics={}",
                suiteId, result.getStatistics());

        OverAllStateEvaluationResultStore store = new OverAllStateEvaluationResultStore(state);
        return store.createUpdateMap(suiteId, result);
    }

    /**
     * 获取评估套件 ID
     *
     * @return 评估套件 ID
     */
    public String getSuiteId() {
        return suiteId;
    }

    /**
     * 是否启用
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 是否异步执行
     *
     * @return 是否异步
     */
    public boolean isAsync() {
        return async;
    }

    /**
     * 获取超时时间
     *
     * @return 超时时间（毫秒）
     */
    public long getTimeoutMs() {
        return timeoutMs;
    }

    // ========== Builder 模式支持 ==========

    /**
     * 创建 Builder
     *
     * @param evaluationService 评估服务
     * @param suiteId 评估套件 ID
     * @return Builder 实例
     */
    public static Builder builder(EvaluationService evaluationService, String suiteId) {
        return new Builder(evaluationService, suiteId);
    }

    /**
     * Builder 类，提供更灵活的构造方式
     */
    public static class Builder {
        private final EvaluationService evaluationService;
        private final String suiteId;
        private BiFunction<OverAllState, RunnableConfig, EvaluationContext> contextBuilder;
        private boolean enabled = true;
        private boolean async = true;
        private long timeoutMs = DEFAULT_TIMEOUT_MS;
        private int order = DEFAULT_ORDER;

        private Builder(EvaluationService evaluationService, String suiteId) {
            this.evaluationService = evaluationService;
            this.suiteId = suiteId;
        }

        public Builder contextBuilder(BiFunction<OverAllState, RunnableConfig, EvaluationContext> contextBuilder) {
            this.contextBuilder = contextBuilder;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public BeforeAgentEvaluationHook build() {
            if (contextBuilder == null) {
                throw new IllegalStateException("contextBuilder is required");
            }
            return new BeforeAgentEvaluationHook(
                    evaluationService, suiteId, contextBuilder, enabled, async, timeoutMs, order);
        }
    }
}
