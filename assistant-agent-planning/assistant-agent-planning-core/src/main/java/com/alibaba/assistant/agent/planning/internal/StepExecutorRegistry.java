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

import com.alibaba.assistant.agent.planning.model.StepType;
import com.alibaba.assistant.agent.planning.spi.StepExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 步骤执行器注册表
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class StepExecutorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(StepExecutorRegistry.class);

    private final Map<StepType, List<StepExecutor>> executors = new ConcurrentHashMap<>();

    public StepExecutorRegistry() {
    }

    public StepExecutorRegistry(List<StepExecutor> stepExecutors) {
        if (stepExecutors != null) {
            stepExecutors.forEach(this::register);
        }
    }

    /**
     * 注册步骤执行器
     */
    public void register(StepExecutor executor) {
        if (executor == null || executor.getSupportedType() == null) {
            return;
        }

        executors.computeIfAbsent(executor.getSupportedType(), k -> new ArrayList<>()).add(executor);

        // 按优先级排序
        executors.get(executor.getSupportedType())
                .sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        logger.info("StepExecutorRegistry#register - reason=registered executor, type={}, executorClass={}",
                executor.getSupportedType(), executor.getClass().getSimpleName());
    }

    /**
     * 获取指定类型的执行器（优先级最高的）
     */
    public StepExecutor getExecutor(StepType type) {
        List<StepExecutor> typeExecutors = executors.get(type);
        if (typeExecutors == null || typeExecutors.isEmpty()) {
            return null;
        }
        return typeExecutors.get(0);
    }

    /**
     * 获取指定类型的所有执行器
     */
    public List<StepExecutor> getExecutors(StepType type) {
        return executors.getOrDefault(type, Collections.emptyList());
    }

    /**
     * 获取所有已注册的执行器
     */
    public Map<StepType, List<StepExecutor>> getAllExecutors() {
        return Collections.unmodifiableMap(executors);
    }

    /**
     * 检查是否有指定类型的执行器
     */
    public boolean hasExecutor(StepType type) {
        List<StepExecutor> typeExecutors = executors.get(type);
        return typeExecutors != null && !typeExecutors.isEmpty();
    }

    /**
     * 获取支持的步骤类型
     */
    public Set<StepType> getSupportedTypes() {
        return executors.keySet();
    }
}
