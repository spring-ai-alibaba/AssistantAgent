/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import com.alibaba.assistant.agent.data.spi.Nl2SqlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * CodeactTool for converting natural language to SQL.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class Nl2SqlCodeactTool implements CodeactTool {

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlCodeactTool.class);

    private static final String TOOL_NAME = "nl2sql";

    private static final String DESCRIPTION =
            "Convert natural language to SQL query. " +
            "Returns the generated SQL statement that can be used with execute_sql tool. " +
            "Useful for translating user questions into database queries.";

    private final Nl2SqlService nl2SqlService;
    private final CodeactToolMetadata codeactMetadata;
    private final ToolDefinition toolDefinition;
    private final CodeactToolDefinition codeactDefinition;
    private final ObjectMapper objectMapper;

    public Nl2SqlCodeactTool(Nl2SqlService nl2SqlService) {
        this.nl2SqlService = nl2SqlService;
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
        log.debug("Nl2SqlCodeactTool#call - toolInput={}", toolInput);

        try {
            Map<String, Object> params = objectMapper.readValue(toolInput, Map.class);

            String systemId = (String) params.get("systemId");
            String query = (String) params.get("query");
            String evidence = (String) params.get("evidence");

            if (systemId == null || systemId.trim().isEmpty()) {
                return "Error: systemId parameter is required";
            }

            if (query == null || query.trim().isEmpty()) {
                return "Error: query parameter is required";
            }

            String sql = nl2SqlService.generateSql(systemId, query, evidence);

            log.info("Nl2SqlCodeactTool#call - SQL generated successfully, systemId={}", systemId);

            return "Generated SQL:\n```sql\n" + sql + "\n```\n\n" +
                   "You can now execute this SQL using execute_sql tool.";

        } catch (Exception e) {
            log.error("Nl2SqlCodeactTool#call - Error generating SQL", e);
            return "Error generating SQL: " + e.getMessage();
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
                        "query": {
                            "type": "string",
                            "description": "Natural language query description"
                        },
                        "evidence": {
                            "type": "string",
                            "description": "Additional context or evidence (optional)"
                        }
                    },
                    "required": ["systemId", "query"]
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
                        .name("query")
                        .type(ParameterType.STRING)
                        .description("Natural language query description")
                        .required(true)
                        .build())
                .addParameter(ParameterNode.builder()
                        .name("evidence")
                        .type(ParameterType.STRING)
                        .description("Additional context or evidence (optional)")
                        .required(false)
                        .build())
                .addRequiredName("systemId")
                .addRequiredName("query")
                .build();

        return DefaultCodeactToolDefinition.builder()
                .name(TOOL_NAME)
                .description(DESCRIPTION)
                .inputSchema(inputSchema)
                .parameterTree(parameterTree)
                .returnDescription("Generated SQL statement wrapped in markdown code block")
                .returnTypeHint("str")
                .build();
    }

    private CodeactToolMetadata buildCodeactMetadata() {
        CodeExample pythonExample = new CodeExample(
                "Convert natural language to SQL",
                """
                # Convert natural language query to SQL
                result = nl2sql(
                    systemId="my-database",
                    query="查询最近30天的活跃用户数量",
                    evidence="活跃用户定义为有登录记录的用户"
                )
                print(result)

                # Then execute the generated SQL
                # sql_result = execute_sql(systemId="my-database", sql=generated_sql)
                """,
                "Returns generated SQL statement in markdown code block"
        );

        return DefaultCodeactToolMetadata.builder()
                .supportedLanguages(Collections.singletonList(Language.PYTHON))
                .addFewShot(pythonExample)
                .build();
    }
}
