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
package com.alibaba.assistant.agent.data.tool;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeExample;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.DefaultCodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.definition.CodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.DefaultCodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.ParameterNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.common.tools.definition.ParameterType;
import com.alibaba.assistant.agent.data.model.*;
import com.alibaba.assistant.agent.data.spi.SchemaProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * CodeactTool for querying database schema metadata.
 * Enables agent to discover database structures.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class QuerySchemaCodeactTool implements CodeactTool {

    private static final Logger log = LoggerFactory.getLogger(QuerySchemaCodeactTool.class);

    private static final String TOOL_NAME = "query_schema";

    private static final String DESCRIPTION = "Query database schema metadata. " +
            "Discover databases, schemas, tables, and table structures (columns, keys, constraints).";

    private final SchemaProvider schemaProvider;
    private final CodeactToolMetadata codeactMetadata;
    private final ToolDefinition toolDefinition;
    private final CodeactToolDefinition codeactDefinition;
    private final ObjectMapper objectMapper;

    public QuerySchemaCodeactTool(SchemaProvider schemaProvider) {
        this.schemaProvider = schemaProvider;
        this.objectMapper = new ObjectMapper();
        this.toolDefinition = buildToolDefinition();
        this.codeactDefinition = buildCodeactDefinition();
        this.codeactMetadata = buildCodeactMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        log.debug("QuerySchemaCodeactTool#call - toolInput={}", toolInput);

        try {
            Map<String, Object> params = objectMapper.readValue(toolInput, Map.class);

            String systemId = (String) params.get("systemId");
            String operation = (String) params.get("operation");
            String databaseName = (String) params.get("databaseName");
            String schemaName = (String) params.get("schemaName");
            String tableName = (String) params.get("tableName");

            if (systemId == null || systemId.trim().isEmpty()) {
                return "Error: systemId parameter is required";
            }

            if (operation == null || operation.trim().isEmpty()) {
                return "Error: operation parameter is required";
            }

            switch (operation.toUpperCase()) {
                case "LIST_DATABASES":
                    return listDatabases(systemId);

                case "LIST_SCHEMAS":
                    return listSchemas(systemId, databaseName);

                case "LIST_TABLES":
                    return listTables(systemId, databaseName, schemaName);

                case "DESCRIBE_TABLE":
                    return describeTable(systemId, databaseName, schemaName, tableName);

                default:
                    return "Error: Invalid operation. Supported: LIST_DATABASES, LIST_SCHEMAS, LIST_TABLES, DESCRIBE_TABLE";
            }

        } catch (Exception e) {
            log.error("QuerySchemaCodeactTool#call - error={}", e.getMessage(), e);
            return "Error querying schema: " + e.getMessage();
        }
    }

    private String listDatabases(String systemId) throws Exception {
        List<DatabaseInfoBO> databases = schemaProvider.getDatabaseList(systemId);

        StringBuilder sb = new StringBuilder();
        sb.append("# Databases\n\n");
        sb.append("| Database Name |\n");
        sb.append("|---------------|\n");

        for (DatabaseInfoBO db : databases) {
            sb.append("| ").append(db.getName()).append(" |\n");
        }

        log.info("QuerySchemaCodeactTool#listDatabases - systemId={}, count={}", systemId, databases.size());
        return sb.toString();
    }

    private String listSchemas(String systemId, String databaseName) throws Exception {
        List<SchemaInfoBO> schemas = schemaProvider.getSchemaList(systemId, databaseName);

        StringBuilder sb = new StringBuilder();
        sb.append("# Schemas");
        if (databaseName != null) {
            sb.append(" in Database: ").append(databaseName);
        }
        sb.append("\n\n");
        sb.append("| Schema Name | Catalog |\n");
        sb.append("|-------------|----------|\n");

        for (SchemaInfoBO schema : schemas) {
            sb.append("| ").append(schema.getName()).append(" | ")
                    .append(schema.getCatalog() != null ? schema.getCatalog() : "").append(" |\n");
        }

        log.info("QuerySchemaCodeactTool#listSchemas - systemId={}, database={}, count={}",
                systemId, databaseName, schemas.size());
        return sb.toString();
    }

    private String listTables(String systemId, String databaseName, String schemaName) throws Exception {
        List<TableInfoBO> tables = schemaProvider.getTableList(systemId, databaseName, schemaName);

        StringBuilder sb = new StringBuilder();
        sb.append("# Tables");
        if (schemaName != null) {
            sb.append(" in Schema: ").append(schemaName);
        }
        sb.append("\n\n");
        sb.append("| Table Name | Type | Description |\n");
        sb.append("|------------|------|-------------|\n");

        for (TableInfoBO table : tables) {
            sb.append("| ").append(table.getName()).append(" | ")
                    .append(table.getType() != null ? table.getType() : "").append(" | ")
                    .append(table.getDescription() != null ? table.getDescription() : "").append(" |\n");
        }

        log.info("QuerySchemaCodeactTool#listTables - systemId={}, schema={}, count={}",
                systemId, schemaName, tables.size());
        return sb.toString();
    }

    private String describeTable(String systemId, String databaseName, String schemaName, String tableName) throws Exception {
        if (tableName == null || tableName.trim().isEmpty()) {
            return "Error: tableName parameter is required for DESCRIBE_TABLE operation";
        }

        TableInfoBO table = schemaProvider.getTableStructure(systemId, databaseName, schemaName, tableName);

        StringBuilder sb = new StringBuilder();
        sb.append("# Table: ").append(table.getName()).append("\n\n");

        if (table.getDescription() != null && !table.getDescription().isEmpty()) {
            sb.append("**Description:** ").append(table.getDescription()).append("\n\n");
        }

        if (table.getSchema() != null) {
            sb.append("**Schema:** ").append(table.getSchema()).append("\n\n");
        }

        sb.append("## Columns\n\n");
        sb.append("| Column Name | Type | Primary | Not Null | Description |\n");
        sb.append("|-------------|------|---------|----------|-------------|\n");

        if (table.getColumns() != null) {
            for (ColumnInfoBO column : table.getColumns()) {
                sb.append("| ").append(column.getName()).append(" | ")
                        .append(column.getType()).append(" | ")
                        .append(column.isPrimary() ? "✓" : "").append(" | ")
                        .append(column.isNotnull() ? "✓" : "").append(" | ")
                        .append(column.getDescription() != null ? column.getDescription() : "").append(" |\n");
            }
        }

        if (table.getPrimaryKeys() != null && !table.getPrimaryKeys().isEmpty()) {
            sb.append("\n**Primary Keys:** ").append(String.join(", ", table.getPrimaryKeys())).append("\n");
        }

        if (table.getForeignKey() != null && !table.getForeignKey().isEmpty()) {
            sb.append("\n**Foreign Keys:** ").append(table.getForeignKey()).append("\n");
        }

        log.info("QuerySchemaCodeactTool#describeTable - systemId={}, table={}, columns={}",
                systemId, tableName, table.getColumns() != null ? table.getColumns().size() : 0);
        return sb.toString();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public CodeactToolDefinition getCodeactDefinition() {
        return codeactDefinition;
    }

    @Override
    public CodeactToolMetadata getCodeactMetadata() {
        return codeactMetadata;
    }

    private ToolDefinition buildToolDefinition() {
        String inputSchema = buildInputSchema();
        return ToolDefinition.builder()
                .name(TOOL_NAME)
                .description(DESCRIPTION)
                .inputSchema(inputSchema)
                .build();
    }

    private String buildInputSchema() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "systemId": {
                            "type": "string",
                            "description": "System ID identifying the datasource"
                        },
                        "operation": {
                            "type": "string",
                            "enum": ["LIST_DATABASES", "LIST_SCHEMAS", "LIST_TABLES", "DESCRIBE_TABLE"],
                            "description": "Operation to perform"
                        },
                        "databaseName": {
                            "type": "string",
                            "description": "Database name (optional for some databases)"
                        },
                        "schemaName": {
                            "type": "string",
                            "description": "Schema name (optional for some databases)"
                        },
                        "tableName": {
                            "type": "string",
                            "description": "Table name (required for DESCRIBE_TABLE)"
                        }
                    },
                    "required": ["systemId", "operation"]
                }
                """;
    }

    private CodeactToolDefinition buildCodeactDefinition() {
        String inputSchema = toolDefinition.inputSchema();

        ParameterTree parameterTree = ParameterTree.builder()
                .rawInputSchema(inputSchema)
                .addParameter(ParameterNode.builder()
                        .name("systemId")
                        .type(ParameterType.STRING)
                        .description("System ID identifying the datasource")
                        .required(true)
                        .build())
                .addParameter(ParameterNode.builder()
                        .name("operation")
                        .type(ParameterType.STRING)
                        .description("Operation: LIST_DATABASES, LIST_SCHEMAS, LIST_TABLES, DESCRIBE_TABLE")
                        .required(true)
                        .build())
                .addParameter(ParameterNode.builder()
                        .name("databaseName")
                        .type(ParameterType.STRING)
                        .description("Database name (optional)")
                        .required(false)
                        .build())
                .addParameter(ParameterNode.builder()
                        .name("schemaName")
                        .type(ParameterType.STRING)
                        .description("Schema name (optional)")
                        .required(false)
                        .build())
                .addParameter(ParameterNode.builder()
                        .name("tableName")
                        .type(ParameterType.STRING)
                        .description("Table name (required for DESCRIBE_TABLE)")
                        .required(false)
                        .build())
                .addRequiredName("systemId")
                .addRequiredName("operation")
                .build();

        return DefaultCodeactToolDefinition.builder()
                .name(TOOL_NAME)
                .description(DESCRIPTION)
                .inputSchema(inputSchema)
                .parameterTree(parameterTree)
                .returnDescription("Schema information in markdown table format")
                .returnTypeHint("str")
                .build();
    }

    private CodeactToolMetadata buildCodeactMetadata() {
        CodeExample pythonExample = new CodeExample(
                "Query database schema and table structure",
                """
                # List all tables in a schema
                tables = query_schema(
                    systemId="my-database",
                    operation="LIST_TABLES",
                    schemaName="public"
                )
                print(tables)

                # Get detailed table structure
                structure = query_schema(
                    systemId="my-database",
                    operation="DESCRIBE_TABLE",
                    schemaName="public",
                    tableName="users"
                )
                print(structure)
                """,
                "Returns schema metadata in markdown format"
        );

        return DefaultCodeactToolMetadata.builder()
                .supportedLanguages(Collections.singletonList(Language.PYTHON))
                .addFewShot(pythonExample)
                .build();
    }
}
