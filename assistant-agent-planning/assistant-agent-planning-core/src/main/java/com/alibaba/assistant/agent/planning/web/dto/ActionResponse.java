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
package com.alibaba.assistant.agent.planning.web.dto;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionParameter;
import com.alibaba.assistant.agent.planning.model.ActionType;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 动作响应
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResponse {

    private String actionId;
    private String actionName;
    private String description;
    private ActionType actionType;
    private String category;
    private List<String> tags;
    private List<String> triggerKeywords;
    private List<String> synonyms;
    private List<String> exampleInputs;
    private List<ActionParameter> parameters;
    private List<StepDefinition> steps;
    private Map<String, Object> stateSchema;
    private String handler;
    private StepDefinition.InterfaceBinding interfaceBinding;
    private Integer priority;
    private Integer timeoutMinutes;
    private Boolean enabled;
    private List<String> requiredPermissions;
    private Map<String, Object> metadata;

    /**
     * 从 ActionDefinition 转换
     */
    public static ActionResponse from(ActionDefinition definition) {
        if (definition == null) {
            return null;
        }
        return ActionResponse.builder()
                .actionId(definition.getActionId())
                .actionName(definition.getActionName())
                .description(definition.getDescription())
                .actionType(definition.getActionType())
                .category(definition.getCategory())
                .tags(definition.getTags())
                .triggerKeywords(definition.getTriggerKeywords())
                .synonyms(definition.getSynonyms())
                .exampleInputs(definition.getExampleInputs())
                .parameters(definition.getParameters())
                .steps(definition.getSteps())
                .stateSchema(definition.getStateSchema())
                .handler(definition.getHandler())
                .interfaceBinding(definition.getInterfaceBinding())
                .priority(definition.getPriority())
                .timeoutMinutes(definition.getTimeoutMinutes())
                .enabled(definition.getEnabled())
                .requiredPermissions(definition.getRequiredPermissions())
                .metadata(definition.getMetadata())
                .build();
    }
}
