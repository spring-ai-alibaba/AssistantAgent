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

import java.util.List;

/**
 * 动作参数定义
 *
 * <p>定义动作所需的参数，包括类型、校验规则、来源等。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionParameter {

    /**
     * 参数名称
     */
    private String name;

    /**
     * 显示标签
     */
    private String label;

    /**
     * 参数描述
     */
    private String description;

    /**
     * 参数类型（string, number, boolean, enum, array, object, date, datetime）
     */
    private String type;

    /**
     * 是否必填
     */
    @Builder.Default
    private Boolean required = false;

    /**
     * 枚举值列表（当 type 为 enum 时）
     */
    private List<String> enumValues;

    /**
     * 最小长度（字符串类型）
     */
    private Integer minLength;

    /**
     * 最大长度（字符串类型）
     */
    private Integer maxLength;

    /**
     * 最小值（数值类型）
     */
    private Number minValue;

    /**
     * 最大值（数值类型）
     */
    private Number maxValue;

    /**
     * 正则表达式校验模式
     */
    private String pattern;

    /**
     * 日期格式（date/datetime 类型）
     */
    private String dateFormat;

    /**
     * 默认值
     */
    private Object defaultValue;

    /**
     * 占位符文本
     */
    private String placeholder;

    /**
     * 参数来源类型
     */
    @Builder.Default
    private ParameterSource source = ParameterSource.USER_INPUT;

    /**
     * 来源引用（如前序步骤 ID）
     */
    private String sourceRef;

    /**
     * 值提取表达式（如 JSONPath: $.brandInfo.brandId）
     */
    private String expression;

    /**
     * 外键引用（用于外键校验）
     */
    private ForeignKeyRef foreignKey;

    /**
     * 数组元素类型（当 type 为 array 时）
     */
    private String itemType;

    /**
     * 对象属性定义（当 type 为 object 时）
     */
    private List<ActionParameter> properties;

    /**
     * 外键引用定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForeignKeyRef {
        /**
         * 引用的实体/表名
         */
        private String entity;

        /**
         * 引用的字段名
         */
        private String field;

        /**
         * 显示字段名（用于用户展示）
         */
        private String displayField;

        /**
         * 校验步骤 ID（用于指定由哪个步骤校验）
         */
        private String validationStepId;
    }
}
