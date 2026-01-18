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
package com.alibaba.assistant.agent.data.config;

import com.alibaba.assistant.agent.data.cache.DatasourceCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bean configuration for DatasourceCache with scheduled cleanup.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(
    prefix = "spring.assistant-agent.data.persistent-datasource.cache",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableScheduling
public class DatasourceCacheBean {

    private static final Logger logger = LoggerFactory.getLogger(DatasourceCacheBean.class);

    /**
     * Create DatasourceCache bean.
     *
     * @param properties the persistent datasource properties
     * @return DatasourceCache instance
     */
    @Bean
    public DatasourceCache datasourceCache(PersistentDatasourceProperties properties) {
        long ttlMillis = properties.getCache().getTtlMinutes() * 60 * 1000L;
        logger.info("DatasourceCacheBean - Creating cache with TTL: {} minutes", properties.getCache().getTtlMinutes());
        return new DatasourceCache(ttlMillis);
    }

    /**
     * Scheduled task runner for cache cleanup.
     */
    @Component
    @ConditionalOnProperty(
        prefix = "spring.assistant-agent.data.persistent-datasource.cache",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public static class CacheCleanupTask {

        private static final Logger logger = LoggerFactory.getLogger(CacheCleanupTask.class);
        private final DatasourceCache cache;

        public CacheCleanupTask(DatasourceCache cache) {
            this.cache = cache;
        }

        /**
         * Cleanup expired cache entries.
         * Runs based on configured cleanup interval.
         */
        @Scheduled(fixedDelayString = "${spring.assistant-agent.data.persistent-datasource.cache.cleanup-interval-seconds:60}000")
        public void cleanupExpiredEntries() {
            logger.debug("CacheCleanupTask - Running cleanup task");
            cache.cleanupExpired();
        }
    }
}
