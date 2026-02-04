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
package com.alibaba.assistant.agent.extension.prompt;

import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.assistant.agent.evaluation.model.EvaluationResult;
import com.alibaba.assistant.agent.evaluation.store.EvaluationResultStore;
import com.alibaba.assistant.agent.prompt.PromptContribution;
import com.alibaba.assistant.agent.prompt.PromptContributor;
import com.alibaba.assistant.agent.prompt.PromptContributorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * 基于评估结果生成 Prompt 的贡献者抽象基类
 * 子类只需实现具体的 Prompt 生成逻辑
 *
 * @author Assistant Agent Team
 */
public abstract class EvaluationBasedPromptContributor implements PromptContributor {

    private static final Logger log = LoggerFactory.getLogger(EvaluationBasedPromptContributor.class);

    private final String name;
    private final String targetSuiteId;
    private final int priority;

    protected EvaluationBasedPromptContributor(String name, String targetSuiteId) {
        this(name, targetSuiteId, 100);
    }

    protected EvaluationBasedPromptContributor(String name, String targetSuiteId, int priority) {
        this.name = name;
        this.targetSuiteId = targetSuiteId;
        this.priority = priority;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean shouldContribute(PromptContributorContext context) {
        // 从上下文获取 EvaluationResultStore
        Optional<EvaluationResultStore> store = context.getAttribute(
                OverAllStatePromptContributorContext.ATTR_EVALUATION_STORE,
                EvaluationResultStore.class);

        if (store.isEmpty()) {
            log.debug("EvaluationBasedPromptContributor#shouldContribute - reason=无评估结果存储, name={}", name);
            return false;
        }

        Optional<EvaluationResult> result = store.get().get(targetSuiteId);
        if (result.isEmpty()) {
            log.debug("EvaluationBasedPromptContributor#shouldContribute - reason=无评估结果, name={}, suiteId={}",
                    name, targetSuiteId);
            return false;
        }

        // 调用子类的具体判断逻辑
        return shouldContributeBasedOnResult(result.get(), context);
    }

    @Override
    public PromptContribution contribute(PromptContributorContext context) {
        Optional<EvaluationResultStore> store = context.getAttribute(
                OverAllStatePromptContributorContext.ATTR_EVALUATION_STORE,
                EvaluationResultStore.class);

        if (store.isEmpty()) {
            return PromptContribution.empty();
        }

        Optional<EvaluationResult> result = store.get().get(targetSuiteId);
        if (result.isEmpty()) {
            return PromptContribution.empty();
        }

        return generatePrompt(result.get(), context);
    }

    /**
     * 子类实现：根据评估结果判断是否应该贡献
     *
     * @param result  评估结果
     * @param context 上下文
     * @return 是否应该贡献
     */
    protected abstract boolean shouldContributeBasedOnResult(EvaluationResult result,
                                                              PromptContributorContext context);

    /**
     * 子类实现：根据评估结果生成 Prompt
     *
     * @param result  评估结果
     * @param context 上下文
     * @return Prompt 贡献
     */
    protected abstract PromptContribution generatePrompt(EvaluationResult result,
                                                          PromptContributorContext context);

    // ===== 便捷方法 =====

    /**
     * 获取指标值
     *
     * @param result        评估结果
     * @param criterionName 指标名称
     * @return 指标值
     */
    protected Optional<Object> getCriterionValue(EvaluationResult result, String criterionName) {
        CriterionResult cr = result.getCriterionResult(criterionName);
        return cr != null ? Optional.ofNullable(cr.getValue()) : Optional.empty();
    }

    /**
     * 获取指标元数据
     *
     * @param result        评估结果
     * @param criterionName 指标名称
     * @return 元数据 Map
     */
    protected Map<String, Object> getCriterionMetadata(EvaluationResult result, String criterionName) {
        CriterionResult cr = result.getCriterionResult(criterionName);
        return cr != null ? cr.getMetadata() : Map.of();
    }

    /**
     * 获取目标评估套件 ID
     *
     * @return 评估套件 ID
     */
    protected String getTargetSuiteId() {
        return targetSuiteId;
    }
}

