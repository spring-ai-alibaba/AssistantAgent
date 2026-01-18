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
 * Represents metadata information about a database schema.
 * This class captures the basic information about a schema (also known as a database
 * in some database systems), including its name and description.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SchemaInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The name of the schema.
     */
    private String name;

    /**
     * A human-readable description of the schema's purpose or content.
     */
    private String description;

    /**
     * The name of the database this schema belongs to.
     */
    private String databaseName;

    /**
     * List of tables contained in this schema.
     */
    private List<TableInfo> tables = new ArrayList<>();

    /**
     * Default constructor.
     */
    public SchemaInfo() {
    }

    /**
     * Gets the name of the schema.
     *
     * @return the schema name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the schema.
     *
     * @param name the schema name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the description of the schema.
     *
     * @return the schema description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the schema.
     *
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the name of the database this schema belongs to.
     *
     * @return the database name
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * Sets the name of the database this schema belongs to.
     *
     * @param databaseName the database name to set
     */
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    /**
     * Gets the list of tables in this schema.
     *
     * @return the list of tables
     */
    public List<TableInfo> getTables() {
        return tables;
    }

    /**
     * Sets the list of tables in this schema.
     *
     * @param tables the list of tables to set
     */
    public void setTables(List<TableInfo> tables) {
        this.tables = tables;
    }

    /**
     * Adds a table to this schema.
     *
     * @param table the table to add
     */
    public void addTable(TableInfo table) {
        this.tables.add(table);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchemaInfo that = (SchemaInfo) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(databaseName, that.databaseName) &&
                Objects.equals(tables, that.tables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, databaseName, tables);
    }

    @Override
    public String toString() {
        return "SchemaInfo{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", databaseName='" + databaseName + '\'' +
                ", tables=" + tables +
                '}';
    }

    /**
     * Creates a new builder for constructing SchemaInfo instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing SchemaInfo instances.
     * Provides a fluent API for setting schema properties.
     */
    public static class Builder {
        private final SchemaInfo instance = new SchemaInfo();

        /**
         * Sets the schema name.
         *
         * @param name the schema name
         * @return this builder instance
         */
        public Builder name(String name) {
            instance.name = name;
            return this;
        }

        /**
         * Sets the schema description.
         *
         * @param description the description
         * @return this builder instance
         */
        public Builder description(String description) {
            instance.description = description;
            return this;
        }

        /**
         * Sets the database name.
         *
         * @param databaseName the database name
         * @return this builder instance
         */
        public Builder databaseName(String databaseName) {
            instance.databaseName = databaseName;
            return this;
        }

        /**
         * Sets the list of tables.
         *
         * @param tables the list of tables
         * @return this builder instance
         */
        public Builder tables(List<TableInfo> tables) {
            instance.tables = tables;
            return this;
        }

        /**
         * Adds a table to the schema.
         *
         * @param table the table to add
         * @return this builder instance
         */
        public Builder addTable(TableInfo table) {
            instance.tables.add(table);
            return this;
        }

        /**
         * Builds and returns the configured SchemaInfo instance.
         *
         * @return the constructed SchemaInfo
         */
        public SchemaInfo build() {
            return instance;
        }
    }
}
