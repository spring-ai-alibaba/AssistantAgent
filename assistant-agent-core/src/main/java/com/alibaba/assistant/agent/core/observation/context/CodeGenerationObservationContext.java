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

import static com.alibaba.assistant.agent.core.observation.CodeactObservationDocumentation.CodeGenerationAttributes;

/**
 * Codeact 代码生成观测上下文
 * <p>
 * 存储代码生成过程中的观测数据，包括模型、函数名、Token使用情况等。
 * <p>
 * 已从 Micrometer Observation.Context 迁移到 OpenTelemetry 原生 API。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class CodeGenerationObservationContext {

    private String functionName;
    private String language;
    private String modelName;
    private int codeLines;
    private int inputTokens;
    private int outputTokens;
    private long durationMs;
    private boolean success;
    private String errorType;
    private String errorMessage;

    public CodeGenerationObservationContext() {
    }

    public CodeGenerationObservationContext(String language, String modelName) {
        this.language = language;
        this.modelName = modelName;
    }

    // Getters and Setters

    public String getFunctionName() {
        return functionName;
    }

    public CodeGenerationObservationContext setFunctionName(String functionName) {
        this.functionName = functionName;
        return this;
    }

    public String getLanguage() {
        return language;
    }

    public CodeGenerationObservationContext setLanguage(String language) {
        this.language = language;
        return this;
    }

    public String getModelName() {
        return modelName;
    }

    public CodeGenerationObservationContext setModelName(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public int getCodeLines() {
        return codeLines;
    }

    public CodeGenerationObservationContext setCodeLines(int codeLines) {
        this.codeLines = codeLines;
        return this;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public CodeGenerationObservationContext setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
        return this;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public CodeGenerationObservationContext setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
        return this;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public CodeGenerationObservationContext setDurationMs(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public CodeGenerationObservationContext setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String getErrorType() {
        return errorType;
    }

    public CodeGenerationObservationContext setErrorType(String errorType) {
        this.errorType = errorType;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public CodeGenerationObservationContext setErrorMessage(String errorMessage) {
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
            builder.put(CodeGenerationAttributes.LANGUAGE, language);
        }
        if (modelName != null) {
            builder.put(CodeGenerationAttributes.MODEL_NAME, modelName);
        }
        builder.put(CodeGenerationAttributes.SUCCESS, success);
        if (functionName != null) {
            builder.put(CodeGenerationAttributes.FUNCTION_NAME, functionName);
        }
        builder.put(CodeGenerationAttributes.CODE_LINES, (long) codeLines);
        builder.put(CodeGenerationAttributes.INPUT_TOKENS, (long) inputTokens);
        builder.put(CodeGenerationAttributes.OUTPUT_TOKENS, (long) outputTokens);
        builder.put(CodeGenerationAttributes.DURATION_MS, durationMs);

        return builder.build();
    }

    @Override
    public String toString() {
        return "CodeGenerationObservationContext{" +
                "functionName='" + functionName + '\'' +
                ", language='" + language + '\'' +
                ", modelName='" + modelName + '\'' +
                ", codeLines=" + codeLines +
                ", inputTokens=" + inputTokens +
                ", outputTokens=" + outputTokens +
                ", durationMs=" + durationMs +
                ", success=" + success +
                '}';
    }
}
