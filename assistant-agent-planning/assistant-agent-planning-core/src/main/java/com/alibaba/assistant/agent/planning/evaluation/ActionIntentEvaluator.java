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
package com.alibaba.assistant.agent.planning.evaluation;

import com.alibaba.assistant.agent.evaluation.evaluator.Evaluator;
import com.alibaba.assistant.agent.evaluation.model.CriterionExecutionContext;
import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.assistant.agent.evaluation.model.CriterionStatus;
import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionMatch;
import com.alibaba.assistant.agent.planning.spi.ActionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动作意图评估器
 *
 * <p>负责评估用户输入是否匹配预定义动作，并返回匹配结果。
 *
 * <p>评估结果（JSON格式）：
 * <pre>
 * {
 *   "matched": true/false,
 *   "actionId": "动作ID",
 *   "actionName": "动作名称",
 *   "confidence": 0.85,
 *   "parameters": {...},
 *   "missingParameters": [...]
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ActionIntentEvaluator implements Evaluator {

    private static final Logger logger = LoggerFactory.getLogger(ActionIntentEvaluator.class);

    private static final String EVALUATOR_ID = "action_intent_evaluator";

    private final ActionProvider actionProvider;

    public ActionIntentEvaluator(ActionProvider actionProvider) {
        this.actionProvider = actionProvider;
    }

    @Override
    public String getEvaluatorId() {
        return EVALUATOR_ID;
    }

    @Override
    public CriterionResult evaluate(CriterionExecutionContext executionContext) {
        CriterionResult result = new CriterionResult();
        result.setCriterionName(executionContext.getCriterion().getName());
        result.setStartTimeMillis(System.currentTimeMillis());

        try {
            // 获取用户输入
            EvaluationContext inputContext = executionContext.getInputContext();
            String userInput = (String) inputContext.getInputValue("userInput");
            if (userInput == null || userInput.isBlank()) {
                result.setStatus(CriterionStatus.SUCCESS);
                result.setValue(buildNoMatchResult());
                return result;
            }

            // 构建匹配上下文
            Map<String, Object> context = buildMatchContext(executionContext);

            // 执行动作匹配
            List<ActionMatch> matches = actionProvider.matchActions(userInput, context);

            // 构建评估结果
            if (CollectionUtils.isEmpty(matches)) {
                logger.debug("ActionIntentEvaluator#evaluate - reason=no action matched, userInput={}", userInput);
                result.setStatus(CriterionStatus.SUCCESS);
                result.setValue(buildNoMatchResult());
            } else {
                ActionMatch bestMatch = matches.get(0);
                logger.info("ActionIntentEvaluator#evaluate - reason=action matched, actionId={}, confidence={}",
                        bestMatch.getAction().getActionId(), bestMatch.getConfidence());
                result.setStatus(CriterionStatus.SUCCESS);
                result.setValue(buildMatchResult(bestMatch));
            }

        } catch (Exception e) {
            logger.error("ActionIntentEvaluator#evaluate - reason=evaluation failed", e);
            result.setStatus(CriterionStatus.ERROR);
            result.setErrorMessage(e.getMessage());
            result.setValue(buildNoMatchResult());
        } finally {
            result.setEndTimeMillis(System.currentTimeMillis());
        }

        return result;
    }

    /**
     * 构建匹配上下文
     */
    private Map<String, Object> buildMatchContext(CriterionExecutionContext executionContext) {
        Map<String, Object> context = new HashMap<>();

        // 从评估上下文获取信息
        EvaluationContext evalContext = executionContext.getInputContext();
        if (evalContext.getInput() != null) {
            context.putAll(evalContext.getInput());
        }
        if (evalContext.getEnvironment() != null) {
            context.putAll(evalContext.getEnvironment());
        }

        return context;
    }

    /**
     * 构建无匹配结果
     */
    private Map<String, Object> buildNoMatchResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("matched", false);
        return result;
    }

    /**
     * 构建匹配结果
     */
    private Map<String, Object> buildMatchResult(ActionMatch match) {
        ActionDefinition action = match.getAction();
        Map<String, Object> result = new HashMap<>();

        result.put("matched", true);
        result.put("actionId", action.getActionId());
        result.put("actionName", action.getActionName());
        result.put("description", action.getDescription());
        result.put("confidence", match.getConfidence() != null ? match.getConfidence() : 0.0);
        result.put("matchType", match.getMatchType() != null ? match.getMatchType().name() : "UNKNOWN");

        // 提取的参数
        if (match.getExtractedParameters() != null && !match.getExtractedParameters().isEmpty()) {
            result.put("parameters", match.getExtractedParameters());
        }

        // 缺失的参数
        if (match.hasMissingParameters()) {
            result.put("missingParameters", match.getMissingParameters().keySet());
        }

        return result;
    }
}
