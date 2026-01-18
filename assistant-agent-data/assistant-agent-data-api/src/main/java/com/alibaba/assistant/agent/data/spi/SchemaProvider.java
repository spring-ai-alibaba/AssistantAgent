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
package com.alibaba.assistant.agent.data.spi;

import com.alibaba.assistant.agent.data.model.ColumnInfo;
import com.alibaba.assistant.agent.data.model.SchemaInfo;
import com.alibaba.assistant.agent.data.model.TableInfo;

import java.util.List;

/**
 * SPI for database schema metadata.
 * <p>
 * Provides methods to retrieve database schema information including
 * tables, columns, and their metadata. This is essential for NL2SQL
 * capabilities, query validation, and dynamic UI generation.
 * <p>
 * Implementations should leverage JDBC metadata APIs or database-specific
 * introspection queries to extract schema information.
 * <p>
 * Example implementation:
 * <pre>
 * {@code
 * @Component
 * public class DefaultSchemaProvider implements SchemaProvider {
 *     @Override
 *     public SchemaInfo getSchema(String systemId) {
 *         // Use JDBC DatabaseMetaData to extract schema
 *     }
 * }
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface SchemaProvider {

    /**
     * Retrieves the complete schema information for a system.
     * <p>
     * Returns a SchemaInfo object containing all tables and their columns
     * for the specified system. This provides a comprehensive view of the
     * database structure.
     *
     * @param systemId the system identifier (agent ID)
     * @return the schema information including all tables and columns, never null
     */
    SchemaInfo getSchema(String systemId);

    /**
     * Retrieves all tables for a system.
     * <p>
     * Returns a list of TableInfo objects without detailed column information.
     * This is useful for listing available tables without the overhead of
     * fetching complete schema metadata.
     *
     * @param systemId the system identifier (agent ID)
     * @return a list of table information objects, never null but may be empty
     */
    List<TableInfo> getTables(String systemId);

    /**
     * Retrieves detailed table information including columns.
     * <p>
     * Returns a TableInfo object with complete column metadata for the specified
     * table. This includes column names, types, constraints, and comments.
     * <p>
     * Implementations should handle table name case sensitivity according to
     * the target database's conventions.
     *
     * @param systemId the system identifier (agent ID)
     * @param tableName the name of the table to retrieve
     * @return the table information including all columns, or null if table not found
     */
    TableInfo getTable(String systemId, String tableName);

    /**
     * Retrieves columns for a specific table.
     * <p>
     * Returns a list of ColumnInfo objects describing each column in the table.
     * This includes column name, data type, nullability, default values,
     * and any comments or descriptions.
     * <p>
     * Column metadata is essential for:
     * <ul>
     *     <li>NL2SQL query generation</li>
     *     <li>Parameter validation</li>
     *     <li>Dynamic form generation</li>
     *     <li>Query result interpretation</li>
     * </ul>
     *
     * @param systemId the system identifier (agent ID)
     * @param tableName the name of the table
     * @return a list of column information objects, never null but may be empty if table not found
     */
    List<ColumnInfo> getColumns(String systemId, String tableName);
}
