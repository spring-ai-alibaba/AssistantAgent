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
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing a database table for NL2SQL conversion.
 * <p>
 * Contains table metadata including name, description, column definitions,
 * and primary key information. Used to provide table structure context for
 * natural language to SQL translation.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class TableDTO {

    /**
     * The name of the database table.
     */
    private String name;

    /**
     * A human-readable description of the table's purpose or content.
     */
    private String description;

    /**
     * List of column definitions within this table.
     * Initialized to an empty ArrayList to avoid null pointer exceptions.
     */
    private List<ColumnDTO> column = new ArrayList<>();

    /**
     * List of column names that form the primary key for this table.
     * For composite primary keys, multiple column names are included.
     */
    private List<String> primaryKeys;
}
