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
package com.alibaba.assistant.agent.planning.executor;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ExecutionResult;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.alibaba.assistant.agent.planning.spi.ActionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP（Model Context Protocol）执行器
 *
 * <p>执行 MCP 类型的 Action，调用 MCP Server 提供的工具。
 *
 * <h3>MCP 协议</h3>
 * <p>MCP 是连接 AI 应用和外部数据源/工具的开放标准。
 *
 * <h3>功能特性</h3>
 * <ul>
 * <li>调用 MCP Server 工具</li>
 * <li>支持参数序列化和反序列化</li>
 * <li>处理 MCP 协议错误</li>
 * <li>支持超时配置</li>
 * </ul>
 *
 * <h3>集成方式</h3>
 * <p>需要实现或集成 MCP Client（例如使用 io.modelcontextprotocol.sdk）。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class McpExecutor implements ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(McpExecutor.class);

    @Override
    public String getExecutorType() {
        return "MCP";
    }

    @Override
    public int getPriority() {
        return 90;
    }

    @Override
    public ExecutionResult execute(ActionDefinition action,
                                   Map<String, Object> params,
                                   Integer timeoutSeconds) {
        long startTime = System.currentTimeMillis();

        StepDefinition.InterfaceBinding binding = action.getBinding();
        StepDefinition.McpConfig mcpConfig = binding.getMcp();

        String serverName = mcpConfig.getServerName();
        String toolName = mcpConfig.getToolName();

        logger.info("McpExecutor#execute - invoking MCP tool, actionId={}, server={}, tool={}",
                action.getActionId(), serverName, toolName);

        try {
            // TODO: 集成 MCP Client
            // 1. 获取 MCP Server 连接（从 MCP Client 管理器）
            // 2. 调用工具
            // 3. 解析结果

            // 示例实现（伪代码）：
            // McpClient mcpClient = mcpClientManager.getClient(serverName);
            // McpToolCallResult result = mcpClient.callTool(toolName, params, timeoutSeconds);
            // return ExecutionResult.success(result.getData());

            logger.warn("McpExecutor#execute - MCP Client not integrated yet, actionId={}", action.getActionId());

            long executionTime = System.currentTimeMillis() - startTime;
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("MCP 执行器尚未集成 MCP Client")
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.error("McpExecutor#execute - execution failed, actionId={}, time={}ms",
                    action.getActionId(), executionTime, e);
            return ExecutionResult.failure("MCP 工具调用失败: " + e.getMessage(), e);
        }
    }
}
