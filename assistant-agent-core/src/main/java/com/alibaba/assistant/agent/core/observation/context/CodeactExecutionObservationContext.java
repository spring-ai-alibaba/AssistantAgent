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

import static com.alibaba.assistant.agent.core.observation.CodeactObservationDocumentation.ExecutionAttributes;

/**
 * Codeact 代码执行观测上下文
 * <p>
 * 存储代码执行过程中的观测数据，包括函数名、参数、结果等。
 * <p>
 * 已从 Micrometer Observation.Context 迁移到 OpenTelemetry 原生 API。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class CodeactExecutionObservationContext {

    private String functionName;
    private String language;
    private String arguments;
    private int argumentsLength;
    private String result;
    private int resultLength;
    private long durationMs;
    private boolean success;
    private String errorType;
    private String errorMessage;

    public CodeactExecutionObservationContext() {
    }

    public CodeactExecutionObservationContext(String functionName, String language) {
        this.functionName = functionName;
        this.language = language;
    }

    // Getters and Setters

    public String getFunctionName() {
        return functionName;
    }

    public CodeactExecutionObservationContext setFunctionName(String functionName) {
        this.functionName = functionName;
        return this;
    }

    public String getLanguage() {
        return language;
    }

    public CodeactExecutionObservationContext setLanguage(String language) {
        this.language = language;
        return this;
    }

    public String getArguments() {
        return arguments;
    }

    public CodeactExecutionObservationContext setArguments(String arguments) {
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

    public CodeactExecutionObservationContext setResult(String result) {
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

    public CodeactExecutionObservationContext setDurationMs(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public CodeactExecutionObservationContext setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String getErrorType() {
        return errorType;
    }

    public CodeactExecutionObservationContext setErrorType(String errorType) {
        this.errorType = errorType;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public CodeactExecutionObservationContext setErrorMessage(String errorMessage) {
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

        if (language != null) {
            builder.put(ExecutionAttributes.LANGUAGE, language);
        }
        builder.put(ExecutionAttributes.SUCCESS, success);
        if (functionName != null) {
            builder.put(ExecutionAttributes.FUNCTION_NAME, functionName);
        }
        builder.put(ExecutionAttributes.ARGUMENTS_LENGTH, (long) argumentsLength);
        builder.put(ExecutionAttributes.RESULT_LENGTH, (long) resultLength);
        builder.put(ExecutionAttributes.DURATION_MS, durationMs);
        if (errorType != null) {
            builder.put(ExecutionAttributes.ERROR_TYPE, errorType);
        }
        if (errorMessage != null) {
            builder.put(ExecutionAttributes.ERROR_MESSAGE, errorMessage);
        }

        return builder.build();
    }

    @Override
    public String toString() {
        return "CodeactExecutionObservationContext{" +
                "functionName='" + functionName + '\'' +
                ", language='" + language + '\'' +
                ", argumentsLength=" + argumentsLength +
                ", resultLength=" + resultLength +
                ", durationMs=" + durationMs +
                ", success=" + success +
                ", errorType='" + errorType + '\'' +
                '}';
    }
}
