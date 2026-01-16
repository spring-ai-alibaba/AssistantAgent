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

import com.alibaba.assistant.agent.autoconfigure.evaluation.EvaluationCriterionProvider;
import com.alibaba.assistant.agent.evaluation.builder.EvaluationCriterionBuilder;
import com.alibaba.assistant.agent.evaluation.evaluator.EvaluatorRegistry;
import com.alibaba.assistant.agent.evaluation.model.EvaluationCriterion;
import com.alibaba.assistant.agent.evaluation.model.ResultType;
import com.alibaba.assistant.agent.planning.spi.ActionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * Planning 模块的评估标准提供者
 *
 * <p>提供动作意图匹配评估标准，用于在评估阶段识别用户意图是否匹配预定义动作。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class PlanningEvaluationCriterionProvider implements EvaluationCriterionProvider {

    private static final Logger logger = LoggerFactory.getLogger(PlanningEvaluationCriterionProvider.class);

    private static final String ACTION_INTENT_EVALUATOR_ID = "action_intent_evaluator";
    private static final String ACTION_INTENT_CRITERION_NAME = "action_intent_match";

    private final ActionProvider actionProvider;
    private final EvaluatorRegistry evaluatorRegistry;

    public PlanningEvaluationCriterionProvider(ActionProvider actionProvider, EvaluatorRegistry evaluatorRegistry) {
        this.actionProvider = actionProvider;
        this.evaluatorRegistry = evaluatorRegistry;
    }

    /**
     * 初始化方法 - 在Bean完全构造后注册evaluator，避免循环依赖
     */
    @PostConstruct
    public void init() {
        registerActionIntentEvaluator();
    }

    /**
     * 注册动作意图评估器
     */
    private void registerActionIntentEvaluator() {
        if (evaluatorRegistry != null) {
            ActionIntentEvaluator evaluator = new ActionIntentEvaluator(actionProvider);
            evaluatorRegistry.registerEvaluator(evaluator);
            logger.info("PlanningEvaluationCriterionProvider#registerActionIntentEvaluator - reason=registered action intent evaluator");
        } else {
            logger.warn("PlanningEvaluationCriterionProvider#registerActionIntentEvaluator - reason=EvaluatorRegistry is null, skipping registration");
        }
    }

    @Override
    public List<EvaluationCriterion> getReactPhaseCriteria() {
        // React 阶段也需要动作意图评估
        return List.of(createActionIntentCriterion());
    }

    @Override
    public List<EvaluationCriterion> getCodeActPhaseCriteria() {
        // CodeAct 阶段也需要动作意图评估
        return List.of(createActionIntentCriterion());
    }

    /**
     * 创建动作意图匹配评估标准
     */
    private EvaluationCriterion createActionIntentCriterion() {
        return EvaluationCriterionBuilder.create(ACTION_INTENT_CRITERION_NAME)
                .description("评估用户输入是否匹配预定义动作")
                .resultType(ResultType.JSON)
                .evaluatorRef(ACTION_INTENT_EVALUATOR_ID)
                .build();
    }
}
