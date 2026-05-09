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
package com.alibaba.assistant.agent.autoconfigure.evaluation;

import com.alibaba.assistant.agent.evaluation.DefaultEvaluationService;
import com.alibaba.assistant.agent.evaluation.EvaluationService;
import com.alibaba.assistant.agent.evaluation.builder.EvaluationSuiteBuilder;
import com.alibaba.assistant.agent.evaluation.evaluator.Evaluator;
import com.alibaba.assistant.agent.evaluation.evaluator.EvaluatorRegistry;
import com.alibaba.assistant.agent.evaluation.evaluator.LLMBasedEvaluator;
import com.alibaba.assistant.agent.evaluation.model.EvaluationCriterion;
import com.alibaba.assistant.agent.evaluation.model.EvaluationSuite;
import com.alibaba.assistant.agent.extension.evaluation.config.CodeactEvaluationContextFactory;
import com.alibaba.assistant.agent.extension.evaluation.hook.ReactBeforeModelEvaluationHook;
import com.alibaba.assistant.agent.extension.prompt.ReactPromptContributorModelHook;
import com.alibaba.assistant.agent.prompt.PromptContributorManager;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认评估套件配置
 *
 * <p>提供默认的评估套件。
 * 用户可以通过配置属性自定义评估行为，或通过实现 {@link EvaluationCriterionProvider}
 * 接口添加自定义 Criterion。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(DefaultEvaluationProperties.class)
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.evaluation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DefaultEvaluationSuiteConfig {

    private static final Logger log = LoggerFactory.getLogger(DefaultEvaluationSuiteConfig.class);

    /**
     * 默认评估套件 ID
     */
    public static final String DEFAULT_SUITE_ID = "default-suite";
    
    /**
     * @deprecated 使用 {@link #DEFAULT_SUITE_ID} 代替
     */
    @Deprecated
    public static final String REACT_PHASE_SUITE_ID = DEFAULT_SUITE_ID;

    private final DefaultEvaluationProperties properties;
    private final ChatModel chatModel;
    private final List<EvaluationCriterionProvider> criterionProviders;
    private final List<Evaluator> customEvaluators;

    public DefaultEvaluationSuiteConfig(
            DefaultEvaluationProperties properties,
            ChatModel chatModel,
            @Autowired(required = false) List<EvaluationCriterionProvider> criterionProviders,
            @Autowired(required = false) List<Evaluator> customEvaluators) {
        this.properties = properties;
        this.chatModel = chatModel;
        this.criterionProviders = criterionProviders;
        this.customEvaluators = customEvaluators;
        log.info("DefaultEvaluationSuiteConfig#<init> - reason=初始化默认评估套件配置");
    }

    /**
     * 默认的 EvaluationService Bean（带套件注册）
     */
    @Bean
    @ConditionalOnMissingBean
    public EvaluationService evaluationService() {
        log.info("DefaultEvaluationSuiteConfig#evaluationService - reason=创建默认 EvaluationService");
        DefaultEvaluationService service = new DefaultEvaluationService();

        // 注册默认评估套件
        if (properties.isEnabled()) {
            EvaluationSuite defaultSuite = createDefaultSuite();
            service.registerSuite(defaultSuite);
            log.info("DefaultEvaluationSuiteConfig#evaluationService - reason=注册默认评估套件, suiteId={}", DEFAULT_SUITE_ID);
        }

        return service;
    }

    /**
     * 默认的 EvaluationContextFactory Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public CodeactEvaluationContextFactory codeactEvaluationContextFactory() {
        log.info("DefaultEvaluationSuiteConfig#codeactEvaluationContextFactory - reason=创建 EvaluationContextFactory");
        return new CodeactEvaluationContextFactory();
    }

    /**
     * 评估 Hooks
     */
    @Bean
    public List<Hook> evaluationHooks(
            EvaluationService evaluationService,
            CodeactEvaluationContextFactory contextFactory,
            @Autowired(required = false) PromptContributorManager promptContributorManager) {

        List<Hook> hooks = new ArrayList<>();

		if (properties.isEnabled()) {
            // 评估 Hook
            ReactBeforeModelEvaluationHook evaluationHook = new ReactBeforeModelEvaluationHook(
                    evaluationService, contextFactory, DEFAULT_SUITE_ID);
            hooks.add(evaluationHook);
            log.info("DefaultEvaluationSuiteConfig#evaluationHooks - reason=创建 BeforeModelEvaluationHook");

            // Prompt 贡献者 Hook
            if (promptContributorManager != null) {
                ReactPromptContributorModelHook promptHook = new ReactPromptContributorModelHook(promptContributorManager);
                hooks.add(promptHook);
                log.info("DefaultEvaluationSuiteConfig#evaluationHooks - reason=创建 PromptContributorModelHook");
            }
        }

        return hooks;
    }

    /**
     * 创建默认评估套件
     */
    private EvaluationSuite createDefaultSuite() {
        EvaluatorRegistry registry = createDefaultEvaluatorRegistry();

        List<EvaluationCriterion> criteria = new ArrayList<>();

        // 添加用户自定义的 Criterion（由 example 层提供，包括 enhanced_user_input）
        if (criterionProviders != null) {
            for (EvaluationCriterionProvider provider : criterionProviders) {
                List<EvaluationCriterion> customCriteria = provider.getCriteria();
                if (customCriteria != null && !customCriteria.isEmpty()) {
                    criteria.addAll(customCriteria);
                    log.info("DefaultEvaluationSuiteConfig#createDefaultSuite - reason=添加自定义 Criterion, provider={}, count={}",
                            provider.getClass().getSimpleName(), customCriteria.size());
                }
            }
        }

        return EvaluationSuiteBuilder
                .create(DEFAULT_SUITE_ID, registry)
                .name("Default Evaluation Suite")
                .description("默认评估套件：用户输入增强")
                .defaultEvaluator("llm-based")
                .addCriteria(criteria.toArray(new EvaluationCriterion[0]))
                .build();
    }

    /**
     * 创建默认的评估器注册表
     *
     * <p>Starter 层自动装配 LLM 评估器。
     */
    private EvaluatorRegistry createDefaultEvaluatorRegistry() {
        EvaluatorRegistry registry = new EvaluatorRegistry();

        // LLM 评估器
        LLMBasedEvaluator llmEvaluator = new LLMBasedEvaluator(chatModel, "llm-based");
        registry.registerEvaluator(llmEvaluator);

        if (customEvaluators != null) {
            for (Evaluator evaluator : customEvaluators) {
                if (evaluator == null || "llm-based".equals(evaluator.getEvaluatorId())) {
                    continue;
                }
                registry.registerEvaluator(evaluator);
                log.info("DefaultEvaluationSuiteConfig#createDefaultEvaluatorRegistry - reason=注册自定义评估器, evaluatorId={}",
                        evaluator.getEvaluatorId());
            }
        }

        return registry;
    }

    /**
     * 暴露 EvaluatorRegistry Bean，供 example 中的 EvaluationCriterionProvider 注册自定义评估器
     *
     * <p>这是一种"约定大于配置"的方式：
     * - starter 提供基础的 EvaluatorRegistry
     * - example 中可以注入并注册自定义的评估器（如经验检索评估器）
     */
    @Bean
    @ConditionalOnMissingBean
    public EvaluatorRegistry evaluatorRegistry() {
        log.info("DefaultEvaluationSuiteConfig#evaluatorRegistry - reason=创建 EvaluatorRegistry Bean");
        return createDefaultEvaluatorRegistry();
    }
}
