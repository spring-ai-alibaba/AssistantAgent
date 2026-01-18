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
 * Represents metadata information about a database column.
 * This class captures the structural and semantic details of a column,
 * including its name, type, constraints, and sample values.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ColumnInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The name of the column.
     */
    private String name;

    /**
     * The name of the table this column belongs to.
     */
    private String tableName;

    /**
     * A human-readable description of the column's purpose or content.
     */
    private String description;

    /**
     * The data type of the column (e.g., VARCHAR, INTEGER, TIMESTAMP).
     */
    private String type;

    /**
     * Indicates whether this column is part of the primary key.
     */
    private boolean primary;

    /**
     * Indicates whether this column has a NOT NULL constraint.
     */
    private boolean notnull;

    /**
     * Sample values from this column, useful for understanding data patterns.
     * Multiple samples may be comma-separated.
     */
    private String samples;

    /**
     * The default value for this column if specified in the schema.
     */
    private String defaultValue;

    /**
     * Default constructor.
     */
    public ColumnInfo() {
    }

    /**
     * Constructor with name and type.
     *
     * @param name the column name
     * @param type the column data type
     */
    public ColumnInfo(String name, String type) {
        this.name = name;
        this.type = type;
    }

    /**
     * Gets the name of the column.
     *
     * @return the column name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the column.
     *
     * @param name the column name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the name of the table this column belongs to.
     *
     * @return the table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Sets the name of the table this column belongs to.
     *
     * @param tableName the table name to set
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * Gets the description of the column.
     *
     * @return the column description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the column.
     *
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the data type of the column.
     *
     * @return the column data type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the data type of the column.
     *
     * @param type the data type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Checks if this column is part of the primary key.
     *
     * @return true if this is a primary key column, false otherwise
     */
    public boolean isPrimary() {
        return primary;
    }

    /**
     * Sets whether this column is part of the primary key.
     *
     * @param primary true if this is a primary key column, false otherwise
     */
    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    /**
     * Checks if this column has a NOT NULL constraint.
     *
     * @return true if the column is NOT NULL, false otherwise
     */
    public boolean isNotnull() {
        return notnull;
    }

    /**
     * Sets whether this column has a NOT NULL constraint.
     *
     * @param notnull true if the column is NOT NULL, false otherwise
     */
    public void setNotnull(boolean notnull) {
        this.notnull = notnull;
    }

    /**
     * Gets sample values from this column.
     *
     * @return sample values as a string
     */
    public String getSamples() {
        return samples;
    }

    /**
     * Sets sample values for this column.
     *
     * @param samples sample values to set
     */
    public void setSamples(String samples) {
        this.samples = samples;
    }

    /**
     * Gets the default value for this column.
     *
     * @return the default value
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default value for this column.
     *
     * @param defaultValue the default value to set
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ColumnInfo that = (ColumnInfo) o;
        return primary == that.primary &&
                notnull == that.notnull &&
                Objects.equals(name, that.name) &&
                Objects.equals(tableName, that.tableName) &&
                Objects.equals(description, that.description) &&
                Objects.equals(type, that.type) &&
                Objects.equals(samples, that.samples) &&
                Objects.equals(defaultValue, that.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, tableName, description, type, primary, notnull, samples, defaultValue);
    }

    @Override
    public String toString() {
        return "ColumnInfo{" +
                "name='" + name + '\'' +
                ", tableName='" + tableName + '\'' +
                ", description='" + description + '\'' +
                ", type='" + type + '\'' +
                ", primary=" + primary +
                ", notnull=" + notnull +
                ", samples='" + samples + '\'' +
                ", defaultValue='" + defaultValue + '\'' +
                '}';
    }

    /**
     * Creates a new builder for constructing ColumnInfo instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing ColumnInfo instances.
     * Provides a fluent API for setting column properties.
     */
    public static class Builder {
        private final ColumnInfo instance = new ColumnInfo();

        /**
         * Sets the column name.
         *
         * @param name the column name
         * @return this builder instance
         */
        public Builder name(String name) {
            instance.name = name;
            return this;
        }

        /**
         * Sets the table name this column belongs to.
         *
         * @param tableName the table name
         * @return this builder instance
         */
        public Builder tableName(String tableName) {
            instance.tableName = tableName;
            return this;
        }

        /**
         * Sets the column description.
         *
         * @param description the description
         * @return this builder instance
         */
        public Builder description(String description) {
            instance.description = description;
            return this;
        }

        /**
         * Sets the column data type.
         *
         * @param type the data type
         * @return this builder instance
         */
        public Builder type(String type) {
            instance.type = type;
            return this;
        }

        /**
         * Sets whether this column is a primary key.
         *
         * @param primary true if primary key, false otherwise
         * @return this builder instance
         */
        public Builder primary(boolean primary) {
            instance.primary = primary;
            return this;
        }

        /**
         * Sets whether this column has a NOT NULL constraint.
         *
         * @param notnull true if NOT NULL, false otherwise
         * @return this builder instance
         */
        public Builder notnull(boolean notnull) {
            instance.notnull = notnull;
            return this;
        }

        /**
         * Sets sample values for this column.
         *
         * @param samples sample values
         * @return this builder instance
         */
        public Builder samples(String samples) {
            instance.samples = samples;
            return this;
        }

        /**
         * Sets the default value for this column.
         *
         * @param defaultValue the default value
         * @return this builder instance
         */
        public Builder defaultValue(String defaultValue) {
            instance.defaultValue = defaultValue;
            return this;
        }

        /**
         * Builds and returns the configured ColumnInfo instance.
         *
         * @return the constructed ColumnInfo
         */
        public ColumnInfo build() {
            return instance;
        }
    }
}
