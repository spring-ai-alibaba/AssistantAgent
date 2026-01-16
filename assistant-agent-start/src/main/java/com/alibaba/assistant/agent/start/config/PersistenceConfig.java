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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import com.alibaba.assistant.agent.extension.learning.spi.LearningRepository;
import com.alibaba.assistant.agent.extension.trigger.repository.TriggerExecutionLogRepository;
import com.alibaba.assistant.agent.extension.trigger.repository.TriggerRepository;
import com.alibaba.assistant.agent.start.persistence.experience.provider.ElasticsearchExperienceProvider;
import com.alibaba.assistant.agent.start.persistence.experience.repository.ElasticsearchExperienceRepository;
import com.alibaba.assistant.agent.start.persistence.learning.mapper.LearningRecordMapper;
import com.alibaba.assistant.agent.start.persistence.learning.repository.MysqlLearningRepository;
import com.alibaba.assistant.agent.start.persistence.trigger.mapper.TriggerDefinitionMapper;
import com.alibaba.assistant.agent.start.persistence.trigger.mapper.TriggerExecutionRecordMapper;
import com.alibaba.assistant.agent.start.persistence.trigger.repository.MysqlTriggerExecutionLogRepository;
import com.alibaba.assistant.agent.start.persistence.trigger.repository.MysqlTriggerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 持久化层配置
 *
 * <p>配置 MySQL 和 Elasticsearch 持久化实现，替换默认的内存实现。
 *
 * <p>存储策略：
 * <ul>
 *     <li>Experience → Elasticsearch（支持全文搜索）</li>
 *     <li>Learning → MySQL</li>
 *     <li>Trigger → MySQL</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Configuration
@MapperScan(basePackages = {
        "com.alibaba.assistant.agent.start.persistence.learning.mapper",
        "com.alibaba.assistant.agent.start.persistence.trigger.mapper",
        "com.alibaba.assistant.agent.planning.persistence.mapper"
})
public class PersistenceConfig {

    private static final Logger log = LoggerFactory.getLogger(PersistenceConfig.class);

    @Value("${spring.ai.alibaba.codeact.extension.experience.elasticsearch.index-name:experiences}")
    private String experienceIndexName;

    public PersistenceConfig() {
        log.info("PersistenceConfig#init - reason=persistence configuration loaded");
    }

    // ==================== Learning 模块 MySQL 存储 ====================

    @Bean
    @ConditionalOnMissingBean(name = "inMemoryLearningRepository")
    @ConditionalOnBean(LearningRecordMapper.class)
    public LearningRepository<?> mysqlLearningRepository(LearningRecordMapper mapper, ObjectMapper objectMapper) {
        log.info("PersistenceConfig#mysqlLearningRepository - reason=creating MySQL learning repository");
        return new MysqlLearningRepository<>(mapper, objectMapper, Object.class);
    }

    // ==================== Trigger 模块 MySQL 存储 ====================

    @Bean
    @ConditionalOnMissingBean(TriggerRepository.class)
    @ConditionalOnBean(TriggerDefinitionMapper.class)
    public TriggerRepository mysqlTriggerRepository(TriggerDefinitionMapper mapper) {
        log.info("PersistenceConfig#mysqlTriggerRepository - reason=creating MySQL trigger repository");
        return new MysqlTriggerRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(TriggerExecutionLogRepository.class)
    @ConditionalOnBean(TriggerExecutionRecordMapper.class)
    public TriggerExecutionLogRepository mysqlTriggerExecutionLogRepository(TriggerExecutionRecordMapper mapper) {
        log.info("PersistenceConfig#mysqlTriggerExecutionLogRepository - reason=creating MySQL trigger execution log repository");
        return new MysqlTriggerExecutionLogRepository(mapper);
    }

    // ==================== Experience 模块 Elasticsearch 存储 ====================

    @Bean
    @Primary
    public ExperienceProvider elasticsearchExperienceProvider(
            ElasticsearchClient esClient,
            ObjectMapper objectMapper) {
        log.info("PersistenceConfig#elasticsearchExperienceProvider - reason=creating Elasticsearch experience provider (PRIMARY), indexName={}", experienceIndexName);
        return new ElasticsearchExperienceProvider(esClient, objectMapper, experienceIndexName);
    }

    @Bean
    @Primary
    public ExperienceRepository elasticsearchExperienceRepository(
            ElasticsearchClient esClient,
            ObjectMapper objectMapper) {
        log.info("PersistenceConfig#elasticsearchExperienceRepository - reason=creating Elasticsearch experience repository (PRIMARY), indexName={}", experienceIndexName);
        return new ElasticsearchExperienceRepository(esClient, objectMapper, experienceIndexName);
    }
}
