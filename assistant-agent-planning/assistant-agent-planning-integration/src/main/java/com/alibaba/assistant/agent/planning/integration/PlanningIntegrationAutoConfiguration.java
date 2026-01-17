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

import com.alibaba.assistant.agent.evaluation.evaluator.Evaluator;
import com.alibaba.assistant.agent.evaluation.evaluator.EvaluatorRegistry;
import com.alibaba.assistant.agent.planning.config.PlanningExtensionProperties;
import com.alibaba.assistant.agent.planning.service.ParamCollectionService;
import com.alibaba.assistant.agent.planning.spi.ActionProvider;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Planning 模块集成层自动配置
 *
 * <p>负责将 planning 模块与 AssistantAgent 的 evaluation 模块集成。
 *
 * <h3>配置属性</h3>
 * <pre>
 * spring.ai.alibaba.codeact.extension.planning:
 *   evaluation:
 *     enabled: true  # 启用评估集成
 *     param-collection-enabled: true  # 启用参数收集
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(EvaluatorRegistry.class)
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning.evaluation", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PlanningExtensionProperties.class)
public class PlanningIntegrationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PlanningIntegrationAutoConfiguration.class);

    /**
     * 配置动作意图评估器
     *
     * <p>创建 ActionIntentEvaluator 并立即注册到 EvaluatorRegistry。
     *
     * <p>支持两种模式：
     * <ul>
     * <li>普通模式：直接返回最佳匹配结果</li>
     * <li>LLM 验证模式：返回候选列表供 LLM 二次判断</li>
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean(name = "actionIntentEvaluator")
    public ActionIntentEvaluator actionIntentEvaluator(
            ActionProvider actionProvider,
            ParamCollectionService paramCollectionService,
            PlanningExtensionProperties properties,
            EvaluatorRegistry evaluatorRegistry) {

        PlanningExtensionProperties.EvaluationConfig evalConfig = properties.getEvaluation();

        boolean enableParamCollection = evalConfig != null && evalConfig.isParamCollectionEnabled();
        boolean enableLLMVerification = evalConfig != null && evalConfig.isLlmVerificationEnabled();
        int maxCandidates = evalConfig != null ? evalConfig.getLlmVerificationMaxCandidates() : 5;

        ActionIntentEvaluator evaluator = new ActionIntentEvaluator(
                actionProvider,
                paramCollectionService,
                enableParamCollection,
                enableLLMVerification,
                maxCandidates
        );

        // 立即注册到评估器注册表，确保在 CriterionProvider 使用之前完成注册
        evaluatorRegistry.registerEvaluator(evaluator);

        logger.info("PlanningIntegrationAutoConfiguration#actionIntentEvaluator - initialized and registered, " +
                        "paramCollection={}, llmVerification={}, maxCandidates={}",
                enableParamCollection, enableLLMVerification, maxCandidates);

        return evaluator;
    }

    /**
     * 配置 Planning 模块的评估准则提供者
     *
     * <p>提供 action_intent_match 和 action_intent_llm_verify 评估准则。
     * <p>依赖 actionIntentEvaluator 确保评估器已注册到 EvaluatorRegistry。
     */
    @Bean
    @ConditionalOnClass(EvaluatorRegistry.class)
    @ConditionalOnMissingBean(PlanningEvaluationCriterionProvider.class)
    public PlanningEvaluationCriterionProvider planningEvaluationCriterionProvider(
            PlanningExtensionProperties properties,
            ActionIntentEvaluator actionIntentEvaluator) {  // 依赖确保评估器已注册

        PlanningEvaluationCriterionProvider provider = new PlanningEvaluationCriterionProvider(properties);

        logger.info("PlanningIntegrationAutoConfiguration#planningEvaluationCriterionProvider - " +
                "registered planning evaluation criterion provider");

        return provider;
    }

    // NOTE: ParamCollectionResponseHook 已禁用，因为 BEFORE_MODEL 位置在当前框架版本中不可用
    // nextQuestion 的处理已移至 UnifiedIntentRecognitionHook 中
    //
    // /**
    //  * 配置参数收集响应 Hook
    //  *
    //  * <p>当 LLM 验证结果中包含 nextQuestion 时，自动将问题作为回复返回给用户。
    //  * <p>Hook 内部会检查是否启用 LLM 验证，如果未启用则直接跳过。
    //  */
    // @Bean
    // @ConditionalOnMissingBean(ParamCollectionResponseHook.class)
    // public ParamCollectionResponseHook paramCollectionResponseHook(PlanningExtensionProperties properties) {
    //     boolean enabled = properties.getEvaluation() != null &&
    //             properties.getEvaluation().isLlmVerificationEnabled();
    //
    //     logger.info("PlanningIntegrationAutoConfiguration#paramCollectionResponseHook - initialized, enabled={}",
    //             enabled);
    //
    //     return new ParamCollectionResponseHook(enabled);
    // }
}
