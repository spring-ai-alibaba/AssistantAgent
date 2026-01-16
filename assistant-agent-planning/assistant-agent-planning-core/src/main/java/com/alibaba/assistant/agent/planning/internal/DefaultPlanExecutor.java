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
import com.alibaba.assistant.agent.planning.spi.PlanExecutor;
import com.alibaba.assistant.agent.planning.spi.StepExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认计划执行器实现
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class DefaultPlanExecutor implements PlanExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPlanExecutor.class);

    private final StepExecutorRegistry stepExecutorRegistry;
    private final Map<String, ExecutionPlan> planCache = new ConcurrentHashMap<>();

    public DefaultPlanExecutor(StepExecutorRegistry stepExecutorRegistry) {
        this.stepExecutorRegistry = stepExecutorRegistry;
    }

    @Override
    public PlanExecutionResult execute(ExecutionPlan plan) {
        return execute(plan, new HashMap<>());
    }

    @Override
    public PlanExecutionResult execute(ExecutionPlan plan, Map<String, Object> context) {
        if (plan == null) {
            return PlanExecutionResult.failure(null, "Plan cannot be null");
        }

        logger.info("DefaultPlanExecutor#execute - reason=starting plan execution, planId={}, actionId={}",
                plan.getPlanId(), plan.getActionId());

        // 缓存计划
        planCache.put(plan.getPlanId(), plan);

        // 更新状态
        plan.setStatus(PlanStatus.IN_PROGRESS);
        plan.setStartedAt(Instant.now());

        // 合并上下文
        if (plan.getContext() == null) {
            plan.setContext(new HashMap<>());
        }
        if (context != null) {
            plan.getContext().putAll(context);
        }

        try {
            // 执行步骤
            return executeSteps(plan);
        } catch (Exception e) {
            logger.error("DefaultPlanExecutor#execute - reason=plan execution failed, planId={}, error={}",
                    plan.getPlanId(), e.getMessage(), e);
            plan.setStatus(PlanStatus.FAILED);
            plan.setErrorMessage(e.getMessage());
            return PlanExecutionResult.failure(plan, e.getMessage());
        }
    }

    private PlanExecutionResult executeSteps(ExecutionPlan plan) {
        List<ExecutionStep> steps = plan.getSteps();
        if (steps == null || steps.isEmpty()) {
            plan.setStatus(PlanStatus.COMPLETED);
            plan.setCompletedAt(Instant.now());
            return PlanExecutionResult.success(plan, null);
        }

        // 创建执行上下文
        DefaultStepExecutionContext executionContext = new DefaultStepExecutionContext(plan);

        // 从当前步骤索引开始执行
        for (int i = plan.getCurrentStepIndex(); i < steps.size(); i++) {
            ExecutionStep step = steps.get(i);
            plan.setCurrentStepIndex(i);

            logger.debug("DefaultPlanExecutor#executeSteps - reason=executing step, planId={}, stepId={}, stepIndex={}",
                    plan.getPlanId(), step.getStepId(), i);

            // 获取步骤执行器
            StepExecutor executor = stepExecutorRegistry.getExecutor(step.getType());
            if (executor == null) {
                String errorMsg = "No executor found for step type: " + step.getType();
                logger.error("DefaultPlanExecutor#executeSteps - reason=executor not found, stepType={}",
                        step.getType());
                step.setStatus(ExecutionStep.StepStatus.FAILED);
                step.setErrorMessage(errorMsg);
                plan.setStatus(PlanStatus.FAILED);
                plan.setErrorMessage(errorMsg);
                return PlanExecutionResult.failure(plan, errorMsg);
            }

            // 解析输入参数（处理 PREVIOUS_STEP 类型）
            resolveStepInputs(step, executionContext);

            // 执行步骤
            step.setStatus(ExecutionStep.StepStatus.RUNNING);
            step.setStartTime(Instant.now());

            StepExecutionResult result = executor.execute(step, executionContext);

            step.setEndTime(Instant.now());
            step.setDurationMs(step.getEndTime().toEpochMilli() - step.getStartTime().toEpochMilli());

            if (result.isSuccess()) {
                if (result.isNeedsUserInput()) {
                    // 需要用户输入，中断执行
                    step.setStatus(ExecutionStep.StepStatus.WAITING_INPUT);
                    step.setUserPrompt(result.getUserPrompt());
                    step.setOptions(result.getOptions());
                    plan.setStatus(PlanStatus.WAITING_INPUT);

                    logger.info("DefaultPlanExecutor#executeSteps - reason=waiting for user input, planId={}, stepId={}",
                            plan.getPlanId(), step.getStepId());

                    return PlanExecutionResult.waitingInput(plan, result.getUserPrompt(), result.getOptions());
                }

                // 步骤成功完成
                step.setStatus(result.getStatus());
                step.setOutputValues(result.getOutput());

                // 更新执行上下文
                if (result.getOutput() != null) {
                    executionContext.setStepOutput(step.getStepId(), result.getOutput());
                }
            } else {
                // 步骤执行失败
                step.setStatus(ExecutionStep.StepStatus.FAILED);
                step.setErrorMessage(result.getErrorMessage());
                step.setErrorDetail(result.getErrorDetail());

                // 检查是否可以继续
                if (step.getDefinition() != null &&
                        step.getDefinition().getExecutionStrategy() != null &&
                        Boolean.TRUE.equals(step.getDefinition().getExecutionStrategy().getContinueOnFailure())) {
                    logger.warn("DefaultPlanExecutor#executeSteps - reason=step failed but continuing, stepId={}",
                            step.getStepId());
                    continue;
                }

                plan.setStatus(PlanStatus.FAILED);
                plan.setErrorMessage(result.getErrorMessage());
                return PlanExecutionResult.failure(plan, result.getErrorMessage());
            }
        }

        // 所有步骤完成
        plan.setStatus(PlanStatus.COMPLETED);
        plan.setCompletedAt(Instant.now());

        // 获取最终输出（最后一个步骤的输出）
        Object finalOutput = null;
        if (!steps.isEmpty()) {
            ExecutionStep lastStep = steps.get(steps.size() - 1);
            finalOutput = lastStep.getOutputValues();
        }
        plan.setResult(finalOutput);

        logger.info("DefaultPlanExecutor#execute - reason=plan completed, planId={}, stepCount={}",
                plan.getPlanId(), steps.size());

        return PlanExecutionResult.success(plan, finalOutput);
    }

    private void resolveStepInputs(ExecutionStep step, DefaultStepExecutionContext context) {
        if (step.getDefinition() == null || step.getDefinition().getInputParams() == null) {
            return;
        }

        Map<String, Object> inputValues = step.getInputValues();
        if (inputValues == null) {
            inputValues = new HashMap<>();
            step.setInputValues(inputValues);
        }

        for (ActionParameter param : step.getDefinition().getInputParams()) {
            if (param.getSource() == ParameterSource.PREVIOUS_STEP ||
                    param.getSource() == ParameterSource.STEP_OUTPUT) {
                String sourceRef = param.getSourceRef();
                if (sourceRef != null) {
                    Map<String, Object> stepOutput = context.getStepOutput(sourceRef);
                    if (stepOutput != null) {
                        // 使用表达式提取值
                        Object value = extractValue(stepOutput, param.getExpression());
                        if (value != null) {
                            inputValues.put(param.getName(), value);
                        }
                    }
                }
            } else if (param.getSource() == ParameterSource.CONTEXT) {
                String sourceRef = param.getSourceRef();
                if (sourceRef != null && context.getVariables().containsKey(sourceRef)) {
                    inputValues.put(param.getName(), context.getVariables().get(sourceRef));
                }
            }
        }
    }

    private Object extractValue(Map<String, Object> data, String expression) {
        if (expression == null || expression.isBlank()) {
            return data;
        }

        // 简单的 JSONPath 支持（$.field.subfield）
        if (expression.startsWith("$.")) {
            String[] parts = expression.substring(2).split("\\.");
            Object current = data;
            for (String part : parts) {
                if (current instanceof Map) {
                    current = ((Map<?, ?>) current).get(part);
                } else {
                    return null;
                }
            }
            return current;
        }

        return data.get(expression);
    }

    @Override
    public PlanExecutionResult resume(ExecutionPlan plan, Map<String, Object> userInput) {
        if (plan == null) {
            return PlanExecutionResult.failure(null, "Plan cannot be null");
        }

        logger.info("DefaultPlanExecutor#resume - reason=resuming plan, planId={}, currentStep={}",
                plan.getPlanId(), plan.getCurrentStepIndex());

        // 获取当前等待的步骤
        ExecutionStep currentStep = plan.getCurrentStep();
        if (currentStep == null) {
            return PlanExecutionResult.failure(plan, "No current step found");
        }

        if (currentStep.getStatus() != ExecutionStep.StepStatus.WAITING_INPUT) {
            return PlanExecutionResult.failure(plan, "Current step is not waiting for input");
        }

        // 将用户输入填充到步骤
        if (userInput != null) {
            if (currentStep.getInputValues() == null) {
                currentStep.setInputValues(new HashMap<>());
            }
            currentStep.getInputValues().putAll(userInput);
        }

        // 重置步骤状态为待执行
        currentStep.setStatus(ExecutionStep.StepStatus.PENDING);

        // 继续执行
        return executeSteps(plan);
    }

    @Override
    public boolean cancel(String planId) {
        ExecutionPlan plan = planCache.get(planId);
        if (plan == null) {
            return false;
        }

        plan.setStatus(PlanStatus.CANCELLED);
        logger.info("DefaultPlanExecutor#cancel - reason=plan cancelled, planId={}", planId);
        return true;
    }

    @Override
    public ExecutionPlan getStatus(String planId) {
        return planCache.get(planId);
    }

    @Override
    public PlanExecutionResult rollback(ExecutionPlan plan) {
        // TODO: 实现 SAGA 补偿逻辑
        logger.warn("DefaultPlanExecutor#rollback - reason=rollback not implemented, planId={}", plan.getPlanId());
        return PlanExecutionResult.failure(plan, "Rollback not implemented");
    }

    /**
     * 默认步骤执行上下文实现
     */
    private static class DefaultStepExecutionContext implements StepExecutor.StepExecutionContext {

        private final ExecutionPlan plan;
        private final Map<String, Map<String, Object>> stepOutputs = new HashMap<>();

        public DefaultStepExecutionContext(ExecutionPlan plan) {
            this.plan = plan;
        }

        @Override
        public String getPlanId() {
            return plan.getPlanId();
        }

        @Override
        public String getSessionId() {
            return plan.getSessionId();
        }

        @Override
        public String getUserId() {
            return plan.getContext() != null ? (String) plan.getContext().get("userId") : null;
        }

        @Override
        public Map<String, Object> getVariables() {
            return plan.getContext() != null ? plan.getContext() : new HashMap<>();
        }

        @Override
        public Map<String, Map<String, Object>> getPreviousStepOutputs() {
            return stepOutputs;
        }

        @Override
        public Map<String, Object> getStepOutput(String stepId) {
            return stepOutputs.get(stepId);
        }

        @Override
        public void setVariable(String key, Object value) {
            if (plan.getContext() == null) {
                plan.setContext(new HashMap<>());
            }
            plan.getContext().put(key, value);
        }

        @Override
        public Map<String, Object> getStateData() {
            return plan.getStateData();
        }

        @Override
        public void updateStateData(String key, Object value) {
            if (plan.getStateData() == null) {
                plan.setStateData(new HashMap<>());
            }
            plan.getStateData().put(key, value);
        }

        public void setStepOutput(String stepId, Map<String, Object> output) {
            stepOutputs.put(stepId, output);
        }
    }
}
