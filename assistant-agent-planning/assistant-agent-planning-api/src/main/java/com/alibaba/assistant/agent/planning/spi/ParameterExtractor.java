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
package com.alibaba.assistant.agent.planning.spi;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionParameter;

import java.util.List;
import java.util.Map;

/**
 * 参数提取器 SPI
 *
 * <p>负责从用户输入中提取动作参数。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface ParameterExtractor {

    /**
     * 从用户输入中提取参数
     *
     * @param userInput 用户输入
     * @param action    动作定义
     * @param context   上下文
     * @return 提取结果
     */
    ExtractionResult extract(String userInput, ActionDefinition action, Map<String, Object> context);

    /**
     * 校验参数值
     *
     * @param parameter 参数定义
     * @param value     参数值
     * @return 校验结果
     */
    ValidationResult validate(ActionParameter parameter, Object value);

    /**
     * 批量校验参数
     *
     * @param parameters 参数定义列表
     * @param values     参数值映射
     * @return 校验结果
     */
    ValidationResult validateAll(List<ActionParameter> parameters, Map<String, Object> values);

    /**
     * 参数提取结果
     */
    class ExtractionResult {
        private final boolean success;
        private final Map<String, Object> extractedValues;
        private final Map<String, ActionParameter> missingParameters;
        private final Map<String, String> extractionErrors;
        private final String message;

        private ExtractionResult(boolean success, Map<String, Object> extractedValues,
                                 Map<String, ActionParameter> missingParameters,
                                 Map<String, String> extractionErrors, String message) {
            this.success = success;
            this.extractedValues = extractedValues;
            this.missingParameters = missingParameters;
            this.extractionErrors = extractionErrors;
            this.message = message;
        }

        public static ExtractionResult success(Map<String, Object> extractedValues) {
            return new ExtractionResult(true, extractedValues, null, null, null);
        }

        public static ExtractionResult partial(Map<String, Object> extractedValues,
                                               Map<String, ActionParameter> missingParameters) {
            return new ExtractionResult(true, extractedValues, missingParameters, null, null);
        }

        public static ExtractionResult failure(String message, Map<String, String> errors) {
            return new ExtractionResult(false, null, null, errors, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public Map<String, Object> getExtractedValues() {
            return extractedValues;
        }

        public Map<String, ActionParameter> getMissingParameters() {
            return missingParameters;
        }

        public Map<String, String> getExtractionErrors() {
            return extractionErrors;
        }

        public String getMessage() {
            return message;
        }

        public boolean hasMissingParameters() {
            return missingParameters != null && !missingParameters.isEmpty();
        }
    }

    /**
     * 参数校验结果
     */
    class ValidationResult {
        private final boolean valid;
        private final Map<String, String> fieldErrors;
        private final String message;

        private ValidationResult(boolean valid, Map<String, String> fieldErrors, String message) {
            this.valid = valid;
            this.fieldErrors = fieldErrors;
            this.message = message;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, null, message);
        }

        public static ValidationResult failure(Map<String, String> fieldErrors) {
            return new ValidationResult(false, fieldErrors, null);
        }

        public boolean isValid() {
            return valid;
        }

        public Map<String, String> getFieldErrors() {
            return fieldErrors;
        }

        public String getMessage() {
            return message;
        }
    }
}
