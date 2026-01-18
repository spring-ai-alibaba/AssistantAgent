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

import com.alibaba.assistant.agent.planning.session.InMemoryParamCollectionSessionStore;
import com.alibaba.assistant.agent.planning.session.ParamCollectionSessionStore;
import com.alibaba.assistant.agent.planning.session.RedisParamCollectionSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 参数收集会话存储自动配置
 *
 * <p>配置优先级：
 * <ol>
 *     <li>如果 Redis 可用且启用，使用 Redis 存储</li>
 *     <li>否则使用内存存储（开发/测试环境）</li>
 * </ol>
 *
 * <p>配置属性：
 * <pre>
 * spring.ai.alibaba.codeact.extension.planning:
 *   param-collection:
 *     session:
 *       store-type: redis  # 可选：redis, memory
 *       ttl-minutes: 30    # 会话过期时间（分钟）
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(PlanningExtensionProperties.class)
public class ParamCollectionSessionAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ParamCollectionSessionAutoConfiguration.class);

    /**
     * Redis 会话存储配置
     */
    @AutoConfiguration
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(
            prefix = "spring.ai.alibaba.codeact.extension.planning.param-collection.session",
            name = "store-type",
            havingValue = "redis",
            matchIfMissing = true  // 默认使用 Redis（如果可用）
    )
    public static class RedisSessionStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean(ParamCollectionSessionStore.class)
        public ParamCollectionSessionStore redisParamCollectionSessionStore(
                StringRedisTemplate redisTemplate,
                PlanningExtensionProperties properties) {

            Duration ttl = Duration.ofMinutes(30); // 默认30分钟

            PlanningExtensionProperties.ParamCollectionConfig config = properties.getParamCollection();
            if (config != null && config.getSession() != null && config.getSession().getTtlMinutes() != null) {
                ttl = Duration.ofMinutes(config.getSession().getTtlMinutes());
            }

            logger.info("ParamCollectionSessionAutoConfiguration#redisParamCollectionSessionStore - " +
                    "reason=creating Redis session store, ttl={}", ttl);

            return new RedisParamCollectionSessionStore(redisTemplate, ttl);
        }
    }

    /**
     * 内存会话存储配置（降级方案）
     */
    @Bean
    @ConditionalOnMissingBean(ParamCollectionSessionStore.class)
    public ParamCollectionSessionStore inMemoryParamCollectionSessionStore(
            PlanningExtensionProperties properties) {

        Duration ttl = Duration.ofMinutes(30); // 默认30分钟

        PlanningExtensionProperties.ParamCollectionConfig config = properties.getParamCollection();
        if (config != null && config.getSession() != null && config.getSession().getTtlMinutes() != null) {
            ttl = Duration.ofMinutes(config.getSession().getTtlMinutes());
        }

        logger.info("ParamCollectionSessionAutoConfiguration#inMemoryParamCollectionSessionStore - " +
                "reason=creating in-memory session store (Redis not available), ttl={}", ttl);
        logger.warn("ParamCollectionSessionAutoConfiguration#inMemoryParamCollectionSessionStore - " +
                "reason=WARNING: In-memory session store is not suitable for distributed environment!");

        return new InMemoryParamCollectionSessionStore(ttl);
    }
}
