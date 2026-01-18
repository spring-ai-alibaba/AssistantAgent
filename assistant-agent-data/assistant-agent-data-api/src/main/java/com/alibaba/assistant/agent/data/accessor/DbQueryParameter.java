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
package com.alibaba.assistant.agent.data.accessor;

import java.util.Objects;

/**
 * Parameter object for database metadata queries.
 * Contains filter criteria for querying databases, schemas, tables, and columns.
 * Supports flexible query construction through builder pattern.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class DbQueryParameter {

    /**
     * The name of the database to query.
     */
    private String databaseName;

    /**
     * The name of the schema to query.
     */
    private String schemaName;

    /**
     * The name of the table to query.
     */
    private String tableName;

    /**
     * Custom SQL query for advanced filtering.
     */
    private String sql;

    /**
     * Default no-args constructor.
     */
    public DbQueryParameter() {
    }

    /**
     * Gets the database name.
     *
     * @return the database name
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * Sets the database name.
     *
     * @param databaseName the database name to set
     */
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    /**
     * Gets the schema name.
     *
     * @return the schema name
     */
    public String getSchemaName() {
        return schemaName;
    }

    /**
     * Sets the schema name.
     *
     * @param schemaName the schema name to set
     */
    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    /**
     * Gets the table name.
     *
     * @return the table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Sets the table name.
     *
     * @param tableName the table name to set
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * Gets the custom SQL query.
     *
     * @return the SQL query
     */
    public String getSql() {
        return sql;
    }

    /**
     * Sets the custom SQL query.
     *
     * @param sql the SQL query to set
     */
    public void setSql(String sql) {
        this.sql = sql;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DbQueryParameter that = (DbQueryParameter) o;
        return Objects.equals(databaseName, that.databaseName) &&
                Objects.equals(schemaName, that.schemaName) &&
                Objects.equals(tableName, that.tableName) &&
                Objects.equals(sql, that.sql);
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseName, schemaName, tableName, sql);
    }

    @Override
    public String toString() {
        return "DbQueryParameter{" +
                "databaseName='" + databaseName + '\'' +
                ", schemaName='" + schemaName + '\'' +
                ", tableName='" + tableName + '\'' +
                ", sql='" + sql + '\'' +
                '}';
    }

    /**
     * Creates a new builder for constructing DbQueryParameter instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing DbQueryParameter instances.
     * Provides a fluent API for setting query parameters.
     */
    public static class Builder {
        private String databaseName;
        private String schemaName;
        private String tableName;
        private String sql;

        /**
         * Sets the database name.
         *
         * @param databaseName the database name
         * @return this builder instance
         */
        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        /**
         * Sets the schema name.
         *
         * @param schemaName the schema name
         * @return this builder instance
         */
        public Builder schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        /**
         * Sets the table name.
         *
         * @param tableName the table name
         * @return this builder instance
         */
        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * Sets the custom SQL query.
         *
         * @param sql the SQL query
         * @return this builder instance
         */
        public Builder sql(String sql) {
            this.sql = sql;
            return this;
        }

        /**
         * Builds and returns the configured DbQueryParameter instance.
         * Creates a new instance each time to avoid shared state issues.
         *
         * @return the constructed DbQueryParameter
         */
        public DbQueryParameter build() {
            DbQueryParameter instance = new DbQueryParameter();
            instance.databaseName = this.databaseName;
            instance.schemaName = this.schemaName;
            instance.tableName = this.tableName;
            instance.sql = this.sql;
            return instance;
        }
    }
}
