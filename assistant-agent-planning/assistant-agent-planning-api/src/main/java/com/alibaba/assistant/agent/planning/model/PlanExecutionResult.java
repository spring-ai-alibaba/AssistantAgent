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
package com.alibaba.assistant.agent.planning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 计划执行结果
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanExecutionResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 计划状态
     */
    private PlanStatus status;

    /**
     * 执行的计划
     */
    private ExecutionPlan plan;

    /**
     * 最终输出
     */
    private Object output;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 用户提示（需要用户输入时）
     */
    private String userPrompt;

    /**
     * 选项（需要用户选择时）
     */
    private Object options;

    /**
     * 是否需要用户输入
     */
    @Builder.Default
    private boolean needsUserInput = false;

    /**
     * 已完成步骤数
     */
    private Integer completedSteps;

    /**
     * 总步骤数
     */
    private Integer totalSteps;

    /**
     * 当前步骤索引
     */
    private Integer currentStepIndex;

    /**
     * 执行耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 每个步骤的输出
     */
    private Map<String, Object> stepOutputs;

    /**
     * 创建成功结果
     */
    public static PlanExecutionResult success(ExecutionPlan plan, Object output) {
        return PlanExecutionResult.builder()
                .success(true)
                .status(PlanStatus.COMPLETED)
                .plan(plan)
                .output(output)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static PlanExecutionResult failure(ExecutionPlan plan, String errorMessage) {
        return PlanExecutionResult.builder()
                .success(false)
                .status(PlanStatus.FAILED)
                .plan(plan)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建等待输入结果
     */
    public static PlanExecutionResult waitingInput(ExecutionPlan plan, String userPrompt, Object options) {
        return PlanExecutionResult.builder()
                .success(true)
                .status(PlanStatus.WAITING_INPUT)
                .plan(plan)
                .needsUserInput(true)
                .userPrompt(userPrompt)
                .options(options)
                .build();
    }

    /**
     * 创建进行中结果
     */
    public static PlanExecutionResult inProgress(ExecutionPlan plan, int currentStep, int totalSteps) {
        return PlanExecutionResult.builder()
                .success(true)
                .status(PlanStatus.IN_PROGRESS)
                .plan(plan)
                .currentStepIndex(currentStep)
                .totalSteps(totalSteps)
                .build();
    }
}
