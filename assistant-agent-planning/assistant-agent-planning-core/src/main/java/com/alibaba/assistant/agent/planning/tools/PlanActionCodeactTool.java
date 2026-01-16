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
import com.alibaba.assistant.agent.planning.model.ActionMatch;
import com.alibaba.assistant.agent.planning.model.ExecutionPlan;
import com.alibaba.assistant.agent.planning.spi.ActionProvider;
import com.alibaba.assistant.agent.planning.spi.PlanGenerator;
import org.springframework.ai.chat.model.ToolContext;

import java.util.*;

/**
 * 动作规划工具
 *
 * <p>根据用户输入匹配动作并生成执行计划。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class PlanActionCodeactTool extends BasePlanningCodeactTool {

    private final ActionProvider actionProvider;
    private final PlanGenerator planGenerator;

    public PlanActionCodeactTool(ActionProvider actionProvider, PlanGenerator planGenerator) {
        super("plan_action", "根据用户输入匹配动作并生成执行计划。返回匹配的动作列表和生成的执行计划。");
        this.actionProvider = actionProvider;
        this.planGenerator = planGenerator;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        String userInput = (String) params.get("user_input");
        if (userInput == null || userInput.isBlank()) {
            return Map.of("success", false, "error", "user_input is required");
        }

        Map<String, Object> context = new HashMap<>();
        if (params.containsKey("context")) {
            Object ctxParam = params.get("context");
            if (ctxParam instanceof Map) {
                context.putAll((Map<String, Object>) ctxParam);
            }
        }

        // 从 ToolContext 获取会话信息
        String sessionId = null;
        String userId = null;
        if (toolContext != null) {
            sessionId = (String) toolContext.getContext().get("sessionId");
            userId = (String) toolContext.getContext().get("userId");
        }

        logger.info("PlanActionCodeactTool#execute - reason=planning action, userInput={}", userInput);

        // 匹配动作
        List<ActionMatch> matches = actionProvider.matchActions(userInput, context);

        if (matches.isEmpty()) {
            return Map.of(
                    "success", false,
                    "message", "未找到匹配的动作",
                    "matched_actions", Collections.emptyList()
            );
        }

        // 获取最佳匹配
        ActionMatch bestMatch = matches.get(0);

        // 生成执行计划
        ExecutionPlan plan = null;
        if (bestMatch.isHighConfidence()) {
            String finalSessionId = sessionId;
            String finalUserId = userId;

            PlanGenerator.PlanGenerationContext genContext = new PlanGenerator.PlanGenerationContext() {
                @Override
                public String getSessionId() {
                    return finalSessionId;
                }

                @Override
                public String getUserId() {
                    return finalUserId;
                }

                @Override
                public String getUserInput() {
                    return userInput;
                }

                @Override
                public Map<String, Object> getContextVariables() {
                    return context;
                }

                @Override
                public Integer getTimeoutMinutes() {
                    return 30;
                }
            };

            plan = planGenerator.generate(
                    bestMatch.getAction(),
                    bestMatch.getExtractedParameters(),
                    genContext
            );
        }

        // 构建结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("matched_actions", buildMatchedActions(matches));
        result.put("best_match", buildMatchInfo(bestMatch));

        if (plan != null) {
            result.put("plan", buildPlanInfo(plan));
        }

        if (bestMatch.hasMissingParameters()) {
            result.put("missing_parameters", buildMissingParams(bestMatch));
        }

        return result;
    }

    private List<Map<String, Object>> buildMatchedActions(List<ActionMatch> matches) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ActionMatch match : matches) {
            result.add(buildMatchInfo(match));
        }
        return result;
    }

    private Map<String, Object> buildMatchInfo(ActionMatch match) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("action_id", match.getAction().getActionId());
        info.put("action_name", match.getAction().getActionName());
        info.put("description", match.getAction().getDescription());
        info.put("confidence", match.getConfidence());
        info.put("match_type", match.getMatchType() != null ? match.getMatchType().name() : null);
        if (match.getExtractedParameters() != null) {
            info.put("extracted_parameters", match.getExtractedParameters());
        }
        return info;
    }

    private Map<String, Object> buildPlanInfo(ExecutionPlan plan) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("plan_id", plan.getPlanId());
        info.put("action_id", plan.getActionId());
        info.put("action_name", plan.getActionName());
        info.put("status", plan.getStatus().name());
        info.put("step_count", plan.getSteps() != null ? plan.getSteps().size() : 0);
        info.put("extracted_parameters", plan.getExtractedParameters());
        return info;
    }

    private List<Map<String, Object>> buildMissingParams(ActionMatch match) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (match.getMissingParameters() != null) {
            match.getMissingParameters().forEach((name, param) -> {
                Map<String, Object> paramInfo = new LinkedHashMap<>();
                paramInfo.put("name", name);
                paramInfo.put("label", param.getLabel());
                paramInfo.put("type", param.getType());
                paramInfo.put("description", param.getDescription());
                result.add(paramInfo);
            });
        }
        return result;
    }

    @Override
    protected List<ParameterNode> getParameters() {
        return List.of(
                ParameterNode.builder()
                        .name("user_input")
                        .type(ParameterType.STRING)
                        .description("用户的自然语言输入，用于匹配动作")
                        .required(true)
                        .build(),
                ParameterNode.builder()
                        .name("context")
                        .type(ParameterType.OBJECT)
                        .description("上下文信息，如 userId, sessionId 等")
                        .required(false)
                        .build()
        );
    }

    @Override
    protected String getReturnDescription() {
        return "返回匹配的动作列表、最佳匹配、执行计划（如果高置信度）和缺失参数（如果有）";
    }

    @Override
    protected List<CodeExample> getCodeExamples() {
        List<CodeExample> examples = new ArrayList<>();

        examples.add(new CodeExample(
                "规划动作",
                """
                # 根据用户输入规划动作
                result = plan_action(user_input="添加产品，名称为超跑电动车，品牌Tesla")
                if result['success']:
                    print(f"匹配到动作: {result['best_match']['action_name']}")
                    print(f"置信度: {result['best_match']['confidence']}")
                    if 'plan' in result:
                        print(f"生成计划: {result['plan']['plan_id']}")
                """,
                "返回匹配结果和执行计划"
        ));

        return examples;
    }
}
