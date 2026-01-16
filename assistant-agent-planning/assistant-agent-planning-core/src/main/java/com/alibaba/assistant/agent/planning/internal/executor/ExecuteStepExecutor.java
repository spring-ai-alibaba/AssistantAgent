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

        // 根据接口类型执行
        StepDefinition.InterfaceBinding binding = definition.getInterfaceBinding();
        String bindingType = binding.getType();

        try {
            Map<String, Object> result = executeAction(step, binding, context);

            Map<String, Object> output = new HashMap<>();
            output.put("executed", true);
            output.put("result", result);

            logger.info("ExecuteStepExecutor#doExecute - reason=step executed successfully, stepId={}",
                    step.getStepId());

            return StepExecutionResult.success(output);

        } catch (Exception e) {
            logger.error("ExecuteStepExecutor#doExecute - reason=execution failed, stepId={}, error={}",
                    step.getStepId(), e.getMessage(), e);
            return StepExecutionResult.failure("Execution failed: " + e.getMessage());
        }
    }

    private Map<String, Object> executeAction(ExecutionStep step, StepDefinition.InterfaceBinding binding,
                                              StepExecutionContext context) {
        String bindingType = binding.getType();
        Map<String, Object> result = new HashMap<>();

        if ("HTTP".equalsIgnoreCase(bindingType) && binding.getHttp() != null) {
            // HTTP 调用（简化实现）
            StepDefinition.HttpConfig http = binding.getHttp();
            logger.info("ExecuteStepExecutor#executeAction - reason=HTTP call, url={}, method={}",
                    http.getUrl(), http.getMethod());

            // TODO: 实际的 HTTP 调用实现
            result.put("type", "HTTP");
            result.put("url", http.getUrl());
            result.put("method", http.getMethod());
            result.put("success", true);
        }

        if ("INTERNAL".equalsIgnoreCase(bindingType) && binding.getInternal() != null) {
            // 内部服务调用（简化实现）
            StepDefinition.InternalConfig internal = binding.getInternal();
            logger.info("ExecuteStepExecutor#executeAction - reason=internal call, bean={}, method={}",
                    internal.getBeanName(), internal.getMethodName());

            // TODO: 实际的内部服务调用实现
            result.put("type", "INTERNAL");
            result.put("bean", internal.getBeanName());
            result.put("method", internal.getMethodName());
            result.put("success", true);
        }

        if ("MCP".equalsIgnoreCase(bindingType) && binding.getMcp() != null) {
            // MCP 工具调用（简化实现）
            StepDefinition.McpConfig mcp = binding.getMcp();
            logger.info("ExecuteStepExecutor#executeAction - reason=MCP call, tool={}, server={}",
                    mcp.getToolName(), mcp.getServerName());

            // TODO: 实际的 MCP 调用实现
            result.put("type", "MCP");
            result.put("tool", mcp.getToolName());
            result.put("server", mcp.getServerName());
            result.put("success", true);
        }

        return result;
    }
}
