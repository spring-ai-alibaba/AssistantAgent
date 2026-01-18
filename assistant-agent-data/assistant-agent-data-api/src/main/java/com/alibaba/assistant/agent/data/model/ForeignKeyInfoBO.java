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
 * Business object representing foreign key constraint metadata information.
 * This class is designed to be compatible with DataAgent's data structures
 * for foreign key relationships between database tables.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ForeignKeyInfoBO {

    /**
     * The name of the foreign key constraint.
     */
    private String constraintName;

    /**
     * The name of the table containing the foreign key.
     */
    private String tableName;

    /**
     * The name of the column in the foreign key table.
     */
    private String columnName;

    /**
     * The name of the referenced (parent) table.
     */
    private String referencedTableName;

    /**
     * The name of the referenced column in the parent table.
     */
    private String referencedColumnName;

    /**
     * Default constructor.
     */
    public ForeignKeyInfoBO() {
    }

    /**
     * Gets the foreign key constraint name.
     *
     * @return the constraint name
     */
    public String getConstraintName() {
        return constraintName;
    }

    /**
     * Sets the foreign key constraint name.
     *
     * @param constraintName the constraint name to set
     */
    public void setConstraintName(String constraintName) {
        this.constraintName = constraintName;
    }

    /**
     * Gets the table name containing the foreign key.
     *
     * @return the table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Sets the table name containing the foreign key.
     *
     * @param tableName the table name to set
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * Gets the column name in the foreign key table.
     *
     * @return the column name
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * Sets the column name in the foreign key table.
     *
     * @param columnName the column name to set
     */
    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    /**
     * Gets the referenced (parent) table name.
     *
     * @return the referenced table name
     */
    public String getReferencedTableName() {
        return referencedTableName;
    }

    /**
     * Sets the referenced (parent) table name.
     *
     * @param referencedTableName the referenced table name to set
     */
    public void setReferencedTableName(String referencedTableName) {
        this.referencedTableName = referencedTableName;
    }

    /**
     * Gets the referenced column name in the parent table.
     *
     * @return the referenced column name
     */
    public String getReferencedColumnName() {
        return referencedColumnName;
    }

    /**
     * Sets the referenced column name in the parent table.
     *
     * @param referencedColumnName the referenced column name to set
     */
    public void setReferencedColumnName(String referencedColumnName) {
        this.referencedColumnName = referencedColumnName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ForeignKeyInfoBO that = (ForeignKeyInfoBO) o;
        return Objects.equals(constraintName, that.constraintName) &&
                Objects.equals(tableName, that.tableName) &&
                Objects.equals(columnName, that.columnName) &&
                Objects.equals(referencedTableName, that.referencedTableName) &&
                Objects.equals(referencedColumnName, that.referencedColumnName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(constraintName, tableName, columnName, referencedTableName, referencedColumnName);
    }

    @Override
    public String toString() {
        return "ForeignKeyInfoBO{" +
                "constraintName='" + constraintName + '\'' +
                ", tableName='" + tableName + '\'' +
                ", columnName='" + columnName + '\'' +
                ", referencedTableName='" + referencedTableName + '\'' +
                ", referencedColumnName='" + referencedColumnName + '\'' +
                '}';
    }

    /**
     * Creates a new builder for constructing ForeignKeyInfoBO instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing ForeignKeyInfoBO instances.
     * Provides a fluent API for setting foreign key properties.
     */
    public static class Builder {
        private final ForeignKeyInfoBO instance = new ForeignKeyInfoBO();

        /**
         * Sets the foreign key constraint name.
         *
         * @param constraintName the constraint name
         * @return this builder instance
         */
        public Builder constraintName(String constraintName) {
            instance.constraintName = constraintName;
            return this;
        }

        /**
         * Sets the table name containing the foreign key.
         *
         * @param tableName the table name
         * @return this builder instance
         */
        public Builder tableName(String tableName) {
            instance.tableName = tableName;
            return this;
        }

        /**
         * Sets the column name in the foreign key table.
         *
         * @param columnName the column name
         * @return this builder instance
         */
        public Builder columnName(String columnName) {
            instance.columnName = columnName;
            return this;
        }

        /**
         * Sets the referenced (parent) table name.
         *
         * @param referencedTableName the referenced table name
         * @return this builder instance
         */
        public Builder referencedTableName(String referencedTableName) {
            instance.referencedTableName = referencedTableName;
            return this;
        }

        /**
         * Sets the referenced column name in the parent table.
         *
         * @param referencedColumnName the referenced column name
         * @return this builder instance
         */
        public Builder referencedColumnName(String referencedColumnName) {
            instance.referencedColumnName = referencedColumnName;
            return this;
        }

        /**
         * Builds and returns the configured ForeignKeyInfoBO instance.
         *
         * @return the constructed ForeignKeyInfoBO
         */
        public ForeignKeyInfoBO build() {
            return instance;
        }
    }
}
