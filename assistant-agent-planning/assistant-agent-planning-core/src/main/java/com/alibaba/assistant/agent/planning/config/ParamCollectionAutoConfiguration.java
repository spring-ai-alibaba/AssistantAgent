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

import com.alibaba.assistant.agent.planning.executor.ActionExecutor;
import com.alibaba.assistant.agent.planning.executor.ActionExecutorFactory;
import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.param.ParameterValidator;
import com.alibaba.assistant.agent.planning.param.StructuredParamExtractor;
import com.alibaba.assistant.agent.planning.service.ParamCollectionService;
import com.alibaba.assistant.agent.planning.spi.SessionProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * 参数收集自动配置
 *
 * <p>配置参数收集流程所需的所有 Bean：
 * <ul>
 * <li>StructuredParamExtractor - LLM 参数提取器</li>
 * <li>ParameterValidator - 参数验证器</li>
 * <li>ActionExecutor - Action 执行器</li>
 * <li>ParamCollectionService - 参数收集服务</li>
 * </ul>
 *
 * <p>注意：ActionIntentEvaluator 的配置在 integration 模块中
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.planning.param-collection", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(PlanningExtensionProperties.class)
public class ParamCollectionAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ParamCollectionAutoConfiguration.class);

    public ParamCollectionAutoConfiguration() {
        logger.info("ParamCollectionAutoConfiguration#init - reason=parameter collection is enabled");
    }

    /**
     * 配置 RestTemplate（用于 HTTP API 调用）
     */
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        logger.info("ParamCollectionAutoConfiguration#restTemplate - bean created");
        return new RestTemplate();
    }

    /**
     * 配置 StructuredParamExtractor
     */
    @Bean
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean
    public StructuredParamExtractor structuredParamExtractor(ChatModel chatModel, ObjectMapper objectMapper) {
        logger.info("ParamCollectionAutoConfiguration#structuredParamExtractor - bean created");
        return new StructuredParamExtractor(chatModel, objectMapper);
    }

    /**
     * 配置 ParameterValidator
     */
    @Bean
    @ConditionalOnMissingBean
    public ParameterValidator parameterValidator() {
        logger.info("ParamCollectionAutoConfiguration#parameterValidator - bean created");
        return new ParameterValidator();
    }

    /**
     * 配置 ActionExecutor
     */
    @Bean
    @ConditionalOnMissingBean
    public ActionExecutor actionExecutor(RestTemplate restTemplate, ObjectMapper objectMapper) {
        logger.info("ParamCollectionAutoConfiguration#actionExecutor - bean created");
        return new ActionExecutor(restTemplate, objectMapper);
    }

    /**
     * 配置 ParamCollectionService
     */
    @Bean
    @ConditionalOnMissingBean
    public ParamCollectionService paramCollectionService(
            StructuredParamExtractor paramExtractor,
            ParameterValidator parameterValidator,
            ActionExecutorFactory actionExecutorFactory,
            ObjectMapper objectMapper,
            SessionProvider sessionProvider) {
        logger.info("ParamCollectionAutoConfiguration#paramCollectionService - bean created");
        return new ParamCollectionService(paramExtractor, parameterValidator, actionExecutorFactory, objectMapper, sessionProvider);
    }

    /**
     * 定时清理过期会话
     */
    @Bean
    @ConditionalOnBean(ParamCollectionService.class)
    public ParamCollectionCleanupTask paramCollectionCleanupTask(ParamCollectionService paramCollectionService) {
        logger.info("ParamCollectionAutoConfiguration#paramCollectionCleanupTask - bean created");
        return new ParamCollectionCleanupTask(paramCollectionService);
    }

    /**
     * 参数收集会话清理任务
     */
    public static class ParamCollectionCleanupTask {
        private static final Logger logger = LoggerFactory.getLogger(ParamCollectionCleanupTask.class);
        private final ParamCollectionService paramCollectionService;

        public ParamCollectionCleanupTask(ParamCollectionService paramCollectionService) {
            this.paramCollectionService = paramCollectionService;
            logger.info("ParamCollectionCleanupTask#init - scheduling cleanup task");
            // 可以在这里添加定时任务，例如使用 @Scheduled
            // 目前简化为在每次创建会话时清理
        }

        // 可以添加 @Scheduled 注解的方法来定期清理
        // @Scheduled(fixedRate = 300000) // 每5分钟清理一次
        public void cleanup() {
            paramCollectionService.cleanupExpiredSessions();
        }
    }
}
