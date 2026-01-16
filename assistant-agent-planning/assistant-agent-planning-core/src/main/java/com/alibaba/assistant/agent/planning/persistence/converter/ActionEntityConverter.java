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
package com.alibaba.assistant.agent.planning.persistence.converter;

import com.alibaba.assistant.agent.planning.model.*;
import com.alibaba.assistant.agent.planning.persistence.entity.ActionRegistryEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 动作实体转换器
 *
 * <p>在 ActionRegistryEntity（数据库实体）和 ActionDefinition（领域模型）之间转换。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ActionEntityConverter {

    private static final Logger logger = LoggerFactory.getLogger(ActionEntityConverter.class);
    private final ObjectMapper objectMapper;

    public ActionEntityConverter() {
        this.objectMapper = new ObjectMapper();
    }

    public ActionEntityConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Entity -> ActionDefinition
     */
    public ActionDefinition toDefinition(ActionRegistryEntity entity) {
        if (entity == null) {
            return null;
        }

        // 解析接口绑定：优先使用 interface_binding 字段，否则从 handler + parameters.execution 构建
        StepDefinition.InterfaceBinding interfaceBinding = parseInterfaceBinding(entity.getInterfaceBinding());
        if (interfaceBinding == null && entity.getHandler() != null && entity.getHandler().startsWith("http")) {
            // handler 是 HTTP URL，构建 InterfaceBinding
            interfaceBinding = buildHttpInterfaceBinding(entity.getHandler(), entity.getParameters());
        }

        return ActionDefinition.builder()
                .actionId(entity.getActionId())
                .actionName(entity.getActionName())
                .description(entity.getDescription())
                .actionType(parseActionType(entity.getActionType()))
                .category(entity.getCategory())
                .tags(parseJsonList(entity.getTags()))
                .triggerKeywords(parseJsonList(entity.getTriggerKeywords()))
                .synonyms(parseJsonList(entity.getSynonyms()))
                .exampleInputs(parseJsonList(entity.getExampleInputs()))
                .parameters(parseParameters(entity.getParameters()))
                .steps(parseSteps(entity.getSteps()))
                .stateSchema(parseJsonMap(entity.getStateSchema()))
                .handler(entity.getHandler())
                .interfaceBinding(interfaceBinding)
                .priority(entity.getPriority())
                .timeoutMinutes(entity.getTimeoutMinutes())
                .enabled(entity.getEnabled())
                .requiredPermissions(parseJsonList(entity.getRequiredPermissions()))
                .metadata(parseJsonMap(entity.getMetadata()))
                .build();
    }

    /**
     * 从 handler URL 和 parameters.execution 构建 HTTP InterfaceBinding
     */
    private StepDefinition.InterfaceBinding buildHttpInterfaceBinding(String url, String parametersJson) {
        Map<String, Object> execution = parseExecutionConfig(parametersJson);

        // 构建 HTTP 配置
        StepDefinition.HttpConfig.HttpConfigBuilder httpBuilder = StepDefinition.HttpConfig.builder()
                .url(url);

        if (execution.containsKey("httpMethod")) {
            httpBuilder.method((String) execution.get("httpMethod"));
        } else {
            httpBuilder.method("POST"); // 默认 POST
        }

        // 处理 headers
        Map<String, String> headerMap = new java.util.HashMap<>();
        if (execution.containsKey("contentType")) {
            headerMap.put("Content-Type", execution.get("contentType").toString());
        }
        if (execution.containsKey("headers")) {
            Object headers = execution.get("headers");
            if (headers instanceof Map) {
                ((Map<?, ?>) headers).forEach((k, v) -> headerMap.put(k.toString(), v.toString()));
            }
        }
        if (!headerMap.isEmpty()) {
            httpBuilder.headers(headerMap);
        }

        return StepDefinition.InterfaceBinding.builder()
                .type("HTTP")
                .http(httpBuilder.build())
                .build();
    }

    /**
     * ActionDefinition -> Entity
     */
    public ActionRegistryEntity toEntity(ActionDefinition definition) {
        if (definition == null) {
            return null;
        }

        return ActionRegistryEntity.builder()
                .actionId(definition.getActionId())
                .actionName(definition.getActionName())
                .description(definition.getDescription())
                .actionType(definition.getActionType() != null ? definition.getActionType().name() : null)
                .category(definition.getCategory())
                .tags(toJson(definition.getTags()))
                .triggerKeywords(toJson(definition.getTriggerKeywords()))
                .synonyms(toJson(definition.getSynonyms()))
                .exampleInputs(toJson(definition.getExampleInputs()))
                .parameters(toJson(definition.getParameters()))
                .steps(toJson(definition.getSteps()))
                .stateSchema(toJson(definition.getStateSchema()))
                .handler(definition.getHandler())
                .interfaceBinding(toJson(definition.getInterfaceBinding()))
                .priority(definition.getPriority())
                .timeoutMinutes(definition.getTimeoutMinutes())
                .enabled(definition.getEnabled())
                .requiredPermissions(toJson(definition.getRequiredPermissions()))
                .metadata(toJson(definition.getMetadata()))
                .build();
    }

    private ActionType parseActionType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return ActionType.valueOf(type);
        } catch (IllegalArgumentException e) {
            logger.warn("ActionEntityConverter#parseActionType - reason=unknown action type, type={}", type);
            return null;
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            logger.warn("ActionEntityConverter#parseJsonList - reason=failed to parse json list, error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            logger.warn("ActionEntityConverter#parseJsonMap - reason=failed to parse json map, error={}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<ActionParameter> parseParameters(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            // 首先尝试解析为数组格式
            if (json.trim().startsWith("[")) {
                return objectMapper.readValue(json, new TypeReference<List<ActionParameter>>() {});
            }
            // 如果是对象格式（assistant-management 项目格式），提取 parameters 字段
            Map<String, Object> wrapper = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Object params = wrapper.get("parameters");
            if (params == null) {
                // 可能整个对象就是参数列表的包装
                return Collections.emptyList();
            }
            // 将 parameters 字段转换为 ActionParameter 列表
            String paramsJson = objectMapper.writeValueAsString(params);
            return objectMapper.readValue(paramsJson, new TypeReference<List<ActionParameter>>() {});
        } catch (JsonProcessingException e) {
            logger.warn("ActionEntityConverter#parseParameters - reason=failed to parse parameters, json={}, error={}",
                    json.length() > 100 ? json.substring(0, 100) + "..." : json, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从 parameters JSON 中提取 execution 配置（用于接口绑定）
     */
    public Map<String, Object> parseExecutionConfig(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            if (!parametersJson.trim().startsWith("{")) {
                return Collections.emptyMap();
            }
            Map<String, Object> wrapper = objectMapper.readValue(parametersJson, new TypeReference<Map<String, Object>>() {});
            Object execution = wrapper.get("execution");
            if (execution instanceof Map) {
                return (Map<String, Object>) execution;
            }
            return Collections.emptyMap();
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private List<StepDefinition> parseSteps(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<StepDefinition>>() {});
        } catch (JsonProcessingException e) {
            logger.warn("ActionEntityConverter#parseSteps - reason=failed to parse steps, error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private StepDefinition.InterfaceBinding parseInterfaceBinding(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StepDefinition.InterfaceBinding.class);
        } catch (JsonProcessingException e) {
            logger.warn("ActionEntityConverter#parseInterfaceBinding - reason=failed to parse interface binding, error={}", e.getMessage());
            return null;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.warn("ActionEntityConverter#toJson - reason=failed to serialize to json, error={}", e.getMessage());
            return null;
        }
    }
}
