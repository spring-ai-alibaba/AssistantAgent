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
import com.alibaba.assistant.agent.planning.spi.ActionProvider;
import org.springframework.ai.chat.model.ToolContext;

import java.util.*;

/**
 * 列出动作工具
 *
 * <p>列出所有可用的动作。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ListActionsCodeactTool extends BasePlanningCodeactTool {

    private final ActionProvider actionProvider;

    public ListActionsCodeactTool(ActionProvider actionProvider) {
        super("list_actions", "列出所有可用的动作，支持按分类和标签筛选。");
        this.actionProvider = actionProvider;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        String category = (String) params.get("category");
        String keyword = (String) params.get("keyword");
        Integer limit = 20;
        if (params.containsKey("limit")) {
            Object limitObj = params.get("limit");
            if (limitObj instanceof Number) {
                limit = ((Number) limitObj).intValue();
            }
        }

        List<String> tags = null;
        if (params.containsKey("tags")) {
            Object tagsObj = params.get("tags");
            if (tagsObj instanceof List) {
                tags = (List<String>) tagsObj;
            }
        }

        logger.info("ListActionsCodeactTool#execute - reason=listing actions, category={}, keyword={}, limit={}",
                category, keyword, limit);

        List<ActionDefinition> actions;

        if (keyword != null && !keyword.isBlank()) {
            // 关键词搜索
            actions = actionProvider.searchActions(keyword, limit);
        } else if (category != null && !category.isBlank()) {
            // 按分类筛选
            actions = actionProvider.getActionsByCategory(category);
        } else if (tags != null && !tags.isEmpty()) {
            // 按标签筛选
            actions = actionProvider.getActionsByTags(tags);
        } else {
            // 获取所有动作
            actions = actionProvider.getAllActions();
        }

        // 限制数量
        if (actions.size() > limit) {
            actions = actions.subList(0, limit);
        }

        // 构建结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("total", actions.size());
        result.put("actions", buildActionList(actions));
        result.put("categories", actionProvider.getAllCategories());
        result.put("tags", actionProvider.getAllTags());

        return result;
    }

    private List<Map<String, Object>> buildActionList(List<ActionDefinition> actions) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ActionDefinition action : actions) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("action_id", action.getActionId());
            info.put("action_name", action.getActionName());
            info.put("description", action.getDescription());
            info.put("category", action.getCategory());
            info.put("action_type", action.getActionType() != null ? action.getActionType().name() : null);
            info.put("tags", action.getTags());
            info.put("is_multi_step", action.isMultiStep());
            result.add(info);
        }
        return result;
    }

    @Override
    protected List<ParameterNode> getParameters() {
        return List.of(
                ParameterNode.builder()
                        .name("category")
                        .type(ParameterType.STRING)
                        .description("按分类筛选动作")
                        .required(false)
                        .build(),
                ParameterNode.builder()
                        .name("tags")
                        .type(ParameterType.ARRAY)
                        .description("按标签筛选动作")
                        .required(false)
                        .build(),
                ParameterNode.builder()
                        .name("keyword")
                        .type(ParameterType.STRING)
                        .description("搜索关键词")
                        .required(false)
                        .build(),
                ParameterNode.builder()
                        .name("limit")
                        .type(ParameterType.INTEGER)
                        .description("最大返回数量")
                        .required(false)
                        .defaultValue(20)
                        .build()
        );
    }

    @Override
    protected String getReturnDescription() {
        return "返回动作列表，包含每个动作的基本信息";
    }

    @Override
    protected List<CodeExample> getCodeExamples() {
        List<CodeExample> examples = new ArrayList<>();

        examples.add(new CodeExample(
                "列出所有动作",
                """
                # 列出所有可用动作
                result = list_actions()
                print(f"共有 {result['total']} 个动作")
                for action in result['actions']:
                    print(f"- {action['action_name']}: {action['description']}")
                """,
                "返回所有动作列表"
        ));

        examples.add(new CodeExample(
                "按分类筛选",
                """
                # 按分类筛选动作
                result = list_actions(category="产品管理")
                print(f"产品管理分类下有 {result['total']} 个动作")
                """,
                "返回指定分类的动作"
        ));

        return examples;
    }
}
