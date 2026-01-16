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

import com.alibaba.assistant.agent.common.tools.CodeExample;
import com.alibaba.assistant.agent.common.tools.definition.ParameterNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterType;
import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionParameter;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.alibaba.assistant.agent.planning.spi.ActionProvider;
import org.springframework.ai.chat.model.ToolContext;

import java.util.*;

/**
 * 获取动作详情工具
 *
 * <p>获取指定动作的完整定义。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class GetActionDetailsCodeactTool extends BasePlanningCodeactTool {

    private final ActionProvider actionProvider;

    public GetActionDetailsCodeactTool(ActionProvider actionProvider) {
        super("get_action_details", "获取指定动作的完整定义，包括参数、步骤等详细信息。");
        this.actionProvider = actionProvider;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        String actionId = (String) params.get("action_id");
        String actionName = (String) params.get("action_name");

        if ((actionId == null || actionId.isBlank()) && (actionName == null || actionName.isBlank())) {
            return Map.of("success", false, "error", "Either action_id or action_name is required");
        }

        logger.info("GetActionDetailsCodeactTool#execute - reason=getting action details, actionId={}, actionName={}",
                actionId, actionName);

        Optional<ActionDefinition> actionOpt;
        if (actionId != null && !actionId.isBlank()) {
            actionOpt = actionProvider.getAction(actionId);
        } else {
            actionOpt = actionProvider.getActionByName(actionName);
        }

        if (actionOpt.isEmpty()) {
            return Map.of("success", false, "error", "Action not found");
        }

        ActionDefinition action = actionOpt.get();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("action", buildActionDetails(action));

        return result;
    }

    private Map<String, Object> buildActionDetails(ActionDefinition action) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("action_id", action.getActionId());
        details.put("action_name", action.getActionName());
        details.put("description", action.getDescription());
        details.put("category", action.getCategory());
        details.put("action_type", action.getActionType() != null ? action.getActionType().name() : null);
        details.put("tags", action.getTags());
        details.put("trigger_keywords", action.getTriggerKeywords());
        details.put("synonyms", action.getSynonyms());
        details.put("example_inputs", action.getExampleInputs());
        details.put("priority", action.getPriority());
        details.put("timeout_minutes", action.getTimeoutMinutes());
        details.put("enabled", action.getEnabled());
        details.put("is_multi_step", action.isMultiStep());

        // 参数详情
        if (action.getParameters() != null && !action.getParameters().isEmpty()) {
            details.put("parameters", buildParameterList(action.getParameters()));
        }

        // 步骤详情（多步骤动作）
        if (action.getSteps() != null && !action.getSteps().isEmpty()) {
            details.put("steps", buildStepList(action.getSteps()));
        }

        // 必填参数
        List<ActionParameter> requiredParams = action.getRequiredParameters();
        if (!requiredParams.isEmpty()) {
            details.put("required_parameters", buildParameterList(requiredParams));
        }

        return details;
    }

    private List<Map<String, Object>> buildParameterList(List<ActionParameter> parameters) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ActionParameter param : parameters) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", param.getName());
            info.put("label", param.getLabel());
            info.put("type", param.getType());
            info.put("required", param.getRequired());
            info.put("description", param.getDescription());
            info.put("default_value", param.getDefaultValue());
            info.put("placeholder", param.getPlaceholder());

            if (param.getEnumValues() != null) {
                info.put("enum_values", param.getEnumValues());
            }
            if (param.getMinLength() != null) {
                info.put("min_length", param.getMinLength());
            }
            if (param.getMaxLength() != null) {
                info.put("max_length", param.getMaxLength());
            }
            if (param.getPattern() != null) {
                info.put("pattern", param.getPattern());
            }
            if (param.getSource() != null) {
                info.put("source", param.getSource().name());
            }
            if (param.getForeignKey() != null) {
                Map<String, Object> fkInfo = new LinkedHashMap<>();
                fkInfo.put("entity", param.getForeignKey().getEntity());
                fkInfo.put("field", param.getForeignKey().getField());
                fkInfo.put("display_field", param.getForeignKey().getDisplayField());
                info.put("foreign_key", fkInfo);
            }

            result.add(info);
        }
        return result;
    }

    private List<Map<String, Object>> buildStepList(List<StepDefinition> steps) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (StepDefinition step : steps) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("step_id", step.getStepId());
            info.put("name", step.getName());
            info.put("description", step.getDescription());
            info.put("type", step.getType() != null ? step.getType().name() : null);
            info.put("order", step.getOrder());
            info.put("skippable", step.getSkippable());
            info.put("interrupt_for_input", step.getInterruptForInput());
            info.put("user_prompt", step.getUserPrompt());

            if (step.getInputParams() != null) {
                info.put("input_params", buildParameterList(step.getInputParams()));
            }

            if (step.getInterfaceBinding() != null) {
                Map<String, Object> binding = new LinkedHashMap<>();
                binding.put("type", step.getInterfaceBinding().getType());
                info.put("interface_binding", binding);
            }

            result.add(info);
        }
        return result;
    }

    @Override
    protected List<ParameterNode> getParameters() {
        return List.of(
                ParameterNode.builder()
                        .name("action_id")
                        .type(ParameterType.STRING)
                        .description("动作 ID")
                        .required(false)
                        .build(),
                ParameterNode.builder()
                        .name("action_name")
                        .type(ParameterType.STRING)
                        .description("动作名称")
                        .required(false)
                        .build()
        );
    }

    @Override
    protected String getReturnDescription() {
        return "返回动作的完整定义，包括参数、步骤等详细信息";
    }

    @Override
    protected List<CodeExample> getCodeExamples() {
        List<CodeExample> examples = new ArrayList<>();

        examples.add(new CodeExample(
                "获取动作详情",
                """
                # 获取动作详情
                result = get_action_details(action_id="add_product")
                if result['success']:
                    action = result['action']
                    print(f"动作: {action['action_name']}")
                    print(f"描述: {action['description']}")
                    print(f"参数数量: {len(action.get('parameters', []))}")
                    if action['is_multi_step']:
                        print(f"步骤数量: {len(action.get('steps', []))}")
                """,
                "返回动作完整定义"
        ));

        return examples;
    }
}
