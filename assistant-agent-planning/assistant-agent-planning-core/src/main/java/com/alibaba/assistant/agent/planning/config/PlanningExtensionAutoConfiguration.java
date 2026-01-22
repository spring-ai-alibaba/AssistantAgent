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
package com.alibaba.assistant.agent.planning.config;

import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.planning.internal.*;
import com.alibaba.assistant.agent.planning.internal.executor.*;
import com.alibaba.assistant.agent.planning.param.StructuredParamExtractor;
import com.alibaba.assistant.agent.planning.param.ParameterValidator;
import com.alibaba.assistant.agent.planning.persistence.MybatisPlusActionRepository;
import com.alibaba.assistant.agent.planning.persistence.converter.ActionEntityConverter;
import com.alibaba.assistant.agent.planning.persistence.mapper.ActionRegistryMapper;
import com.alibaba.assistant.agent.planning.spi.*;
import com.alibaba.assistant.agent.planning.tools.*;
import com.alibaba.assistant.agent.planning.vector.ActionVectorizationService;
import com.alibaba.assistant.agent.planning.web.controller.ActionController;
import com.alibaba.assistant.agent.planning.session.InMemorySessionProvider;
import com.alibaba.assistant.agent.planning.session.ParamCollectionSessionStore;
import com.alibaba.assistant.agent.planning.web.controller.PlanController;
import com.alibaba.assistant.agent.planning.internal.SemanticActionProvider;
import com.alibaba.assistant.agent.planning.intent.KeywordMatcher;
import com.alibaba.assistant.agent.planning.intent.PlanningIntentHook;
// ActionIntentPromptBuilder 和 PlanningEvaluationCriterionProvider 在 integration 模块中
import com.alibaba.assistant.agent.evaluation.evaluator.EvaluatorRegistry;
import com.alibaba.assistant.agent.prompt.PromptBuilder;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Planning 扩展自动配置
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PlanningExtensionProperties.class)
@Import({
        PlanningExtensionAutoConfiguration.MybatisPlusConfiguration.class,
        PlanningExtensionAutoConfiguration.ElasticsearchConfiguration.class
})
public class PlanningExtensionAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PlanningExtensionAutoConfiguration.class);

    public PlanningExtensionAutoConfiguration() {
        logger.info("PlanningExtensionAutoConfiguration#init - reason=planning extension is enabled");
    }

    // ==================== Diagnostics ====================

    /**
     * 全局诊断 Bean - 追踪 Planning 模块组件创建情况
     */
    @Bean
    public Object planningModuleDiagnostics(
            @Autowired(required = false) ActionRepository actionRepository,
            @Autowired(required = false) ActionProvider actionProvider,
            @Autowired(required = false) PlanGenerator planGenerator,
            @Autowired(required = false) PlanExecutor planExecutor) {
        logger.info("PlanningExtensionAutoConfiguration#diagnostics - Planning module status:");
        logger.info("  - ActionRepository: {}", actionRepository != null ? actionRepository.getClass().getSimpleName() : "NOT FOUND");
        logger.info("  - ActionProvider: {}", actionProvider != null ? actionProvider.getClass().getSimpleName() : "NOT FOUND");
        logger.info("  - PlanGenerator: {}", planGenerator != null ? planGenerator.getClass().getSimpleName() : "NOT FOUND");
        logger.info("  - PlanExecutor: {}", planExecutor != null ? planExecutor.getClass().getSimpleName() : "NOT FOUND");
        return new Object();
    }

    // ==================== SPI Beans ====================

    /**
     * RestTemplate Bean - 用于 HTTP 请求执行
     */
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        logger.info("PlanningExtensionAutoConfiguration#restTemplate - reason=creating RestTemplate bean");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return new RestTemplate(factory);
    }

    /**
     * SessionProvider Bean - 用于参数收集会话存储
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionProvider sessionProvider() {
        logger.info("PlanningExtensionAutoConfiguration#sessionProvider - reason=creating in-memory session provider");
        return new InMemorySessionProvider();
    }

    /**
     * StructuredParamExtractor Bean - 用于 LLM 参数提取
     */
    @Bean
    @ConditionalOnMissingBean
    public StructuredParamExtractor structuredParamExtractor(ChatModel chatModel, ObjectMapper objectMapper) {
        logger.info("PlanningExtensionAutoConfiguration#structuredParamExtractor - reason=creating structured param extractor");
        return new StructuredParamExtractor(chatModel, objectMapper);
    }

    /**
     * ParameterValidator Bean - 用于参数验证
     */
    @Bean
    @ConditionalOnMissingBean
    public ParameterValidator parameterValidator() {
        logger.info("PlanningExtensionAutoConfiguration#parameterValidator - reason=creating parameter validator");
        return new ParameterValidator();
    }


    @Bean
    @ConditionalOnMissingBean(PlanGenerator.class)
    public PlanGenerator defaultPlanGenerator() {
        logger.info("PlanningExtensionAutoConfiguration#defaultPlanGenerator - reason=creating default plan generator");
        return new DefaultPlanGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(StepExecutorRegistry.class)
    public StepExecutorRegistry stepExecutorRegistry(List<StepExecutor> executors) {
        logger.info("PlanningExtensionAutoConfiguration#stepExecutorRegistry - reason=creating executor registry, executorCount={}",
                executors != null ? executors.size() : 0);
        return new StepExecutorRegistry(executors);
    }

    @Bean
    @ConditionalOnMissingBean(PlanExecutor.class)
    public PlanExecutor defaultPlanExecutor(StepExecutorRegistry registry) {
        logger.info("PlanningExtensionAutoConfiguration#defaultPlanExecutor - reason=creating default plan executor");
        return new DefaultPlanExecutor(registry);
    }

    // ==================== Step Executors ====================

    @Bean
    public StepExecutor queryStepExecutor() {
        logger.debug("PlanningExtensionAutoConfiguration#queryStepExecutor - reason=creating query step executor");
        return new QueryStepExecutor();
    }

    @Bean
    public StepExecutor inputStepExecutor() {
        logger.debug("PlanningExtensionAutoConfiguration#inputStepExecutor - reason=creating input step executor");
        return new InputStepExecutor();
    }

    @Bean
    public StepExecutor executeStepExecutor() {
        logger.debug("PlanningExtensionAutoConfiguration#executeStepExecutor - reason=creating execute step executor");
        return new ExecuteStepExecutor();
    }

    @Bean
    public StepExecutor apiCallStepExecutor() {
        logger.debug("PlanningExtensionAutoConfiguration#apiCallStepExecutor - reason=creating api call step executor");
        return new ApiCallStepExecutor();
    }

    @Bean
    public StepExecutor validationStepExecutor() {
        logger.debug("PlanningExtensionAutoConfiguration#validationStepExecutor - reason=creating validation step executor");
        return new ValidationStepExecutor();
    }

    // ==================== CodeactTools ====================

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning", name = "plan-action-enabled", havingValue = "true", matchIfMissing = true)
    public PlanActionCodeactTool planActionCodeactTool(ActionProvider actionProvider, PlanGenerator planGenerator) {
        logger.info("PlanningExtensionAutoConfiguration#planActionCodeactTool - reason=creating plan_action tool");
        return new PlanActionCodeactTool(actionProvider, planGenerator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning", name = "execute-action-enabled", havingValue = "true", matchIfMissing = true)
    public ExecuteActionCodeactTool executeActionCodeactTool(PlanExecutor planExecutor) {
        logger.info("PlanningExtensionAutoConfiguration#executeActionCodeactTool - reason=creating execute_action tool");
        return new ExecuteActionCodeactTool(planExecutor);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning", name = "list-actions-enabled", havingValue = "true", matchIfMissing = true)
    public ListActionsCodeactTool listActionsCodeactTool(ActionProvider actionProvider) {
        logger.info("PlanningExtensionAutoConfiguration#listActionsCodeactTool - reason=creating list_actions tool");
        return new ListActionsCodeactTool(actionProvider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning", name = "get-action-details-enabled", havingValue = "true", matchIfMissing = true)
    public GetActionDetailsCodeactTool getActionDetailsCodeactTool(ActionProvider actionProvider) {
        logger.info("PlanningExtensionAutoConfiguration#getActionDetailsCodeactTool - reason=creating get_action_details tool");
        return new GetActionDetailsCodeactTool(actionProvider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning", name = "execute-system-action-enabled", havingValue = "true", matchIfMissing = true)
    public ExecuteSystemActionCodeactTool executeSystemActionCodeactTool(ActionProvider actionProvider,
                                                                          ApplicationContext applicationContext) {
        logger.info("PlanningExtensionAutoConfiguration#executeSystemActionCodeactTool - reason=creating execute_system_action tool");
        return new ExecuteSystemActionCodeactTool(actionProvider, applicationContext);
    }

    /**
     * Planning CodeactTools 列表 Bean
     */
    @Bean
    public List<CodeactTool> planningCodeactTools(
            PlanningExtensionProperties properties,
            ActionProvider actionProvider,
            PlanGenerator planGenerator,
            PlanExecutor planExecutor,
            ApplicationContext applicationContext) {

        logger.info("PlanningExtensionAutoConfiguration#planningCodeactTools - reason=creating planning tools list");

        List<CodeactTool> tools = new ArrayList<>();

        if (properties.isPlanActionEnabled()) {
            tools.add(new PlanActionCodeactTool(actionProvider, planGenerator));
        }

        if (properties.isExecuteActionEnabled()) {
            tools.add(new ExecuteActionCodeactTool(planExecutor));
        }

        if (properties.isListActionsEnabled()) {
            tools.add(new ListActionsCodeactTool(actionProvider));
        }

        if (properties.isGetActionDetailsEnabled()) {
            tools.add(new GetActionDetailsCodeactTool(actionProvider));
        }

        if (properties.isExecuteSystemActionEnabled()) {
            tools.add(new ExecuteSystemActionCodeactTool(actionProvider, applicationContext));
        }

        logger.info("PlanningExtensionAutoConfiguration#planningCodeactTools - reason=created planning tools, count={}",
                tools.size());

        return tools;
    }

    // ==================== Web Controllers ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning", name = "web-enabled", havingValue = "true", matchIfMissing = true)
    public ActionController actionController(ActionRepository actionRepository,
                                              ActionProvider actionProvider,
                                              @Autowired(required = false) ActionVectorizationService vectorizationService,
                                              @Autowired(required = false) PlanningIntentHook planningIntentHook,
                                              @Autowired(required = false) com.alibaba.assistant.agent.planning.intent.UnifiedIntentRecognitionHook unifiedIntentHook) {
        logger.info("PlanningExtensionAutoConfiguration#actionController - reason=creating action controller, " +
                        "vectorization={}, planningIntentHook={}, unifiedIntentHook={}",
                vectorizationService != null, planningIntentHook != null, unifiedIntentHook != null);
        return new ActionController(actionRepository, actionProvider, vectorizationService, planningIntentHook, unifiedIntentHook);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning", name = "web-enabled", havingValue = "true", matchIfMissing = true)
    public PlanController planController(ActionProvider actionProvider,
                                         PlanGenerator planGenerator,
                                         PlanExecutor planExecutor) {
        logger.info("PlanningExtensionAutoConfiguration#planController - reason=creating plan controller");
        return new PlanController(actionProvider, planGenerator, planExecutor);
    }

    // ==================== Evaluation Integration ====================

    // 以下 Bean 已移至 integration 模块：
    // - PlanningEvaluationCriterionProvider
    // - ActionIntentPromptBuilder

    // ==================== Intent Recognition Hook ====================

    /**
     * 关键词匹配器
     */
    @Bean
    @ConditionalOnMissingBean
    public KeywordMatcher keywordMatcher() {
        logger.info("PlanningExtensionAutoConfiguration#keywordMatcher - reason=creating keyword matcher");
        return new KeywordMatcher();
    }

    // ==================== Intent Hook Configurations ====================

    /**
     * Planning 意图识别 Hook 配置（原始版本）
     */
    @Configuration
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning.intent", name = "enabled", havingValue = "true")
    static class PlanningIntentHookConfiguration {

        private static final Logger logger = LoggerFactory.getLogger(PlanningIntentHookConfiguration.class);

        /**
         * Planning 意图识别 Hook（原始版本，不整合 Experience）
         *
         * <p>实现三层匹配策略：
         * <ol>
         *     <li>关键词快速匹配</li>
         *     <li>语义匹配（ES 向量搜索）</li>
         *     <li>置信度分流（直接执行/注入提示/不干预）</li>
         * </ol>
         *
         * <p>仅在 spring.ai.alibaba.codeact.extension.planning.intent.use-unified=false 时启用
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning.intent", name = "use-unified", havingValue = "false", matchIfMissing = false)
        public PlanningIntentHook planningIntentHook(ActionProvider actionProvider,
                                                      PlanGenerator planGenerator,
                                                      PlanExecutor planExecutor,
                                                      KeywordMatcher keywordMatcher,
                                                      PlanningExtensionProperties properties) {
            logger.info("PlanningIntentHookConfiguration#planningIntentHook - reason=creating planning intent hook (original), " +
                            "directExecuteThreshold={}, hintThreshold={}",
                    properties.getIntent().getDirectExecuteThreshold(),
                    properties.getIntent().getHintThreshold());
            return new PlanningIntentHook(actionProvider, planGenerator, planExecutor, keywordMatcher, properties);
        }
    }

    /**
     * 统一意图识别 Hook 配置（整合 Planning + Experience）
     */
    @Configuration
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning.intent", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class UnifiedIntentHookConfiguration {

        private static final Logger logger = LoggerFactory.getLogger(UnifiedIntentHookConfiguration.class);

        /**
         * 统一意图识别 Hook（整合 Planning + Experience）
         *
         * <p>整合策略：
         * <ol>
         *     <li>第一层：关键词快速过滤（Planning KeywordMatcher）</li>
         *     <li>第二层：语义匹配（Planning ActionProvider）</li>
         *     <li>第三层：置信度分流</li>
         * </ol>
         *
         * <p>执行策略（根据置信度）：
         * <ul>
         *     <li>>= 0.95（高置信度）：
         *         <ul>
         *             <li>1. 检查 Experience：有 → FastIntent 快速执行</li>
         *             <li>2. 无 Experience：Planning 直接执行</li>
         *         </ul>
         *     </li>
         *     <li>>= 0.7（中等置信度）：注入提示，让 LLM 决策</li>
         *     <li>< 0.7（低置信度）：不干预，走正常 ReAct 流程</li>
         * </ul>
         *
         * <p>默认启用（matchIfMissing = true）
         */
        @Bean
        @ConditionalOnMissingBean(name = "unifiedIntentRecognitionHook")
        @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning.intent", name = "use-unified", havingValue = "true", matchIfMissing = true)
        public com.alibaba.assistant.agent.planning.intent.UnifiedIntentRecognitionHook unifiedIntentRecognitionHook(
                ActionProvider actionProvider,
                PlanGenerator planGenerator,
                PlanExecutor planExecutor,
                KeywordMatcher keywordMatcher,
                @Autowired(required = false) com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider experienceProvider,
                @Autowired(required = false) org.springframework.ai.chat.model.ChatModel chatModel,
                @Autowired(required = false) ParamCollectionSessionStore sessionStore,
                PlanningExtensionProperties properties) {
            logger.info("UnifiedIntentHookConfiguration#unifiedIntentRecognitionHook - reason=creating unified intent hook, " +
                            "experienceProviderPresent={}, chatModelPresent={}, sessionStorePresent={}, directExecuteThreshold={}, hintThreshold={}",
                    experienceProvider != null,
                    chatModel != null,
                    sessionStore != null,
                    properties.getIntent().getDirectExecuteThreshold(),
                    properties.getIntent().getHintThreshold());
            return new com.alibaba.assistant.agent.planning.intent.UnifiedIntentRecognitionHook(
                    actionProvider, planGenerator, planExecutor, keywordMatcher, experienceProvider, chatModel, sessionStore, properties);
        }
    }

    // ==================== MySQL/MyBatis Plus 配置（默认启用）====================

    @Configuration
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning.mysql", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
    static class MybatisPlusConfiguration {

        private static final Logger logger = LoggerFactory.getLogger(MybatisPlusConfiguration.class);

        public MybatisPlusConfiguration() {
            logger.info("MybatisPlusConfiguration#init - reason=MySQL configuration class loaded (mysql.enabled=true, MyBatisPlus present)");
        }

        @Bean
        @ConditionalOnMissingBean
        public ActionEntityConverter actionEntityConverter() {
            logger.info("MybatisPlusConfiguration#actionEntityConverter - reason=creating entity converter");
            return new ActionEntityConverter();
        }

        /**
         * MySQL 动作存储库 - 使用 @Autowired 注入 mapper，避免 @ConditionalOnBean 时序问题
         * 注意：@ConditionalOnBean 在自动配置阶段评估，此时 @MapperScan 创建的 Bean 可能尚未可用
         */
        @Bean
        @ConditionalOnMissingBean(ActionRepository.class)
        public ActionRepository mybatisPlusActionRepository(
                @Autowired(required = false) ActionRegistryMapper mapper,
                ActionEntityConverter converter) {

            logger.info("MybatisPlusConfiguration#mybatisPlusActionRepository - reason=ActionRegistryMapper found, creating MySQL repository");
            return new MybatisPlusActionRepository(mapper, converter);

        }
    }

    // ==================== Elasticsearch 配置 ====================

    @Configuration
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning.elasticsearch", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "co.elastic.clients.elasticsearch.ElasticsearchClient")
    static class ElasticsearchConfiguration {

        private static final Logger logger = LoggerFactory.getLogger(ElasticsearchConfiguration.class);

        public ElasticsearchConfiguration() {
            logger.info("ElasticsearchConfiguration#init - reason=ES configuration class loaded (elasticsearch.enabled=true)");
        }

        @Bean
        public ActionVectorizationService actionVectorizationService(ElasticsearchClient esClient,
                                                                     EmbeddingModel embeddingModel) {
            logger.info("ElasticsearchConfiguration#actionVectorizationService - reason=creating ES vectorization service");
            return new ActionVectorizationService(esClient, embeddingModel);
        }

        /**
         * 诊断方法：当 ElasticsearchClient 或 EmbeddingModel 不存在时输出日志
         */
        @Bean
        public Object esConfigDiagnostics(
                @Autowired(required = false) ElasticsearchClient esClient,
                @Autowired(required = false) EmbeddingModel embeddingModel) {
            if (esClient == null) {
                logger.warn("ElasticsearchConfiguration#diagnostics - reason=ElasticsearchClient bean NOT found. " +
                        "Check: spring.elasticsearch.uris configuration and spring-boot-starter-data-elasticsearch dependency");
            } else {
                logger.info("ElasticsearchConfiguration#diagnostics - reason=ElasticsearchClient bean found");
            }
            if (embeddingModel == null) {
                logger.warn("ElasticsearchConfiguration#diagnostics - reason=EmbeddingModel bean NOT found. " +
                        "Check: spring.ai.dashscope.embedding configuration");
            } else {
                logger.info("ElasticsearchConfiguration#diagnostics - reason=EmbeddingModel bean found");
            }
            return new Object(); // dummy bean for diagnostics
        }

        @Bean
        public ActionProvider semanticActionProvider(ActionRepository actionRepository,
                                                     ActionVectorizationService vectorService,
                                                     PlanningExtensionProperties properties) {
            PlanningExtensionProperties.ElasticsearchConfig esConfig = properties.getElasticsearch();
            logger.info("ElasticsearchConfiguration#semanticActionProvider - reason=creating semantic action provider, " +
                            "semanticWeight={}, keywordWeight={}, matchThreshold={}",
                    esConfig.getSemanticWeight(), esConfig.getKeywordWeight(), esConfig.getMatchThreshold());

            return new SemanticActionProvider(
                    actionRepository,
                    vectorService,
                    esConfig.getSemanticWeight(),
                    esConfig.getKeywordWeight(),
                    esConfig.getMatchThreshold()
            );
        }
    }
}
