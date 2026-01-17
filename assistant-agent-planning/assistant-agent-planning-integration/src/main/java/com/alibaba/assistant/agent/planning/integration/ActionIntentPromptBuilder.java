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
package com.alibaba.assistant.agent.planning.integration;

import com.alibaba.assistant.agent.prompt.PromptBuilder;
import com.alibaba.assistant.agent.prompt.PromptContribution;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 动作意图提示构建器
 *
 * <p>根据动作匹配评估结果，生成相应的提示注入到LLM请求中。
 *
 * <p>处理策略（Hook已处理>= 0.95的高置信度）：
 * <ul>
 *     <li>>= 0.7（中等置信度）：注入动作提示，建议使用 planning 工具</li>
 *     <li>< 0.7（低置信度）：不注入提示</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
@ConditionalOnClass(PromptBuilder.class)
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning.evaluation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ActionIntentPromptBuilder implements PromptBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ActionIntentPromptBuilder.class);

    /**
     * 评估结果在context中的key
     */
    private static final String EVALUATION_CONTEXT_KEY = "evaluation";

    /**
     * 动作意图匹配criterion名称
     */
    private static final String ACTION_INTENT_CRITERION_NAME = "action_intent_match";

    /**
     * 中等置信度阈值
     */
    private static final double HINT_THRESHOLD = 0.7;

    /**
     * 优先级（在评估Hook之后执行）
     */
    private final int priority;

    public ActionIntentPromptBuilder() {
        this(100); // 默认优先级100
    }

    public ActionIntentPromptBuilder(int priority) {
        this.priority = priority;
    }

    @Override
    public boolean match(ModelRequest request) {
        Map<String, Object> context = request.getContext();
        if (context == null || context.isEmpty()) {
            return false;
        }

        // 检查是否有评估结果
        Object evaluation = context.get(EVALUATION_CONTEXT_KEY);
        if (evaluation == null) {
            return false;
        }

        // 检查是否有动作意图匹配结果
        Map<String, Object> actionIntent = extractActionIntent(evaluation);
        if (actionIntent == null || actionIntent.isEmpty()) {
            return false;
        }

        // 检查是否匹配成功
        Boolean matched = (Boolean) actionIntent.get("matched");
        if (matched == null || !matched) {
            return false;
        }

        // 检查置信度是否在中等范围
        Double confidence = getConfidence(actionIntent);
        if (confidence == null || confidence < HINT_THRESHOLD) {
            // 低置信度，不注入提示
            return false;
        }

        // 高置信度(>= 0.95)已由Hook处理，这里只处理中等置信度
        if (confidence >= 0.95) {
            return false;
        }

        logger.debug("ActionIntentPromptBuilder#match - reason=match success, confidence={}", confidence);
        return true;
    }

    @Override
    public PromptContribution build(ModelRequest request) {
        logger.info("ActionIntentPromptBuilder#build - reason=building action intent prompt");

        Map<String, Object> context = request.getContext();
        Object evaluationObj = context.get(EVALUATION_CONTEXT_KEY);

        Map<String, Object> actionIntent = extractActionIntent(evaluationObj);
        if (actionIntent == null || actionIntent.isEmpty()) {
            return PromptContribution.empty();
        }

        String promptText = generatePrompt(actionIntent);

        if (promptText == null || promptText.isBlank()) {
            return PromptContribution.empty();
        }

        logger.info("ActionIntentPromptBuilder#build - reason=prompt generated, actionId={}, confidence={}",
                actionIntent.get("actionId"), actionIntent.get("confidence"));

        return PromptContribution.builder()
                .systemTextToAppend(promptText)
                .build();
    }

    @Override
    public int priority() {
        return priority;
    }

    /**
     * 从评估结果中提取动作意图
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractActionIntent(Object evaluation) {
        if (!(evaluation instanceof Map)) {
            return null;
        }

        Map<String, Object> evalMap = (Map<String, Object>) evaluation;

        // 尝试从不同路径获取
        // 1. 从 criteriaResults 直接获取
        Object criteriaResults = evalMap.get("criteriaResults");
        if (criteriaResults instanceof Map) {
            Map<String, Object> criteriaMap = (Map<String, Object>) criteriaResults;
            Object criterionResult = criteriaMap.get(ACTION_INTENT_CRITERION_NAME);
            if (criterionResult instanceof Map) {
                Map<String, Object> resultMap = (Map<String, Object>) criterionResult;
                Object value = resultMap.get("value");
                if (value instanceof Map) {
                    return (Map<String, Object>) value;
                }
            }
        }

        // 2. 从其他可能的路径获取
        Object inputRouting = evalMap.get("inputRouting");
        if (inputRouting instanceof Map) {
            Map<String, Object> routingMap = (Map<String, Object>) inputRouting;
            Object routingCriteria = routingMap.get("criteriaResults");
            if (routingCriteria instanceof Map) {
                Map<String, Object> criteriaMap = (Map<String, Object>) routingCriteria;
                Object criterionResult = criteriaMap.get(ACTION_INTENT_CRITERION_NAME);
                if (criterionResult instanceof Map) {
                    Map<String, Object> resultMap = (Map<String, Object>) criterionResult;
                    Object value = resultMap.get("value");
                    if (value instanceof Map) {
                        return (Map<String, Object>) value;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 获取置信度
     */
    private Double getConfidence(Map<String, Object> actionIntent) {
        Object confidence = actionIntent.get("confidence");
        if (confidence instanceof Number) {
            return ((Number) confidence).doubleValue();
        }
        return null;
    }

    /**
     * 生成提示文本
     */
    private String generatePrompt(Map<String, Object> actionIntent) {
        String actionId = (String) actionIntent.get("actionId");
        String actionName = (String) actionIntent.get("actionName");
        String description = (String) actionIntent.get("description");
        Double confidence = getConfidence(actionIntent);
        Object parameters = actionIntent.get("parameters");
        Object missingParameters = actionIntent.get("missingParameters");

        StringBuilder prompt = new StringBuilder();
        prompt.append("\n\n【系统提示 - 检测到预定义动作】\n");
        prompt.append("根据用户输入，检测到可能匹配以下预定义动作：\n\n");
        prompt.append("- 动作ID: ").append(actionId).append("\n");
        prompt.append("- 动作名称: ").append(actionName).append("\n");
        prompt.append("- 置信度: ").append(String.format("%.2f", confidence)).append("\n");
        if (description != null && !description.isBlank()) {
            prompt.append("- 描述: ").append(description).append("\n");
        }

        if (parameters != null) {
            prompt.append("- 已提取参数: ").append(JSON.toJSONString(parameters)).append("\n");
        }

        if (missingParameters != null) {
            prompt.append("- 缺失参数: ").append(JSON.toJSONString(missingParameters)).append("\n");
        }

        prompt.append("\n建议操作：\n");
        if (confidence >= 0.85) {
            prompt.append("1. 置信度较高（>= 0.85），建议使用 plan_action 工具生成执行计划\n");
            prompt.append("2. 使用 execute_action 工具执行计划\n");
            if (missingParameters != null) {
                prompt.append("3. 注意：缺失部分参数，执行前可能需要向用户确认\n");
            }
        } else {
            prompt.append("1. 置信度中等（0.7-0.85），建议：\n");
            prompt.append("   - 可以使用 plan_action 工具尝试执行\n");
            prompt.append("   - 或向用户确认意图后再执行\n");
        }

        prompt.append("\n");

        return prompt.toString();
    }
}
