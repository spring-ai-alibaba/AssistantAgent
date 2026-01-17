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
 * DataAgent 执行器
 *
 * <p>执行 DATA_AGENT 类型的 Action，调用 DataAgent 进行数据查询。
 *
 * <h3>DataAgent</h3>
 * <p>DataAgent 是 Spring AI Alibaba 提供的数据查询智能体，支持自然语言查询数据库。
 *
 * <h3>功能特性</h3>
 * <ul>
 * <li>自然语言转 SQL</li>
 * <li>安全的数据访问控制</li>
 * <li>支持多种数据库</li>
 * <li>支持数据权限过滤</li>
 * </ul>
 *
 * <h3>配置示例</h3>
 * <pre>
 * binding:
 *   type: DATA_AGENT
 *   dataSourceId: user_db
 *   queryType: NATURAL_LANGUAGE
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class DataAgentExecutor implements ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DataAgentExecutor.class);

    @Override
    public String getExecutorType() {
        return "DATA_AGENT";
    }

    @Override
    public int getPriority() {
        return 70;
    }

    @Override
    public ExecutionResult execute(ActionDefinition action,
                                   Map<String, Object> params,
                                   Integer timeoutSeconds) {
        long startTime = System.currentTimeMillis();

        StepDefinition.InterfaceBinding binding = action.getBinding();
        StepDefinition.DataAgentConfig dataAgentConfig = binding.getDataAgent();

        String dataSourceId = dataAgentConfig.getDataSourceId();

        logger.info("DataAgentExecutor#execute - querying data, actionId={}, dataSource={}",
                action.getActionId(), dataSourceId);

        try {
            // TODO: 集成 DataAgent
            // 1. 获取 DataAgent 实例
            // 2. 构建自然语言查询（从 params 中提取用户问题）
            // 3. 执行查询并返回结果

            // 示例实现（伪代码）：
            // DataAgent dataAgent = dataAgentManager.getAgent(dataSourceId);
            // String query = (String) params.get("query");
            // DataAgentResult result = dataAgent.query(query, timeoutSeconds);
            // return ExecutionResult.success(result.getData());

            logger.warn("DataAgentExecutor#execute - DataAgent not integrated yet, actionId={}", action.getActionId());

            long executionTime = System.currentTimeMillis() - startTime;
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("DataAgent 执行器尚未集成")
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.error("DataAgentExecutor#execute - query failed, actionId={}, time={}ms",
                    action.getActionId(), executionTime, e);
            return ExecutionResult.failure("DataAgent 查询失败: " + e.getMessage(), e);
        }
    }
}
