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
package com.alibaba.assistant.agent.planning.web.dto;

import com.alibaba.assistant.agent.planning.model.PlanExecutionResult;
import com.alibaba.assistant.agent.planning.model.PlanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 计划执行响应
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanExecuteResponse {

    /**
     * 计划ID
     */
    private String planId;

    /**
     * 动作ID
     */
    private String actionId;

    /**
     * 执行状态
     */
    private PlanStatus status;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 执行输出
     */
    private Object output;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 用户提示（等待输入时）
     */
    private String userPrompt;

    /**
     * 选项（等待选择时）
     */
    private Object options;

    /**
     * 是否需要用户输入
     */
    private boolean needsUserInput;

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
     * 步骤输出
     */
    private Map<String, Object> stepOutputs;

    /**
     * 执行耗时（毫秒）
     */
    private Long executionTimeMs;

    public static PlanExecuteResponse from(PlanExecutionResult result) {
        PlanExecuteResponseBuilder builder = PlanExecuteResponse.builder()
                .status(result.getStatus())
                .success(result.isSuccess())
                .output(result.getOutput())
                .errorMessage(result.getErrorMessage())
                .userPrompt(result.getUserPrompt())
                .options(result.getOptions())
                .needsUserInput(result.isNeedsUserInput())
                .completedSteps(result.getCompletedSteps())
                .totalSteps(result.getTotalSteps())
                .currentStepIndex(result.getCurrentStepIndex())
                .stepOutputs(result.getStepOutputs())
                .executionTimeMs(result.getDurationMs());

        // 从 plan 获取 ID
        if (result.getPlan() != null) {
            builder.planId(result.getPlan().getPlanId());
            builder.actionId(result.getPlan().getActionId());
        }

        return builder.build();
    }
}
