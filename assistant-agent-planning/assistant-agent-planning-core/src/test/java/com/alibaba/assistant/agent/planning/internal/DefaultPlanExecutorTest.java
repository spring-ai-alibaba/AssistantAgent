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

import com.alibaba.assistant.agent.planning.model.*;
import com.alibaba.assistant.agent.planning.spi.StepExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DefaultPlanExecutor 单元测试
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultPlanExecutor Tests")
class DefaultPlanExecutorTest {

    @Mock
    private StepExecutor mockExecutor;

    private StepExecutorRegistry registry;
    private DefaultPlanExecutor planExecutor;

    @BeforeEach
    void setUp() {
        registry = new StepExecutorRegistry();
        planExecutor = new DefaultPlanExecutor(registry);
    }

    @Nested
    @DisplayName("Execute Plan Tests")
    class ExecutePlanTests {

        @Test
        @DisplayName("should return failure when plan is null")
        void shouldReturnFailureWhenPlanIsNull() {
            PlanExecutionResult result = planExecutor.execute(null);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("null");
        }

        @Test
        @DisplayName("should complete plan with no steps")
        void shouldCompletePlanWithNoSteps() {
            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .steps(List.of())
                    .build();

            PlanExecutionResult result = planExecutor.execute(plan);

            assertThat(result.isSuccess()).isTrue();
            assertThat(plan.getStatus()).isEqualTo(PlanStatus.COMPLETED);
        }

        @Test
        @DisplayName("should execute single step successfully")
        void shouldExecuteSingleStepSuccessfully() {
            when(mockExecutor.getSupportedType()).thenReturn(StepType.EXECUTE);
            when(mockExecutor.execute(any(), any())).thenReturn(
                    StepExecutionResult.success(Map.of("result", "OK")));
            registry.register(mockExecutor);

            ExecutionStep step = ExecutionStep.builder()
                    .stepInstanceId("step-inst-1")
                    .stepId("step-1")
                    .name("Execute Step")
                    .type(StepType.EXECUTE)
                    .build();

            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .steps(List.of(step))
                    .build();

            PlanExecutionResult result = planExecutor.execute(plan);

            assertThat(result.isSuccess()).isTrue();
            assertThat(plan.getStatus()).isEqualTo(PlanStatus.COMPLETED);
            verify(mockExecutor, times(1)).execute(any(), any());
        }

        @Test
        @DisplayName("should execute multiple steps in order")
        void shouldExecuteMultipleStepsInOrder() {
            when(mockExecutor.getSupportedType()).thenReturn(StepType.EXECUTE);
            when(mockExecutor.execute(any(), any())).thenReturn(
                    StepExecutionResult.success(Map.of("result", "OK")));
            registry.register(mockExecutor);

            ExecutionStep step1 = ExecutionStep.builder()
                    .stepInstanceId("step-inst-1")
                    .stepId("step-1")
                    .type(StepType.EXECUTE)
                    .build();

            ExecutionStep step2 = ExecutionStep.builder()
                    .stepInstanceId("step-inst-2")
                    .stepId("step-2")
                    .type(StepType.EXECUTE)
                    .build();

            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .steps(List.of(step1, step2))
                    .build();

            PlanExecutionResult result = planExecutor.execute(plan);

            assertThat(result.isSuccess()).isTrue();
            verify(mockExecutor, times(2)).execute(any(), any());
        }

        @Test
        @DisplayName("should fail when no executor found for step type")
        void shouldFailWhenNoExecutorFoundForStepType() {
            ExecutionStep step = ExecutionStep.builder()
                    .stepInstanceId("step-inst-1")
                    .stepId("step-1")
                    .type(StepType.API_CALL)
                    .build();

            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .steps(List.of(step))
                    .build();

            PlanExecutionResult result = planExecutor.execute(plan);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("No executor found");
            assertThat(plan.getStatus()).isEqualTo(PlanStatus.FAILED);
        }

        @Test
        @DisplayName("should fail when step execution fails")
        void shouldFailWhenStepExecutionFails() {
            when(mockExecutor.getSupportedType()).thenReturn(StepType.EXECUTE);
            when(mockExecutor.execute(any(), any())).thenReturn(
                    StepExecutionResult.failure("Step failed", "Detailed error"));
            registry.register(mockExecutor);

            ExecutionStep step = ExecutionStep.builder()
                    .stepInstanceId("step-inst-1")
                    .stepId("step-1")
                    .type(StepType.EXECUTE)
                    .build();

            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .steps(List.of(step))
                    .build();

            PlanExecutionResult result = planExecutor.execute(plan);

            assertThat(result.isSuccess()).isFalse();
            assertThat(plan.getStatus()).isEqualTo(PlanStatus.FAILED);
        }

        @Test
        @DisplayName("should wait for user input when step requires it")
        void shouldWaitForUserInputWhenStepRequiresIt() {
            when(mockExecutor.getSupportedType()).thenReturn(StepType.QUERY);
            when(mockExecutor.execute(any(), any())).thenReturn(
                    StepExecutionResult.waitingInput("请选择", List.of("选项A", "选项B")));
            registry.register(mockExecutor);

            ExecutionStep step = ExecutionStep.builder()
                    .stepInstanceId("step-inst-1")
                    .stepId("step-1")
                    .type(StepType.QUERY)
                    .build();

            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .steps(List.of(step))
                    .build();

            PlanExecutionResult result = planExecutor.execute(plan);

            assertThat(result.isNeedsUserInput()).isTrue();
            assertThat(result.getUserPrompt()).isEqualTo("请选择");
            assertThat(result.getOptions()).isEqualTo(List.of("选项A", "选项B"));
            assertThat(plan.getStatus()).isEqualTo(PlanStatus.WAITING_INPUT);
        }
    }

    @Nested
    @DisplayName("Resume Plan Tests")
    class ResumePlanTests {

        @Test
        @DisplayName("should return failure when plan is null")
        void shouldReturnFailureWhenPlanIsNull() {
            PlanExecutionResult result = planExecutor.resume(null, null);

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("should resume and complete after user input")
        void shouldResumeAndCompleteAfterUserInput() {
            when(mockExecutor.getSupportedType()).thenReturn(StepType.QUERY);
            // Resume 会重新执行当前步骤，所以这里直接返回成功
            when(mockExecutor.execute(any(), any()))
                    .thenReturn(StepExecutionResult.success(Map.of("selection", "A")));
            registry.register(mockExecutor);

            ExecutionStep step = ExecutionStep.builder()
                    .stepInstanceId("step-inst-1")
                    .stepId("step-1")
                    .type(StepType.QUERY)
                    .status(ExecutionStep.StepStatus.WAITING_INPUT)
                    .build();

            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .status(PlanStatus.WAITING_INPUT)
                    .currentStepIndex(0)
                    .steps(List.of(step))
                    .build();

            Map<String, Object> userInput = new HashMap<>();
            userInput.put("selection", "A");

            PlanExecutionResult result = planExecutor.resume(plan, userInput);

            assertThat(result.isSuccess()).isTrue();
            assertThat(plan.getStatus()).isEqualTo(PlanStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Cancel Plan Tests")
    class CancelPlanTests {

        @Test
        @DisplayName("should cancel existing plan")
        void shouldCancelExistingPlan() {
            when(mockExecutor.getSupportedType()).thenReturn(StepType.QUERY);
            when(mockExecutor.execute(any(), any())).thenReturn(
                    StepExecutionResult.waitingInput("请选择", List.of("A")));
            registry.register(mockExecutor);

            ExecutionStep step = ExecutionStep.builder()
                    .stepInstanceId("step-inst-1")
                    .stepId("step-1")
                    .type(StepType.QUERY)
                    .build();

            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .steps(List.of(step))
                    .build();

            planExecutor.execute(plan);

            boolean cancelled = planExecutor.cancel("plan-001");

            assertThat(cancelled).isTrue();
            assertThat(plan.getStatus()).isEqualTo(PlanStatus.CANCELLED);
        }

        @Test
        @DisplayName("should return false when cancelling non-existent plan")
        void shouldReturnFalseWhenCancellingNonExistentPlan() {
            boolean cancelled = planExecutor.cancel("non-existent");

            assertThat(cancelled).isFalse();
        }
    }

    @Nested
    @DisplayName("Get Status Tests")
    class GetStatusTests {

        @Test
        @DisplayName("should return null for non-existent plan")
        void shouldReturnNullForNonExistentPlan() {
            ExecutionPlan status = planExecutor.getStatus("non-existent");

            assertThat(status).isNull();
        }

        @Test
        @DisplayName("should return plan status after execution")
        void shouldReturnPlanStatusAfterExecution() {
            when(mockExecutor.getSupportedType()).thenReturn(StepType.EXECUTE);
            when(mockExecutor.execute(any(), any())).thenReturn(
                    StepExecutionResult.success(Map.of("result", "OK")));
            registry.register(mockExecutor);

            ExecutionStep step = ExecutionStep.builder()
                    .stepInstanceId("step-inst-1")
                    .stepId("step-1")
                    .type(StepType.EXECUTE)
                    .build();

            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .steps(List.of(step))
                    .build();

            planExecutor.execute(plan);

            ExecutionPlan status = planExecutor.getStatus("plan-001");

            assertThat(status).isNotNull();
            assertThat(status.getStatus()).isEqualTo(PlanStatus.COMPLETED);
        }
    }
}
