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
package com.alibaba.assistant.agent.evaluation.observation;

import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 评估 Graph 的可观测性生命周期监听器
 * <p>
 * 为每个评估项（criterion）创建独立的 OpenTelemetry Span，记录：
 * <ul>
 *   <li>评估项名称</li>
 *   <li>LLM 输入（prompt）</li>
 *   <li>LLM 输出（response）</li>
 *   <li>评估结果</li>
 *   <li>执行时长</li>
 * </ul>
 * <p>
 * 已从 Micrometer Observation 迁移到 OpenTelemetry 原生 API。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class EvaluationObservationLifecycleListener implements GraphLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(EvaluationObservationLifecycleListener.class);

    /**
     * 在 RunnableConfig metadata 中存储 parent Span 的 key
     * @deprecated 使用 ThreadLocal 代替，因为 Span 对象不能被序列化
     */
    @Deprecated
    public static final String PARENT_SPAN_KEY = "_evaluation_parent_span_";

    /**
     * InheritableThreadLocal 存储 parent Span
     * 使用 InheritableThreadLocal 确保子线程（异步执行）能继承 parent
     * 因为 Span 对象包含复杂的引用链，不能放入会被序列化的 Map 中
     */
    private static final InheritableThreadLocal<Span> PARENT_SPAN_HOLDER = new InheritableThreadLocal<>();

    /**
     * 设置 parent Span（由 GraphBasedEvaluationExecutor 调用）
     */
    public static void setParentSpan(Span parentSpan) {
        PARENT_SPAN_HOLDER.set(parentSpan);
    }

    /**
     * 获取 parent Span
     */
    public static Span getParentSpanFromThreadLocal() {
        return PARENT_SPAN_HOLDER.get();
    }

    /**
     * 清理 parent Span（执行完成后由 GraphBasedEvaluationExecutor 调用）
     */
    public static void clearParentSpan() {
        PARENT_SPAN_HOLDER.remove();
    }

    private final Tracer tracer;

    /**
     * 默认的 parent Span（在构造时设置）
     */
    private final Span defaultParentSpan;

    /**
     * 存储每个节点的 Span
     */
    private final ConcurrentHashMap<String, Span> nodeSpans = new ConcurrentHashMap<>();

    /**
     * 存储每个节点的 Scope
     */
    private final ConcurrentHashMap<String, Scope> nodeScopes = new ConcurrentHashMap<>();

    /**
     * 存储每个节点的开始时间
     */
    private final ConcurrentHashMap<String, Long> nodeStartTimes = new ConcurrentHashMap<>();

    /**
     * 存储每个节点的 parent Scope（用于正确的父子关系）
     */
    private final ConcurrentHashMap<String, Scope> parentScopes = new ConcurrentHashMap<>();

    /**
     * 静态存储 criterion 结果（按 nodeKey 分组）
     * 因为 after() 中的 state 可能是快照，不包含刚刚写入的结果
     */
    private static final ConcurrentHashMap<String, CriterionResult> CRITERION_RESULT_STORE = new ConcurrentHashMap<>();

    /**
     * 注册 criterion 结果（由 CriterionEvaluationAction 调用）
     *
     * @param criterionName criterion 名称
     * @param result        结果
     */
    public static void registerCriterionResult(String criterionName, CriterionResult result) {
        if (criterionName != null && result != null) {
            CRITERION_RESULT_STORE.put(criterionName, result);
            log.debug("EvaluationObservationLifecycleListener#registerCriterionResult - " +
                    "reason=注册结果, criterionName={}, status={}", criterionName, result.getStatus());
        }
    }

    /**
     * 获取并清除 criterion 结果
     *
     * @param criterionName criterion 名称
     * @return 结果
     */
    public static CriterionResult getAndClearCriterionResult(String criterionName) {
        return CRITERION_RESULT_STORE.remove(criterionName);
    }

    public EvaluationObservationLifecycleListener(Tracer tracer) {
        this(tracer, null);
    }

    /**
     * 构造函数，支持设置 parent Span
     *
     * @param tracer        OpenTelemetry Tracer
     * @param parentSpan    父 Span，所有评估项的 span 都会继承此父 span
     */
    public EvaluationObservationLifecycleListener(Tracer tracer, Span parentSpan) {
        this.tracer = tracer;
        this.defaultParentSpan = parentSpan;
        log.info("EvaluationObservationLifecycleListener#<init> - reason=初始化完成, hasTracer={}, hasParent={}",
                tracer != null, parentSpan != null);
    }

    @Override
    public void onStart(String nodeId, Map<String, Object> state, RunnableConfig config) {
        // 评估 Graph 开始时不需要特殊处理
        if (nodeId != null && nodeId.equalsIgnoreCase("__start__")) {
            log.debug("EvaluationObservationLifecycleListener#onStart - reason=评估Graph开始");
        }
    }

    @Override
    public void onComplete(String nodeId, Map<String, Object> state, RunnableConfig config) {
        // 评估 Graph 完成时不需要特殊处理
        if (nodeId != null && nodeId.equalsIgnoreCase("__end__")) {
            log.debug("EvaluationObservationLifecycleListener#onComplete - reason=评估Graph完成");
        }
    }

    @Override
    public void onError(String nodeId, Map<String, Object> state, Throwable ex, RunnableConfig config) {
        log.error("EvaluationObservationLifecycleListener#onError - reason=评估执行出错, nodeId={}", nodeId, ex);

        // 停止对应节点的 Span
        String nodeKey = getNodeKey(nodeId, config);
        stopSpan(nodeKey, ex);
    }

    @Override
    public void before(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
        // 跳过汇聚节点和系统节点
        if (nodeId == null || nodeId.startsWith("join_") || nodeId.startsWith("__")) {
            return;
        }

        if (tracer == null) {
            return;
        }

        String nodeKey = getNodeKey(nodeId, config);
        nodeStartTimes.put(nodeKey, System.currentTimeMillis());

        // 获取 parent Span
        Span parentSpan = getParentSpan(state);

        // 关键：如果有 parent，需要先将其设置为当前 context
        Context parentContext = Context.current();
        if (parentSpan != null) {
            parentContext = parentContext.with(parentSpan);
        }

        // 创建 criterion 评估的 Span
        Span span = tracer.spanBuilder("codeact.evaluation.criterion")
                .setParent(parentContext)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("gen_ai.span_kind_name", "EVALUATOR")
                .setAttribute("gen_ai.operation.name", "evaluate")
                .setAttribute("codeact.evaluation.criterion_name", nodeId)
                .startSpan();

        // 打开当前 span 的 scope
        Scope scope = span.makeCurrent();

        nodeSpans.put(nodeKey, span);
        nodeScopes.put(nodeKey, scope);

        log.debug("EvaluationObservationLifecycleListener#before - reason=评估项开始执行, criterionName={}", nodeId);
    }

    /**
     * 获取 parent Span
     * 优先从 ThreadLocal 中获取，其次使用构造时传入的默认值
     */
    private Span getParentSpan(Map<String, Object> state) {
        // 先尝试从 ThreadLocal 中获取
        Span fromThreadLocal = PARENT_SPAN_HOLDER.get();
        if (fromThreadLocal != null) {
            return fromThreadLocal;
        }
        // 使用默认的 parent Span
        return defaultParentSpan;
    }

    @Override
    public void after(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
        // 跳过汇聚节点和系统节点
        if (nodeId == null || nodeId.startsWith("join_") || nodeId.startsWith("__")) {
            return;
        }

        String nodeKey = getNodeKey(nodeId, config);

        // 计算执行时长
        Long startTime = nodeStartTimes.remove(nodeKey);
        long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0;

        // 先关闭当前 span 的 scope
        Scope scope = nodeScopes.remove(nodeKey);
        if (scope != null) {
            scope.close();
        }

        // 获取并停止 Span
        Span span = nodeSpans.remove(nodeKey);
        if (span != null) {
            span.setAttribute("duration.ms", durationMs);

            // 优先从静态存储获取结果（解决 state 快照问题）
            CriterionResult result = getAndClearCriterionResult(nodeId);

            // 如果静态存储没有，尝试从 state 获取
            if (result == null) {
                String resultKey = nodeId + "_result";
                Object resultObj = state.get(resultKey);
                if (resultObj instanceof CriterionResult) {
                    result = (CriterionResult) resultObj;
                }
            }

            if (result != null) {
                // 记录评估结果
                span.setAttribute("codeact.evaluation.status",
                        result.getStatus() != null ? result.getStatus().name() : "UNKNOWN");

                if (result.getValue() != null) {
                    span.setAttribute("codeact.evaluation.value",
                            truncate(result.getValue().toString(), 500));
                }

                if (result.getReason() != null) {
                    span.setAttribute("codeact.evaluation.reason",
                            truncate(result.getReason(), 500));
                }

                if (result.getRawPrompt() != null) {
                    // LLM 输入
                    span.setAttribute("gen_ai.input.messages",
                            truncate(result.getRawPrompt(), 2000));
                }

                if (result.getRawResponse() != null) {
                    // LLM 输出
                    span.setAttribute("gen_ai.output.messages",
                            truncate(result.getRawResponse(), 2000));
                }

                if (result.getErrorMessage() != null) {
                    span.setAttribute("codeact.evaluation.error",
                            truncate(result.getErrorMessage(), 500));
                }

                log.info("EvaluationObservationLifecycleListener#after - reason=评估项执行完成, " +
                                "criterionName={}, status={}, durationMs={}",
                        nodeId, result.getStatus(), durationMs);
            } else {
                log.warn("EvaluationObservationLifecycleListener#after - reason=评估结果未找到, criterionName={}", nodeId);
            }

            span.end();
        }
    }

    /**
     * 生成节点唯一标识
     */
    private String getNodeKey(String nodeId, RunnableConfig config) {
        String threadId = config != null ? config.threadId().orElse("default") : "default";
        return threadId + ":" + nodeId;
    }

    /**
     * 停止 Span（出错时调用）
     */
    private void stopSpan(String nodeKey, Throwable error) {
        Scope scope = nodeScopes.remove(nodeKey);
        if (scope != null) {
            scope.close();
        }

        Span span = nodeSpans.remove(nodeKey);
        if (span != null) {
            if (error != null) {
                span.setStatus(StatusCode.ERROR, error.getMessage());
                span.recordException(error);
                span.setAttribute("error.type", error.getClass().getSimpleName());
            }
            span.end();
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...[truncated]";
    }
}

