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
 * Configuration for parameter options data source.
 * <p>
 * Defines how to fetch dynamic options for action parameters.
 * Supports three source types:
 * <ul>
 *     <li>SQL - Direct SQL query against a datasource</li>
 *     <li>API - HTTP API call to external service</li>
 *     <li>NL2SQL - Natural language to SQL conversion</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class OptionsSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Source type for fetching parameter options.
     */
    public enum SourceType {
        /**
         * Direct SQL query
         */
        SQL,
        /**
         * HTTP API call
         */
        API,
        /**
         * Natural language to SQL
         */
        NL2SQL
    }

    /**
     * The type of options source
     */
    private SourceType sourceType;

    /**
     * SQL source configuration (used when sourceType is SQL)
     */
    private SqlSourceConfig sql;

    /**
     * API source configuration (used when sourceType is API)
     */
    private ApiSourceConfig api;

    /**
     * NL2SQL source configuration (used when sourceType is NL2SQL)
     */
    private Nl2SqlSourceConfig nl2sql;

    /**
     * Gets the source type.
     *
     * @return the source type
     */
    public SourceType getSourceType() {
        return sourceType;
    }

    /**
     * Sets the source type.
     *
     * @param sourceType the source type to set
     */
    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    /**
     * Gets the SQL source configuration.
     *
     * @return the SQL source configuration, or null if not using SQL source
     */
    public SqlSourceConfig getSql() {
        return sql;
    }

    /**
     * Sets the SQL source configuration.
     *
     * @param sql the SQL source configuration to set
     */
    public void setSql(SqlSourceConfig sql) {
        this.sql = sql;
    }

    /**
     * Gets the API source configuration.
     *
     * @return the API source configuration, or null if not using API source
     */
    public ApiSourceConfig getApi() {
        return api;
    }

    /**
     * Sets the API source configuration.
     *
     * @param api the API source configuration to set
     */
    public void setApi(ApiSourceConfig api) {
        this.api = api;
    }

    /**
     * Gets the NL2SQL source configuration.
     *
     * @return the NL2SQL source configuration, or null if not using NL2SQL source
     */
    public Nl2SqlSourceConfig getNl2sql() {
        return nl2sql;
    }

    /**
     * Sets the NL2SQL source configuration.
     *
     * @param nl2sql the NL2SQL source configuration to set
     */
    public void setNl2sql(Nl2SqlSourceConfig nl2sql) {
        this.nl2sql = nl2sql;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OptionsSourceConfig that = (OptionsSourceConfig) o;
        return sourceType == that.sourceType &&
                Objects.equals(sql, that.sql) &&
                Objects.equals(api, that.api) &&
                Objects.equals(nl2sql, that.nl2sql);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceType, sql, api, nl2sql);
    }

    @Override
    public String toString() {
        return "OptionsSourceConfig{" +
                "sourceType=" + sourceType +
                ", sql=" + sql +
                ", api=" + api +
                ", nl2sql=" + nl2sql +
                '}';
    }
}
