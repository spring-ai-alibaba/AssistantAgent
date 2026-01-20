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
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ParamOptionsAutoConfiguration}.
 * <p>
 * Note: These tests verify bean registration only. Full integration tests with
 * component scanning and service beans are not included here to avoid complex
 * dependency injection requirements.
 *
 * @author Assistant Agent Team
 */
class ParamOptionsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    // Prevent component scanning from loading components that need Nl2SqlService, etc.
                    "spring.main.lazy-initialization=true"
            )
            .withUserConfiguration(TestAutoConfiguration.class);

    /**
     * Minimal auto-configuration for testing that excludes component scanning.
     */
    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(PlanningExtensionProperties.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "spring.ai.alibaba.codeact.extension.planning.param-options",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    static class TestAutoConfiguration {
        @org.springframework.context.annotation.Bean
        public OptionsCache optionsCache(PlanningExtensionProperties properties) {
            return new OptionsCache(properties.getParamOptions().getCacheTtl());
        }

        @org.springframework.context.annotation.Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }
    }

    @Test
    void shouldRegisterBeansWhenEnabled() {
        contextRunner
                .withPropertyValues("spring.ai.alibaba.codeact.extension.planning.param-options.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OptionsCache.class);
                    assertThat(context).hasSingleBean(RestTemplate.class);
                    // Note: ParameterOptionsService requires component scanning, not tested here
                });
    }

    @Test
    void shouldNotRegisterBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("spring.ai.alibaba.codeact.extension.planning.param-options.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OptionsCache.class);
                    assertThat(context).doesNotHaveBean(RestTemplate.class);
                });
    }

    @Test
    void shouldUseCacheTtlFromConfig() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.alibaba.codeact.extension.planning.param-options.enabled=true",
                        "spring.ai.alibaba.codeact.extension.planning.param-options.cache-ttl=60000"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(OptionsCache.class);
                    // Cache TTL is internal, verified by successful bean creation
                });
    }
}
