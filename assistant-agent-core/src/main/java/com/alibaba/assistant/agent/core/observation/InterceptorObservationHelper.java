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

import com.alibaba.assistant.agent.core.observation.context.InterceptorObservationContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Interceptor 观测辅助工具类
 * <p>
 * 提供便捷的方法在 ModelInterceptor 和 ToolInterceptor 中创建和管理 OpenTelemetry Span。
 * <p>
 * 已从 Micrometer Observation 迁移到 OpenTelemetry 原生 API。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public final class InterceptorObservationHelper {

    private static final Logger log = LoggerFactory.getLogger(InterceptorObservationHelper.class);

    private InterceptorObservationHelper() {
        // Utility class
    }

    /**
     * 为 ModelInterceptor 执行创建观测并执行操作
     *
     * @param tracer          OpenTelemetry Tracer
     * @param interceptorName Interceptor名称
     * @param sessionId       会话ID
     * @param modelName       模型名称
     * @param action          要执行的操作
     * @param <T>             返回类型
     * @return 操作结果
     */
    public static <T> T observeModelInterceptor(
            Tracer tracer,
            String interceptorName,
            String sessionId,
            String modelName,
            Supplier<T> action) {

        InterceptorObservationContext context = new InterceptorObservationContext(
                interceptorName, InterceptorObservationContext.InterceptorType.MODEL);
        context.setSessionId(sessionId);
        context.setModelName(modelName);

        return observeInterceptor(tracer, context, action);
    }

    /**
     * 为 ToolInterceptor 执行创建观测并执行操作
     *
     * @param tracer          OpenTelemetry Tracer
     * @param interceptorName Interceptor名称
     * @param sessionId       会话ID
     * @param toolName        工具名称
     * @param toolArguments   工具参数
     * @param action          要执行的操作
     * @param <T>             返回类型
     * @return 操作结果
     */
    public static <T> T observeToolInterceptor(
            Tracer tracer,
            String interceptorName,
            String sessionId,
            String toolName,
            String toolArguments,
            Supplier<T> action) {

        InterceptorObservationContext context = new InterceptorObservationContext(
                interceptorName, InterceptorObservationContext.InterceptorType.TOOL);
        context.setSessionId(sessionId);
        context.setToolName(toolName);
        context.setToolArguments(toolArguments);

        return observeInterceptor(tracer, context, action);
    }

    /**
     * 通用的 Interceptor 观测执行方法
     */
    private static <T> T observeInterceptor(
            Tracer tracer,
            InterceptorObservationContext context,
            Supplier<T> action) {

        if (tracer == null) {
            return action.get();
        }

        long startTime = System.currentTimeMillis();
        String spanName = buildSpanName(context);

        SpanKind spanKind = context.getInterceptorType() == InterceptorObservationContext.InterceptorType.MODEL
                ? SpanKind.CLIENT
                : SpanKind.INTERNAL;

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(spanKind)
                .setAttribute("gen_ai.conversation.id",
                        context.getSessionId() != null ? context.getSessionId() : "unknown")
                .setAttribute("gen_ai.span_kind_name",
                        context.getInterceptorType() == InterceptorObservationContext.InterceptorType.MODEL
                                ? "LLM" : "TOOL")
                .setAttribute("gen_ai.operation.name",
                        context.getInterceptorType() == InterceptorObservationContext.InterceptorType.MODEL
                                ? "chat" : "execute_tool")
                .setAttribute("codeact.interceptor.name",
                        context.getInterceptorName() != null ? context.getInterceptorName() : "unknown")
                .setAttribute("codeact.interceptor.type",
                        context.getInterceptorType() != null ? context.getInterceptorType().name() : "unknown")
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            T result = action.get();

            long durationMs = System.currentTimeMillis() - startTime;
            context.setDurationMs(durationMs);
            context.setSuccess(true);

            span.setAttribute("duration.ms", durationMs);

            log.debug("InterceptorObservationHelper#observeInterceptor - reason=Interceptor执行成功, " +
                    "interceptorName={}, durationMs={}", context.getInterceptorName(), durationMs);

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

            log.warn("InterceptorObservationHelper#observeInterceptor - reason=Interceptor执行失败, " +
                    "interceptorName={}, durationMs={}, errorType={}",
                    context.getInterceptorName(), durationMs, e.getClass().getSimpleName());

            throw e;

        } finally {
            span.end();
        }
    }

    /**
     * 构建 Span 名称
     */
    private static String buildSpanName(InterceptorObservationContext context) {
        String typeName = context.getInterceptorType() != null
                ? context.getInterceptorType().name().toLowerCase()
                : "unknown";
        String interceptorName = context.getInterceptorName() != null
                ? context.getInterceptorName().toLowerCase()
                : "unknown";
        return "codeact.interceptor." + typeName + "." + interceptorName;
    }
}

