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
import java.util.Map;

/**
 * Data Transfer Object representing a database column for NL2SQL conversion.
 * <p>
 * Contains column metadata including name, description, data type, sample data,
 * value mappings, enumeration flags, and value ranges. This rich metadata helps
 * the NL2SQL engine generate accurate SQL queries by understanding the column's
 * semantic meaning and constraints.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class ColumnDTO {

    /**
     * The name of the database column.
     */
    private String name;

    /**
     * A human-readable description of the column's purpose or content.
     */
    private String description;

    /**
     * The data type of the column (e.g., VARCHAR, INTEGER, TIMESTAMP).
     */
    private String type;

    /**
     * Sample data values from this column.
     * Useful for understanding data patterns and typical values.
     */
    private List<String> data;

    /**
     * Mapping of coded values to their human-readable meanings.
     * For example: {"M": "Male", "F": "Female"} for a gender column.
     */
    private Map<String, String> mapping;

    /**
     * Flag indicating whether this column contains enumerated values.
     * Non-zero values typically indicate the column has a limited set of discrete values.
     */
    private int enumeration;

    /**
     * Description of the valid range for this column's values.
     * For example: "1-100", ">= 0", "2020-01-01 to 2025-12-31".
     */
    private String range;
}
