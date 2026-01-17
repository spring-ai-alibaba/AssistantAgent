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
package com.alibaba.assistant.agent.planning.param;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionParameter;
import com.alibaba.assistant.agent.planning.model.ParamCollectionSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 参数验证器
 *
 * <p>验证 action 参数的完整性、类型和格式。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ParameterValidator {

    private static final Logger logger = LoggerFactory.getLogger(ParameterValidator.class);

    /**
     * 验证参数并返回验证结果
     *
     * @param action       动作定义
     * @param providedParams 已提供的参数
     * @return 验证结果
     */
    public ValidationResult validate(ActionDefinition action, Map<String, Object> providedParams) {
        ValidationResult result = new ValidationResult();
        result.setActionId(action.getActionId());
        result.setValid(true);

        if (CollectionUtils.isEmpty(action.getParameters())) {
            logger.debug("ParameterValidator#validate - reason=no parameters defined, actionId={}",
                    action.getActionId());
            return result;
        }

        List<ParamCollectionSession.MissingParamInfo> missingParams = new ArrayList<>();
        List<ValidationError> validationErrors = new ArrayList<>();

        for (ActionParameter param : action.getParameters()) {
            String paramName = param.getName();
            Object paramValue = providedParams.get(paramName);

            // 检查必填参数
            if (Boolean.TRUE.equals(param.getRequired()) && isValueEmpty(paramValue)) {
                logger.debug("ParameterValidator#validate - reason=missing required param, paramName={}", paramName);

                // 检查是否有默认值
                if (param.getDefaultValue() != null) {
                    result.addDefaultValue(paramName, param.getDefaultValue());
                    logger.debug("ParameterValidator#validate - reason=using default value, paramName={}, defaultValue={}",
                            paramName, param.getDefaultValue());
                } else {
                    // 添加到缺失参数列表
                    missingParams.add(createMissingParamInfo(param));
                    result.setValid(false);
                }
                continue;
            }

            // 如果参数值为空且非必填，跳过验证
            if (isValueEmpty(paramValue)) {
                continue;
            }

            // 验证参数类型和格式
            ValidationError error = validateParameter(param, paramValue);
            if (error != null) {
                validationErrors.add(error);
                result.setValid(false);
                logger.debug("ParameterValidator#validate - reason=validation failed, paramName={}, error={}",
                        paramName, error.getMessage());
            }
        }

        result.setMissingParams(missingParams);
        result.setValidationErrors(validationErrors);

        logger.info("ParameterValidator#validate - actionId={}, valid={}, missingCount={}, errorCount={}",
                action.getActionId(), result.isValid(), missingParams.size(), validationErrors.size());

        return result;
    }

    /**
     * 验证单个参数
     */
    private ValidationError validateParameter(ActionParameter param, Object value) {
        String paramName = param.getName();
        String paramType = param.getType();

        try {
            // 类型验证
            if (!validateType(paramType, value)) {
                return ValidationError.builder()
                        .paramName(paramName)
                        .message(String.format("参数类型错误：期望 %s，实际 %s",
                                paramType, value.getClass().getSimpleName()))
                        .errorType("TYPE_MISMATCH")
                        .build();
            }

            // 字符串长度验证
            if ("string".equals(paramType) && value instanceof String) {
                String strValue = (String) value;
                if (param.getMinLength() != null && strValue.length() < param.getMinLength()) {
                    return ValidationError.builder()
                            .paramName(paramName)
                            .message(String.format("字符串长度不能小于 %d", param.getMinLength()))
                            .errorType("MIN_LENGTH_VIOLATION")
                            .build();
                }
                if (param.getMaxLength() != null && strValue.length() > param.getMaxLength()) {
                    return ValidationError.builder()
                            .paramName(paramName)
                            .message(String.format("字符串长度不能大于 %d", param.getMaxLength()))
                            .errorType("MAX_LENGTH_VIOLATION")
                            .build();
                }
            }

            // 数值范围验证
            if ("number".equals(paramType) || "integer".equals(paramType)) {
                if (value instanceof Number) {
                    double numValue = ((Number) value).doubleValue();
                    if (param.getMinValue() != null && numValue < param.getMinValue().doubleValue()) {
                        return ValidationError.builder()
                                .paramName(paramName)
                                .message(String.format("数值不能小于 %s", param.getMinValue()))
                                .errorType("MIN_VALUE_VIOLATION")
                                .build();
                    }
                    if (param.getMaxValue() != null && numValue > param.getMaxValue().doubleValue()) {
                        return ValidationError.builder()
                                .paramName(paramName)
                                .message(String.format("数值不能大于 %s", param.getMaxValue()))
                                .errorType("MAX_VALUE_VIOLATION")
                                .build();
                    }
                }
            }

            // 枚举值验证
            if ("enum".equals(paramType) && !CollectionUtils.isEmpty(param.getEnumValues())) {
                if (!param.getEnumValues().contains(String.valueOf(value))) {
                    return ValidationError.builder()
                            .paramName(paramName)
                            .message(String.format("枚举值无效，有效值：%s", param.getEnumValues()))
                            .errorType("INVALID_ENUM_VALUE")
                            .build();
                }
            }

            // 正则表达式验证
            if (StringUtils.hasText(param.getPattern()) && value instanceof String) {
                try {
                    Pattern pattern = Pattern.compile(param.getPattern());
                    if (!pattern.matcher((String) value).matches()) {
                        return ValidationError.builder()
                                .paramName(paramName)
                                .message(String.format("格式不匹配，要求格式：%s", param.getPattern()))
                                .errorType("PATTERN_MISMATCH")
                                .build();
                    }
                } catch (PatternSyntaxException e) {
                    logger.warn("ParameterValidator#validateParameter - reason=invalid pattern, pattern={}",
                            param.getPattern(), e);
                }
            }

            // 日期格式验证
            if (("date".equals(paramType) || "datetime".equals(paramType)) && value instanceof String) {
                if (StringUtils.hasText(param.getDateFormat())) {
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(param.getDateFormat());
                        if ("date".equals(paramType)) {
                            LocalDate.parse((String) value, formatter);
                        } else {
                            LocalDateTime.parse((String) value, formatter);
                        }
                    } catch (DateTimeParseException e) {
                        return ValidationError.builder()
                                .paramName(paramName)
                                .message(String.format("日期格式错误，期望格式：%s", param.getDateFormat()))
                                .errorType("DATE_FORMAT_MISMATCH")
                                .build();
                    }
                }
            }

        } catch (Exception e) {
            logger.error("ParameterValidator#validateParameter - reason=validation error, paramName={}", paramName, e);
            return ValidationError.builder()
                    .paramName(paramName)
                    .message("参数验证失败：" + e.getMessage())
                    .errorType("VALIDATION_ERROR")
                    .build();
        }

        return null;
    }

    /**
     * 验证参数类型
     */
    private boolean validateType(String expectedType, Object value) {
        if (value == null) {
            return true;
        }

        switch (expectedType) {
            case "string":
                return value instanceof String || value instanceof Number || value instanceof Boolean;
            case "number":
            case "integer":
                return value instanceof Number;
            case "boolean":
                return value instanceof Boolean || "true".equalsIgnoreCase(String.valueOf(value))
                        || "false".equalsIgnoreCase(String.valueOf(value));
            case "enum":
                return true; // 枚举值单独验证
            case "array":
                return value instanceof List || value instanceof Object[];
            case "object":
                return value instanceof Map;
            case "date":
            case "datetime":
                return value instanceof String;
            default:
                return true; // 未知类型，跳过验证
        }
    }

    /**
     * 判断值是否为空
     */
    private boolean isValueEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }
        if (value instanceof List) {
            return ((List<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }
        return false;
    }

    /**
     * 创建缺失参数信息
     */
    private ParamCollectionSession.MissingParamInfo createMissingParamInfo(ActionParameter param) {
        return ParamCollectionSession.MissingParamInfo.builder()
                .name(param.getName())
                .label(param.getLabel() != null ? param.getLabel() : param.getName())
                .type(param.getType())
                .required(param.getRequired())
                .description(param.getDescription())
                .promptHint(generatePromptHint(param))
                .enumOptions(param.getEnumValues())
                .defaultValue(param.getDefaultValue())
                .build();
    }

    /**
     * 生成参数提示信息
     */
    private String generatePromptHint(ActionParameter param) {
        StringBuilder hint = new StringBuilder();

        if (StringUtils.hasText(param.getDescription())) {
            hint.append(param.getDescription());
        }

        if ("enum".equals(param.getType()) && !CollectionUtils.isEmpty(param.getEnumValues())) {
            if (hint.length() > 0) {
                hint.append(" ");
            }
            hint.append("可选值：").append(param.getEnumValues());
        }

        if (StringUtils.hasText(param.getPlaceholder())) {
            if (hint.length() > 0) {
                hint.append(" ");
            }
            hint.append("示例：").append(param.getPlaceholder());
        }

        return hint.length() > 0 ? hint.toString() : null;
    }

    /**
     * 验证结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationResult {
        /**
         * 动作 ID
         */
        private String actionId;

        /**
         * 是否验证通过
         */
        @lombok.Builder.Default
        private boolean valid = true;

        /**
         * 缺失的必填参数列表
         */
        @lombok.Builder.Default
        private List<ParamCollectionSession.MissingParamInfo> missingParams = new ArrayList<>();

        /**
         * 验证错误列表
         */
        @lombok.Builder.Default
        private List<ValidationError> validationErrors = new ArrayList<>();

        /**
         * 默认值映射（参数名 -> 默认值）
         */
        @lombok.Builder.Default
        private Map<String, Object> defaultValues = new java.util.HashMap<>();

        /**
         * 添加默认值
         */
        public void addDefaultValue(String paramName, Object defaultValue) {
            this.defaultValues.put(paramName, defaultValue);
        }

        /**
         * 是否有缺失参数
         */
        public boolean hasMissingParams() {
            return !CollectionUtils.isEmpty(missingParams);
        }

        /**
         * 是否有验证错误
         */
        public boolean hasValidationErrors() {
            return !CollectionUtils.isEmpty(validationErrors);
        }
    }

    /**
     * 验证错误
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationError {
        /**
         * 参数名
         */
        private String paramName;

        /**
         * 错误信息
         */
        private String message;

        /**
         * 错误类型
         */
        private String errorType;

        /**
         * 期望值
         */
        private String expectedValue;

        /**
         * 实际值
         */
        private Object actualValue;
    }
}
