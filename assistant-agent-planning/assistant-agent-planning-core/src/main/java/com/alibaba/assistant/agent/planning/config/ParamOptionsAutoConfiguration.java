/*
 * Copyright 2025 Alibaba Group Holding Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.planning.config;

import com.alibaba.assistant.agent.planning.cache.OptionsCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Spring Boot auto-configuration for parameter options service.
 * <p>
 * This configuration conditionally enables the parameter options service based on
 * the {@code spring.ai.alibaba.codeact.extension.planning.param-options.enabled} property.
 * When enabled, it registers the following beans:
 * </p>
 * <ul>
 *   <li>{@link OptionsCache} - In-memory cache with configurable TTL</li>
 *   <li>{@link RestTemplate} - HTTP client for external API calls</li>
 *   <li>Component scanned internal handlers and service implementations</li>
 * </ul>
 *
 * @author Assistant Agent Team
 */
@Configuration
@EnableConfigurationProperties(PlanningExtensionProperties.class)
@ConditionalOnProperty(
        prefix = "spring.ai.alibaba.codeact.extension.planning.param-options",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ComponentScan(basePackages = {
        "com.alibaba.assistant.agent.planning.internal",
        "com.alibaba.assistant.agent.planning.service"
})
public class ParamOptionsAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ParamOptionsAutoConfiguration.class);

    public ParamOptionsAutoConfiguration() {
        logger.info("ParamOptionsAutoConfiguration - Parameter options module initialized");
    }

    /**
     * Creates the options cache bean with configured TTL.
     *
     * @param properties the planning extension properties
     * @return the options cache instance
     */
    @Bean
    public OptionsCache optionsCache(PlanningExtensionProperties properties) {
        long cacheTtl = properties.getParamOptions().getCacheTtl();
        logger.info("ParamOptionsAutoConfiguration - Creating OptionsCache with TTL: {}ms", cacheTtl);
        return new OptionsCache(cacheTtl);
    }

    /**
     * Creates the REST template bean for HTTP handlers.
     *
     * @return the REST template instance
     */
    @Bean
    public RestTemplate restTemplate() {
        logger.info("ParamOptionsAutoConfiguration - Creating RestTemplate for HTTP handlers");
        return new RestTemplate();
    }
}
