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
package com.alibaba.assistant.agent.core.observation.context;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor 观测上下文
 * <p>
 * 存储 Interceptor 执行过程中的观测数据，支持自定义数据注册。
 * 适用于 ModelInterceptor 和 ToolInterceptor。
 * <p>
 * 已从 Micrometer Observation.Context 迁移到 OpenTelemetry 原生 API。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class InterceptorObservationContext {

    // 标准属性键定义
    public static final AttributeKey<String> INTERCEPTOR_NAME_KEY = AttributeKey.stringKey("codeact.interceptor.name");
    public static final AttributeKey<String> INTERCEPTOR_TYPE_KEY = AttributeKey.stringKey("codeact.interceptor.type");
    public static final AttributeKey<String> AGENT_NAME_KEY = AttributeKey.stringKey("gen_ai.agent.name");
    public static final AttributeKey<String> SESSION_ID_KEY = AttributeKey.stringKey("gen_ai.conversation.id");
    public static final AttributeKey<Long> DURATION_MS_KEY = AttributeKey.longKey("duration.ms");
    public static final AttributeKey<Boolean> SUCCESS_KEY = AttributeKey.booleanKey("codeact.interceptor.success");
    public static final AttributeKey<String> MODEL_NAME_KEY = AttributeKey.stringKey("gen_ai.response.model");
    public static final AttributeKey<Long> INPUT_TOKENS_KEY = AttributeKey.longKey("gen_ai.usage.input_tokens");
    public static final AttributeKey<Long> OUTPUT_TOKENS_KEY = AttributeKey.longKey("gen_ai.usage.output_tokens");
    public static final AttributeKey<String> TOOL_NAME_KEY = AttributeKey.stringKey("gen_ai.tool.name");

    /**
     * 拦截器类型枚举
     */
    public enum InterceptorType {
        MODEL,
        TOOL
    }

    private String interceptorName;
    private InterceptorType interceptorType;
    private String agentName;
    private String sessionId;
    private long durationMs;
    private boolean success = true;
    private String errorType;
    private String errorMessage;

    // Model Interceptor specific fields
    private String modelName;
    private int inputTokens;
    private int outputTokens;
    private int messageCount;

    // Tool Interceptor specific fields
    private String toolName;
    private String toolArguments;
    private int toolArgumentsLength;
    private String toolResult;
    private int toolResultLength;

    /**
     * 自定义数据存储，允许Interceptor在执行过程中注册定制数据
     */
    private final Map<String, Object> customData = new HashMap<>();

    public InterceptorObservationContext() {
    }

    public InterceptorObservationContext(String interceptorName, InterceptorType interceptorType) {
        this.interceptorName = interceptorName;
        this.interceptorType = interceptorType;
    }

    // ==================== Common Getters and Setters ====================

    public String getInterceptorName() {
        return interceptorName;
    }

    public InterceptorObservationContext setInterceptorName(String interceptorName) {
        this.interceptorName = interceptorName;
        return this;
    }

    public InterceptorType getInterceptorType() {
        return interceptorType;
    }

    public InterceptorObservationContext setInterceptorType(InterceptorType interceptorType) {
        this.interceptorType = interceptorType;
        return this;
    }

    public String getAgentName() {
        return agentName;
    }

    public InterceptorObservationContext setAgentName(String agentName) {
        this.agentName = agentName;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public InterceptorObservationContext setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public InterceptorObservationContext setDurationMs(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public InterceptorObservationContext setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String getErrorType() {
        return errorType;
    }

    public InterceptorObservationContext setErrorType(String errorType) {
        this.errorType = errorType;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public InterceptorObservationContext setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    // ==================== Model Interceptor Getters and Setters ====================

    public String getModelName() {
        return modelName;
    }

    public InterceptorObservationContext setModelName(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public InterceptorObservationContext setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
        return this;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public InterceptorObservationContext setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
        return this;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public InterceptorObservationContext setMessageCount(int messageCount) {
        this.messageCount = messageCount;
        return this;
    }

    // ==================== Tool Interceptor Getters and Setters ====================

    public String getToolName() {
        return toolName;
    }

    public InterceptorObservationContext setToolName(String toolName) {
        this.toolName = toolName;
        return this;
    }

    public String getToolArguments() {
        return toolArguments;
    }

    public InterceptorObservationContext setToolArguments(String toolArguments) {
        this.toolArguments = toolArguments;
        this.toolArgumentsLength = toolArguments != null ? toolArguments.length() : 0;
        return this;
    }

    public int getToolArgumentsLength() {
        return toolArgumentsLength;
    }

    public String getToolResult() {
        return toolResult;
    }

    public InterceptorObservationContext setToolResult(String toolResult) {
        this.toolResult = toolResult;
        this.toolResultLength = toolResult != null ? toolResult.length() : 0;
        return this;
    }

    public int getToolResultLength() {
        return toolResultLength;
    }

    // ==================== Custom Data Methods ====================

    /**
     * 注册自定义数据
     *
     * @param key   数据键
     * @param value 数据值
     * @return this
     */
    public InterceptorObservationContext putCustomData(String key, Object value) {
        this.customData.put(key, value);
        return this;
    }

    /**
     * 批量注册自定义数据
     *
     * @param data 数据Map
     * @return this
     */
    public InterceptorObservationContext putAllCustomData(Map<String, Object> data) {
        if (data != null) {
            this.customData.putAll(data);
        }
        return this;
    }

    /**
     * 获取自定义数据
     *
     * @param key 数据键
     * @return 数据值，如果不存在返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getCustomData(String key) {
        return (T) this.customData.get(key);
    }

    /**
     * 获取所有自定义数据
     *
     * @return 自定义数据的不可变视图
     */
    public Map<String, Object> getAllCustomData() {
        return Map.copyOf(customData);
    }

    /**
     * 检查是否存在指定键的自定义数据
     *
     * @param key 数据键
     * @return 是否存在
     */
    public boolean hasCustomData(String key) {
        return customData.containsKey(key);
    }

    @Override
    public String toString() {
        return "InterceptorObservationContext{" +
                "interceptorName='" + interceptorName + '\'' +
                ", interceptorType=" + interceptorType +
                ", agentName='" + agentName + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", durationMs=" + durationMs +
                ", success=" + success +
                ", modelName='" + modelName + '\'' +
                ", toolName='" + toolName + '\'' +
                ", customDataKeys=" + customData.keySet() +
                '}';
    }

    /**
     * 将上下文转换为 OpenTelemetry Attributes
     *
     * @return Attributes 对象
     */
    public Attributes toAttributes() {
        AttributesBuilder builder = Attributes.builder();

        if (interceptorName != null) {
            builder.put(INTERCEPTOR_NAME_KEY, interceptorName);
        }
        if (interceptorType != null) {
            builder.put(INTERCEPTOR_TYPE_KEY, interceptorType.name());
        }
        if (agentName != null) {
            builder.put(AGENT_NAME_KEY, agentName);
        }
        if (sessionId != null) {
            builder.put(SESSION_ID_KEY, sessionId);
        }
        if (durationMs > 0) {
            builder.put(DURATION_MS_KEY, durationMs);
        }
        builder.put(SUCCESS_KEY, success);

        // Model specific
        if (modelName != null) {
            builder.put(MODEL_NAME_KEY, modelName);
        }
        if (inputTokens > 0) {
            builder.put(INPUT_TOKENS_KEY, (long) inputTokens);
        }
        if (outputTokens > 0) {
            builder.put(OUTPUT_TOKENS_KEY, (long) outputTokens);
        }

        // Tool specific
        if (toolName != null) {
            builder.put(TOOL_NAME_KEY, toolName);
        }

        // Error info
        if (errorType != null) {
            builder.put(AttributeKey.stringKey("error.type"), errorType);
        }
        if (errorMessage != null) {
            builder.put(AttributeKey.stringKey("error.message"), errorMessage);
        }

        // 添加自定义数据
        for (Map.Entry<String, Object> entry : customData.entrySet()) {
            if (entry.getValue() != null) {
                String key = "codeact.interceptor.custom." + entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    builder.put(AttributeKey.stringKey(key), (String) value);
                } else if (value instanceof Long) {
                    builder.put(AttributeKey.longKey(key), (Long) value);
                } else if (value instanceof Integer) {
                    builder.put(AttributeKey.longKey(key), ((Integer) value).longValue());
                } else if (value instanceof Double) {
                    builder.put(AttributeKey.doubleKey(key), (Double) value);
                } else if (value instanceof Boolean) {
                    builder.put(AttributeKey.booleanKey(key), (Boolean) value);
                } else {
                    builder.put(AttributeKey.stringKey(key), String.valueOf(value));
                }
            }
        }

        return builder.build();
    }
}

