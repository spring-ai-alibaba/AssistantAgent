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
package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.evaluation.builder.EvaluationCriterionBuilder;
import com.alibaba.assistant.agent.evaluation.model.EvaluationCriterion;
import com.alibaba.assistant.agent.evaluation.model.EvaluatorType;
import com.alibaba.assistant.agent.evaluation.model.ReasoningPolicy;
import com.alibaba.assistant.agent.evaluation.model.ResultType;
import com.alibaba.assistant.agent.autoconfigure.evaluation.EvaluationCriterionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 经验评估 Criterion 提供者
 *
 * <p>Example 层只负责定义 Criterion 的结构和数据，不包含评估器创建逻辑。
 * 评估器由 Starter 层自动装配。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(
    prefix = "spring.ai.alibaba.codeact.starter.evaluation.experience",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ExperienceEvaluationCriterionProvider implements EvaluationCriterionProvider {

    private static final Logger log = LoggerFactory.getLogger(ExperienceEvaluationCriterionProvider.class);

    public ExperienceEvaluationCriterionProvider() {
        log.info("ExperienceEvaluationCriterionProvider#<init> - reason=初始化经验评估 Criterion 提供者");
    }

    @Override
    public List<EvaluationCriterion> getReactPhaseCriteria() {
        log.info("ExperienceEvaluationCriterionProvider#getReactPhaseCriteria - reason=提供 React 阶段评估 Criteria");

        // 1. 用户输入增强 Criterion（作为 react_experience_retrieval 的依赖）
        EvaluationCriterion enhancedUserInput = EvaluationCriterionBuilder
            .create("enhanced_user_input")
            .description("改写用户输入，使其更加清晰、完整")
            .resultType(ResultType.TEXT)
            .workingMechanism(
                "你是一个用户输入优化专家。请根据用户输入，改写并完善需求。" +
                "改写要求：" +
                "1. 补全隐含的约束条件" +
                "2. 明确输入输出格式" +
                "3. 保持原始意图不变" +
                "4. 直接输出改写后的内容，不要添加任何解释"
            )
            .reasoningPolicy(ReasoningPolicy.NONE)
            .evaluatorType(EvaluatorType.LLM_BASED)
            .evaluatorRef("llm-based")
            .contextBindings("context.userInput")
            .build();

        // 2. 模糊程度判断 Criterion
        EvaluationCriterion isFuzzy = EvaluationCriterionBuilder
            .create("is_fuzzy")
            .description("判断用户输入是否模糊，是否需要向用户澄清")
            .resultType(ResultType.ENUM)
            .options("模糊", "清晰")
            .workingMechanism(
                "你是一个用户意图清晰度判断专家。请判断用户输入是否明确。\n\n" +
                "【模糊】的特征：\n" +
                "- 没有具体指明要做什么操作\n" +
                "- 使用模糊词汇如'帮我看看'、'处理一下'\n" +
                "- 请求可能有多种理解方式\n" +
                "- 缺少必要的参数或信息\n\n" +
                "【清晰】的特征：\n" +
                "- 明确提到要查询、搜索、获取某个具体信息\n" +
                "- 提供了具体的查询关键词或目标\n" +
                "- 意图明确，执行路径清晰\n\n" +
                "请直接输出'模糊'或'清晰'，不要添加任何解释。"
            )
            .reasoningPolicy(ReasoningPolicy.NONE)
            .evaluatorType(EvaluatorType.LLM_BASED)
            .evaluatorRef("llm-based")
            .contextBindings("userInput")
            .build();

        EvaluationCriterion reactExperienceRetrieval = EvaluationCriterionBuilder
            .create("react_experience_retrieval")
            .description("基于增强后的用户输入检索常识经验和React策略经验")
            .resultType(ResultType.TEXT)
            .evaluatorType(EvaluatorType.RULE_BASED)
            .evaluatorRef("react_experience_evaluator")
            .dependsOn("enhanced_user_input")
            .contextBindings("userInput")
            .build();

        return List.of(enhancedUserInput, isFuzzy, reactExperienceRetrieval);
    }

    @Override
    public List<EvaluationCriterion> getCodeActPhaseCriteria() {
        log.info("ExperienceEvaluationCriterionProvider#getCodeActPhaseCriteria - reason=提供 CodeAct 阶段评估 Criteria");

        // 1. 用户输入增强 Criterion（作为 codeact_experience_retrieval 的依赖）
        EvaluationCriterion enhancedUserInput = EvaluationCriterionBuilder
            .create("enhanced_user_input")
            .description("改写用户输入，使其更加清晰、完整")
            .resultType(ResultType.TEXT)
            .workingMechanism(
                "你是一个用户输入优化专家。请根据用户输入，改写并完善需求。" +
                "改写要求：" +
                "1. 补全隐含的约束条件" +
                "2. 明确输入输出格式" +
                "3. 保持原始意图不变" +
                "4. 直接输出改写后的内容，不要添加任何解释"
            )
            .reasoningPolicy(ReasoningPolicy.NONE)
            .evaluatorType(EvaluatorType.LLM_BASED)
            .evaluatorRef("llm-based")
            .contextBindings("userInput")
            .build();

        // 2. purpose 判断 Criterion
        EvaluationCriterion purpose = EvaluationCriterionBuilder
            .create("purpose")
            .description("判断用户请求是咨询类还是操作类")
            .resultType(ResultType.ENUM)
            .options("咨询", "操作")
            .workingMechanism(
                "你是一个用户意图分类专家。请判断用户请求的类型。\n\n" +
                "【咨询】的特征：\n" +
                "- 用户想要查询、了解、获取某些信息\n" +
                "- 不需要对系统进行修改\n" +
                "- 只需要返回查询结果即可\n" +
                "- 例如：'什么是XXX'、'查询XXX'、'帮我了解XXX'\n\n" +
                "【操作】的特征：\n" +
                "- 用户想要执行某个操作、修改某些内容\n" +
                "- 涉及创建、修改、删除等操作\n" +
                "- 可能会对系统产生影响\n" +
                "- 例如：'帮我创建XXX'、'修改XXX'、'执行XXX'\n\n" +
                "请直接输出'咨询'或'操作'，不要添加任何解释。"
            )
            .reasoningPolicy(ReasoningPolicy.NONE)
            .evaluatorType(EvaluatorType.LLM_BASED)
            .evaluatorRef("llm-based")
            .contextBindings("userInput")
            .build();

        EvaluationCriterion codeactExperienceRetrieval = EvaluationCriterionBuilder
            .create("codeact_experience_retrieval")
            .description("基于增强后的用户输入检索常识经验和代码经验")
            .resultType(ResultType.TEXT)
            .evaluatorType(EvaluatorType.RULE_BASED)
            .evaluatorRef("codeact_experience_evaluator")
            .dependsOn("enhanced_user_input")
            .contextBindings("userInput")
            .build();

        return List.of(enhancedUserInput, purpose, codeactExperienceRetrieval);
    }
}
