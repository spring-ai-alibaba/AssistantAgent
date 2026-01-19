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
package com.alibaba.assistant.agent.data.model.nl2sql;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Data Transfer Object containing all parameters required for SQL generation from natural language.
 * <p>
 * This DTO encapsulates the complete context needed for NL2SQL conversion, including
 * the user's natural language query, any additional evidence or hints, the target database
 * schema structure, the SQL dialect to use, and a description of the expected execution behavior.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@AllArgsConstructor
public class SqlGenerationDTO {

    /**
     * The natural language query provided by the user.
     * Example: "Show me all employees hired in the last year"
     */
    private String query;

    /**
     * Additional contextual information or hints to help with SQL generation.
     * This may include business rules, domain knowledge, or clarifications.
     */
    private String evidence;

    /**
     * The database schema structure containing tables, columns, and relationships.
     * Provides the structural context needed for accurate SQL generation.
     */
    private SchemaDTO schemaDTO;

    /**
     * The target SQL dialect (e.g., "MySQL", "PostgreSQL", "Oracle", "SQLite").
     * Used to generate dialect-specific SQL syntax and functions.
     */
    private String dialect;

    /**
     * A description of how the generated SQL should be executed or what it should accomplish.
     * May include performance hints, execution preferences, or result formatting requirements.
     */
    private String executionDescription;
}
