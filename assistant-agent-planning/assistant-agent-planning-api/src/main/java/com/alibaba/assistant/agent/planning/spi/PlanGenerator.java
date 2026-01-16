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

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ExecutionPlan;

import java.util.Map;

/**
 * 执行计划生成器 SPI
 *
 * <p>负责根据动作定义和参数生成执行计划。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface PlanGenerator {

    /**
     * 生成执行计划
     *
     * @param action     动作定义
     * @param parameters 提取的参数
     * @param context    生成上下文
     * @return 执行计划
     */
    ExecutionPlan generate(ActionDefinition action, Map<String, Object> parameters, PlanGenerationContext context);

    /**
     * 校验执行计划
     *
     * @param plan 执行计划
     * @return 校验结果
     */
    ValidationResult validate(ExecutionPlan plan);

    /**
     * 优化执行计划
     *
     * @param plan 原始执行计划
     * @return 优化后的执行计划
     */
    default ExecutionPlan optimize(ExecutionPlan plan) {
        return plan;
    }

    /**
     * 计划生成上下文
     */
    interface PlanGenerationContext {
        /**
         * 获取会话 ID
         */
        String getSessionId();

        /**
         * 获取用户 ID
         */
        String getUserId();

        /**
         * 获取原始用户输入
         */
        String getUserInput();

        /**
         * 获取上下文变量
         */
        Map<String, Object> getContextVariables();

        /**
         * 获取超时时间（分钟）
         */
        Integer getTimeoutMinutes();
    }

    /**
     * 校验结果
     */
    class ValidationResult {
        private final boolean valid;
        private final String message;
        private final Map<String, String> fieldErrors;

        private ValidationResult(boolean valid, String message, Map<String, String> fieldErrors) {
            this.valid = valid;
            this.message = message;
            this.fieldErrors = fieldErrors;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message, null);
        }

        public static ValidationResult failure(String message, Map<String, String> fieldErrors) {
            return new ValidationResult(false, message, fieldErrors);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, String> getFieldErrors() {
            return fieldErrors;
        }
    }
}
