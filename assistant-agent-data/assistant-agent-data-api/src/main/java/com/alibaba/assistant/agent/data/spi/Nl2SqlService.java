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

import com.alibaba.assistant.agent.data.model.nl2sql.Nl2SqlException;
import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;

import java.util.List;

/**
 * Service Provider Interface for NL2SQL conversion.
 * <p>
 * Converts natural language queries to SQL statements using LLM-based inference.
 * This is the core SPI for NL2SQL capabilities in the AssistantAgent framework,
 * providing two primary conversion modes:
 * <ul>
 *     <li>SQL generation only: Convert natural language to executable SQL</li>
 *     <li>SQL generation with execution: Convert, execute, and map results to option items</li>
 * </ul>
 * <p>
 * Implementations should integrate with:
 * <ul>
 *     <li>LLM services (e.g., Qwen-Max) for natural language understanding</li>
 *     <li>Schema providers for database metadata and context</li>
 *     <li>SQL execution providers for running generated queries</li>
 * </ul>
 * <p>
 * This interface is typically implemented by {@code DefaultNl2SqlService} and
 * auto-configured via Spring Boot. Custom implementations can be provided by
 * implementing this interface and registering as a Spring bean.
 * <p>
 * Example implementation:
 * <pre>
 * {@code
 * @Component
 * public class DefaultNl2SqlService implements Nl2SqlService {
 *     @Override
 *     public String generateSql(String systemId, String query, String evidence) {
 *         // 1. Fetch schema from SchemaProvider
 *         // 2. Build prompt with schema context and evidence
 *         // 3. Call LLM to generate SQL
 *         // 4. Return generated SQL
 *     }
 * }
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface Nl2SqlService {

    /**
     * Convert natural language to SQL query.
     * <p>
     * This method generates a SQL statement from a natural language query
     * using LLM-based inference. The generated SQL is optimized for the
     * target datasource identified by systemId.
     * <p>
     * The generation process typically includes:
     * <ol>
     *     <li>Retrieving database schema metadata (tables, columns, types)</li>
     *     <li>Incorporating optional evidence for context disambiguation</li>
     *     <li>Constructing a prompt with schema and query</li>
     *     <li>Invoking LLM to generate syntactically correct SQL</li>
     *     <li>Validating and returning the generated SQL</li>
     * </ol>
     * <p>
     * Example usage:
     * <pre>
     * {@code
     * String sql = nl2SqlService.generateSql(
     *     "agent-001",
     *     "Show me all users registered last month",
     *     "Focus on the users table, registration_date column"
     * );
     * // Result: SELECT * FROM users WHERE registration_date >= DATE_SUB(NOW(), INTERVAL 1 MONTH)
     * }
     * </pre>
     *
     * @param systemId Datasource system ID (used to locate the target database and schema)
     * @param query Natural language query describing the desired data retrieval
     * @param evidence Additional context or evidence to improve generation accuracy (optional, can be null)
     * @return Generated SQL statement ready for execution
     * @throws Nl2SqlException if generation fails due to invalid input, schema retrieval error, or LLM failure
     */
    String generateSql(String systemId, String query, String evidence) throws Nl2SqlException;

    /**
     * Generate SQL and execute (for parameter collection).
     * <p>
     * This method combines SQL generation and execution in a single operation,
     * specifically designed for parameter collection workflows. It generates SQL
     * from natural language, executes it against the target datasource, and maps
     * the result set to option items (label-value pairs).
     * <p>
     * Typical use cases:
     * <ul>
     *     <li>Populating dropdown options from database queries</li>
     *     <li>Fetching enumeration values for parameter selection</li>
     *     <li>Retrieving autocomplete suggestions based on user input</li>
     * </ul>
     * <p>
     * The execution process:
     * <ol>
     *     <li>Generate SQL using {@link #generateSql(String, String, String)}</li>
     *     <li>Execute the generated SQL via SqlExecutionProvider</li>
     *     <li>Extract specified columns from the result set</li>
     *     <li>Map each row to an OptionItem (labelColumn → label, valueColumn → value)</li>
     *     <li>Return the list of OptionItem objects</li>
     * </ol>
     * <p>
     * Example usage:
     * <pre>
     * {@code
     * List<OptionItem> options = nl2SqlService.generateAndExecute(
     *     "agent-001",
     *     "Get all department names and IDs",
     *     "department_name",  // Display label column
     *     "department_id"     // Actual value column
     * );
     * // Result: [
     * //   OptionItem(label="Engineering", value="1"),
     * //   OptionItem(label="Sales", value="2"),
     * //   ...
     * // ]
     * }
     * </pre>
     *
     * @param systemId Datasource system ID (used to locate the target database and schema)
     * @param query Natural language query describing the desired data retrieval
     * @param labelColumn Column name to use as the display label in option items
     * @param valueColumn Column name to use as the actual value in option items
     * @return List of option items (label-value pairs) mapped from the query result set
     * @throws Nl2SqlException if generation, execution, or result mapping fails
     */
    List<OptionItem> generateAndExecute(String systemId, String query,
                                        String labelColumn, String valueColumn) throws Nl2SqlException;
}
