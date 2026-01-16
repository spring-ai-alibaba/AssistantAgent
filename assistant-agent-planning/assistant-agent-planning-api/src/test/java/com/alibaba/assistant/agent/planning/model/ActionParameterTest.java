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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ActionParameter 单元测试
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@DisplayName("ActionParameter Tests")
class ActionParameterTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("should create string parameter with validation")
        void shouldCreateStringParameterWithValidation() {
            ActionParameter param = ActionParameter.builder()
                    .name("reason")
                    .label("请假原因")
                    .description("请输入请假原因")
                    .type("string")
                    .required(true)
                    .minLength(10)
                    .maxLength(500)
                    .placeholder("请详细说明请假原因...")
                    .build();

            assertThat(param.getName()).isEqualTo("reason");
            assertThat(param.getLabel()).isEqualTo("请假原因");
            assertThat(param.getType()).isEqualTo("string");
            assertThat(param.getRequired()).isTrue();
            assertThat(param.getMinLength()).isEqualTo(10);
            assertThat(param.getMaxLength()).isEqualTo(500);
        }

        @Test
        @DisplayName("should create enum parameter with values")
        void shouldCreateEnumParameterWithValues() {
            ActionParameter param = ActionParameter.builder()
                    .name("leaveType")
                    .label("请假类型")
                    .type("enum")
                    .required(true)
                    .enumValues(List.of("年假", "病假", "事假", "调休"))
                    .defaultValue("年假")
                    .build();

            assertThat(param.getName()).isEqualTo("leaveType");
            assertThat(param.getType()).isEqualTo("enum");
            assertThat(param.getEnumValues()).containsExactly("年假", "病假", "事假", "调休");
            assertThat(param.getDefaultValue()).isEqualTo("年假");
        }

        @Test
        @DisplayName("should create number parameter with range")
        void shouldCreateNumberParameterWithRange() {
            ActionParameter param = ActionParameter.builder()
                    .name("days")
                    .label("请假天数")
                    .type("number")
                    .required(true)
                    .minValue(0.5)
                    .maxValue(30)
                    .build();

            assertThat(param.getName()).isEqualTo("days");
            assertThat(param.getType()).isEqualTo("number");
            assertThat(param.getMinValue()).isEqualTo(0.5);
            assertThat(param.getMaxValue()).isEqualTo(30);
        }

        @Test
        @DisplayName("should create date parameter with format")
        void shouldCreateDateParameterWithFormat() {
            ActionParameter param = ActionParameter.builder()
                    .name("startDate")
                    .label("开始日期")
                    .type("date")
                    .required(true)
                    .dateFormat("yyyy-MM-dd")
                    .build();

            assertThat(param.getName()).isEqualTo("startDate");
            assertThat(param.getType()).isEqualTo("date");
            assertThat(param.getDateFormat()).isEqualTo("yyyy-MM-dd");
        }

        @Test
        @DisplayName("should use default values for source and required")
        void shouldUseDefaultValues() {
            ActionParameter param = ActionParameter.builder()
                    .name("test")
                    .type("string")
                    .build();

            assertThat(param.getRequired()).isFalse();
            assertThat(param.getSource()).isEqualTo(ParameterSource.USER_INPUT);
        }
    }

    @Nested
    @DisplayName("Parameter Source Tests")
    class ParameterSourceTests {

        @Test
        @DisplayName("should create parameter with PREVIOUS_STEP source")
        void shouldCreateParameterWithPreviousStepSource() {
            ActionParameter param = ActionParameter.builder()
                    .name("approverId")
                    .type("string")
                    .source(ParameterSource.PREVIOUS_STEP)
                    .sourceRef("query-approver")
                    .expression("$.approverInfo.approverId")
                    .build();

            assertThat(param.getSource()).isEqualTo(ParameterSource.PREVIOUS_STEP);
            assertThat(param.getSourceRef()).isEqualTo("query-approver");
            assertThat(param.getExpression()).isEqualTo("$.approverInfo.approverId");
        }

        @Test
        @DisplayName("should create parameter with CONTEXT source")
        void shouldCreateParameterWithContextSource() {
            ActionParameter param = ActionParameter.builder()
                    .name("userId")
                    .type("string")
                    .source(ParameterSource.CONTEXT)
                    .sourceRef("userId")
                    .build();

            assertThat(param.getSource()).isEqualTo(ParameterSource.CONTEXT);
            assertThat(param.getSourceRef()).isEqualTo("userId");
        }
    }

    @Nested
    @DisplayName("ForeignKeyRef Tests")
    class ForeignKeyRefTests {

        @Test
        @DisplayName("should create parameter with foreign key reference")
        void shouldCreateParameterWithForeignKeyReference() {
            ActionParameter.ForeignKeyRef fkRef = ActionParameter.ForeignKeyRef.builder()
                    .entity("brand")
                    .field("brand_id")
                    .displayField("brand_name")
                    .validationStepId("check-brand")
                    .build();

            ActionParameter param = ActionParameter.builder()
                    .name("brandId")
                    .type("string")
                    .required(true)
                    .foreignKey(fkRef)
                    .build();

            assertThat(param.getForeignKey()).isNotNull();
            assertThat(param.getForeignKey().getEntity()).isEqualTo("brand");
            assertThat(param.getForeignKey().getField()).isEqualTo("brand_id");
            assertThat(param.getForeignKey().getDisplayField()).isEqualTo("brand_name");
            assertThat(param.getForeignKey().getValidationStepId()).isEqualTo("check-brand");
        }
    }

    @Nested
    @DisplayName("Nested Object Parameter Tests")
    class NestedObjectParameterTests {

        @Test
        @DisplayName("should create object parameter with nested properties")
        void shouldCreateObjectParameterWithNestedProperties() {
            ActionParameter nestedProp1 = ActionParameter.builder()
                    .name("street")
                    .type("string")
                    .required(true)
                    .build();

            ActionParameter nestedProp2 = ActionParameter.builder()
                    .name("city")
                    .type("string")
                    .required(true)
                    .build();

            ActionParameter param = ActionParameter.builder()
                    .name("address")
                    .type("object")
                    .required(true)
                    .properties(List.of(nestedProp1, nestedProp2))
                    .build();

            assertThat(param.getType()).isEqualTo("object");
            assertThat(param.getProperties()).hasSize(2);
            assertThat(param.getProperties().get(0).getName()).isEqualTo("street");
            assertThat(param.getProperties().get(1).getName()).isEqualTo("city");
        }

        @Test
        @DisplayName("should create array parameter with item type")
        void shouldCreateArrayParameterWithItemType() {
            ActionParameter param = ActionParameter.builder()
                    .name("attachments")
                    .type("array")
                    .itemType("string")
                    .build();

            assertThat(param.getType()).isEqualTo("array");
            assertThat(param.getItemType()).isEqualTo("string");
        }
    }

    @Nested
    @DisplayName("Pattern Validation Tests")
    class PatternValidationTests {

        @Test
        @DisplayName("should create parameter with regex pattern")
        void shouldCreateParameterWithRegexPattern() {
            ActionParameter param = ActionParameter.builder()
                    .name("email")
                    .type("string")
                    .required(true)
                    .pattern("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
                    .build();

            assertThat(param.getPattern()).isNotNull();
            assertThat(param.getPattern()).contains("@");
        }

        @Test
        @DisplayName("should create phone number parameter with pattern")
        void shouldCreatePhoneNumberParameterWithPattern() {
            ActionParameter param = ActionParameter.builder()
                    .name("phone")
                    .type("string")
                    .required(true)
                    .pattern("^1[3-9]\\d{9}$")
                    .placeholder("请输入11位手机号")
                    .build();

            assertThat(param.getPattern()).isEqualTo("^1[3-9]\\d{9}$");
            assertThat(param.getPlaceholder()).isEqualTo("请输入11位手机号");
        }
    }
}
