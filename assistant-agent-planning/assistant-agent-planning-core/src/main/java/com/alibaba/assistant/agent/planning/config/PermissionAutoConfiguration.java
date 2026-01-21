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

import com.alibaba.assistant.agent.planning.permission.adapter.GovPermissionAdapter;
import com.alibaba.assistant.agent.planning.permission.adapter.OaPermissionAdapter;
import com.alibaba.assistant.agent.planning.permission.adapter.PermissionAdapterRegistry;
import com.alibaba.assistant.agent.planning.permission.interceptor.PermissionInterceptor;
import com.alibaba.assistant.agent.planning.permission.service.DefaultPermissionService;
import com.alibaba.assistant.agent.planning.permission.service.InMemoryIdentityMappingService;
import com.alibaba.assistant.agent.planning.permission.service.UnifiedChatService;
import com.alibaba.assistant.agent.planning.permission.spi.IdentityMappingService;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionAdapter;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionService;
import com.alibaba.assistant.agent.planning.web.controller.PermissionController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Auto-configuration for the permission system.
 * <p>
 * Configures permission adapters, services, and controllers when
 * the permission module is enabled.
 * <p>
 * Enable with:
 * <pre>
 * spring.ai.alibaba.codeact.extension.planning.permission.enabled=true
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(
        prefix = "spring.ai.alibaba.codeact.extension.planning.permission",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PermissionAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PermissionAutoConfiguration.class);

    /**
     * Register OA permission adapter.
     */
    @Bean
    @ConditionalOnMissingBean(OaPermissionAdapter.class)
    public OaPermissionAdapter oaPermissionAdapter() {
        logger.info("PermissionAutoConfiguration - creating OaPermissionAdapter");
        return new OaPermissionAdapter();
    }

    /**
     * Register Government platform permission adapter.
     */
    @Bean
    @ConditionalOnMissingBean(GovPermissionAdapter.class)
    public GovPermissionAdapter govPermissionAdapter() {
        logger.info("PermissionAutoConfiguration - creating GovPermissionAdapter");
        return new GovPermissionAdapter();
    }

    /**
     * Create permission adapter registry.
     */
    @Bean
    @ConditionalOnMissingBean(PermissionAdapterRegistry.class)
    public PermissionAdapterRegistry permissionAdapterRegistry(List<PermissionAdapter> adapters) {
        logger.info("PermissionAutoConfiguration - creating PermissionAdapterRegistry with {} adapters",
                adapters != null ? adapters.size() : 0);
        return new PermissionAdapterRegistry(adapters);
    }

    /**
     * Create identity mapping service (in-memory implementation for demo).
     */
    @Bean
    @ConditionalOnMissingBean(IdentityMappingService.class)
    public IdentityMappingService identityMappingService() {
        logger.info("PermissionAutoConfiguration - creating InMemoryIdentityMappingService");
        return new InMemoryIdentityMappingService();
    }

    /**
     * Create permission service.
     */
    @Bean
    @ConditionalOnMissingBean(PermissionService.class)
    public PermissionService permissionService(PermissionAdapterRegistry adapterRegistry,
                                               IdentityMappingService identityMappingService) {
        logger.info("PermissionAutoConfiguration - creating DefaultPermissionService");
        return new DefaultPermissionService(adapterRegistry, identityMappingService);
    }

    /**
     * Create unified chat service.
     */
    @Bean
    @ConditionalOnMissingBean(UnifiedChatService.class)
    public UnifiedChatService unifiedChatService(IdentityMappingService identityMappingService,
                                                  PermissionService permissionService) {
        logger.info("PermissionAutoConfiguration - creating UnifiedChatService");
        return new UnifiedChatService(identityMappingService, permissionService);
    }

    /**
     * Create permission interceptor.
     */
    @Bean
    @ConditionalOnMissingBean(PermissionInterceptor.class)
    public PermissionInterceptor permissionInterceptor(PermissionService permissionService) {
        logger.info("PermissionAutoConfiguration - creating PermissionInterceptor");
        return new PermissionInterceptor(permissionService);
    }

    /**
     * Create permission controller.
     */
    @Bean
    @ConditionalOnMissingBean(PermissionController.class)
    public PermissionController permissionController(IdentityMappingService identityMappingService,
                                                     PermissionService permissionService,
                                                     UnifiedChatService unifiedChatService) {
        logger.info("PermissionAutoConfiguration - creating PermissionController");
        return new PermissionController(identityMappingService, permissionService, unifiedChatService);
    }
}
