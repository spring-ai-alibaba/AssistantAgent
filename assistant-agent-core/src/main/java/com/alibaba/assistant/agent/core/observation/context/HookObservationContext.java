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
 * Hook 观测上下文
 * <p>
 * 存储 Hook 执行过程中的观测数据，支持自定义数据注册。
 * <p>
 * 已从 Micrometer Observation.Context 迁移到 OpenTelemetry 原生 API。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class HookObservationContext {

    // 标准属性键定义
    public static final AttributeKey<String> HOOK_NAME = AttributeKey.stringKey("codeact.hook.name");
    public static final AttributeKey<String> HOOK_POSITION = AttributeKey.stringKey("codeact.hook.position");
    public static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("gen_ai.agent.name");
    public static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("gen_ai.conversation.id");
    public static final AttributeKey<Long> DURATION_MS = AttributeKey.longKey("duration.ms");
    public static final AttributeKey<Boolean> SUCCESS = AttributeKey.booleanKey("codeact.hook.success");
    public static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");
    public static final AttributeKey<String> ERROR_MESSAGE = AttributeKey.stringKey("error.message");

    private String hookName;
    private String hookPosition;
    private String agentName;
    private String sessionId;
    private long durationMs;
    private boolean success = true;
    private String errorType;
    private String errorMessage;

    /**
     * 自定义数据存储，允许Hook在执行过程中注册定制数据
     */
    private final Map<String, Object> customData = new HashMap<>();

    public HookObservationContext() {
    }

    public HookObservationContext(String hookName, String hookPosition) {
        this.hookName = hookName;
        this.hookPosition = hookPosition;
    }

    // ==================== Getters and Setters ====================

    public String getHookName() {
        return hookName;
    }

    public HookObservationContext setHookName(String hookName) {
        this.hookName = hookName;
        return this;
    }

    public String getHookPosition() {
        return hookPosition;
    }

    public HookObservationContext setHookPosition(String hookPosition) {
        this.hookPosition = hookPosition;
        return this;
    }

    public String getAgentName() {
        return agentName;
    }

    public HookObservationContext setAgentName(String agentName) {
        this.agentName = agentName;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public HookObservationContext setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public HookObservationContext setDurationMs(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public HookObservationContext setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String getErrorType() {
        return errorType;
    }

    public HookObservationContext setErrorType(String errorType) {
        this.errorType = errorType;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public HookObservationContext setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    // ==================== Custom Data Methods ====================

    /**
     * 注册自定义数据
     *
     * @param key   数据键
     * @param value 数据值
     * @return this
     */
    public HookObservationContext putCustomData(String key, Object value) {
        this.customData.put(key, value);
        return this;
    }

    /**
     * 批量注册自定义数据
     *
     * @param data 数据Map
     * @return this
     */
    public HookObservationContext putAllCustomData(Map<String, Object> data) {
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
        return "HookObservationContext{" +
                "hookName='" + hookName + '\'' +
                ", hookPosition='" + hookPosition + '\'' +
                ", agentName='" + agentName + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", durationMs=" + durationMs +
                ", success=" + success +
                ", errorType='" + errorType + '\'' +
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

        if (hookName != null) {
            builder.put(HOOK_NAME, hookName);
        }
        if (hookPosition != null) {
            builder.put(HOOK_POSITION, hookPosition);
        }
        if (agentName != null) {
            builder.put(AGENT_NAME, agentName);
        }
        if (sessionId != null) {
            builder.put(SESSION_ID, sessionId);
        }
        if (durationMs > 0) {
            builder.put(DURATION_MS, durationMs);
        }
        builder.put(SUCCESS, success);
        if (errorType != null) {
            builder.put(ERROR_TYPE, errorType);
        }
        if (errorMessage != null) {
            builder.put(ERROR_MESSAGE, errorMessage);
        }

        // 添加自定义数据
        for (Map.Entry<String, Object> entry : customData.entrySet()) {
            if (entry.getValue() != null) {
                String key = "codeact.hook.custom." + entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    builder.put(AttributeKey.stringKey(key), (String) value);
                } else if (value instanceof Long) {
                    builder.put(AttributeKey.longKey(key), (Long) value);
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

