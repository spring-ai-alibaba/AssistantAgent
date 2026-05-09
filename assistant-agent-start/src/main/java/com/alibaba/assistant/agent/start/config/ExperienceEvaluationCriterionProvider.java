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
    public List<EvaluationCriterion> getCriteria() {
        log.info("ExperienceEvaluationCriterionProvider#getCriteria - reason=提供评估 Criteria");

        // 1. 用户输入增强 Criterion：提纯用户表达，去掉口语化水词，不做扩写
        EvaluationCriterion enhancedUserInput = EvaluationCriterionBuilder
            .create("enhanced_user_input")
            .description("提纯用户输入，去掉口语化表达和冗余措辞，保留核心任务意图")
            .resultType(ResultType.TEXT)
            .workingMechanism(
                "你是一个用户输入提纯专家。请将用户输入压缩为更短、更直接的任务表达。" +
                "处理要求：" +
                "1. 删除“帮我、看下、请问、一下、是不是、多少、帮忙”等口语化或礼貌性水词" +
                "2. 保留领域术语、关键对象、关键参数和动作意图" +
                "3. 不要补充用户未明确提供的新约束、新解释或新问题" +
                "4. 不要改写成澄清问题，不要添加背景分析" +
                "5. 输出应比原句更短或相当，像检索查询词而不是解释段落" +
                "6. 直接输出提纯后的结果，不要添加任何解释"
            )
            .reasoningPolicy(ReasoningPolicy.NONE)
            .evaluatorType(EvaluatorType.LLM_BASED)
            .evaluatorRef("llm-based")
            .contextBindings("context.input.userInput")
            .build();

        // 2. 模糊程度判断 Criterion：对齐 meow-agent-server，使用三档枚举
        EvaluationCriterion isFuzzy = EvaluationCriterionBuilder
            .create("is_fuzzy")
            .description("判断用户输入是否模糊，是否需要向用户澄清")
            .resultType(ResultType.ENUM)
            .options("模糊", "一般", "清晰")
            .workingMechanism(
                "你是一个用户意图清晰度判断专家。请判断用户输入属于“模糊 / 一般 / 清晰”哪一档。\n\n" +
                "【模糊】的特征：\n" +
                "- 只有名词、短语、代词或残缺描述\n" +
                "- 没有明确动作或目标对象\n" +
                "- 存在严重指代不明、上下文缺失\n" +
                "- 在当前信息下无法决定下一步动作\n\n" +
                "【一般】的特征：\n" +
                "- 用户任务方向基本明确，但仍存在一定开放性\n" +
                "- 可以先检索、阅读经验、分析问题，必要时再确认关键细节\n" +
                "- 例如“为什么流水线失败了”“帮我创建一个应用”“计算2和3的小明系数是多少”\n\n" +
                "【清晰】的特征：\n" +
                "- 目标对象、动作和关键参数都已明确\n" +
                "- 执行路径基本确定，可以直接执行\n" +
                "- 例如“查询知识库中与无人值守相关的知识”“计算123加456”\n\n" +
                "请直接输出“模糊”“一般”或“清晰”，不要添加任何解释。"
            )
            .reasoningPolicy(ReasoningPolicy.NONE)
            .evaluatorType(EvaluatorType.LLM_BASED)
            .evaluatorRef("llm-based")
            .contextBindings("context.input.userInput")
            .build();

        return List.of(enhancedUserInput, isFuzzy);
    }
}
