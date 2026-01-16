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
package com.alibaba.assistant.agent.planning.internal.executor;

import com.alibaba.assistant.agent.planning.model.ActionParameter;
import com.alibaba.assistant.agent.planning.model.ExecutionStep;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.alibaba.assistant.agent.planning.model.StepExecutionResult;
import com.alibaba.assistant.agent.planning.model.StepType;

import java.util.HashMap;
import java.util.Map;

/**
 * 校验步骤执行器
 *
 * <p>执行参数校验和业务规则检查，包括外键依赖检查。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ValidationStepExecutor extends AbstractStepExecutor {

    @Override
    public StepType getSupportedType() {
        return StepType.VALIDATION;
    }

    @Override
    protected StepExecutionResult doExecute(ExecutionStep step, StepExecutionContext context) {
        logger.debug("ValidationStepExecutor#doExecute - reason=executing validation step, stepId={}",
                step.getStepId());

        StepDefinition definition = step.getDefinition();
        Map<String, Object> inputValues = step.getInputValues();

        if (inputValues == null || inputValues.isEmpty()) {
            return StepExecutionResult.success(Map.of("valid", true, "message", "No inputs to validate"));
        }

        Map<String, String> errors = new HashMap<>();

        // 参数校验
        if (definition != null && definition.getInputParams() != null) {
            for (ActionParameter param : definition.getInputParams()) {
                Object value = inputValues.get(param.getName());

                // 外键校验
                if (param.getForeignKey() != null && value != null) {
                    String fkError = validateForeignKey(param, value, context);
                    if (fkError != null) {
                        errors.put(param.getName(), fkError);
                    }
                }

                // 基本校验
                String basicError = validateBasic(param, value);
                if (basicError != null) {
                    errors.put(param.getName(), basicError);
                }
            }
        }

        if (!errors.isEmpty()) {
            Map<String, Object> output = new HashMap<>();
            output.put("valid", false);
            output.put("errors", errors);
            return StepExecutionResult.failure("Validation failed: " + errors);
        }

        Map<String, Object> output = new HashMap<>();
        output.put("valid", true);
        output.put("validatedInputs", inputValues);

        return StepExecutionResult.success(output);
    }

    private String validateForeignKey(ActionParameter param, Object value, StepExecutionContext context) {
        ActionParameter.ForeignKeyRef fk = param.getForeignKey();

        logger.debug("ValidationStepExecutor#validateForeignKey - reason=checking foreign key, " +
                        "param={}, entity={}, field={}, value={}",
                param.getName(), fk.getEntity(), fk.getField(), value);

        // TODO: 实际的外键校验需要查询数据库或调用服务
        // 这里只是示例实现
        // 如果配置了 validationStepId，则由该步骤执行校验
        if (fk.getValidationStepId() != null) {
            // 将校验委托给指定的步骤
            logger.debug("ValidationStepExecutor#validateForeignKey - reason=delegating to validation step, " +
                    "validationStepId={}", fk.getValidationStepId());
            return null;
        }

        // 简单实现：假设外键存在
        return null;
    }

    private String validateBasic(ActionParameter param, Object value) {
        if (value == null) {
            if (Boolean.TRUE.equals(param.getRequired())) {
                return "必填字段不能为空";
            }
            return null;
        }

        String type = param.getType();

        // 字符串校验
        if ("string".equals(type) && value instanceof String str) {
            if (param.getMinLength() != null && str.length() < param.getMinLength()) {
                return "长度不能小于 " + param.getMinLength();
            }
            if (param.getMaxLength() != null && str.length() > param.getMaxLength()) {
                return "长度不能大于 " + param.getMaxLength();
            }
            if (param.getPattern() != null && !str.matches(param.getPattern())) {
                return "格式不正确";
            }
        }

        // 数值校验
        if ("number".equals(type) && value instanceof Number num) {
            if (param.getMinValue() != null && num.doubleValue() < param.getMinValue().doubleValue()) {
                return "值不能小于 " + param.getMinValue();
            }
            if (param.getMaxValue() != null && num.doubleValue() > param.getMaxValue().doubleValue()) {
                return "值不能大于 " + param.getMaxValue();
            }
        }

        // 枚举校验
        if (param.getEnumValues() != null && !param.getEnumValues().isEmpty()) {
            String strValue = String.valueOf(value);
            if (!param.getEnumValues().contains(strValue)) {
                return "值必须是 " + param.getEnumValues() + " 之一";
            }
        }

        return null;
    }
}
