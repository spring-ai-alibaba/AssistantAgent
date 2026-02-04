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
package com.alibaba.assistant.agent.core.observation;

import com.alibaba.assistant.agent.core.observation.context.HookObservationContext;
import com.alibaba.assistant.agent.core.observation.context.InterceptorObservationContext;
import com.alibaba.assistant.agent.core.observation.context.ReactPhaseObservationContext;
import io.opentelemetry.api.common.Attributes;
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
import java.util.function.Supplier;

/**
 * OpenTelemetry 可观测性辅助类
 * <p>
 * 提供统一的 Span 创建和管理能力，替代原有的 Micrometer Observation 实现。
 * <p>
 * 主要功能：
 * <ul>
 *   <li>Hook 执行的 Span 创建和管理</li>
 *   <li>Interceptor 执行的 Span 创建和管理</li>
 *   <li>React 阶段（LlmNode/ToolNode）的 Span 创建和管理</li>
 *   <li>Context 传播和 Scope 管理</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class OpenTelemetryObservationHelper {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryObservationHelper.class);

    private final Tracer tracer;

    /**
     * 存储活跃的 Span（按唯一标识）
     */
    private final ConcurrentHashMap<String, SpanHolder> activeSpans = new ConcurrentHashMap<>();

    public OpenTelemetryObservationHelper(Tracer tracer) {
        this.tracer = tracer;
        log.info("OpenTelemetryObservationHelper#<init> - reason=初始化完成");
    }

    // ==================== Hook Span ====================

    /**
     * 开始 Hook Span
     *
     * @param spanKey 唯一标识（如 sessionId:hookName）
     * @param context Hook观测上下文
     * @return 创建的 Span
     */
    public Span startHookSpan(String spanKey, HookObservationContext context) {
        String spanName = "codeact.hook." + (context.getHookName() != null
                ? context.getHookName().toLowerCase()
                : "unknown");

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAllAttributes(context.toAttributes())
                .startSpan();

        Scope scope = span.makeCurrent();
        activeSpans.put(spanKey, new SpanHolder(span, scope));

        log.debug("OpenTelemetryObservationHelper#startHookSpan - reason=开始Hook Span, " +
                "spanKey={}, hookName={}", spanKey, context.getHookName());

        return span;
    }

    /**
     * 结束 Hook Span
     *
     * @param spanKey  唯一标识
     * @param context  更新后的上下文
     * @param error    异常（可选）
     */
    public void endHookSpan(String spanKey, HookObservationContext context, Throwable error) {
        SpanHolder holder = activeSpans.remove(spanKey);
        if (holder == null) {
            log.warn("OpenTelemetryObservationHelper#endHookSpan - reason=未找到Span, spanKey={}", spanKey);
            return;
        }

        try {
            Span span = holder.span;

            // 添加结束时的属性
            if (context.getDurationMs() > 0) {
                span.setAttribute("duration.ms", context.getDurationMs());
            }
            span.setAttribute("codeact.hook.success", context.isSuccess());

            if (error != null) {
                span.setStatus(StatusCode.ERROR, error.getMessage());
                span.recordException(error);
            } else if (!context.isSuccess()) {
                span.setStatus(StatusCode.ERROR, context.getErrorMessage());
            }

            span.end();
        } finally {
            holder.scope.close();
        }

        log.debug("OpenTelemetryObservationHelper#endHookSpan - reason=结束Hook Span, " +
                "spanKey={}, success={}", spanKey, context.isSuccess());
    }

    // ==================== Interceptor Span ====================

    /**
     * 开始 Interceptor Span
     *
     * @param spanKey 唯一标识
     * @param context Interceptor观测上下文
     * @return 创建的 Span
     */
    public Span startInterceptorSpan(String spanKey, InterceptorObservationContext context) {
        String typeName = context.getInterceptorType() != null
                ? context.getInterceptorType().name().toLowerCase()
                : "unknown";
        String spanName = "codeact.interceptor." + typeName + "." +
                (context.getInterceptorName() != null ? context.getInterceptorName().toLowerCase() : "unknown");

        SpanKind kind = context.getInterceptorType() == InterceptorObservationContext.InterceptorType.MODEL
                ? SpanKind.CLIENT
                : SpanKind.INTERNAL;

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(kind)
                .setAllAttributes(context.toAttributes())
                .startSpan();

        Scope scope = span.makeCurrent();
        activeSpans.put(spanKey, new SpanHolder(span, scope));

        log.debug("OpenTelemetryObservationHelper#startInterceptorSpan - reason=开始Interceptor Span, " +
                "spanKey={}, interceptorName={}", spanKey, context.getInterceptorName());

        return span;
    }

    /**
     * 结束 Interceptor Span
     *
     * @param spanKey  唯一标识
     * @param context  更新后的上下文
     * @param error    异常（可选）
     */
    public void endInterceptorSpan(String spanKey, InterceptorObservationContext context, Throwable error) {
        SpanHolder holder = activeSpans.remove(spanKey);
        if (holder == null) {
            log.warn("OpenTelemetryObservationHelper#endInterceptorSpan - reason=未找到Span, spanKey={}", spanKey);
            return;
        }

        try {
            Span span = holder.span;

            // 添加结束时的属性
            if (context.getDurationMs() > 0) {
                span.setAttribute("duration.ms", context.getDurationMs());
            }
            span.setAttribute("codeact.interceptor.success", context.isSuccess());

            // Token usage for model interceptors
            if (context.getInputTokens() > 0) {
                span.setAttribute("gen_ai.usage.input_tokens", (long) context.getInputTokens());
            }
            if (context.getOutputTokens() > 0) {
                span.setAttribute("gen_ai.usage.output_tokens", (long) context.getOutputTokens());
            }

            if (error != null) {
                span.setStatus(StatusCode.ERROR, error.getMessage());
                span.recordException(error);
            } else if (!context.isSuccess()) {
                span.setStatus(StatusCode.ERROR, context.getErrorMessage());
            }

            span.end();
        } finally {
            holder.scope.close();
        }

        log.debug("OpenTelemetryObservationHelper#endInterceptorSpan - reason=结束Interceptor Span, " +
                "spanKey={}, success={}", spanKey, context.isSuccess());
    }

    // ==================== React Phase Span ====================

    /**
     * 开始 React Phase Span
     *
     * @param spanKey 唯一标识
     * @param context React阶段观测上下文
     * @return 创建的 Span
     */
    public Span startReactPhaseSpan(String spanKey, ReactPhaseObservationContext context) {
        String nodeTypeName = context.getNodeType() != null
                ? context.getNodeType().name().toLowerCase()
                : "unknown";
        String spanName = "codeact.react." + nodeTypeName;

        SpanKind kind = context.getNodeType() == ReactPhaseObservationContext.NodeType.LLM
                ? SpanKind.CLIENT
                : SpanKind.INTERNAL;

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(kind)
                .setAllAttributes(context.toAttributes())
                .startSpan();

        Scope scope = span.makeCurrent();
        activeSpans.put(spanKey, new SpanHolder(span, scope));

        log.debug("OpenTelemetryObservationHelper#startReactPhaseSpan - reason=开始React Phase Span, " +
                "spanKey={}, nodeType={}", spanKey, nodeTypeName);

        return span;
    }

    /**
     * 结束 React Phase Span
     *
     * @param spanKey  唯一标识
     * @param context  更新后的上下文
     * @param error    异常（可选）
     */
    public void endReactPhaseSpan(String spanKey, ReactPhaseObservationContext context, Throwable error) {
        SpanHolder holder = activeSpans.remove(spanKey);
        if (holder == null) {
            log.warn("OpenTelemetryObservationHelper#endReactPhaseSpan - reason=未找到Span, spanKey={}", spanKey);
            return;
        }

        try {
            Span span = holder.span;

            // 添加结束时的属性
            if (context.getDurationMs() > 0) {
                span.setAttribute("duration.ms", context.getDurationMs());
            }
            span.setAttribute("codeact.react.success", context.isSuccess());

            // Token usage for LLM nodes
            if (context.getInputTokens() > 0) {
                span.setAttribute("gen_ai.usage.input_tokens", (long) context.getInputTokens());
            }
            if (context.getOutputTokens() > 0) {
                span.setAttribute("gen_ai.usage.output_tokens", (long) context.getOutputTokens());
            }
            if (context.getFinishReason() != null) {
                span.setAttribute("gen_ai.response.finish_reasons", context.getFinishReason());
            }

            if (error != null) {
                span.setStatus(StatusCode.ERROR, error.getMessage());
                span.recordException(error);
            } else if (!context.isSuccess()) {
                span.setStatus(StatusCode.ERROR, context.getErrorMessage());
            }

            span.end();
        } finally {
            holder.scope.close();
        }

        log.debug("OpenTelemetryObservationHelper#endReactPhaseSpan - reason=结束React Phase Span, " +
                "spanKey={}, success={}", spanKey, context.isSuccess());
    }

    // ==================== Generic Span Operations ====================

    /**
     * 在指定的 Span 作用域内执行操作
     *
     * @param spanName   Span名称
     * @param attributes Span属性
     * @param action     要执行的操作
     * @param <T>        返回值类型
     * @return 操作结果
     */
    public <T> T withSpan(String spanName, Attributes attributes, Supplier<T> action) {
        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAllAttributes(attributes)
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            T result = action.get();
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * 在指定的 Span 作用域内执行操作（无返回值）
     *
     * @param spanName   Span名称
     * @param attributes Span属性
     * @param action     要执行的操作
     */
    public void withSpanVoid(String spanName, Attributes attributes, Runnable action) {
        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAllAttributes(attributes)
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            action.run();
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * 获取当前活跃的 Span 数量
     */
    public int getActiveSpanCount() {
        return activeSpans.size();
    }

    /**
     * 清理指定 session 相关的所有 Span
     *
     * @param sessionIdPrefix session前缀
     */
    public void cleanupSession(String sessionIdPrefix) {
        activeSpans.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(sessionIdPrefix)) {
                SpanHolder holder = entry.getValue();
                try {
                    holder.span.end();
                } finally {
                    holder.scope.close();
                }
                log.debug("OpenTelemetryObservationHelper#cleanupSession - reason=清理Span, spanKey={}",
                        entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Span 持有者，包含 Span 和对应的 Scope
     */
    private static class SpanHolder {
        final Span span;
        final Scope scope;

        SpanHolder(Span span, Scope scope) {
            this.span = span;
            this.scope = scope;
        }
    }
}

