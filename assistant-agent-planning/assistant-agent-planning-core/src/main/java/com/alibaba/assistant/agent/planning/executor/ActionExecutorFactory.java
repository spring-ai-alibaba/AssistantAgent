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
import com.alibaba.assistant.agent.planning.permission.interceptor.PermissionInterceptor;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionCheckResult;
import com.alibaba.assistant.agent.planning.spi.ActionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Action 执行器工厂
 *
 * <p>负责管理和路由到正确的 ActionExecutor 实现。
 *
 * <h3>工作原理</h3>
 * <ul>
 * <li>收集所有 {@link ActionExecutor} Spring Bean</li>
 * <li>根据 {@link ActionDefinition.ActionBinding#getType() 路由到对应的执行器</li>
 * <li>如果多个执行器支持同一类型，选择优先级最高的</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class ActionExecutorFactory {

    private static final Logger logger = LoggerFactory.getLogger(ActionExecutorFactory.class);

    private final Map<String, ActionExecutor> executorMap;
    private final PermissionInterceptor permissionInterceptor;

    @Autowired(required = false)
    public ActionExecutorFactory(List<ActionExecutor> executors, PermissionInterceptor permissionInterceptor) {
        this.executorMap = buildExecutorMap(executors);
        this.permissionInterceptor = permissionInterceptor;
        logInitializedExecutors();
        if (permissionInterceptor != null) {
            logger.info("ActionExecutorFactory#init - permission interceptor enabled");
        } else {
            logger.info("ActionExecutorFactory#init - permission interceptor disabled");
        }
    }

    /**
     * 构建执行器映射表
     *
     * <p>如果多个执行器支持同一类型，选择优先级最高的。
     */
    private Map<String, ActionExecutor> buildExecutorMap(List<ActionExecutor> executors) {
        if (executors == null || executors.isEmpty()) {
            logger.warn("ActionExecutorFactory#buildExecutorMap - no ActionExecutor beans found");
            return Collections.emptyMap();
        }

        Map<String, ActionExecutor> map = new HashMap<>();

        for (ActionExecutor executor : executors) {
            String type = executor.getExecutorType().toUpperCase();

            ActionExecutor existing = map.get(type);
            if (existing == null || executor.getPriority() > existing.getPriority()) {
                map.put(type, executor);
            }
        }

        return map;
    }

    /**
     * 记录初始化的执行器
     */
    private void logInitializedExecutors() {
        if (executorMap.isEmpty()) {
            logger.warn("ActionExecutorFactory#init - no executors initialized");
            return;
        }

        String executorsStr = executorMap.values().stream()
                .map(e -> e.getExecutorType() + "(" + e.getClass().getSimpleName() + ")")
                .collect(Collectors.joining(", "));

        logger.info("ActionExecutorFactory#init - initialized executors: {}", executorsStr);
    }

    /**
     * 获取指定类型的执行器
     *
     * @param type 执行器类型
     * @return 执行器实例，如果不存在返回 null
     */
    public ActionExecutor getExecutor(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Executor type cannot be null or empty");
        }

        String key = type.toUpperCase();
        ActionExecutor executor = executorMap.get(key);

        if (executor == null) {
            logger.error("ActionExecutorFactory#getExecutor - no executor found for type: {}", type);
        }

        return executor;
    }

    /**
     * 执行 Action
     *
     * @param action         Action 定义
     * @param params         参数值
     * @param timeoutSeconds 超时时间（秒）
     * @return 执行结果
     * @throws IllegalArgumentException 如果找不到对应的执行器
     */
    public ExecutionResult execute(ActionDefinition action,
                                   Map<String, Object> params,
                                   Integer timeoutSeconds) {
        if (action == null) {
            throw new IllegalArgumentException("Action cannot be null");
        }

        StepDefinition.InterfaceBinding binding = action.getInterfaceBinding();
        if (binding == null) {
            throw new IllegalArgumentException("Action binding cannot be null");
        }

        String type = binding.getType();
        ActionExecutor executor = getExecutor(type);

        if (executor == null) {
            String error = String.format("不支持的 Action 类型: %s", type);
            logger.error("ActionExecutorFactory#execute - {}", error);
            return ExecutionResult.failure(error);
        }

        logger.debug("ActionExecutorFactory#execute - executing actionId={}, type={}, executor={}",
                action.getActionId(), type, executor.getClass().getSimpleName());

        // Apply permission check and data permission injection if enabled
        Map<String, Object> finalParams = params;
        if (permissionInterceptor != null) {
            // Extract context from params (platformUserId, systemId, etc.)
            String platformUserId = extractFromParams(params, "platformUserId", "userId", "_userId");
            String systemId = extractFromParams(params, "systemId", "_systemId");
            Map<String, Object> context = extractContext(params);

            // Check functional permission first
            if (platformUserId != null && systemId != null) {
                PermissionCheckResult checkResult = permissionInterceptor.checkPermission(
                        action, platformUserId, systemId, context);

                if (!checkResult.isAllowed()) {
                    logger.warn("ActionExecutorFactory#execute - permission denied: userId={}, systemId={}, actionId={}, reason={}",
                            platformUserId, systemId, action.getActionId(), checkResult.getMessage());
                    return ExecutionResult.failure("权限不足: " + checkResult.getMessage());
                }

                // Inject data permissions into parameters
                finalParams = permissionInterceptor.injectDataPermission(
                        action, params, platformUserId, systemId, context);

                logger.debug("ActionExecutorFactory#execute - permission check passed and data permission injected");
            } else {
                logger.debug("ActionExecutorFactory#execute - skipping permission check (missing userId or systemId)");
            }
        }

        try {
            return executor.execute(action, finalParams, timeoutSeconds);
        } catch (Exception e) {
            logger.error("ActionExecutorFactory#execute - execution failed, actionId={}",
                    action.getActionId(), e);
            return ExecutionResult.failure("执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * Extract value from params by trying multiple keys.
     */
    private String extractFromParams(Map<String, Object> params, String... keys) {
        if (params == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = params.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * Extract context map from params.
     */
    private Map<String, Object> extractContext(Map<String, Object> params) {
        Map<String, Object> context = new HashMap<>();
        if (params != null) {
            // Copy all params to context
            context.putAll(params);
        }
        return context;
    }

    /**
     * 获取所有已注册的执行器类型
     *
     * @return 执行器类型集合
     */
    public Set<String> getSupportedTypes() {
        return Collections.unmodifiableSet(executorMap.keySet());
    }

    /**
     * 检查是否支持指定类型
     *
     * @param type 执行器类型
     * @return 如果支持返回 true
     */
    public boolean supports(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        return executorMap.containsKey(type.toUpperCase());
    }
}
