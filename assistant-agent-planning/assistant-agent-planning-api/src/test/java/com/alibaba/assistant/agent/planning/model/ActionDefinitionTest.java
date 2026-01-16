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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ActionDefinition 单元测试
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@DisplayName("ActionDefinition Tests")
class ActionDefinitionTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("should create ActionDefinition with all fields")
        void shouldCreateActionDefinitionWithAllFields() {
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("leave-apply")
                    .actionName("请假申请")
                    .description("员工请假申请流程")
                    .actionType(ActionType.MULTI_STEP)
                    .category("HR")
                    .tags(List.of("请假", "HR", "审批"))
                    .triggerKeywords(List.of("请假", "休假", "年假"))
                    .priority(10)
                    .timeoutMinutes(60)
                    .enabled(true)
                    .build();

            assertThat(action.getActionId()).isEqualTo("leave-apply");
            assertThat(action.getActionName()).isEqualTo("请假申请");
            assertThat(action.getDescription()).isEqualTo("员工请假申请流程");
            assertThat(action.getActionType()).isEqualTo(ActionType.MULTI_STEP);
            assertThat(action.getCategory()).isEqualTo("HR");
            assertThat(action.getTags()).containsExactly("请假", "HR", "审批");
            assertThat(action.getTriggerKeywords()).containsExactly("请假", "休假", "年假");
            assertThat(action.getPriority()).isEqualTo(10);
            assertThat(action.getTimeoutMinutes()).isEqualTo(60);
            assertThat(action.getEnabled()).isTrue();
        }

        @Test
        @DisplayName("should use default values when not specified")
        void shouldUseDefaultValuesWhenNotSpecified() {
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("test-action")
                    .actionName("Test Action")
                    .build();

            assertThat(action.getPriority()).isEqualTo(0);
            assertThat(action.getTimeoutMinutes()).isEqualTo(30);
            assertThat(action.getEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("isMultiStep Tests")
    class IsMultiStepTests {

        @Test
        @DisplayName("should return true when actionType is MULTI_STEP")
        void shouldReturnTrueWhenActionTypeIsMultiStep() {
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("test")
                    .actionType(ActionType.MULTI_STEP)
                    .build();

            assertThat(action.isMultiStep()).isTrue();
        }

        @Test
        @DisplayName("should return true when steps list is not empty")
        void shouldReturnTrueWhenStepsListIsNotEmpty() {
            StepDefinition step = StepDefinition.builder()
                    .stepId("step-1")
                    .name("Step 1")
                    .type(StepType.QUERY)
                    .build();

            ActionDefinition action = ActionDefinition.builder()
                    .actionId("test")
                    .actionType(ActionType.API_CALL)
                    .steps(List.of(step))
                    .build();

            assertThat(action.isMultiStep()).isTrue();
        }

        @Test
        @DisplayName("should return false for single step action")
        void shouldReturnFalseForSingleStepAction() {
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("test")
                    .actionType(ActionType.API_CALL)
                    .build();

            assertThat(action.isMultiStep()).isFalse();
        }
    }

    @Nested
    @DisplayName("getRequiredParameters Tests")
    class GetRequiredParametersTests {

        @Test
        @DisplayName("should return only required parameters")
        void shouldReturnOnlyRequiredParameters() {
            ActionParameter requiredParam = ActionParameter.builder()
                    .name("leaveType")
                    .type("enum")
                    .required(true)
                    .build();

            ActionParameter optionalParam = ActionParameter.builder()
                    .name("reason")
                    .type("string")
                    .required(false)
                    .build();

            ActionDefinition action = ActionDefinition.builder()
                    .actionId("test")
                    .parameters(Arrays.asList(requiredParam, optionalParam))
                    .build();

            List<ActionParameter> required = action.getRequiredParameters();

            assertThat(required).hasSize(1);
            assertThat(required.get(0).getName()).isEqualTo("leaveType");
        }

        @Test
        @DisplayName("should return empty list when no parameters")
        void shouldReturnEmptyListWhenNoParameters() {
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("test")
                    .build();

            assertThat(action.getRequiredParameters()).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when no required parameters")
        void shouldReturnEmptyListWhenNoRequiredParameters() {
            ActionParameter optionalParam = ActionParameter.builder()
                    .name("reason")
                    .type("string")
                    .required(false)
                    .build();

            ActionDefinition action = ActionDefinition.builder()
                    .actionId("test")
                    .parameters(List.of(optionalParam))
                    .build();

            assertThat(action.getRequiredParameters()).isEmpty();
        }
    }
}
