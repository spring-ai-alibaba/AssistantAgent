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
import java.util.Map;

/**
 * 执行步骤实例
 *
 * <p>表示执行计划中的一个具体执行步骤，包含运行时状态。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStep {

    /**
     * 步骤实例 ID
     */
    private String stepInstanceId;

    /**
     * 步骤定义 ID
     */
    private String stepId;

    /**
     * 步骤名称
     */
    private String name;

    /**
     * 步骤类型
     */
    private StepType type;

    /**
     * 步骤顺序
     */
    private Integer order;

    /**
     * 步骤定义（引用）
     */
    private StepDefinition definition;

    /**
     * 输入参数值
     */
    private Map<String, Object> inputValues;

    /**
     * 输出结果
     */
    private Map<String, Object> outputValues;

    /**
     * 步骤状态
     */
    @Builder.Default
    private StepStatus status = StepStatus.PENDING;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 错误详情
     */
    private String errorDetail;

    /**
     * 重试次数
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 开始时间
     */
    private Instant startTime;

    /**
     * 结束时间
     */
    private Instant endTime;

    /**
     * 执行耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 用户提示（等待输入时）
     */
    private String userPrompt;

    /**
     * 可选项（QUERY 步骤返回）
     */
    private Object options;

    /**
     * 步骤状态枚举
     */
    public enum StepStatus {
        /**
         * 待执行
         */
        PENDING,

        /**
         * 执行中
         */
        RUNNING,

        /**
         * 等待输入
         */
        WAITING_INPUT,

        /**
         * 已完成
         */
        COMPLETED,

        /**
         * 已跳过
         */
        SKIPPED,

        /**
         * 已失败
         */
        FAILED,

        /**
         * 已取消
         */
        CANCELLED,

        /**
         * 已补偿
         */
        COMPENSATED
    }

    /**
     * 判断步骤是否已完成
     */
    public boolean isCompleted() {
        return StepStatus.COMPLETED.equals(status)
                || StepStatus.SKIPPED.equals(status);
    }

    /**
     * 判断步骤是否失败
     */
    public boolean isFailed() {
        return StepStatus.FAILED.equals(status);
    }

    /**
     * 判断步骤是否需要等待
     */
    public boolean isWaiting() {
        return StepStatus.WAITING_INPUT.equals(status);
    }
}
