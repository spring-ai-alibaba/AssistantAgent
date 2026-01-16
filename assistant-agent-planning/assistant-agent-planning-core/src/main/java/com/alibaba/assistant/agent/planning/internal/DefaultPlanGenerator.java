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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.planning.model.*;
import com.alibaba.assistant.agent.planning.spi.PlanGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 默认执行计划生成器实现
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class DefaultPlanGenerator implements PlanGenerator {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPlanGenerator.class);

    @Override
    public ExecutionPlan generate(ActionDefinition action, Map<String, Object> parameters,
                                  PlanGenerationContext context) {
        logger.debug("DefaultPlanGenerator#generate - reason=generating plan, actionId={}, actionName={}",
                action.getActionId(), action.getActionName());

        // 创建执行计划
        ExecutionPlan plan = ExecutionPlan.builder()
                .planId(UUID.randomUUID().toString())
                .sessionId(context != null ? context.getSessionId() : null)
                .actionId(action.getActionId())
                .actionName(action.getActionName())
                .userInput(context != null ? context.getUserInput() : null)
                .actionDefinition(action)
                .extractedParameters(parameters != null ? new HashMap<>(parameters) : new HashMap<>())
                .status(PlanStatus.PENDING)
                .stateData(new HashMap<>())
                .context(context != null ? context.getContextVariables() : new HashMap<>())
                .createdAt(Instant.now())
                .build();

        // 设置过期时间
        int timeoutMinutes = action.getTimeoutMinutes() != null ? action.getTimeoutMinutes() : 30;
        if (context != null && context.getTimeoutMinutes() != null) {
            timeoutMinutes = context.getTimeoutMinutes();
        }
        plan.setExpireAt(Instant.now().plus(timeoutMinutes, ChronoUnit.MINUTES));

        // 生成执行步骤
        List<ExecutionStep> steps = generateSteps(action, parameters);
        plan.setSteps(steps);

        logger.info("DefaultPlanGenerator#generate - reason=plan generated, planId={}, stepCount={}",
                plan.getPlanId(), steps.size());

        return plan;
    }

    private List<ExecutionStep> generateSteps(ActionDefinition action, Map<String, Object> parameters) {
        List<ExecutionStep> steps = new ArrayList<>();

        if (action.isMultiStep() && action.getSteps() != null && !action.getSteps().isEmpty()) {
            // 多步骤动作 - 根据步骤定义生成
            int order = 0;
            for (StepDefinition stepDef : action.getSteps()) {
                ExecutionStep step = createExecutionStep(stepDef, order++, parameters);
                steps.add(step);
            }
        } else {
            // 单步骤动作 - 创建一个执行步骤
            ExecutionStep step = ExecutionStep.builder()
                    .stepInstanceId(UUID.randomUUID().toString())
                    .stepId("execute")
                    .name(action.getActionName())
                    .type(mapActionTypeToStepType(action.getActionType()))
                    .order(0)
                    .inputValues(parameters != null ? new HashMap<>(parameters) : new HashMap<>())
                    .status(ExecutionStep.StepStatus.PENDING)
                    .build();

            // 如果有接口绑定，创建对应的步骤定义
            if (action.getInterfaceBinding() != null) {
                StepDefinition stepDef = StepDefinition.builder()
                        .stepId("execute")
                        .name(action.getActionName())
                        .type(mapActionTypeToStepType(action.getActionType()))
                        .interfaceBinding(action.getInterfaceBinding())
                        .build();
                step.setDefinition(stepDef);
            }

            steps.add(step);
        }

        return steps;
    }

    private ExecutionStep createExecutionStep(StepDefinition stepDef, int order, Map<String, Object> parameters) {
        // 解析输入参数值
        Map<String, Object> inputValues = resolveInputValues(stepDef, parameters);

        return ExecutionStep.builder()
                .stepInstanceId(UUID.randomUUID().toString())
                .stepId(stepDef.getStepId())
                .name(stepDef.getName())
                .type(stepDef.getType())
                .order(order)
                .definition(stepDef)
                .inputValues(inputValues)
                .status(ExecutionStep.StepStatus.PENDING)
                .userPrompt(stepDef.getUserPrompt())
                .build();
    }

    private Map<String, Object> resolveInputValues(StepDefinition stepDef, Map<String, Object> parameters) {
        Map<String, Object> inputValues = new HashMap<>();

        if (stepDef.getInputParams() == null) {
            return inputValues;
        }

        for (ActionParameter param : stepDef.getInputParams()) {
            Object value = resolveParameterValue(param, parameters);
            if (value != null) {
                inputValues.put(param.getName(), value);
            }
        }

        return inputValues;
    }

    private Object resolveParameterValue(ActionParameter param, Map<String, Object> parameters) {
        if (param.getSource() == null || param.getSource() == ParameterSource.USER_INPUT) {
            // 从提取的参数中获取
            if (parameters != null && parameters.containsKey(param.getName())) {
                return parameters.get(param.getName());
            }
        }

        if (param.getSource() == ParameterSource.DEFAULT) {
            return param.getDefaultValue();
        }

        // PREVIOUS_STEP 和 CONTEXT 类型需要在执行时解析
        return null;
    }

    private StepType mapActionTypeToStepType(ActionType actionType) {
        if (actionType == null) {
            return StepType.EXECUTE;
        }
        return switch (actionType) {
            case API_CALL -> StepType.API_CALL;
            case INTERNAL_SERVICE -> StepType.INTERNAL_SERVICE;
            case PAGE_NAVIGATION, FORM_PREFILL, WORKFLOW_TRIGGER -> StepType.EXECUTE;
            case MCP_TOOL -> StepType.EXECUTE;
            case MULTI_STEP -> StepType.EXECUTE;
        };
    }

    @Override
    public ValidationResult validate(ExecutionPlan plan) {
        if (plan == null) {
            return ValidationResult.failure("Plan cannot be null");
        }

        if (plan.getActionId() == null || plan.getActionId().isBlank()) {
            return ValidationResult.failure("Action ID is required");
        }

        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return ValidationResult.failure("Plan must have at least one step");
        }

        // 校验每个步骤
        Map<String, String> fieldErrors = new HashMap<>();
        for (int i = 0; i < plan.getSteps().size(); i++) {
            ExecutionStep step = plan.getSteps().get(i);
            if (step.getType() == null) {
                fieldErrors.put("steps[" + i + "].type", "Step type is required");
            }
        }

        if (!fieldErrors.isEmpty()) {
            return ValidationResult.failure("Validation failed", fieldErrors);
        }

        return ValidationResult.success();
    }

    @Override
    public ExecutionPlan optimize(ExecutionPlan plan) {
        // 默认不做优化，直接返回
        return plan;
    }
}
