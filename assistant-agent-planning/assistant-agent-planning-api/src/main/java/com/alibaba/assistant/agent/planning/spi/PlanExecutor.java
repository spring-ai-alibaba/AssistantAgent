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
package com.alibaba.assistant.agent.planning.spi;

import com.alibaba.assistant.agent.planning.model.ExecutionPlan;
import com.alibaba.assistant.agent.planning.model.PlanExecutionResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 执行计划执行器 SPI
 *
 * <p>负责执行执行计划。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface PlanExecutor {

    /**
     * 执行计划
     *
     * @param plan 执行计划
     * @return 执行结果
     */
    PlanExecutionResult execute(ExecutionPlan plan);

    /**
     * 执行计划（带上下文）
     *
     * @param plan    执行计划
     * @param context 执行上下文
     * @return 执行结果
     */
    PlanExecutionResult execute(ExecutionPlan plan, Map<String, Object> context);

    /**
     * 异步执行计划
     *
     * @param plan 执行计划
     * @return 执行结果 Future
     */
    default CompletableFuture<PlanExecutionResult> executeAsync(ExecutionPlan plan) {
        return CompletableFuture.supplyAsync(() -> execute(plan));
    }

    /**
     * 恢复执行计划
     *
     * @param plan      执行计划
     * @param userInput 用户输入（用于继续等待输入的步骤）
     * @return 执行结果
     */
    PlanExecutionResult resume(ExecutionPlan plan, Map<String, Object> userInput);

    /**
     * 取消执行计划
     *
     * @param planId 计划 ID
     * @return 是否取消成功
     */
    boolean cancel(String planId);

    /**
     * 获取计划执行状态
     *
     * @param planId 计划 ID
     * @return 执行计划（包含最新状态）
     */
    ExecutionPlan getStatus(String planId);

    /**
     * 回滚计划（执行补偿）
     *
     * @param plan 执行计划
     * @return 回滚结果
     */
    PlanExecutionResult rollback(ExecutionPlan plan);
}
