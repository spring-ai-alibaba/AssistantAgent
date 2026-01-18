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

import java.util.Objects;

/**
 * Business object representing column metadata information.
 * This class is designed to be compatible with DataAgent's data structures
 * for database column metadata.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ColumnInfoBO {

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
     * Default constructor.
     */
    public ColumnInfoBO() {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ColumnInfoBO that = (ColumnInfoBO) o;
        return primary == that.primary &&
                notnull == that.notnull &&
                Objects.equals(name, that.name) &&
                Objects.equals(tableName, that.tableName) &&
                Objects.equals(description, that.description) &&
                Objects.equals(type, that.type) &&
                Objects.equals(samples, that.samples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, tableName, description, type, primary, notnull, samples);
    }

    @Override
    public String toString() {
        return "ColumnInfoBO{" +
                "name='" + name + '\'' +
                ", tableName='" + tableName + '\'' +
                ", description='" + description + '\'' +
                ", type='" + type + '\'' +
                ", primary=" + primary +
                ", notnull=" + notnull +
                ", samples='" + samples + '\'' +
                '}';
    }

    /**
     * Creates a new builder for constructing ColumnInfoBO instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing ColumnInfoBO instances.
     * Provides a fluent API for setting column properties.
     */
    public static class Builder {
        private String name;
        private String tableName;
        private String description;
        private String type;
        private boolean primary;
        private boolean notnull;
        private String samples;

        /**
         * Sets the column name.
         *
         * @param name the column name
         * @return this builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the table name this column belongs to.
         *
         * @param tableName the table name
         * @return this builder instance
         */
        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * Sets the column description.
         *
         * @param description the description
         * @return this builder instance
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the column data type.
         *
         * @param type the data type
         * @return this builder instance
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets whether this column is a primary key.
         *
         * @param primary true if primary key, false otherwise
         * @return this builder instance
         */
        public Builder primary(boolean primary) {
            this.primary = primary;
            return this;
        }

        /**
         * Sets whether this column has a NOT NULL constraint.
         *
         * @param notnull true if NOT NULL, false otherwise
         * @return this builder instance
         */
        public Builder notnull(boolean notnull) {
            this.notnull = notnull;
            return this;
        }

        /**
         * Sets sample values for this column.
         *
         * @param samples sample values
         * @return this builder instance
         */
        public Builder samples(String samples) {
            this.samples = samples;
            return this;
        }

        /**
         * Builds and returns the configured ColumnInfoBO instance.
         *
         * @return the constructed ColumnInfoBO
         */
        public ColumnInfoBO build() {
            ColumnInfoBO instance = new ColumnInfoBO();
            instance.name = this.name;
            instance.tableName = this.tableName;
            instance.description = this.description;
            instance.type = this.type;
            instance.primary = this.primary;
            instance.notnull = this.notnull;
            instance.samples = this.samples;
            return instance;
        }
    }
}
