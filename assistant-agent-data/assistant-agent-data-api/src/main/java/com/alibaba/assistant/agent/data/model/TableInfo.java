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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents metadata information about a database table.
 * This class captures the structural and semantic details of a table,
 * including its name, schema, columns, and key constraints.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class TableInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The schema (database) this table belongs to.
     */
    private String schema;

    /**
     * The name of the table.
     */
    private String name;

    /**
     * A human-readable description of the table's purpose or content.
     */
    private String description;

    /**
     * The type of the table (e.g., TABLE, VIEW, SYSTEM TABLE).
     */
    private String type;

    /**
     * Foreign key relationships for this table.
     * Format depends on database-specific metadata.
     */
    private String foreignKey;

    /**
     * List of column names that form the primary key.
     */
    private List<String> primaryKeys;

    /**
     * List of columns contained in this table.
     */
    private List<ColumnInfo> columns;

    /**
     * Default constructor.
     */
    public TableInfo() {
        this.primaryKeys = new ArrayList<>();
        this.columns = new ArrayList<>();
    }

    /**
     * Constructor with table name.
     *
     * @param name the table name
     */
    public TableInfo(String name) {
        this();
        this.name = name;
    }

    /**
     * Gets the schema this table belongs to.
     *
     * @return the schema name
     */
    public String getSchema() {
        return schema;
    }

    /**
     * Sets the schema this table belongs to.
     *
     * @param schema the schema name to set
     */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    /**
     * Gets the name of the table.
     *
     * @return the table name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the table.
     *
     * @param name the table name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the description of the table.
     *
     * @return the table description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the table.
     *
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the type of the table.
     *
     * @return the table type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of the table.
     *
     * @param type the table type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the foreign key relationships for this table.
     *
     * @return the foreign key definition
     */
    public String getForeignKey() {
        return foreignKey;
    }

    /**
     * Sets the foreign key relationships for this table.
     *
     * @param foreignKey the foreign key definition to set
     */
    public void setForeignKey(String foreignKey) {
        this.foreignKey = foreignKey;
    }

    /**
     * Gets the list of primary key column names.
     *
     * @return the list of primary key columns
     */
    public List<String> getPrimaryKeys() {
        return primaryKeys;
    }

    /**
     * Sets the list of primary key column names.
     *
     * @param primaryKeys the list of primary key columns to set
     */
    public void setPrimaryKeys(List<String> primaryKeys) {
        this.primaryKeys = primaryKeys;
    }

    /**
     * Gets the list of columns in this table.
     *
     * @return the list of columns
     */
    public List<ColumnInfo> getColumns() {
        return columns;
    }

    /**
     * Sets the list of columns in this table.
     *
     * @param columns the list of columns to set
     */
    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns;
    }

    /**
     * Adds a column to this table.
     *
     * @param column the column to add
     */
    public void addColumn(ColumnInfo column) {
        this.columns.add(column);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TableInfo tableInfo = (TableInfo) o;
        return Objects.equals(schema, tableInfo.schema) &&
                Objects.equals(name, tableInfo.name) &&
                Objects.equals(description, tableInfo.description) &&
                Objects.equals(type, tableInfo.type) &&
                Objects.equals(foreignKey, tableInfo.foreignKey) &&
                Objects.equals(primaryKeys, tableInfo.primaryKeys) &&
                Objects.equals(columns, tableInfo.columns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, name, description, type, foreignKey, primaryKeys, columns);
    }

    @Override
    public String toString() {
        return "TableInfo{" +
                "schema='" + schema + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", type='" + type + '\'' +
                ", foreignKey='" + foreignKey + '\'' +
                ", primaryKeys=" + primaryKeys +
                ", columns=" + columns +
                '}';
    }

    /**
     * Creates a new builder for constructing TableInfo instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing TableInfo instances.
     * Provides a fluent API for setting table properties.
     */
    public static class Builder {
        private final TableInfo instance = new TableInfo();

        /**
         * Sets the schema name.
         *
         * @param schema the schema name
         * @return this builder instance
         */
        public Builder schema(String schema) {
            instance.schema = schema;
            return this;
        }

        /**
         * Sets the table name.
         *
         * @param name the table name
         * @return this builder instance
         */
        public Builder name(String name) {
            instance.name = name;
            return this;
        }

        /**
         * Sets the table description.
         *
         * @param description the description
         * @return this builder instance
         */
        public Builder description(String description) {
            instance.description = description;
            return this;
        }

        /**
         * Sets the table type.
         *
         * @param type the table type
         * @return this builder instance
         */
        public Builder type(String type) {
            instance.type = type;
            return this;
        }

        /**
         * Sets the foreign key definition.
         *
         * @param foreignKey the foreign key definition
         * @return this builder instance
         */
        public Builder foreignKey(String foreignKey) {
            instance.foreignKey = foreignKey;
            return this;
        }

        /**
         * Sets the list of primary key columns.
         *
         * @param primaryKeys the list of primary key columns
         * @return this builder instance
         */
        public Builder primaryKeys(List<String> primaryKeys) {
            instance.primaryKeys = primaryKeys;
            return this;
        }

        /**
         * Sets the list of columns.
         *
         * @param columns the list of columns
         * @return this builder instance
         */
        public Builder columns(List<ColumnInfo> columns) {
            instance.columns = columns;
            return this;
        }

        /**
         * Builds and returns the configured TableInfo instance.
         *
         * @return the constructed TableInfo
         */
        public TableInfo build() {
            return instance;
        }
    }
}
