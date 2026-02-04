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
import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Agent 可观测性生命周期监听器抽象基类
 * <p>
 * 提供通用的 OpenTelemetry Span 创建和管理能力，包括：
 * <ul>
 *   <li>Hook 执行的观测</li>
 *   <li>Interceptor 执行的观测</li>
 *   <li>React 阶段（LlmNode/ToolNode）的观测</li>
 *   <li>自定义数据注册机制</li>
 * </ul>
 * <p>
 * 已从 Micrometer Observation 迁移到 OpenTelemetry 原生 API。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public abstract class BaseAgentObservationLifecycleListener implements GraphLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(BaseAgentObservationLifecycleListener.class);

    /**
     * 状态 key：React 阶段开始时间
     */
    protected static final String REACT_START_TIME_KEY = "_react_start_time_";

    /**
     * 状态 key：ObservationState
     */
    protected static final String OBSERVATION_STATE_KEY = "_observation_state_";

    protected final Tracer tracer;

    /**
     * 存储每个 sessionId 的根 Span
     */
    protected final ConcurrentHashMap<String, Span> sessionSpans = new ConcurrentHashMap<>();

    /**
     * 存储每个 sessionId 的根 Span Scope
     */
    protected final ConcurrentHashMap<String, Scope> sessionScopes = new ConcurrentHashMap<>();

    /**
     * 存储每个节点的 Span
     */
    protected final ConcurrentHashMap<String, Span> nodeSpans = new ConcurrentHashMap<>();

    /**
     * 存储每个节点的 Span Scope
     */
    protected final ConcurrentHashMap<String, Scope> nodeScopes = new ConcurrentHashMap<>();

    /**
     * 存储每个 sessionId 的迭代计数器
     */
    protected final ConcurrentHashMap<String, AtomicInteger> iterationCounters = new ConcurrentHashMap<>();

    /**
     * 存储每个节点的开始时间
     */
    protected final ConcurrentHashMap<String, Long> nodeStartTimes = new ConcurrentHashMap<>();

    /**
     * 存储每个 sessionId 的 ObservationState
     */
    protected final ConcurrentHashMap<String, ObservationState> sessionStates = new ConcurrentHashMap<>();

    protected BaseAgentObservationLifecycleListener(Tracer tracer) {
        this.tracer = tracer;
        log.info("BaseAgentObservationLifecycleListener#<init> - reason=初始化完成, hasTracer={}", tracer != null);
    }

    // ==================== ObservationState Management ====================

    /**
     * 获取或创建会话的 ObservationState
     *
     * @param sessionId 会话ID
     * @return ObservationState
     */
    public ObservationState getOrCreateObservationState(String sessionId) {
        return sessionStates.computeIfAbsent(sessionId, k -> new DefaultObservationState());
    }

    /**
     * 获取会话的 ObservationState
     *
     * @param sessionId 会话ID
     * @return ObservationState，如果不存在返回null
     */
    public ObservationState getObservationState(String sessionId) {
        return sessionStates.get(sessionId);
    }

    /**
     * 从 OverAllState 中获取或创建 ObservationState
     *
     * @param state  OverAllState
     * @param config RunnableConfig
     * @return ObservationState
     */
    protected ObservationState getOrCreateObservationState(Map<String, Object> state, RunnableConfig config) {
        String sessionId = extractSessionId(config);

        // 先从缓存中获取
        ObservationState obsState = sessionStates.get(sessionId);
        if (obsState != null) {
            return obsState;
        }

        // 尝试从 state 中获取
        Object stateObj = state.get(OBSERVATION_STATE_KEY);
        if (stateObj instanceof ObservationState) {
            obsState = (ObservationState) stateObj;
            sessionStates.put(sessionId, obsState);
            return obsState;
        }

        // 创建新的并存入 state
        obsState = new DefaultObservationState();
        sessionStates.put(sessionId, obsState);
        state.put(OBSERVATION_STATE_KEY, obsState);
        return obsState;
    }

    // ==================== Hook Span ====================

    /**
     * 创建 Hook 的 Span
     *
     * @param context Hook观测上下文
     * @return Span
     */
    protected Span createHookSpan(HookObservationContext context) {
        if (tracer == null) {
            return null;
        }

        String spanName = CodeactObservationDocumentation.SPAN_HOOK + "." +
                (context.getHookName() != null ? context.getHookName().toLowerCase() : "unknown");

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(CodeactObservationDocumentation.GenAIAttributes.CONVERSATION_ID,
                        context.getSessionId() != null ? context.getSessionId() : "unknown")
                .setAttribute(CodeactObservationDocumentation.GenAIAttributes.SPAN_KIND_NAME, "CHAIN")
                .setAttribute(CodeactObservationDocumentation.GenAIAttributes.OPERATION_NAME, "chain")
                .setAttribute(CodeactObservationDocumentation.HookAttributes.NAME,
                        context.getHookName() != null ? context.getHookName() : "unknown")
                .setAttribute(CodeactObservationDocumentation.HookAttributes.POSITION,
                        context.getHookPosition() != null ? context.getHookPosition() : "unknown")
                .startSpan();

        // 添加自定义数据
        context.getAllCustomData().forEach((key, value) -> {
            if (value != null) {
                span.setAttribute("codeact.hook.custom." + key, truncate(value.toString(), 500));
            }
        });

        return span;
    }

    // ==================== Interceptor Span ====================

    /**
     * 创建 Interceptor 的 Span
     *
     * @param context Interceptor观测上下文
     * @return Span
     */
    protected Span createInterceptorSpan(InterceptorObservationContext context) {
        if (tracer == null) {
            return null;
        }

        String typeName = context.getInterceptorType() != null
                ? context.getInterceptorType().name().toLowerCase()
                : "unknown";
        String spanName = CodeactObservationDocumentation.SPAN_INTERCEPTOR + "." + typeName + "." +
                (context.getInterceptorName() != null ? context.getInterceptorName().toLowerCase() : "unknown");

        SpanKind spanKind = context.getInterceptorType() == InterceptorObservationContext.InterceptorType.MODEL
                ? SpanKind.CLIENT
                : SpanKind.INTERNAL;

        String genAiSpanKind = context.getInterceptorType() == InterceptorObservationContext.InterceptorType.MODEL
                ? "LLM"
                : (context.getInterceptorType() == InterceptorObservationContext.InterceptorType.TOOL
                        ? "TOOL"
                        : "CHAIN");

        String operationName = genAiSpanKind.equals("LLM") ? "chat"
                : (genAiSpanKind.equals("TOOL") ? "execute_tool" : "chain");

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(spanKind)
                .setAttribute(CodeactObservationDocumentation.GenAIAttributes.CONVERSATION_ID,
                        context.getSessionId() != null ? context.getSessionId() : "unknown")
                .setAttribute(CodeactObservationDocumentation.GenAIAttributes.SPAN_KIND_NAME, genAiSpanKind)
                .setAttribute(CodeactObservationDocumentation.GenAIAttributes.OPERATION_NAME, operationName)
                .setAttribute(CodeactObservationDocumentation.InterceptorAttributes.NAME,
                        context.getInterceptorName() != null ? context.getInterceptorName() : "unknown")
                .setAttribute(CodeactObservationDocumentation.InterceptorAttributes.TYPE, typeName)
                .startSpan();

        // Model Interceptor specific attributes
        if (context.getModelName() != null) {
            span.setAttribute(CodeactObservationDocumentation.InterceptorAttributes.MODEL_NAME, context.getModelName());
        }

        // Tool Interceptor specific attributes
        if (context.getToolName() != null) {
            span.setAttribute(CodeactObservationDocumentation.InterceptorAttributes.TOOL_NAME, context.getToolName());
        }

        // 添加自定义数据
        context.getAllCustomData().forEach((key, value) -> {
            if (value != null) {
                span.setAttribute("codeact.interceptor.custom." + key, truncate(value.toString(), 500));
            }
        });

        return span;
    }

    // ==================== React Phase Span ====================

    /**
     * 创建 React 阶段的 Span
     *
     * @param context React阶段观测上下文
     * @return Span
     */
    protected Span createReactPhaseSpan(ReactPhaseObservationContext context) {
        if (tracer == null) {
            return null;
        }

        String nodeTypeName = context.getNodeType() != null
                ? context.getNodeType().name().toLowerCase()
                : "unknown";
        String spanName = CodeactObservationDocumentation.SPAN_REACT + "." + nodeTypeName;

        SpanKind spanKind = context.getNodeType() == ReactPhaseObservationContext.NodeType.LLM
                ? SpanKind.CLIENT
                : SpanKind.INTERNAL;

        String genAiSpanKind = context.getNodeType() == ReactPhaseObservationContext.NodeType.LLM
                ? "LLM"
                : (context.getNodeType() == ReactPhaseObservationContext.NodeType.TOOL
                        ? "TOOL"
                        : "CHAIN");

        String operationName = genAiSpanKind.equals("LLM") ? "chat"
                : (genAiSpanKind.equals("TOOL") ? "execute_tool" : "chain");

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(spanKind)
                .setAttribute(CodeactObservationDocumentation.GenAIAttributes.CONVERSATION_ID,
                        context.getSessionId() != null ? context.getSessionId() : "unknown")
                .setAttribute(CodeactObservationDocumentation.GenAIAttributes.SPAN_KIND_NAME, genAiSpanKind)
                .setAttribute(CodeactObservationDocumentation.GenAIAttributes.OPERATION_NAME, operationName)
                .setAttribute(CodeactObservationDocumentation.ReactPhaseAttributes.NODE_TYPE, nodeTypeName)
                .startSpan();

        // Add model name for LLM nodes
        if (context.getModelName() != null) {
            span.setAttribute(CodeactObservationDocumentation.ReactPhaseAttributes.MODEL_NAME, context.getModelName());
        }

        // Add iteration info
        if (context.getIteration() > 0) {
            span.setAttribute(CodeactObservationDocumentation.ReactPhaseAttributes.ITERATION, (long) context.getIteration());
        }

        // Add node ID
        if (context.getNodeId() != null) {
            span.setAttribute(CodeactObservationDocumentation.ReactPhaseAttributes.NODE_ID, context.getNodeId());
        }

        // ==================== LLM Node 特定属性 ====================
        if (context.getNodeType() == ReactPhaseObservationContext.NodeType.LLM) {
            // 记录 prompt 消息数量
            if (context.getPromptMessageCount() > 0) {
                span.setAttribute("alibaba.llm.prompt_message_count", (long) context.getPromptMessageCount());
            }

            // 记录可用工具列表（只记录工具名）
            if (context.getAvailableToolNames() != null && !context.getAvailableToolNames().isEmpty()) {
                span.setAttribute("alibaba.llm.available_tools", String.join(",", context.getAvailableToolNames()));
                span.setAttribute("alibaba.llm.available_tools_count", (long) context.getAvailableToolNames().size());
            }

            // 记录输入摘要
            if (context.getInputSummary() != null) {
                span.setAttribute("alibaba.llm.input_summary", truncate(context.getInputSummary(), 1000));
            }

            // 记录输出摘要
            if (context.getOutputSummary() != null) {
                span.setAttribute("alibaba.llm.output_summary", truncate(context.getOutputSummary(), 1000));
            }

            // 记录 token 使用量
            if (context.getInputTokens() > 0) {
                span.setAttribute("alibaba.llm.input_tokens", (long) context.getInputTokens());
            }
            if (context.getOutputTokens() > 0) {
                span.setAttribute("alibaba.llm.output_tokens", (long) context.getOutputTokens());
            }

            // 记录完成原因
            if (context.getFinishReason() != null) {
                span.setAttribute("alibaba.llm.finish_reason", context.getFinishReason());
            }
        }

        // ==================== Tool Node 特定属性 ====================
        if (context.getNodeType() == ReactPhaseObservationContext.NodeType.TOOL) {
            List<ReactPhaseObservationContext.ToolCallInfo> toolCalls = context.getToolCalls();
            if (toolCalls != null && !toolCalls.isEmpty()) {
                // 记录工具调用数量
                span.setAttribute("alibaba.tool.call_count", (long) toolCalls.size());

                // 记录所有调用的工具名
                String toolNames = toolCalls.stream()
                        .map(ReactPhaseObservationContext.ToolCallInfo::getToolName)
                        .filter(name -> name != null)
                        .collect(Collectors.joining(","));
                span.setAttribute("alibaba.tool.names", toolNames);

                // 记录工具调用总时长
                long totalDuration = toolCalls.stream()
                        .mapToLong(ReactPhaseObservationContext.ToolCallInfo::getDurationMs)
                        .sum();
                span.setAttribute("alibaba.tool.total_duration_ms", totalDuration);

                // 记录工具调用结果总长度
                int totalResultLength = toolCalls.stream()
                        .mapToInt(ReactPhaseObservationContext.ToolCallInfo::getResultLength)
                        .sum();
                span.setAttribute("alibaba.tool.total_result_length", (long) totalResultLength);

                // 记录每个工具调用的详细信息
                for (int i = 0; i < toolCalls.size() && i < 10; i++) { // 最多记录10个工具调用
                    ReactPhaseObservationContext.ToolCallInfo toolCall = toolCalls.get(i);
                    String prefix = "alibaba.tool." + i + ".";
                    span.setAttribute(prefix + "name",
                            toolCall.getToolName() != null ? toolCall.getToolName() : "unknown");
                    span.setAttribute(prefix + "duration_ms", toolCall.getDurationMs());
                    span.setAttribute(prefix + "success", toolCall.isSuccess());
                    if (toolCall.getArguments() != null) {
                        span.setAttribute(prefix + "arguments", truncate(toolCall.getArguments(), 500));
                    }
                    if (toolCall.getResult() != null) {
                        span.setAttribute(prefix + "result", truncate(toolCall.getResult(), 500));
                    }
                    if (!toolCall.isSuccess() && toolCall.getErrorMessage() != null) {
                        span.setAttribute(prefix + "error", truncate(toolCall.getErrorMessage(), 200));
                    }
                }
            }
        }

        // 添加自定义数据
        context.getAllCustomData().forEach((key, value) -> {
            if (value != null) {
                span.setAttribute("codeact.react.custom." + key, truncate(value.toString(), 500));
            }
        });

        return span;
    }

    // ==================== Helper Methods ====================

    /**
     * 从 RunnableConfig 中提取 sessionId
     */
    protected String extractSessionId(RunnableConfig config) {
        if (config == null) {
            return "unknown";
        }
        return config.threadId().orElse("unknown");
    }

    /**
     * 从 RunnableConfig 的 metadata 中提取指定字段
     */
    protected String extractMetadata(RunnableConfig config, String key) {
        if (config == null) {
            return null;
        }
        return config.metadata(key)
                .map(Object::toString)
                .orElse(null);
    }

    /**
     * 截断字符串
     */
    protected String truncate(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...[truncated]";
    }

    /**
     * 停止节点 Span 并记录时长
     *
     * @param nodeKey     节点Key
     * @param durationMs  时长（毫秒）
     * @param success     是否成功
     * @param error       错误信息（可选）
     */
    protected void stopNodeSpan(String nodeKey, long durationMs, boolean success, Throwable error) {
        // 先关闭 Scope
        Scope scope = nodeScopes.remove(nodeKey);
        if (scope != null) {
            scope.close();
        }

        // 再结束 Span
        Span span = nodeSpans.remove(nodeKey);
        if (span != null) {
            span.setAttribute("duration.ms", durationMs);
            span.setAttribute("success", success);

            if (error != null) {
                span.setStatus(StatusCode.ERROR, error.getMessage());
                span.recordException(error);
            }

            span.end();
        }
    }

    /**
     * 清理会话资源
     *
     * @param sessionId 会话ID
     */
    protected void cleanupSession(String sessionId) {
        // 关闭会话 Scope
        Scope sessionScope = sessionScopes.remove(sessionId);
        if (sessionScope != null) {
            sessionScope.close();
        }

        // 结束会话 Span
        Span sessionSpan = sessionSpans.remove(sessionId);
        if (sessionSpan != null) {
            sessionSpan.end();
        }

        sessionStates.remove(sessionId);
        iterationCounters.remove(sessionId);

        // 清理该会话的所有节点 Span
        nodeSpans.keySet().removeIf(key -> key.startsWith(sessionId + ":"));
        nodeScopes.keySet().removeIf(key -> key.startsWith(sessionId + ":"));
        nodeStartTimes.keySet().removeIf(key -> key.startsWith(sessionId + ":"));
    }
}

