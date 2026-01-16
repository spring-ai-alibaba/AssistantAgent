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
 * 步骤执行结果
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepExecutionResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 结果状态
     */
    private ExecutionStep.StepStatus status;

    /**
     * 输出数据
     */
    private Map<String, Object> output;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 错误详情
     */
    private String errorDetail;

    /**
     * 用户提示（需要用户输入时）
     */
    private String userPrompt;

    /**
     * 选项列表（QUERY 步骤）
     */
    private Object options;

    /**
     * 是否需要中断等待用户输入
     */
    @Builder.Default
    private boolean needsUserInput = false;

    /**
     * 执行耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 创建成功结果
     */
    public static StepExecutionResult success(Map<String, Object> output) {
        return StepExecutionResult.builder()
                .success(true)
                .status(ExecutionStep.StepStatus.COMPLETED)
                .output(output)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static StepExecutionResult failure(String errorMessage) {
        return StepExecutionResult.builder()
                .success(false)
                .status(ExecutionStep.StepStatus.FAILED)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建失败结果（带详情）
     */
    public static StepExecutionResult failure(String errorMessage, String errorDetail) {
        return StepExecutionResult.builder()
                .success(false)
                .status(ExecutionStep.StepStatus.FAILED)
                .errorMessage(errorMessage)
                .errorDetail(errorDetail)
                .build();
    }

    /**
     * 创建等待输入结果
     */
    public static StepExecutionResult waitingInput(String userPrompt, Object options) {
        return StepExecutionResult.builder()
                .success(true)
                .status(ExecutionStep.StepStatus.WAITING_INPUT)
                .needsUserInput(true)
                .userPrompt(userPrompt)
                .options(options)
                .build();
    }

    /**
     * 创建跳过结果
     */
    public static StepExecutionResult skipped() {
        return StepExecutionResult.builder()
                .success(true)
                .status(ExecutionStep.StepStatus.SKIPPED)
                .build();
    }
}
