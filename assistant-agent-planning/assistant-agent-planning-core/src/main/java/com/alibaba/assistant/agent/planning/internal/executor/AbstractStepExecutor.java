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
package com.alibaba.assistant.agent.planning.internal.executor;

import com.alibaba.assistant.agent.planning.model.ExecutionStep;
import com.alibaba.assistant.agent.planning.model.StepExecutionResult;
import com.alibaba.assistant.agent.planning.spi.StepExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 步骤执行器抽象基类
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public abstract class AbstractStepExecutor implements StepExecutor {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public StepExecutionResult execute(ExecutionStep step, StepExecutionContext context) {
        logger.debug("AbstractStepExecutor#execute - reason=executing step, stepId={}, type={}",
                step.getStepId(), getSupportedType());

        try {
            // 校验
            ValidationResult validation = validate(step);
            if (!validation.isValid()) {
                return StepExecutionResult.failure(validation.getMessage());
            }

            // 执行
            return doExecute(step, context);
        } catch (Exception e) {
            logger.error("AbstractStepExecutor#execute - reason=step execution failed, stepId={}, error={}",
                    step.getStepId(), e.getMessage(), e);
            return StepExecutionResult.failure("Step execution failed: " + e.getMessage(), e.toString());
        }
    }

    /**
     * 具体执行逻辑，由子类实现
     */
    protected abstract StepExecutionResult doExecute(ExecutionStep step, StepExecutionContext context);
}
