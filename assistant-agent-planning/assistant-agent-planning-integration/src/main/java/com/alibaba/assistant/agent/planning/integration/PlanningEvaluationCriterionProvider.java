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

import com.alibaba.assistant.agent.autoconfigure.evaluation.EvaluationCriterionProvider;
import com.alibaba.assistant.agent.evaluation.builder.EvaluationCriterionBuilder;
import com.alibaba.assistant.agent.evaluation.evaluator.EvaluatorRegistry;
import com.alibaba.assistant.agent.evaluation.model.EvaluationCriterion;
import com.alibaba.assistant.agent.evaluation.model.EvaluatorType;
import com.alibaba.assistant.agent.evaluation.model.ReasoningPolicy;
import com.alibaba.assistant.agent.evaluation.model.ResultType;
import com.alibaba.assistant.agent.planning.config.PlanningExtensionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Planning 模块的评估标准提供者
 *
 * <p>提供动作意图匹配评估标准，用于在评估阶段识别用户意图是否匹配预定义动作。
 *
 * <p>注意：此类由 PlanningIntegrationAutoConfiguration 注册为 Bean，不使用 @Component。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class PlanningEvaluationCriterionProvider implements EvaluationCriterionProvider {

    private static final Logger logger = LoggerFactory.getLogger(PlanningEvaluationCriterionProvider.class);

    private static final String ACTION_INTENT_EVALUATOR_ID = "action_intent_evaluator";
    private static final String ACTION_INTENT_CRITERION_NAME = "action_intent_match";
    private static final String ACTION_INTENT_LLM_VERIFY_CRITERION_NAME = "action_intent_llm_verify";

    private final PlanningExtensionProperties properties;

    public PlanningEvaluationCriterionProvider(PlanningExtensionProperties properties) {
        this.properties = properties;
        logger.info("PlanningEvaluationCriterionProvider#init - initialized, llmVerificationEnabled={}",
                isLLMVerificationEnabled());
    }

    @Override
    public List<EvaluationCriterion> getReactPhaseCriteria() {
        return buildCriteria();
    }

    @Override
    public List<EvaluationCriterion> getCodeActPhaseCriteria() {
        return buildCriteria();
    }

    /**
     * 构建评估准则列表
     */
    private List<EvaluationCriterion> buildCriteria() {
        List<EvaluationCriterion> criteria = new ArrayList<>();

        // 第一层：动作意图匹配（关键词 + 向量）
        criteria.add(createActionIntentCriterion());

        // 第二层：LLM 意图验证（如果启用）
        if (isLLMVerificationEnabled()) {
            criteria.add(createLLMVerifyCriterion());
        }

        return criteria;
    }

    /**
     * 检查是否启用 LLM 验证
     */
    private boolean isLLMVerificationEnabled() {
        return properties != null
                && properties.getEvaluation() != null
                && properties.getEvaluation().isLlmVerificationEnabled();
    }

    /**
     * 创建动作意图匹配评估标准（第一层：快速筛选）
     */
    private EvaluationCriterion createActionIntentCriterion() {
        return EvaluationCriterionBuilder.create(ACTION_INTENT_CRITERION_NAME)
                .description("评估用户输入是否匹配预定义动作（关键词+向量快速筛选）")
                .resultType(ResultType.JSON)
                .evaluatorRef(ACTION_INTENT_EVALUATOR_ID)
                .build();
    }

    /**
     * 创建 LLM 意图验证评估标准（第二层：LLM 精确判断 + 参数提取）
     */
    private EvaluationCriterion createLLMVerifyCriterion() {
        String llmPrompt = """
                你是一个意图识别和参数提取专家。请根据用户输入和候选动作列表：
                1. 判断用户最可能想要执行的动作
                2. 从用户输入中提取动作所需的参数
                3. 检查是否有缺失的必填参数

                ## 用户输入
                {{userInput}}

                ## 候选动作列表（包含参数定义）
                {{action_intent_match}}

                ## 处理要求
                1. 仔细分析用户输入的语义和意图
                2. 选择最匹配的动作，如果没有合适的动作则返回 NO_MATCH
                3. 如果选中了动作，从用户输入中提取该动作需要的参数值
                4. 检查必填参数（required=true）是否都已提取到，如果有缺失则生成追问问题

                ## 返回格式（严格 JSON）
                如果匹配到动作：
                RESULT: {
                    "actionId": "选中的动作ID",
                    "actionName": "动作名称",
                    "confidence": 0.95,
                    "reason": "判断理由",
                    "extractedParams": {"参数名": "提取到的值", ...},
                    "missingParams": ["缺失的必填参数名", ...],
                    "nextQuestion": "如果有缺失参数，生成追问问题，否则为null"
                }

                如果未匹配到动作：
                RESULT: {"actionId": "NO_MATCH", "confidence": 0.95, "reason": "判断理由"}
                """;

        return EvaluationCriterionBuilder.create(ACTION_INTENT_LLM_VERIFY_CRITERION_NAME)
                .description("使用 LLM 验证用户意图，提取参数，检查缺失的必填参数")
                .resultType(ResultType.JSON)
                .evaluatorType(EvaluatorType.LLM_BASED)
                .dependsOn(ACTION_INTENT_CRITERION_NAME)  // 依赖第一层结果
                .contextBindings("context.input.userInput")
                .promptTemplate(llmPrompt)
                .reasoningPolicy(ReasoningPolicy.BRIEF)
                .addFewShot(
                        "添加单位",
                        "{\"status\":\"CANDIDATES\",\"candidates\":[{\"actionId\":\"erp:product-unit:create\",\"actionName\":\"添加产品单位\",\"parameters\":[{\"name\":\"name\",\"label\":\"单位名称\",\"description\":\"产品计量单位的名称\",\"required\":true}]}]}",
                        "{\"actionId\":\"erp:product-unit:create\",\"actionName\":\"添加产品单位\",\"confidence\":0.95,\"reason\":\"用户想添加单位，匹配添加产品单位动作\",\"extractedParams\":{},\"missingParams\":[\"name\"],\"nextQuestion\":\"请输入单位名称\"}"
                )
                .addFewShot(
                        "添加单位 千克",
                        "{\"status\":\"CANDIDATES\",\"candidates\":[{\"actionId\":\"erp:product-unit:create\",\"actionName\":\"添加产品单位\",\"parameters\":[{\"name\":\"name\",\"label\":\"单位名称\",\"description\":\"产品计量单位的名称\",\"required\":true}]}]}",
                        "{\"actionId\":\"erp:product-unit:create\",\"actionName\":\"添加产品单位\",\"confidence\":0.98,\"reason\":\"用户想添加单位千克\",\"extractedParams\":{\"name\":\"千克\"},\"missingParams\":[],\"nextQuestion\":null}"
                )
                .addFewShot(
                        "帮我查一下天气",
                        "{\"status\":\"CANDIDATES\",\"candidates\":[{\"actionId\":\"erp:product-unit:create\",\"actionName\":\"添加产品单位\",\"triggerKeywords\":[\"添加单位\"]}]}",
                        "{\"actionId\":\"NO_MATCH\",\"confidence\":0.95,\"reason\":\"用户想查天气，与候选的添加产品单位动作完全不相关\"}"
                )
                .build();
    }
}
