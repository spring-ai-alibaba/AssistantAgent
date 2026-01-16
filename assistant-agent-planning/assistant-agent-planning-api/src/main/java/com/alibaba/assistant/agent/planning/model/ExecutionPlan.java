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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 执行计划
 *
 * <p>表示一个完整的执行计划，包含要执行的动作、参数和步骤。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPlan {

    /**
     * 计划唯一 ID
     */
    @Builder.Default
    private String planId = UUID.randomUUID().toString();

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 动作 ID
     */
    private String actionId;

    /**
     * 动作名称
     */
    private String actionName;

    /**
     * 原始用户输入
     */
    private String userInput;

    /**
     * 动作定义引用
     */
    private ActionDefinition actionDefinition;

    /**
     * 从用户输入中提取的参数
     */
    private Map<String, Object> extractedParameters;

    /**
     * 执行步骤列表
     */
    private List<ExecutionStep> steps;

    /**
     * 当前步骤索引
     */
    @Builder.Default
    private Integer currentStepIndex = 0;

    /**
     * 计划状态
     */
    @Builder.Default
    private PlanStatus status = PlanStatus.PENDING;

    /**
     * 状态数据（中间结果存储）
     */
    private Map<String, Object> stateData;

    /**
     * 执行上下文
     */
    private Map<String, Object> context;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * 开始执行时间
     */
    private Instant startedAt;

    /**
     * 完成时间
     */
    private Instant completedAt;

    /**
     * 过期时间
     */
    private Instant expireAt;

    /**
     * 最终执行结果
     */
    private Object result;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 获取当前步骤
     */
    public ExecutionStep getCurrentStep() {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        if (currentStepIndex < 0 || currentStepIndex >= steps.size()) {
            return null;
        }
        return steps.get(currentStepIndex);
    }

    /**
     * 移动到下一步骤
     */
    public boolean moveToNextStep() {
        if (steps == null || currentStepIndex >= steps.size() - 1) {
            return false;
        }
        currentStepIndex++;
        return true;
    }

    /**
     * 判断是否所有步骤已完成
     */
    public boolean isAllStepsCompleted() {
        if (steps == null || steps.isEmpty()) {
            return true;
        }
        return steps.stream().allMatch(ExecutionStep::isCompleted);
    }

    /**
     * 判断是否有步骤失败
     */
    public boolean hasFailedStep() {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        return steps.stream().anyMatch(ExecutionStep::isFailed);
    }

    /**
     * 判断是否有步骤等待输入
     */
    public boolean hasWaitingStep() {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        return steps.stream().anyMatch(ExecutionStep::isWaiting);
    }

    /**
     * 获取已完成步骤的输出
     */
    public Map<String, Object> getStepOutput(String stepId) {
        if (steps == null) {
            return null;
        }
        return steps.stream()
                .filter(s -> stepId.equals(s.getStepId()))
                .findFirst()
                .map(ExecutionStep::getOutputValues)
                .orElse(null);
    }
}
