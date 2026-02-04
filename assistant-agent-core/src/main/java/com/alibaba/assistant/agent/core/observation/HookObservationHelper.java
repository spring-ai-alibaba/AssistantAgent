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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Hook 观测辅助工具类
 * <p>
 * 提供便捷的方法在 Hook 中创建和管理 OpenTelemetry Span。
 * <p>
 * 已从 Micrometer Observation 迁移到 OpenTelemetry 原生 API。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public final class HookObservationHelper {

    private static final Logger log = LoggerFactory.getLogger(HookObservationHelper.class);

    private HookObservationHelper() {
        // Utility class
    }

    /**
     * 为 Hook 执行创建观测并执行操作
     *
     * @param tracer       OpenTelemetry Tracer
     * @param hookName     Hook名称
     * @param hookPosition Hook位置（before/after）
     * @param sessionId    会话ID
     * @param action       要执行的操作
     * @param <T>          返回类型
     * @return 操作结果
     */
    public static <T> T observeHook(
            Tracer tracer,
            String hookName,
            String hookPosition,
            String sessionId,
            Supplier<T> action) {

        return observeHook(tracer, hookName, hookPosition, sessionId, null, action);
    }

    /**
     * 为 Hook 执行创建观测并执行操作（带自定义数据）
     *
     * @param tracer       OpenTelemetry Tracer
     * @param hookName     Hook名称
     * @param hookPosition Hook位置（before/after）
     * @param sessionId    会话ID
     * @param customData   自定义数据
     * @param action       要执行的操作
     * @param <T>          返回类型
     * @return 操作结果
     */
    public static <T> T observeHook(
            Tracer tracer,
            String hookName,
            String hookPosition,
            String sessionId,
            Map<String, Object> customData,
            Supplier<T> action) {

        if (tracer == null) {
            return action.get();
        }

        HookObservationContext context = new HookObservationContext(hookName, hookPosition);
        context.setSessionId(sessionId);
        if (customData != null) {
            context.putAllCustomData(customData);
        }

        long startTime = System.currentTimeMillis();
        String spanName = "codeact.hook." + (hookName != null ? hookName.toLowerCase() : "unknown");

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("gen_ai.conversation.id", sessionId != null ? sessionId : "unknown")
                .setAttribute("gen_ai.span_kind_name", "CHAIN")
                .setAttribute("gen_ai.operation.name", "chain")
                .setAttribute("codeact.hook.name", hookName != null ? hookName : "unknown")
                .setAttribute("codeact.hook.position", hookPosition != null ? hookPosition : "unknown")
                .startSpan();

        // 添加自定义数据到 Span
        if (customData != null) {
            customData.forEach((key, value) -> {
                if (value != null) {
                    span.setAttribute("codeact.hook.custom." + key, truncate(value.toString(), 200));
                }
            });
        }

        try (Scope ignored = span.makeCurrent()) {
            T result = action.get();

            long durationMs = System.currentTimeMillis() - startTime;
            context.setDurationMs(durationMs);
            context.setSuccess(true);

            span.setAttribute("duration.ms", durationMs);

            log.debug("HookObservationHelper#observeHook - reason=Hook执行成功, " +
                    "hookName={}, hookPosition={}, durationMs={}", hookName, hookPosition, durationMs);

            return result;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            context.setDurationMs(durationMs);
            context.setSuccess(false);
            context.setErrorType(e.getClass().getSimpleName());
            context.setErrorMessage(e.getMessage());

            span.setAttribute("duration.ms", durationMs);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);

            log.warn("HookObservationHelper#observeHook - reason=Hook执行失败, " +
                    "hookName={}, hookPosition={}, durationMs={}, errorType={}",
                    hookName, hookPosition, durationMs, e.getClass().getSimpleName());

            throw e;

        } finally {
            span.end();
        }
    }

    /**
     * 为异步 Hook 执行创建观测上下文（用于手动管理 Span 生命周期）
     *
     * @param tracer       OpenTelemetry Tracer
     * @param hookName     Hook名称
     * @param hookPosition Hook位置
     * @param sessionId    会话ID
     * @return Span 和 Context 的包装对象
     */
    public static HookObservationScope startHookObservation(
            Tracer tracer,
            String hookName,
            String hookPosition,
            String sessionId) {

        if (tracer == null) {
            return new HookObservationScope(null, null, null);
        }

        HookObservationContext context = new HookObservationContext(hookName, hookPosition);
        context.setSessionId(sessionId);

        String spanName = "codeact.hook." + (hookName != null ? hookName.toLowerCase() : "unknown");

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("gen_ai.conversation.id", sessionId != null ? sessionId : "unknown")
                .setAttribute("gen_ai.span_kind_name", "CHAIN")
                .setAttribute("gen_ai.operation.name", "chain")
                .setAttribute("codeact.hook.name", hookName != null ? hookName : "unknown")
                .setAttribute("codeact.hook.position", hookPosition != null ? hookPosition : "unknown")
                .startSpan();

        Scope scope = span.makeCurrent();

        return new HookObservationScope(span, scope, context);
    }

    /**
     * 截断字符串
     */
    private static String truncate(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    /**
     * Hook 观测作用域
     * <p>
     * 用于手动管理 Span 的生命周期，适用于异步场景。
     */
    public static class HookObservationScope implements AutoCloseable {
        private final Span span;
        private final Scope scope;
        private final HookObservationContext context;
        private final long startTime;

        HookObservationScope(Span span, Scope scope, HookObservationContext context) {
            this.span = span;
            this.scope = scope;
            this.context = context;
            this.startTime = System.currentTimeMillis();
        }

        /**
         * 添加自定义数据
         */
        public HookObservationScope putCustomData(String key, Object value) {
            if (context != null && key != null && value != null) {
                context.putCustomData(key, value);
                if (span != null) {
                    span.setAttribute("codeact.hook.custom." + key, truncate(value.toString(), 200));
                }
            }
            return this;
        }

        /**
         * 标记成功并关闭
         */
        public void success() {
            if (span != null && context != null) {
                long durationMs = System.currentTimeMillis() - startTime;
                context.setDurationMs(durationMs);
                context.setSuccess(true);
                span.setAttribute("duration.ms", durationMs);
            }
            close();
        }

        /**
         * 标记失败并关闭
         */
        public void failure(Throwable error) {
            if (span != null && context != null) {
                long durationMs = System.currentTimeMillis() - startTime;
                context.setDurationMs(durationMs);
                context.setSuccess(false);
                if (error != null) {
                    context.setErrorType(error.getClass().getSimpleName());
                    context.setErrorMessage(error.getMessage());
                    span.setStatus(StatusCode.ERROR, error.getMessage());
                    span.recordException(error);
                }
                span.setAttribute("duration.ms", durationMs);
            }
            close();
        }

        @Override
        public void close() {
            if (span != null) {
                span.end();
            }
            if (scope != null) {
                scope.close();
            }
        }

        /**
         * 获取上下文
         */
        public HookObservationContext getContext() {
            return context;
        }

        /**
         * 获取 Span
         */
        public Span getSpan() {
            return span;
        }

        /**
         * 检查是否为有效的 Scope
         */
        public boolean isValid() {
            return span != null;
        }
    }
}

