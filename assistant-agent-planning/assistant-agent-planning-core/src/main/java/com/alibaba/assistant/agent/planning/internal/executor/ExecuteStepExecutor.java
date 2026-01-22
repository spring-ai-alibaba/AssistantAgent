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

import com.alibaba.assistant.agent.planning.executor.ActionExecutorFactory;
import com.alibaba.assistant.agent.planning.model.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

/**
 * 执行步骤执行器
 *
 * <p>执行具体的业务动作。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ExecuteStepExecutor extends AbstractStepExecutor {

    @Autowired(required = false)
    private ActionExecutorFactory actionExecutorFactory;

    @Override
    public StepType getSupportedType() {
        return StepType.EXECUTE;
    }

    @Override
    protected StepExecutionResult doExecute(ExecutionStep step, StepExecutionContext context) {
        logger.debug("ExecuteStepExecutor#doExecute - reason=executing execute step, stepId={}", step.getStepId());

        StepDefinition definition = step.getDefinition();
        if (definition == null || definition.getInterfaceBinding() == null) {
            // 没有接口绑定，直接返回输入作为输出
            Map<String, Object> output = new HashMap<>();
            output.put("executed", true);
            output.put("inputs", step.getInputValues());
            return StepExecutionResult.success(output);
        }

        // 使用 ActionExecutorFactory 执行
        try {
            // 构建 ActionDefinition（从 StepDefinition 转换）
            ActionDefinition action = buildActionDefinition(step, context);

            // 准备参数（合并 step 的 inputValues 和 context）
            Map<String, Object> params = prepareParams(step, context);

            // 执行
            ExecutionResult executionResult = actionExecutorFactory.execute(action, params, null);

            Map<String, Object> output = new HashMap<>();
            output.put("executed", true);
            output.put("success", executionResult.isSuccess());
            output.put("result", executionResult.getResponseData());

            if (!executionResult.isSuccess()) {
                output.put("error", executionResult.getErrorMessage());
            }

            logger.info("ExecuteStepExecutor#doExecute - reason=step executed, stepId={}, success={}",
                    step.getStepId(), executionResult.isSuccess());

            return executionResult.isSuccess()
                    ? StepExecutionResult.success(output)
                    : StepExecutionResult.failure(executionResult.getErrorMessage());

        } catch (Exception e) {
            logger.error("ExecuteStepExecutor#doExecute - reason=execution failed, stepId={}, error={}",
                    step.getStepId(), e.getMessage(), e);
            return StepExecutionResult.failure("Execution failed: " + e.getMessage());
        }
    }

    /**
     * 构建 ActionDefinition（从 StepDefinition 转换）
     */
    private ActionDefinition buildActionDefinition(ExecutionStep step, StepExecutionContext context) {
        StepDefinition definition = step.getDefinition();

        ActionDefinition action = new ActionDefinition();
        action.setActionId(step.getStepId());
        action.setActionName(definition.getName());
        action.setInterfaceBinding(definition.getInterfaceBinding());

        // 从 context 中获取 systemId（如果有）
        Map<String, Object> variables = context.getVariables();
        if (variables != null) {
            Object systemId = variables.get("systemId");
            if (systemId != null) {
                action.setSystemId(systemId.toString());
            }
        }

        return action;
    }

    /**
     * 准备参数（合并 step 的 inputValues 和 context）
     */
    private Map<String, Object> prepareParams(ExecutionStep step, StepExecutionContext context) {
        Map<String, Object> params = new HashMap<>();

        // 添加 step 的输入值
        if (step.getInputValues() != null) {
            params.putAll(step.getInputValues());
        }

        // 添加 action_id（OaSystemActionService需要）
        params.put("action_id", step.getStepId());

        // 添加 context（用于传递 userId, systemId 等）
        Map<String, Object> contextMap = new HashMap<>();

        // 获取 userId（优先从 variables，然后从 context）
        String userId = context.getUserId();
        Map<String, Object> variables = context.getVariables();
        if (variables != null) {
            // 尝试从 variables 中获取 userId 或 assistantUserId
            Object varUserId = variables.get("userId");
            if (varUserId == null) {
                varUserId = variables.get("assistantUserId");
            }
            if (varUserId != null) {
                userId = varUserId.toString();
            }
        }

        // 设置 userId 和 assistantUserId
        if (userId != null) {
            contextMap.put("userId", userId);
            contextMap.put("assistantUserId", userId);
        }

        contextMap.put("sessionId", context.getSessionId());
        contextMap.put("planId", context.getPlanId());

        // 添加 variables 中的其他内容
        if (variables != null) {
            contextMap.putAll(variables);
        }

        params.put("context", contextMap);

        logger.debug("ExecuteStepExecutor#prepareParams - stepId={}, userId={}, params={}",
                step.getStepId(), userId, params.keySet());

        return params;
    }
}
