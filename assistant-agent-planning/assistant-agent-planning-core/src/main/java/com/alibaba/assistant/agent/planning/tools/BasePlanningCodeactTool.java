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
package com.alibaba.assistant.agent.planning.tools;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.*;

/**
 * Planning 工具的 CodeactTool 基础类
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public abstract class BasePlanningCodeactTool implements CodeactTool {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected final String toolName;
    protected final String description;

    private ToolDefinition toolDefinition;
    private CodeactToolDefinition codeactDefinition;
    private CodeactToolMetadata codeactMetadata;

    protected BasePlanningCodeactTool(String toolName, String description) {
        this.toolName = toolName;
        this.description = description;
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        logger.debug("BasePlanningCodeactTool#call - reason=executing tool, toolName={}, toolInput={}",
                toolName, toolInput);

        try {
            Map<String, Object> params = objectMapper.readValue(toolInput, Map.class);
            Object result = execute(params, toolContext);
            String resultJson = objectMapper.writeValueAsString(result);

            logger.info("BasePlanningCodeactTool#call - reason=tool executed successfully, toolName={}", toolName);
            return resultJson;

        } catch (Exception e) {
            logger.error("BasePlanningCodeactTool#call - reason=tool execution failed, toolName={}, error={}",
                    toolName, e.getMessage(), e);

            try {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", e.getMessage());
                return objectMapper.writeValueAsString(errorResult);
            } catch (Exception ex) {
                return "{\"success\":false,\"error\":\"Failed to serialize error result\"}";
            }
        }
    }

    /**
     * 执行工具逻辑，由子类实现
     */
    protected abstract Object execute(Map<String, Object> params, ToolContext toolContext);

    /**
     * 获取参数定义，由子类实现
     */
    protected abstract List<ParameterNode> getParameters();

    /**
     * 获取返回值描述，由子类实现
     */
    protected abstract String getReturnDescription();

    /**
     * 获取代码示例，由子类实现
     */
    protected abstract List<CodeExample> getCodeExamples();

    @Override
    public ToolDefinition getToolDefinition() {
        if (toolDefinition == null) {
            toolDefinition = buildToolDefinition();
        }
        return toolDefinition;
    }

    @Override
    public CodeactToolDefinition getCodeactDefinition() {
        if (codeactDefinition == null) {
            codeactDefinition = buildCodeactDefinition();
        }
        return codeactDefinition;
    }

    @Override
    public CodeactToolMetadata getCodeactMetadata() {
        if (codeactMetadata == null) {
            codeactMetadata = buildCodeactMetadata();
        }
        return codeactMetadata;
    }

    private ToolDefinition buildToolDefinition() {
        String inputSchema = buildInputSchema();
        return ToolDefinition.builder()
                .name(toolName)
                .description(description)
                .inputSchema(inputSchema)
                .build();
    }

    private CodeactToolDefinition buildCodeactDefinition() {
        String inputSchema = buildInputSchema();

        ParameterTree.Builder treeBuilder = ParameterTree.builder().rawInputSchema(inputSchema);

        List<ParameterNode> params = getParameters();
        if (params != null) {
            for (ParameterNode param : params) {
                treeBuilder.addParameter(param);
                if (param.isRequired()) {
                    treeBuilder.addRequiredName(param.getName());
                }
            }
        }

        return DefaultCodeactToolDefinition.builder()
                .name(toolName)
                .description(description)
                .inputSchema(inputSchema)
                .parameterTree(treeBuilder.build())
                .returnDescription(getReturnDescription())
                .returnTypeHint("Dict[str, Any]")
                .build();
    }

    private CodeactToolMetadata buildCodeactMetadata() {
        List<CodeExample> examples = getCodeExamples();
        if (examples == null) {
            examples = new ArrayList<>();
        }

        return DefaultCodeactToolMetadata.builder()
                .addSupportedLanguage(Language.PYTHON)
                .targetClassName("planning_tools")
                .targetClassDescription("动作规划和执行工具集合")
                .fewShots(examples)
                .displayName(toolName)
                .returnDirect(false)
                .build();
    }

    private String buildInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        List<ParameterNode> params = getParameters();
        if (params != null) {
            for (ParameterNode param : params) {
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", mapParameterType(param.getType()));
                prop.put("description", param.getDescription());
                if (param.getDefaultValue() != null) {
                    prop.put("default", param.getDefaultValue());
                }
                properties.put(param.getName(), prop);

                if (param.isRequired()) {
                    required.add(param.getName());
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            logger.error("BasePlanningCodeactTool#buildInputSchema - reason=failed to build schema, error={}",
                    e.getMessage());
            return "{}";
        }
    }

    private String mapParameterType(ParameterType type) {
        if (type == null) {
            return "string";
        }
        return switch (type) {
            case STRING -> "string";
            case INTEGER, NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case ARRAY -> "array";
            case OBJECT -> "object";
            default -> "string";
        };
    }
}
