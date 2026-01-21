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
package com.alibaba.assistant.agent.planning.permission.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Data filter condition for permission-based data filtering.
 * <p>
 * Represents a single filter condition that will be applied when querying data.
 * Multiple filters can be combined to create complex permission rules.
 * <p>
 * Example:
 * <pre>
 * DataFilter filter = new DataFilter("departmentId", "eq", "tech-001");
 * // Results in: WHERE departmentId = 'tech-001'
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class DataFilter implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The field name to filter on.
     * <p>
     * Example: "departmentId", "userId", "bureauId"
     */
    private String field;

    /**
     * The filter operator.
     * <p>
     * Supported operators:
     * <ul>
     *   <li>eq - equals</li>
     *   <li>ne - not equals</li>
     *   <li>in - in list</li>
     *   <li>not_in - not in list</li>
     *   <li>like - contains</li>
     *   <li>between - between range</li>
     *   <li>gt - greater than</li>
     *   <li>gte - greater than or equal</li>
     *   <li>lt - less than</li>
     *   <li>lte - less than or equal</li>
     * </ul>
     */
    private String operator;

    /**
     * The filter value.
     * <p>
     * Can be a single value or a collection for 'in' operators.
     */
    private Object value;

    public DataFilter() {
    }

    public DataFilter(String field, String operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    /**
     * Create an equals filter.
     */
    public static DataFilter eq(String field, Object value) {
        return new DataFilter(field, "eq", value);
    }

    /**
     * Create a not equals filter.
     */
    public static DataFilter ne(String field, Object value) {
        return new DataFilter(field, "ne", value);
    }

    /**
     * Create an 'in' filter.
     */
    public static DataFilter in(String field, Object... values) {
        return new DataFilter(field, "in", values);
    }

    /**
     * Create a 'like' filter.
     */
    public static DataFilter like(String field, String value) {
        return new DataFilter(field, "like", value);
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFilter that = (DataFilter) o;
        return Objects.equals(field, that.field) &&
                Objects.equals(operator, that.operator) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, operator, value);
    }

    @Override
    public String toString() {
        return "DataFilter{" +
                "field='" + field + '\'' +
                ", operator='" + operator + '\'' +
                ", value=" + value +
                '}';
    }
}
