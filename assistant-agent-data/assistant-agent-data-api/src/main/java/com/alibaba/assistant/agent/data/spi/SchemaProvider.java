/*
 * Copyright 2025 the original author or authors.
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
package com.alibaba.assistant.agent.data.spi;

import com.alibaba.assistant.agent.data.model.*;

import java.util.List;

/**
 * Service Provider Interface for database schema operations.
 * Provides methods to query database metadata and structure.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface SchemaProvider {

    /**
     * Get list of databases for a system.
     *
     * @param systemId system identifier
     * @return list of databases
     * @throws Exception if query fails
     */
    List<DatabaseInfoBO> getDatabaseList(String systemId) throws Exception;

    /**
     * Get list of schemas for a database.
     *
     * @param systemId system identifier
     * @param databaseName database name
     * @return list of schemas
     * @throws Exception if query fails
     */
    List<SchemaInfoBO> getSchemaList(String systemId, String databaseName) throws Exception;

    /**
     * Get list of tables for a schema.
     *
     * @param systemId system identifier
     * @param databaseName database name (optional for some databases)
     * @param schemaName schema name (optional for some databases)
     * @return list of tables with basic metadata
     * @throws Exception if query fails
     */
    List<TableInfoBO> getTableList(String systemId, String databaseName, String schemaName) throws Exception;

    /**
     * Get complete table structure including columns, keys, constraints.
     *
     * @param systemId system identifier
     * @param databaseName database name (optional for some databases)
     * @param schemaName schema name (optional for some databases)
     * @param tableName table name
     * @return complete table structure
     * @throws Exception if query fails
     */
    TableInfoBO getTableStructure(String systemId, String databaseName, String schemaName, String tableName) throws Exception;
}
