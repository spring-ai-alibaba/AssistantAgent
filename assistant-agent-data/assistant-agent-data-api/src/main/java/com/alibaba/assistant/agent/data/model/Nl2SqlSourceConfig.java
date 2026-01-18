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
import java.util.Objects;

/**
 * NL2SQL-based options source configuration.
 * <p>
 * Defines how to use natural language to SQL conversion to fetch parameter options.
 * The description is converted to SQL using NL2SQL capabilities, executed,
 * and results are mapped to options using the specified label and value columns.
 * <p>
 * Example usage:
 * <pre>
 * Nl2SqlSourceConfig config = new Nl2SqlSourceConfig();
 * config.setDescription("Get all active departments with their names and IDs");
 * config.setLabelColumn("name");
 * config.setValueColumn("id");
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class Nl2SqlSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Natural language description of the data to fetch.
     * This description will be converted to SQL using NL2SQL.
     * <p>
     * Example: "Get all active product categories"
     */
    private String description;

    /**
     * The column name containing the display label in the result set.
     */
    private String labelColumn;

    /**
     * The column name containing the actual value in the result set.
     */
    private String valueColumn;

    /**
     * Gets the natural language description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the natural language description.
     *
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Nl2SqlSourceConfig that = (Nl2SqlSourceConfig) o;
        return Objects.equals(description, that.description) &&
                Objects.equals(labelColumn, that.labelColumn) &&
                Objects.equals(valueColumn, that.valueColumn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, labelColumn, valueColumn);
    }

    @Override
    public String toString() {
        return "Nl2SqlSourceConfig{" +
                "description='" + description + '\'' +
                ", labelColumn='" + labelColumn + '\'' +
                ", valueColumn='" + valueColumn + '\'' +
                '}';
    }
}
