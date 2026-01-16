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
import com.alibaba.assistant.agent.planning.spi.PlanGenerator.PlanGenerationContext;
import com.alibaba.assistant.agent.planning.spi.PlanGenerator.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试用 PlanGenerationContext 实现
 */
class TestPlanGenerationContext implements PlanGenerationContext {
    private String sessionId;
    private String userId;
    private String userInput;
    private Map<String, Object> contextVariables;
    private Integer timeoutMinutes;

    public TestPlanGenerationContext(String sessionId, String userInput) {
        this.sessionId = sessionId;
        this.userInput = userInput;
        this.contextVariables = new HashMap<>();
    }

    @Override
    public String getSessionId() { return sessionId; }

    @Override
    public String getUserId() { return userId; }

    @Override
    public String getUserInput() { return userInput; }

    @Override
    public Map<String, Object> getContextVariables() { return contextVariables; }

    @Override
    public Integer getTimeoutMinutes() { return timeoutMinutes; }

    public void setTimeoutMinutes(Integer timeout) { this.timeoutMinutes = timeout; }
}

/**
 * DefaultPlanGenerator 单元测试
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@DisplayName("DefaultPlanGenerator Tests")
class DefaultPlanGeneratorTest {

    private DefaultPlanGenerator planGenerator;

    @BeforeEach
    void setUp() {
        planGenerator = new DefaultPlanGenerator();
    }

    @Nested
    @DisplayName("Generate Plan Tests")
    class GeneratePlanTests {

        @Test
        @DisplayName("should generate plan for single step action")
        void shouldGeneratePlanForSingleStepAction() {
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("query-stock")
                    .actionName("查询库存")
                    .actionType(ActionType.API_CALL)
                    .build();

            Map<String, Object> params = new HashMap<>();
            params.put("productId", "P12345");

            PlanGenerationContext context = new TestPlanGenerationContext("session-001", "查询产品P12345的库存");

            ExecutionPlan plan = planGenerator.generate(action, params, context);

            assertThat(plan).isNotNull();
            assertThat(plan.getPlanId()).isNotBlank();
            assertThat(plan.getActionId()).isEqualTo("query-stock");
            assertThat(plan.getSessionId()).isEqualTo("session-001");
            assertThat(plan.getStatus()).isEqualTo(PlanStatus.PENDING);
            assertThat(plan.getSteps()).hasSize(1);
            assertThat(plan.getSteps().get(0).getType()).isEqualTo(StepType.API_CALL);
        }

        @Test
        @DisplayName("should generate plan for multi-step action")
        void shouldGeneratePlanForMultiStepAction() {
            StepDefinition step1 = StepDefinition.builder()
                    .stepId("validate")
                    .name("校验请假信息")
                    .type(StepType.VALIDATION)
                    .build();

            StepDefinition step2 = StepDefinition.builder()
                    .stepId("query-approver")
                    .name("查询审批人")
                    .type(StepType.API_CALL)
                    .build();

            StepDefinition step3 = StepDefinition.builder()
                    .stepId("submit")
                    .name("提交申请")
                    .type(StepType.EXECUTE)
                    .build();

            ActionDefinition action = ActionDefinition.builder()
                    .actionId("leave-apply")
                    .actionName("请假申请")
                    .actionType(ActionType.MULTI_STEP)
                    .steps(List.of(step1, step2, step3))
                    .timeoutMinutes(60)
                    .build();

            ExecutionPlan plan = planGenerator.generate(action, null, null);

            assertThat(plan.getSteps()).hasSize(3);
            assertThat(plan.getSteps().get(0).getStepId()).isEqualTo("validate");
            assertThat(plan.getSteps().get(1).getStepId()).isEqualTo("query-approver");
            assertThat(plan.getSteps().get(2).getStepId()).isEqualTo("submit");
            assertThat(plan.getExpireAt()).isNotNull();
        }

        @Test
        @DisplayName("should set expire time based on action timeout")
        void shouldSetExpireTimeBasedOnActionTimeout() {
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("test")
                    .actionName("Test")
                    .timeoutMinutes(120)
                    .build();

            ExecutionPlan plan = planGenerator.generate(action, null, null);

            assertThat(plan.getExpireAt()).isNotNull();
            assertThat(plan.getCreatedAt()).isNotNull();
            // expireAt should be approximately 120 minutes after createdAt
        }

        @Test
        @DisplayName("should resolve USER_INPUT parameters")
        void shouldResolveUserInputParameters() {
            ActionParameter param = ActionParameter.builder()
                    .name("leaveType")
                    .type("enum")
                    .source(ParameterSource.USER_INPUT)
                    .build();

            StepDefinition step = StepDefinition.builder()
                    .stepId("submit")
                    .name("提交")
                    .type(StepType.EXECUTE)
                    .inputParams(List.of(param))
                    .build();

            ActionDefinition action = ActionDefinition.builder()
                    .actionId("leave-apply")
                    .actionName("请假申请")
                    .actionType(ActionType.MULTI_STEP)
                    .steps(List.of(step))
                    .build();

            Map<String, Object> params = new HashMap<>();
            params.put("leaveType", "年假");

            ExecutionPlan plan = planGenerator.generate(action, params, null);

            assertThat(plan.getSteps().get(0).getInputValues())
                    .containsEntry("leaveType", "年假");
        }

        @Test
        @DisplayName("should handle null context gracefully")
        void shouldHandleNullContextGracefully() {
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("test")
                    .actionName("Test")
                    .build();

            ExecutionPlan plan = planGenerator.generate(action, null, null);

            assertThat(plan).isNotNull();
            assertThat(plan.getSessionId()).isNull();
            assertThat(plan.getUserInput()).isNull();
        }
    }

    @Nested
    @DisplayName("Validate Plan Tests")
    class ValidatePlanTests {

        @Test
        @DisplayName("should return failure when plan is null")
        void shouldReturnFailureWhenPlanIsNull() {
            ValidationResult result = planGenerator.validate(null);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("null");
        }

        @Test
        @DisplayName("should return failure when action ID is missing")
        void shouldReturnFailureWhenActionIdIsMissing() {
            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .steps(List.of(ExecutionStep.builder()
                            .stepId("step-1")
                            .type(StepType.EXECUTE)
                            .build()))
                    .build();

            ValidationResult result = planGenerator.validate(plan);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("Action ID");
        }

        @Test
        @DisplayName("should return failure when steps are empty")
        void shouldReturnFailureWhenStepsAreEmpty() {
            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .steps(List.of())
                    .build();

            ValidationResult result = planGenerator.validate(plan);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("at least one step");
        }

        @Test
        @DisplayName("should return failure when step type is null")
        void shouldReturnFailureWhenStepTypeIsNull() {
            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .steps(List.of(ExecutionStep.builder()
                            .stepId("step-1")
                            .build()))
                    .build();

            ValidationResult result = planGenerator.validate(plan);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getFieldErrors()).containsKey("steps[0].type");
        }

        @Test
        @DisplayName("should return success for valid plan")
        void shouldReturnSuccessForValidPlan() {
            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .steps(List.of(ExecutionStep.builder()
                            .stepId("step-1")
                            .type(StepType.EXECUTE)
                            .build()))
                    .build();

            ValidationResult result = planGenerator.validate(plan);

            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Optimize Plan Tests")
    class OptimizePlanTests {

        @Test
        @DisplayName("should return same plan (no optimization by default)")
        void shouldReturnSamePlan() {
            ExecutionPlan plan = ExecutionPlan.builder()
                    .planId("plan-001")
                    .actionId("test-action")
                    .build();

            ExecutionPlan optimized = planGenerator.optimize(plan);

            assertThat(optimized).isSameAs(plan);
        }
    }

    @Nested
    @DisplayName("ActionType to StepType Mapping Tests")
    class ActionTypeToStepTypeMappingTests {

        @Test
        @DisplayName("should map API_CALL to StepType.API_CALL")
        void shouldMapApiCallToStepTypeApiCall() {
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("test")
                    .actionName("Test")
                    .actionType(ActionType.API_CALL)
                    .build();

            ExecutionPlan plan = planGenerator.generate(action, null, null);

            assertThat(plan.getSteps().get(0).getType()).isEqualTo(StepType.API_CALL);
        }

        @Test
        @DisplayName("should map INTERNAL_SERVICE to StepType.INTERNAL_SERVICE")
        void shouldMapInternalServiceToStepTypeInternalService() {
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("test")
                    .actionName("Test")
                    .actionType(ActionType.INTERNAL_SERVICE)
                    .build();

            ExecutionPlan plan = planGenerator.generate(action, null, null);

            assertThat(plan.getSteps().get(0).getType()).isEqualTo(StepType.INTERNAL_SERVICE);
        }

        @Test
        @DisplayName("should map null actionType to StepType.EXECUTE")
        void shouldMapNullActionTypeToStepTypeExecute() {
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("test")
                    .actionName("Test")
                    .build();

            ExecutionPlan plan = planGenerator.generate(action, null, null);

            assertThat(plan.getSteps().get(0).getType()).isEqualTo(StepType.EXECUTE);
        }
    }
}
