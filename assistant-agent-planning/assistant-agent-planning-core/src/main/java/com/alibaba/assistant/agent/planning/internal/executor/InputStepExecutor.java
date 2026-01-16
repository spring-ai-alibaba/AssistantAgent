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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 输入步骤执行器
 *
 * <p>收集和验证用户输入。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class InputStepExecutor extends AbstractStepExecutor {

    @Override
    public StepType getSupportedType() {
        return StepType.INPUT;
    }

    @Override
    protected StepExecutionResult doExecute(ExecutionStep step, StepExecutionContext context) {
        logger.debug("InputStepExecutor#doExecute - reason=executing input step, stepId={}", step.getStepId());

        StepDefinition definition = step.getDefinition();
        Map<String, Object> inputValues = step.getInputValues();

        // 检查是否有必填参数缺失
        List<ActionParameter> missingParams = checkMissingParameters(definition, inputValues);

        if (!missingParams.isEmpty()) {
            // 有缺失参数，返回等待输入
            String prompt = buildInputPrompt(step, missingParams);
            return StepExecutionResult.waitingInput(prompt, buildParameterInfo(missingParams));
        }

        // 验证输入参数
        Map<String, String> validationErrors = validateInputs(definition, inputValues);
        if (!validationErrors.isEmpty()) {
            return StepExecutionResult.failure("输入验证失败: " + validationErrors.toString());
        }

        // 所有输入已收集，返回成功
        Map<String, Object> output = new HashMap<>();
        output.put("collectedInputs", inputValues);

        // 更新状态数据
        if (inputValues != null) {
            inputValues.forEach(context::updateStateData);
        }

        return StepExecutionResult.success(output);
    }

    private List<ActionParameter> checkMissingParameters(StepDefinition definition, Map<String, Object> inputValues) {
        List<ActionParameter> missing = new ArrayList<>();

        if (definition == null || definition.getInputParams() == null) {
            return missing;
        }

        for (ActionParameter param : definition.getInputParams()) {
            if (Boolean.TRUE.equals(param.getRequired())) {
                Object value = inputValues != null ? inputValues.get(param.getName()) : null;
                if (value == null || (value instanceof String && ((String) value).isBlank())) {
                    missing.add(param);
                }
            }
        }

        return missing;
    }

    private String buildInputPrompt(ExecutionStep step, List<ActionParameter> missingParams) {
        if (step.getUserPrompt() != null) {
            return step.getUserPrompt();
        }

        StringBuilder prompt = new StringBuilder("请提供以下信息：\n");
        for (ActionParameter param : missingParams) {
            String label = param.getLabel() != null ? param.getLabel() : param.getName();
            prompt.append("- ").append(label);
            if (param.getDescription() != null) {
                prompt.append(" (").append(param.getDescription()).append(")");
            }
            prompt.append("\n");
        }

        return prompt.toString();
    }

    private List<Map<String, Object>> buildParameterInfo(List<ActionParameter> params) {
        List<Map<String, Object>> info = new ArrayList<>();
        for (ActionParameter param : params) {
            Map<String, Object> paramInfo = new HashMap<>();
            paramInfo.put("name", param.getName());
            paramInfo.put("label", param.getLabel() != null ? param.getLabel() : param.getName());
            paramInfo.put("type", param.getType());
            paramInfo.put("required", param.getRequired());
            paramInfo.put("description", param.getDescription());
            paramInfo.put("placeholder", param.getPlaceholder());
            if (param.getEnumValues() != null) {
                paramInfo.put("options", param.getEnumValues());
            }
            info.add(paramInfo);
        }
        return info;
    }

    private Map<String, String> validateInputs(StepDefinition definition, Map<String, Object> inputValues) {
        Map<String, String> errors = new HashMap<>();

        if (definition == null || definition.getInputParams() == null || inputValues == null) {
            return errors;
        }

        for (ActionParameter param : definition.getInputParams()) {
            Object value = inputValues.get(param.getName());
            if (value == null) {
                continue;
            }

            // 类型校验
            String type = param.getType();
            if ("string".equals(type) && !(value instanceof String)) {
                errors.put(param.getName(), "期望字符串类型");
                continue;
            }

            if ("number".equals(type) && !(value instanceof Number)) {
                errors.put(param.getName(), "期望数字类型");
                continue;
            }

            // 字符串长度校验
            if (value instanceof String str) {
                if (param.getMinLength() != null && str.length() < param.getMinLength()) {
                    errors.put(param.getName(), "长度不能小于 " + param.getMinLength());
                }
                if (param.getMaxLength() != null && str.length() > param.getMaxLength()) {
                    errors.put(param.getName(), "长度不能大于 " + param.getMaxLength());
                }
                if (param.getPattern() != null && !str.matches(param.getPattern())) {
                    errors.put(param.getName(), "格式不正确");
                }
            }

            // 数值范围校验
            if (value instanceof Number num) {
                if (param.getMinValue() != null && num.doubleValue() < param.getMinValue().doubleValue()) {
                    errors.put(param.getName(), "值不能小于 " + param.getMinValue());
                }
                if (param.getMaxValue() != null && num.doubleValue() > param.getMaxValue().doubleValue()) {
                    errors.put(param.getName(), "值不能大于 " + param.getMaxValue());
                }
            }

            // 枚举校验
            if (param.getEnumValues() != null && !param.getEnumValues().isEmpty()) {
                String strValue = String.valueOf(value);
                if (!param.getEnumValues().contains(strValue)) {
                    errors.put(param.getName(), "值必须是 " + param.getEnumValues() + " 之一");
                }
            }
        }

        return errors;
    }
}
