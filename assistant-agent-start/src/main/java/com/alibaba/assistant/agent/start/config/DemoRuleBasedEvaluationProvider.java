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

import com.alibaba.assistant.agent.autoconfigure.evaluation.EvaluationCriterionProvider;
import com.alibaba.assistant.agent.evaluation.builder.EvaluationCriterionBuilder;
import com.alibaba.assistant.agent.evaluation.evaluator.Evaluator;
import com.alibaba.assistant.agent.evaluation.evaluator.RuleBasedEvaluator;
import com.alibaba.assistant.agent.evaluation.model.CriterionExecutionContext;
import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.assistant.agent.evaluation.model.CriterionStatus;
import com.alibaba.assistant.agent.evaluation.model.EvaluationCriterion;
import com.alibaba.assistant.agent.evaluation.model.EvaluatorType;
import com.alibaba.assistant.agent.evaluation.model.ResultType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 示例用 RuleBasedEvaluator。
 *
 * <p>该示例只提取输入中的显式信号，便于框架用户参考如何：
 * <ul>
 *   <li>声明 RULE_BASED 类型的 Criterion</li>
 *   <li>读取原始输入与依赖结果</li>
 *   <li>返回 value + metadata</li>
 * </ul>
 *
 * <p>它不参与后续决策，不会改变 Agent 行为。
 */
@Component
public class DemoRuleBasedEvaluationProvider implements EvaluationCriterionProvider {

    private static final Logger log = LoggerFactory.getLogger(DemoRuleBasedEvaluationProvider.class);

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    @Override
    public List<EvaluationCriterion> getCriteria() {
        EvaluationCriterion inputSignalSummary = EvaluationCriterionBuilder
                .create("input_signal_summary")
                .description("规则提取用户输入中的显式信号，用于演示 RuleBasedEvaluator 的写法")
                .resultType(ResultType.TEXT)
                .evaluatorType(EvaluatorType.RULE_BASED)
                .evaluatorRef("input-signal-summary")
                .dependsOn("enhanced_user_input")
                .contextBindings("context.input.userInput")
                .build();

        return List.of(inputSignalSummary);
    }

    @Bean
    public Evaluator inputSignalSummaryEvaluator() {
        return new RuleBasedEvaluator("input-signal-summary", this::evaluateInputSignalSummary);
    }

    private CriterionResult evaluateInputSignalSummary(CriterionExecutionContext ctx) {
        CriterionResult result = new CriterionResult();
        result.setCriterionName(ctx.getCriterion().getName());
        result.setStatus(CriterionStatus.SUCCESS);

        String rawInput = extractRawInput(ctx);
        String normalizedInput = extractNormalizedInput(ctx);
        List<String> numbers = extractNumbers(rawInput);
        boolean hasUrl = URL_PATTERN.matcher(rawInput).find();
        boolean hasCodeLikeSignal = rawInput.contains("```")
                || rawInput.contains("def ")
                || rawInput.contains("class ")
                || rawInput.contains("Exception")
                || rawInput.contains("stacktrace");
        boolean isQuestion = rawInput.contains("?") || rawInput.contains("？") || rawInput.contains("多少");
        boolean hasCalculationSignal = rawInput.contains("计算")
                || rawInput.contains("求")
                || rawInput.contains("+")
                || rawInput.contains("-")
                || rawInput.contains("*")
                || rawInput.contains("/")
                || rawInput.contains("乘")
                || rawInput.contains("加");

        Set<String> signals = new LinkedHashSet<>();
        if (hasCalculationSignal) {
            signals.add("calculation");
        }
        if (hasUrl) {
            signals.add("url");
        }
        if (hasCodeLikeSignal) {
            signals.add("code");
        }
        if (isQuestion) {
            signals.add("question");
        }
        if (!numbers.isEmpty()) {
            signals.add("numbers");
        }
        if (signals.isEmpty()) {
            signals.add("plain_text");
        }

        String summary = "signals=" + String.join(",", signals)
                + "; numberCount=" + numbers.size()
                + "; normalizedInput=" + normalizedInput;

        result.setValue(summary);
        result.getMetadata().put("raw_input", rawInput);
        result.getMetadata().put("normalized_input", normalizedInput);
        result.getMetadata().put("numbers", numbers);
        result.getMetadata().put("has_url", hasUrl);
        result.getMetadata().put("has_code_like_signal", hasCodeLikeSignal);
        result.getMetadata().put("is_question", isQuestion);
        result.getMetadata().put("signals", new ArrayList<>(signals));

        log.info("DemoRuleBasedEvaluationProvider#evaluateInputSignalSummary - reason=规则评估完成, summary={}", summary);
        return result;
    }

    private String extractRawInput(CriterionExecutionContext ctx) {
        Object userInput = ctx.getInputContext().getInput().get("userInput");
        if (userInput == null) {
            userInput = ctx.getInputContext().getInput().get("user_input");
        }
        return userInput != null ? userInput.toString() : "";
    }

    private String extractNormalizedInput(CriterionExecutionContext ctx) {
        CriterionResult enhancedResult = ctx.getDependencyResult("enhanced_user_input");
        if (enhancedResult != null && enhancedResult.getValue() != null) {
            String enhanced = enhancedResult.getValue().toString();
            if (!enhanced.isBlank()) {
                return enhanced;
            }
        }
        return extractRawInput(ctx);
    }

    private List<String> extractNumbers(String text) {
        List<String> numbers = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }
}
