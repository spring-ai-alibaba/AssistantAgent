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

import com.alibaba.assistant.agent.data.model.QueryResult;

/**
 * SPI for SQL execution.
 * <p>
 * Provides methods to execute SQL queries against configured datasources.
 * This interface enforces read-only access by validating SQL statements
 * before execution to prevent data modification.
 * <p>
 * Security considerations:
 * <ul>
 *     <li>All SQL statements must be SELECT queries only</li>
 *     <li>DML/DDL operations (INSERT, UPDATE, DELETE, DROP, etc.) are prohibited</li>
 *     <li>Implementations should validate SQL syntax and structure</li>
 *     <li>Result sets should be limited to prevent memory exhaustion</li>
 * </ul>
 * <p>
 * Example implementation:
 * <pre>
 * {@code
 * @Component
 * public class DefaultSqlExecutionProvider implements SqlExecutionProvider {
 *     @Override
 *     public QueryResult execute(String systemId, String sql) {
 *         validateReadOnly(sql);
 *         // Execute using JDBC
 *     }
 * }
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface SqlExecutionProvider {

    /**
     * Executes a SQL query with default row limit.
     * <p>
     * The SQL statement must be a SELECT query. The result set will be
     * limited to a default maximum number of rows (typically 100-1000)
     * to prevent performance issues.
     *
     * @param systemId the system identifier (agent ID)
     * @param sql the SQL statement (must be SELECT)
     * @return the query result containing columns and rows
     * @throws SecurityException if SQL contains write operations
     * @throws IllegalArgumentException if SQL is invalid
     */
    QueryResult execute(String systemId, String sql);

    /**
     * Executes a SQL query with a specified row limit.
     * <p>
     * The SQL statement must be a SELECT query. Results will be limited
     * to the specified maximum number of rows. If the actual result set
     * exceeds this limit, the QueryResult will have its truncated flag set.
     * <p>
     * Example usage:
     * <pre>
     * QueryResult result = provider.execute("system-123",
     *     "SELECT * FROM users WHERE status = 'ACTIVE'", 50);
     * if (result.isTruncated()) {
     *     System.out.println("Results limited to 50 rows");
     * }
     * </pre>
     *
     * @param systemId the system identifier (agent ID)
     * @param sql the SQL statement (must be SELECT)
     * @param maxRows the maximum number of rows to return (must be positive)
     * @return the query result containing columns and rows
     * @throws SecurityException if SQL contains write operations
     * @throws IllegalArgumentException if SQL is invalid or maxRows is not positive
     */
    QueryResult execute(String systemId, String sql, int maxRows);

    /**
     * Validates that SQL is read-only.
     * <p>
     * Checks that the SQL statement is a SELECT query and does not contain
     * any data modification operations. This method should be called before
     * executing any SQL to enforce security policies.
     * <p>
     * The validation should check for:
     * <ul>
     *     <li>DML statements: INSERT, UPDATE, DELETE, MERGE</li>
     *     <li>DDL statements: CREATE, ALTER, DROP, TRUNCATE</li>
     *     <li>DCL statements: GRANT, REVOKE</li>
     *     <li>Transaction control: COMMIT, ROLLBACK</li>
     * </ul>
     * <p>
     * Example usage:
     * <pre>
     * try {
     *     provider.validateReadOnly(sql);
     *     // SQL is safe to execute
     * } catch (SecurityException e) {
     *     // SQL contains forbidden operations
     * }
     * </pre>
     *
     * @param sql the SQL statement to validate
     * @throws SecurityException if SQL contains write operations or is invalid
     */
    void validateReadOnly(String sql);
}
