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
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * SQL-based options source configuration.
 * <p>
 * Defines how to execute a SQL query to fetch parameter options.
 * The SQL query should return rows with at least two columns:
 * one for the display label and one for the actual value.
 * <p>
 * Example usage:
 * <pre>
 * SqlSourceConfig config = new SqlSourceConfig();
 * config.setSql("SELECT name, id FROM departments WHERE status = 'ACTIVE'");
 * config.setLabelColumn("name");
 * config.setValueColumn("id");
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SqlSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The SQL query to execute. Must be a SELECT statement.
     */
    private String sql;

    /**
     * The column name containing the display label.
     */
    private String labelColumn;

    /**
     * The column name containing the actual value.
     */
    private String valueColumn;

    /**
     * Parameter mapping for SQL query parameters.
     * Maps parameter names in SQL to parameter names from context.
     * <p>
     * Example: {"deptId": "departmentId"} maps :deptId in SQL to
     * the "departmentId" value from the context.
     */
    private Map<String, String> paramMapping = new HashMap<>();

    /**
     * Gets the SQL query.
     *
     * @return the SQL query string
     */
    public String getSql() {
        return sql;
    }

    /**
     * Sets the SQL query.
     *
     * @param sql the SQL query string to set
     */
    public void setSql(String sql) {
        this.sql = sql;
    }

    /**
     * Gets the label column name.
     *
     * @return the label column name
     */
    public String getLabelColumn() {
        return labelColumn;
    }

    /**
     * Sets the label column name.
     *
     * @param labelColumn the label column name to set
     */
    public void setLabelColumn(String labelColumn) {
        this.labelColumn = labelColumn;
    }

    /**
     * Gets the value column name.
     *
     * @return the value column name
     */
    public String getValueColumn() {
        return valueColumn;
    }

    /**
     * Sets the value column name.
     *
     * @param valueColumn the value column name to set
     */
    public void setValueColumn(String valueColumn) {
        this.valueColumn = valueColumn;
    }

    /**
     * Gets the parameter mapping.
     *
     * @return the parameter mapping map
     */
    public Map<String, String> getParamMapping() {
        return paramMapping;
    }

    /**
     * Sets the parameter mapping.
     *
     * @param paramMapping the parameter mapping map to set
     */
    public void setParamMapping(Map<String, String> paramMapping) {
        this.paramMapping = paramMapping;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SqlSourceConfig that = (SqlSourceConfig) o;
        return Objects.equals(sql, that.sql) &&
                Objects.equals(labelColumn, that.labelColumn) &&
                Objects.equals(valueColumn, that.valueColumn) &&
                Objects.equals(paramMapping, that.paramMapping);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sql, labelColumn, valueColumn, paramMapping);
    }

    @Override
    public String toString() {
        return "SqlSourceConfig{" +
                "sql='" + sql + '\'' +
                ", labelColumn='" + labelColumn + '\'' +
                ", valueColumn='" + valueColumn + '\'' +
                ", paramMapping=" + paramMapping +
                '}';
    }
}
