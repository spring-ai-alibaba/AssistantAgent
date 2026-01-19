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

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Data Transfer Object representing a database schema for NL2SQL conversion.
 * <p>
 * Contains schema metadata including name, description, table count, table definitions,
 * and foreign key relationships. Used to provide database structure context for
 * natural language to SQL translation.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class SchemaDTO {

    /**
     * The name of the database schema.
     */
    private String name;

    /**
     * A human-readable description of the schema's purpose or content.
     */
    private String description;

    /**
     * The total number of tables in this schema.
     */
    private Integer tableCount;

    /**
     * List of table definitions within this schema.
     */
    private List<TableDTO> table;

    /**
     * List of foreign key constraints defined across tables in this schema.
     * Each entry typically describes the relationship between tables.
     */
    private List<String> foreignKeys;
}
