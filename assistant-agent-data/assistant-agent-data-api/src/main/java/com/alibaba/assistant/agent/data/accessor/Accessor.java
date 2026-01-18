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

import com.alibaba.assistant.agent.data.model.ColumnInfoBO;
import com.alibaba.assistant.agent.data.model.DatabaseInfoBO;
import com.alibaba.assistant.agent.data.model.ForeignKeyInfoBO;
import com.alibaba.assistant.agent.data.model.SchemaInfoBO;
import com.alibaba.assistant.agent.data.model.TableInfoBO;

import java.sql.Connection;
import java.util.List;

/**
 * Database metadata accessor interface.
 * Provides database-agnostic methods for retrieving schema metadata information.
 * Implementations should handle database-specific variations in metadata queries.
 *
 * <p>This interface is designed to support multiple database types (MySQL, PostgreSQL, Oracle, etc.)
 * by abstracting metadata retrieval operations. Each implementation should:
 * <ul>
 *   <li>Execute appropriate SQL or JDBC metadata calls for the target database</li>
 *   <li>Transform results into standardized BO objects</li>
 *   <li>Handle database-specific naming conventions and limitations</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface Accessor {

    /**
     * Retrieves all available databases from the connection.
     * For databases without explicit catalog support, this may return a single default database.
     *
     * @param connection the database connection to use
     * @return list of database information objects
     * @throws Exception if database access fails or connection is invalid
     */
    List<DatabaseInfoBO> showDatabases(Connection connection) throws Exception;

    /**
     * Retrieves all schemas within the specified database.
     * For databases without schema support, this may return an empty list or default schema.
     *
     * @param connection the database connection to use
     * @param param query parameters specifying database name and optional filters
     * @return list of schema information objects
     * @throws Exception if database access fails or parameters are invalid
     */
    List<SchemaInfoBO> showSchemas(Connection connection, DbQueryParameter param) throws Exception;

    /**
     * Retrieves all tables within the specified schema or database.
     * Results may be filtered by database name, schema name, or custom SQL criteria.
     *
     * @param connection the database connection to use
     * @param param query parameters specifying database, schema, and optional filters
     * @return list of table information objects
     * @throws Exception if database access fails or parameters are invalid
     */
    List<TableInfoBO> showTables(Connection connection, DbQueryParameter param) throws Exception;

    /**
     * Retrieves all columns within the specified table.
     * Results include column names, data types, nullability, and other metadata.
     *
     * @param connection the database connection to use
     * @param param query parameters specifying database, schema, table, and optional filters
     * @return list of column information objects
     * @throws Exception if database access fails or parameters are invalid
     */
    List<ColumnInfoBO> showColumns(Connection connection, DbQueryParameter param) throws Exception;

    /**
     * Retrieves all foreign key relationships for the specified table.
     * Results include foreign key names, columns, referenced tables, and constraint details.
     *
     * @param connection the database connection to use
     * @param param query parameters specifying database, schema, table, and optional filters
     * @return list of foreign key information objects
     * @throws Exception if database access fails or parameters are invalid
     */
    List<ForeignKeyInfoBO> showForeignKeys(Connection connection, DbQueryParameter param) throws Exception;

    /**
     * Returns the database type identifier for this accessor.
     * This should match the database product name or a standardized type identifier.
     * Examples: "MySQL", "PostgreSQL", "Oracle", "SQLServer"
     *
     * @return the database type identifier
     */
    String getDatabaseType();
}
