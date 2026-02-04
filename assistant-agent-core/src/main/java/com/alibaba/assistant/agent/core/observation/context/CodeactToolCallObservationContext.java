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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

import static com.alibaba.assistant.agent.core.observation.CodeactObservationDocumentation.ToolCallAttributes;
import static com.alibaba.assistant.agent.core.observation.CodeactObservationDocumentation.GenAIAttributes;

/**
 * Codeact 工具调用观测上下文
 * <p>
 * 存储在代码执行过程中调用工具的观测数据。
 * <p>
 * 已从 Micrometer Observation.Context 迁移到 OpenTelemetry 原生 API。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class CodeactToolCallObservationContext {

    private String sessionId;
    private String toolName;
    private String arguments;
    private int argumentsLength;
    private String result;
    private int resultLength;
    private long durationMs;
    private boolean success;
    private String errorType;
    private String errorMessage;

    public CodeactToolCallObservationContext() {
    }

    public CodeactToolCallObservationContext(String toolName) {
        this.toolName = toolName;
    }

    // Getters and Setters

    public String getSessionId() {
        return sessionId;
    }

    public CodeactToolCallObservationContext setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getToolName() {
        return toolName;
    }

    public CodeactToolCallObservationContext setToolName(String toolName) {
        this.toolName = toolName;
        return this;
    }

    public String getArguments() {
        return arguments;
    }

    public CodeactToolCallObservationContext setArguments(String arguments) {
        this.arguments = arguments;
        this.argumentsLength = arguments != null ? arguments.length() : 0;
        return this;
    }

    public int getArgumentsLength() {
        return argumentsLength;
    }

    public String getResult() {
        return result;
    }

    public CodeactToolCallObservationContext setResult(String result) {
        this.result = result;
        this.resultLength = result != null ? result.length() : 0;
        return this;
    }

    public int getResultLength() {
        return resultLength;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public CodeactToolCallObservationContext setDurationMs(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public CodeactToolCallObservationContext setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String getErrorType() {
        return errorType;
    }

    public CodeactToolCallObservationContext setErrorType(String errorType) {
        this.errorType = errorType;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public CodeactToolCallObservationContext setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    /**
     * 构建 OpenTelemetry Attributes
     *
     * @return Attributes
     */
    public Attributes toAttributes() {
        AttributesBuilder builder = Attributes.builder();

        if (sessionId != null) {
            builder.put(GenAIAttributes.CONVERSATION_ID, sessionId);
        }
        if (toolName != null) {
            builder.put(ToolCallAttributes.TOOL_NAME, toolName);
        }
        builder.put(ToolCallAttributes.SUCCESS, success);
        if (arguments != null) {
            builder.put(ToolCallAttributes.ARGUMENTS, truncate(arguments, 500));
        }
        builder.put(ToolCallAttributes.ARGUMENTS_LENGTH, (long) argumentsLength);
        builder.put(ToolCallAttributes.RESULT_LENGTH, (long) resultLength);
        builder.put(ToolCallAttributes.DURATION_MS, durationMs);
        if (errorType != null) {
            builder.put(ToolCallAttributes.ERROR_TYPE, errorType);
        }

        return builder.build();
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...[truncated]";
    }

    @Override
    public String toString() {
        return "CodeactToolCallObservationContext{" +
                "toolName='" + toolName + '\'' +
                ", argumentsLength=" + argumentsLength +
                ", resultLength=" + resultLength +
                ", durationMs=" + durationMs +
                ", success=" + success +
                ", errorType='" + errorType + '\'' +
                '}';
    }
}
