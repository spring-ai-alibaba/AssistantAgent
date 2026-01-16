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

import com.alibaba.assistant.agent.planning.model.ExecutionStep;
import com.alibaba.assistant.agent.planning.model.StepExecutionResult;
import com.alibaba.assistant.agent.planning.model.StepType;

import java.util.Map;

/**
 * 步骤执行器 SPI
 *
 * <p>负责执行特定类型的步骤。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface StepExecutor {

    /**
     * 获取支持的步骤类型
     *
     * @return 步骤类型
     */
    StepType getSupportedType();

    /**
     * 执行步骤
     *
     * @param step    执行步骤
     * @param context 执行上下文
     * @return 执行结果
     */
    StepExecutionResult execute(ExecutionStep step, StepExecutionContext context);

    /**
     * 校验步骤定义
     *
     * @param step 执行步骤
     * @return 校验结果，null 表示校验通过
     */
    default ValidationResult validate(ExecutionStep step) {
        return ValidationResult.success();
    }

    /**
     * 是否支持异步执行
     *
     * @return 是否支持异步
     */
    default boolean supportsAsync() {
        return false;
    }

    /**
     * 获取执行器优先级
     *
     * @return 优先级（数值越大优先级越高）
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 步骤执行上下文
     */
    interface StepExecutionContext {
        /**
         * 获取计划 ID
         */
        String getPlanId();

        /**
         * 获取会话 ID
         */
        String getSessionId();

        /**
         * 获取用户 ID
         */
        String getUserId();

        /**
         * 获取上下文变量
         */
        Map<String, Object> getVariables();

        /**
         * 获取前序步骤输出
         */
        Map<String, Map<String, Object>> getPreviousStepOutputs();

        /**
         * 获取指定步骤的输出
         */
        Map<String, Object> getStepOutput(String stepId);

        /**
         * 设置变量
         */
        void setVariable(String key, Object value);

        /**
         * 获取状态数据
         */
        Map<String, Object> getStateData();

        /**
         * 更新状态数据
         */
        void updateStateData(String key, Object value);
    }

    /**
     * 校验结果
     */
    class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}
