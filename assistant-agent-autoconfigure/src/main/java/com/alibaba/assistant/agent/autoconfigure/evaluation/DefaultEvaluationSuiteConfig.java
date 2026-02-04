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
import com.alibaba.assistant.agent.evaluation.evaluator.EvaluatorRegistry;
import com.alibaba.assistant.agent.evaluation.evaluator.LLMBasedEvaluator;
import com.alibaba.assistant.agent.evaluation.evaluator.RuleBasedEvaluator;
import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.assistant.agent.evaluation.model.CriterionStatus;
import com.alibaba.assistant.agent.evaluation.model.EvaluationCriterion;
import com.alibaba.assistant.agent.evaluation.model.EvaluationSuite;
import com.alibaba.assistant.agent.extension.evaluation.config.CodeactEvaluationContextFactory;
import com.alibaba.assistant.agent.extension.evaluation.experience.ExperienceRetrievalEvaluatorFactory;
import com.alibaba.assistant.agent.extension.evaluation.hook.CodeactBeforeModelEvaluationHook;
import com.alibaba.assistant.agent.extension.evaluation.hook.ReactBeforeModelEvaluationHook;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.prompt.CodeactPromptContributorModelHook;
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
 * <p>提供 react-phase-suite 和 codeact-phase-suite 两个默认评估套件。
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

    public static final String REACT_PHASE_SUITE_ID = "react-phase-suite";
    public static final String CODEACT_PHASE_SUITE_ID = "codeact-phase-suite";

    private final DefaultEvaluationProperties properties;
    private final ChatModel chatModel;
    private final List<EvaluationCriterionProvider> criterionProviders;
    private final ExperienceProvider experienceProvider;

    public DefaultEvaluationSuiteConfig(
            DefaultEvaluationProperties properties,
            ChatModel chatModel,
            @Autowired(required = false) List<EvaluationCriterionProvider> criterionProviders,
            @Autowired(required = false) ExperienceProvider experienceProvider) {
        this.properties = properties;
        this.chatModel = chatModel;
        this.criterionProviders = criterionProviders;
        this.experienceProvider = experienceProvider;
        log.info("DefaultEvaluationSuiteConfig#<init> - reason=初始化默认评估套件配置, experienceProviderAvailable={}", experienceProvider != null);
    }

    /**
     * 默认的 EvaluationService Bean（带套件注册）
     */
    @Bean
    @ConditionalOnMissingBean
    public EvaluationService evaluationService() {
        log.info("DefaultEvaluationSuiteConfig#evaluationService - reason=创建默认 EvaluationService");
        DefaultEvaluationService service = new DefaultEvaluationService();

        // 在创建 service 时就注册默认套件
        if (properties.getReactPhase().isEnabled()) {
            EvaluationSuite reactSuite = createReactPhaseSuite();
            service.registerSuite(reactSuite);
            log.info("DefaultEvaluationSuiteConfig#evaluationService - reason=注册 React Phase Suite, suiteId={}", REACT_PHASE_SUITE_ID);
        }

        if (properties.getCodeactPhase().isEnabled()) {
            EvaluationSuite codeactSuite = createCodeActPhaseSuite();
            service.registerSuite(codeactSuite);
            log.info("DefaultEvaluationSuiteConfig#evaluationService - reason=注册 CodeAct Phase Suite, suiteId={}", CODEACT_PHASE_SUITE_ID);
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
     * React 阶段评估 Hooks
     * 
     * <p>所有返回的 Hook 都通过 {@code @HookPhases(AgentPhase.REACT)} 注解声明，
     * 会被 HookPhaseUtils 自动分配到 React Agent。
     */
    @Bean
    public List<Hook> reactPhaseEvaluationHooks(
            EvaluationService evaluationService,
            CodeactEvaluationContextFactory contextFactory,
            @Autowired(required = false) PromptContributorManager promptContributorManager) {

        List<Hook> hooks = new ArrayList<>();

		if (properties.getReactPhase().isEnabled()) {
            // 评估 Hook（使用 @HookPhases 注解声明阶段）
            ReactBeforeModelEvaluationHook evaluationHook = new ReactBeforeModelEvaluationHook(
                    evaluationService, contextFactory, REACT_PHASE_SUITE_ID);
            hooks.add(evaluationHook);
            log.info("DefaultEvaluationSuiteConfig#reactPhaseEvaluationHooks - reason=创建 React Phase BeforeModelEvaluationHook");

            // Prompt 贡献者 Hook - 共享同一个 Manager，Contributor 在 shouldContribute() 中通过 context.getPhase() 判断
            if (promptContributorManager != null) {
                ReactPromptContributorModelHook promptHook = new ReactPromptContributorModelHook(promptContributorManager);
                hooks.add(promptHook);
                log.info("DefaultEvaluationSuiteConfig#reactPhaseEvaluationHooks - reason=创建 React Phase PromptContributorModelHook");
            }
        }

        return hooks;
    }

    /**
     * CodeAct 阶段评估 Hooks
     * 
     * <p>所有返回的 Hook 都通过 {@code @HookPhases(AgentPhase.CODEACT)} 注解声明，
     * 会被 HookPhaseUtils 自动分配到 CodeAct Agent。
     */
    @Bean
    public List<Hook> codeactPhaseEvaluationHooks(
            EvaluationService evaluationService,
            CodeactEvaluationContextFactory contextFactory,
            @Autowired(required = false) PromptContributorManager promptContributorManager) {

        List<Hook> hooks = new ArrayList<>();

		if (properties.getCodeactPhase().isEnabled()) {
            // 评估 Hook（使用 @HookPhases 注解声明阶段）
            CodeactBeforeModelEvaluationHook evaluationHook = new CodeactBeforeModelEvaluationHook(
                    evaluationService, contextFactory, CODEACT_PHASE_SUITE_ID);
            hooks.add(evaluationHook);
            log.info("DefaultEvaluationSuiteConfig#codeactPhaseEvaluationHooks - reason=创建 CodeAct Phase BeforeModelEvaluationHook");

            // Prompt 贡献者 Hook - 共享同一个 Manager，Contributor 在 shouldContribute() 中通过 context.getPhase() 判断
            if (promptContributorManager != null) {
                CodeactPromptContributorModelHook promptHook = new CodeactPromptContributorModelHook(promptContributorManager);
                hooks.add(promptHook);
                log.info("DefaultEvaluationSuiteConfig#codeactPhaseEvaluationHooks - reason=创建 CodeAct Phase PromptContributorModelHook");
            }
        }

        return hooks;
    }

    /**
     * 创建 React 阶段评估套件
     */
    private EvaluationSuite createReactPhaseSuite() {
        EvaluatorRegistry registry = createDefaultEvaluatorRegistry();

        List<EvaluationCriterion> criteria = new ArrayList<>();

        // 添加用户自定义的 Criterion（由 example 层提供，包括 enhanced_user_input）
        if (criterionProviders != null) {
            for (EvaluationCriterionProvider provider : criterionProviders) {
                List<EvaluationCriterion> customCriteria = provider.getReactPhaseCriteria();
                if (customCriteria != null && !customCriteria.isEmpty()) {
                    criteria.addAll(customCriteria);
                    log.info("DefaultEvaluationSuiteConfig#createReactPhaseSuite - reason=添加自定义 Criterion, provider={}, count={}",
                            provider.getClass().getSimpleName(), customCriteria.size());
                }
            }
        }

        return EvaluationSuiteBuilder
                .create(REACT_PHASE_SUITE_ID, registry)
                .name("React Phase Evaluation Suite")
                .description("React阶段默认评估套件：用户输入增强")
                .defaultEvaluator("llm-based")
                .addCriteria(criteria.toArray(new EvaluationCriterion[0]))
                .build();
    }

    /**
     * 创建 CodeAct 阶段评估套件
     */
    private EvaluationSuite createCodeActPhaseSuite() {
        EvaluatorRegistry registry = createDefaultEvaluatorRegistry();

        List<EvaluationCriterion> criteria = new ArrayList<>();

        // 添加用户自定义的 Criterion（由 example 层提供，包括 enhanced_user_input）
        if (criterionProviders != null) {
            for (EvaluationCriterionProvider provider : criterionProviders) {
                List<EvaluationCriterion> customCriteria = provider.getCodeActPhaseCriteria();
                if (customCriteria != null && !customCriteria.isEmpty()) {
                    criteria.addAll(customCriteria);
                    log.info("DefaultEvaluationSuiteConfig#createCodeActPhaseSuite - reason=添加自定义 Criterion, provider={}, count={}",
                            provider.getClass().getSimpleName(), customCriteria.size());
                }
            }
        }

        return EvaluationSuiteBuilder
                .create(CODEACT_PHASE_SUITE_ID, registry)
                .name("CodeAct Phase Evaluation Suite")
                .description("CodeAct阶段默认评估套件：代码任务增强")
                .defaultEvaluator("llm-based")
                .addCriteria(criteria.toArray(new EvaluationCriterion[0]))
                .build();
    }

    /**
     * 创建默认的评估器注册表
     *
     * <p>Starter 层自动装配：
     * - LLM 评估器
     * - 透传评估器
     * - 经验检索评估器（如果 ExperienceProvider 可用）
     */
    private EvaluatorRegistry createDefaultEvaluatorRegistry() {
        EvaluatorRegistry registry = new EvaluatorRegistry();

        // LLM 评估器
        LLMBasedEvaluator llmEvaluator = new LLMBasedEvaluator(chatModel, "llm-based");
        registry.registerEvaluator(llmEvaluator);

        // 规则评估器（透传）
        RuleBasedEvaluator passthroughEvaluator = new RuleBasedEvaluator("passthrough", ctx -> {
            CriterionResult result = new CriterionResult();
            result.setCriterionName(ctx.getCriterion().getName());
            result.setStatus(CriterionStatus.SUCCESS);
            result.setValue(ctx.getInputContext().getInput().getOrDefault("user_input", ""));
            return result;
        });
        registry.registerEvaluator(passthroughEvaluator);

        // 自动注册经验检索评估器（如果 ExperienceProvider 可用）
        if (experienceProvider != null && properties.getExperience().isEnabled()) {
            int maxExperiences = properties.getExperience().getMaxExperiencesPerType();

            // React 阶段经验评估器
            if (properties.getExperience().isReactPhaseEnabled()) {
                RuleBasedEvaluator reactExpEvaluator = ExperienceRetrievalEvaluatorFactory.createReactPhaseEvaluator(
                        experienceProvider, maxExperiences);
                registry.registerEvaluator(reactExpEvaluator);
                log.info("DefaultEvaluationSuiteConfig#createDefaultEvaluatorRegistry - reason=注册 React 阶段经验检索评估器");
            }

            // CodeAct 阶段经验评估器
            if (properties.getExperience().isCodeactPhaseEnabled()) {
                RuleBasedEvaluator codeactExpEvaluator = ExperienceRetrievalEvaluatorFactory.createCodeActPhaseEvaluator(
                        experienceProvider, maxExperiences);
                registry.registerEvaluator(codeactExpEvaluator);
                log.info("DefaultEvaluationSuiteConfig#createDefaultEvaluatorRegistry - reason=注册 CodeAct 阶段经验检索评估器");
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

