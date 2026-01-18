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
import com.alibaba.assistant.agent.data.model.QueryResult;
import com.alibaba.assistant.agent.data.spi.SqlExecutionProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * CodeactTool for executing SQL queries.
 * Enforces read-only operations for security.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class ExecuteSqlCodeactTool implements CodeactTool {

    private static final Logger log = LoggerFactory.getLogger(ExecuteSqlCodeactTool.class);

    private static final String TOOL_NAME = "execute_sql";

    private static final String DESCRIPTION = "Execute read-only SQL query against a datasource. " +
            "Only SELECT statements are allowed for security. Returns results in markdown table format.";

    private final SqlExecutionProvider sqlExecutionProvider;

    private final CodeactToolMetadata codeactMetadata;

    private final ToolDefinition toolDefinition;

    private final CodeactToolDefinition codeactDefinition;

    private final ObjectMapper objectMapper;

    /**
     * Constructor for ExecuteSqlCodeactTool.
     *
     * @param sqlExecutionProvider SQL execution provider
     */
    public ExecuteSqlCodeactTool(SqlExecutionProvider sqlExecutionProvider) {
        this.sqlExecutionProvider = sqlExecutionProvider;
        this.objectMapper = new ObjectMapper();

        // Build ToolDefinition
        this.toolDefinition = buildToolDefinition();

        // Build CodeactToolDefinition (with ParameterTree)
        this.codeactDefinition = buildCodeactDefinition();

        // Build CodeactToolMetadata
        this.codeactMetadata = buildCodeactMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        log.debug("ExecuteSqlCodeactTool#call - reason=开始执行SQL查询, toolInput={}", toolInput);

        try {
            // Parse JSON input parameters
            Map<String, Object> params = objectMapper.readValue(toolInput, Map.class);

            String systemId = (String) params.get("systemId");
            String sql = (String) params.get("sql");
            Integer maxRows = params.containsKey("maxRows") ?
                    ((Number) params.get("maxRows")).intValue() : 1000;

            if (systemId == null || systemId.trim().isEmpty()) {
                return "Error: systemId parameter is required";
            }

            if (sql == null || sql.trim().isEmpty()) {
                return "Error: sql parameter is required";
            }

            // Execute SQL query
            QueryResult result = sqlExecutionProvider.execute(systemId, sql, maxRows);

            log.info("ExecuteSqlCodeactTool#call - reason=SQL查询执行成功, systemId={}, rows={}, time={}ms",
                    systemId, result.getRows().size(), result.getExecutionTimeMs());

            // Return markdown table format
            return result.toMarkdownTable();

        } catch (SecurityException e) {
            log.error("ExecuteSqlCodeactTool#call - reason=安全违规, error={}", e.getMessage());
            return "Security violation: " + e.getMessage();
        } catch (Exception e) {
            log.error("ExecuteSqlCodeactTool#call - reason=SQL执行失败, error={}", e.getMessage(), e);
            return "Error executing SQL: " + e.getMessage();
        }
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

    /**
     * Build Spring AI ToolDefinition.
     */
    private ToolDefinition buildToolDefinition() {
        String inputSchema = buildInputSchema();
        return ToolDefinition.builder()
                .name(TOOL_NAME)
                .description(DESCRIPTION)
                .inputSchema(inputSchema)
                .build();
    }

    /**
     * Build input schema JSON for Spring AI.
     */
    private String buildInputSchema() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "systemId": {
                            "type": "string",
                            "description": "System ID identifying the datasource to query"
                        },
                        "sql": {
                            "type": "string",
                            "description": "SQL SELECT query to execute (read-only)"
                        },
                        "maxRows": {
                            "type": "integer",
                            "description": "Maximum number of rows to return (default: 1000)"
                        }
                    },
                    "required": ["systemId", "sql"]
                }
                """;
    }

    /**
     * Build CodeactToolDefinition with structured parameter tree.
     */
    private CodeactToolDefinition buildCodeactDefinition() {
        String inputSchema = toolDefinition.inputSchema();

        // Build parameter tree
        ParameterTree parameterTree = ParameterTree.builder()
                .rawInputSchema(inputSchema)
                .addParameter(ParameterNode.builder()
                        .name("systemId")
                        .type(ParameterType.STRING)
                        .description("System ID identifying the datasource to query")
                        .required(true)
                        .build())
                .addParameter(ParameterNode.builder()
                        .name("sql")
                        .type(ParameterType.STRING)
                        .description("SQL SELECT query to execute (read-only)")
                        .required(true)
                        .build())
                .addParameter(ParameterNode.builder()
                        .name("maxRows")
                        .type(ParameterType.INTEGER)
                        .description("Maximum number of rows to return (default: 1000)")
                        .required(false)
                        .defaultValue(1000)
                        .build())
                .addRequiredName("systemId")
                .addRequiredName("sql")
                .build();

        return DefaultCodeactToolDefinition.builder()
                .name(TOOL_NAME)
                .description(DESCRIPTION)
                .inputSchema(inputSchema)
                .parameterTree(parameterTree)
                .returnDescription("Query results in markdown table format")
                .returnTypeHint("str")
                .build();
    }

    /**
     * Build CodeactToolMetadata with code examples.
     */
    private CodeactToolMetadata buildCodeactMetadata() {
        // Python code example
        CodeExample pythonExample = new CodeExample(
                "Execute SQL query and return markdown table",
                """
                # Execute SQL query against a datasource
                result = execute_sql(
                    systemId="my-database",
                    sql="SELECT * FROM users WHERE active = 1",
                    maxRows=100
                )
                print(result)
                """,
                "Returns query results in markdown table format"
        );

        return DefaultCodeactToolMetadata.builder()
                .supportedLanguages(Collections.singletonList(Language.PYTHON))
                .addFewShot(pythonExample)
                .build();
    }
}
