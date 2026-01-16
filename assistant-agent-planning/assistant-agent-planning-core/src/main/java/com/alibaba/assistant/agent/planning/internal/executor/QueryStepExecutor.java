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

import com.alibaba.assistant.agent.planning.model.ExecutionStep;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.alibaba.assistant.agent.planning.model.StepExecutionResult;
import com.alibaba.assistant.agent.planning.model.StepType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询步骤执行器
 *
 * <p>执行查询操作，返回选项供用户选择。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class QueryStepExecutor extends AbstractStepExecutor {

    @Override
    public StepType getSupportedType() {
        return StepType.QUERY;
    }

    @Override
    protected StepExecutionResult doExecute(ExecutionStep step, StepExecutionContext context) {
        logger.debug("QueryStepExecutor#doExecute - reason=executing query step, stepId={}", step.getStepId());

        StepDefinition definition = step.getDefinition();
        if (definition == null || definition.getInterfaceBinding() == null) {
            // 没有接口绑定，返回模拟数据或等待用户输入
            return handleNoBinding(step);
        }

        // 根据接口类型执行查询
        StepDefinition.InterfaceBinding binding = definition.getInterfaceBinding();
        String bindingType = binding.getType();

        try {
            List<?> queryResults = executeQuery(step, binding, context);

            if (queryResults == null || queryResults.isEmpty()) {
                return StepExecutionResult.waitingInput(
                        step.getUserPrompt() != null ? step.getUserPrompt() : "没有找到匹配的选项，请手动输入",
                        null
                );
            }

            // 如果需要用户选择，返回等待输入
            if (definition.getInterruptForInput() != null && definition.getInterruptForInput()) {
                return StepExecutionResult.waitingInput(
                        step.getUserPrompt() != null ? step.getUserPrompt() : "请从以下选项中选择：",
                        queryResults
                );
            }

            // 否则直接返回结果
            Map<String, Object> output = new HashMap<>();
            output.put("results", queryResults);
            output.put("count", queryResults.size());

            return StepExecutionResult.success(output);

        } catch (Exception e) {
            logger.error("QueryStepExecutor#doExecute - reason=query failed, stepId={}, error={}",
                    step.getStepId(), e.getMessage(), e);
            return StepExecutionResult.failure("Query execution failed: " + e.getMessage());
        }
    }

    private StepExecutionResult handleNoBinding(ExecutionStep step) {
        // 没有绑定接口时，返回等待用户输入
        String prompt = step.getUserPrompt();
        if (prompt == null) {
            prompt = "请提供查询条件";
        }

        return StepExecutionResult.waitingInput(prompt, null);
    }

    private List<?> executeQuery(ExecutionStep step, StepDefinition.InterfaceBinding binding,
                                 StepExecutionContext context) {
        String bindingType = binding.getType();

        if ("HTTP".equalsIgnoreCase(bindingType) && binding.getHttp() != null) {
            // HTTP 查询（简化实现，实际需要集成 HTTP 客户端）
            logger.warn("QueryStepExecutor#executeQuery - reason=HTTP query not fully implemented");
            return List.of();
        }

        if ("INTERNAL".equalsIgnoreCase(bindingType) && binding.getInternal() != null) {
            // 内部服务查询（需要通过 Spring 容器获取 Bean）
            logger.warn("QueryStepExecutor#executeQuery - reason=Internal query not fully implemented");
            return List.of();
        }

        return List.of();
    }

    @Override
    public ValidationResult validate(ExecutionStep step) {
        if (step == null) {
            return ValidationResult.failure("Step cannot be null");
        }
        return ValidationResult.success();
    }
}
