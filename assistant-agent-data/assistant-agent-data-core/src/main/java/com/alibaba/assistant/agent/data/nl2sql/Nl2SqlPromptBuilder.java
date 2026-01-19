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
package com.alibaba.assistant.agent.data.nl2sql;

import com.alibaba.assistant.agent.data.model.nl2sql.ColumnDTO;
import com.alibaba.assistant.agent.data.model.nl2sql.SchemaDTO;
import com.alibaba.assistant.agent.data.model.nl2sql.SqlGenerationDTO;
import com.alibaba.assistant.agent.data.model.nl2sql.TableDTO;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for building NL2SQL prompts.
 * Reference: DataAgent's PromptHelper.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class Nl2SqlPromptBuilder {

    private static final String SQL_GENERATION_TEMPLATE_PATH = "prompts/nl2sql-generation.st";
    private static final String SCHEMA_FILTER_TEMPLATE_PATH = "prompts/nl2sql-filter.st";

    /**
     * Build SQL generation prompt.
     */
    public static String buildSqlGenerationPrompt(SqlGenerationDTO dto) {
        Map<String, Object> params = new HashMap<>();
        params.put("dialect", dto.getDialect());
        params.put("question", dto.getQuery());
        params.put("schema_info", buildSchemaInfo(dto.getSchemaDTO()));
        params.put("evidence", dto.getEvidence() != null ? dto.getEvidence() : "无");
        params.put("execution_description", dto.getExecutionDescription());

        String template = loadTemplate(SQL_GENERATION_TEMPLATE_PATH);
        return new PromptTemplate(template).render(params);
    }

    /**
     * Build schema information string.
     * Reference: DataAgent's buildMixMacSqlDbPrompt().
     */
    public static String buildSchemaInfo(SchemaDTO schemaDTO) {
        StringBuilder sb = new StringBuilder();
        sb.append("【DB_ID】").append(schemaDTO.getName() != null ? schemaDTO.getName() : "").append("\n");

        for (TableDTO table : schemaDTO.getTable()) {
            sb.append("# Table: ").append(table.getName());
            if (table.getDescription() != null && !table.getName().equals(table.getDescription())) {
                sb.append(", ").append(table.getDescription());
            }
            sb.append("\n[\n");

            // Build column list
            List<String> columnLines = new ArrayList<>();
            for (ColumnDTO column : table.getColumn()) {
                StringBuilder line = new StringBuilder();
                line.append("(").append(column.getName());

                // Add type
                if (column.getType() != null && !column.getType().isEmpty()) {
                    line.append(":").append(column.getType().toUpperCase());
                }

                // Add description
                if (column.getDescription() != null && !column.getName().equals(column.getDescription())) {
                    line.append(", ").append(column.getDescription());
                }

                // Add Primary Key marker
                if (table.getPrimaryKeys() != null && table.getPrimaryKeys().contains(column.getName())) {
                    line.append(", Primary Key");
                }

                // Add sample values
                if (column.getData() != null && !column.getData().isEmpty() && !"id".equals(column.getName())) {
                    line.append(", Examples: [");
                    List<String> samples = column.getData().stream()
                            .filter(d -> d != null && !d.isEmpty())
                            .limit(3)
                            .collect(Collectors.toList());
                    line.append(String.join(",", samples));
                    line.append("]");
                }

                // Add value mapping
                if (column.getMapping() != null && !column.getMapping().isEmpty()) {
                    line.append(", ValueMapping: {");
                    List<String> mappings = column.getMapping().entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.toList());
                    line.append(String.join(", ", mappings));
                    line.append("}");
                }

                line.append(")");
                columnLines.add(line.toString());
            }

            sb.append(String.join(",\n", columnLines));
            sb.append("\n]\n");
        }

        // Add foreign keys
        if (schemaDTO.getForeignKeys() != null && !schemaDTO.getForeignKeys().isEmpty()) {
            sb.append("【Foreign keys】\n");
            sb.append(String.join("\n", schemaDTO.getForeignKeys()));
        }

        return sb.toString();
    }

    /**
     * Build schema filter prompt.
     */
    public static String buildSchemaFilterPrompt(String query, List<String> tableNames) {
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("tables", String.join(", ", tableNames));

        String template = loadTemplate(SCHEMA_FILTER_TEMPLATE_PATH);
        return new PromptTemplate(template).render(params);
    }

    /**
     * Load template from classpath.
     */
    private static String loadTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + path, e);
        }
    }
}
