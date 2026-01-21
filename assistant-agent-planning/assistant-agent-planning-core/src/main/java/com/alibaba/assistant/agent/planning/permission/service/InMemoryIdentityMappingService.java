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
package com.alibaba.assistant.agent.planning.permission.service;

import com.alibaba.assistant.agent.planning.permission.model.AccessibleSystem;
import com.alibaba.assistant.agent.planning.permission.model.ExternalIdentity;
import com.alibaba.assistant.agent.planning.permission.model.ExternalSystemConfig;
import com.alibaba.assistant.agent.planning.permission.model.UserIdentityMapping;
import com.alibaba.assistant.agent.planning.permission.spi.IdentityMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of IdentityMappingService.
 * <p>
 * This implementation stores identity mappings in memory,
 * suitable for development and demo purposes.
 * <p>
 * For production, implement a database-backed version.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Service
public class InMemoryIdentityMappingService implements IdentityMappingService {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryIdentityMappingService.class);

    /**
     * Storage: platformUserId -> (systemId -> mapping)
     */
    private final Map<String, Map<String, UserIdentityMapping>> mappings = new ConcurrentHashMap<>();

    /**
     * Available external systems.
     */
    private final Map<String, ExternalSystemConfig> systems = new ConcurrentHashMap<>();

    public InMemoryIdentityMappingService() {
        initializeDemoData();
    }

    /**
     * Initialize demo data for testing.
     */
    private void initializeDemoData() {
        // Initialize external systems
        ExternalSystemConfig oaSystem = new ExternalSystemConfig();
        oaSystem.setSystemId("oa-system");
        oaSystem.setSystemName("OA办公系统");
        oaSystem.setSystemType("OA");
        oaSystem.setDescription("企业OA办公系统，包含考勤、任务管理等功能");
        oaSystem.setEnabled(true);
        systems.put("oa-system", oaSystem);

        ExternalSystemConfig govSystem = new ExternalSystemConfig();
        govSystem.setSystemId("gov-platform");
        govSystem.setSystemName("政务服务平台");
        govSystem.setSystemType("GOV");
        govSystem.setDescription("政务服务平台，提供业务办理、预约等功能");
        govSystem.setEnabled(true);
        systems.put("gov-platform", govSystem);

        // Initialize demo user mappings
        // 张三：OA员工，政务平台群众
        bindIdentity("U001", "oa-system", "zhang.san@company.com", "张三",
                Map.of("role", "employee", "deptId", "tech-001", "deptName", "技术部"));
        bindIdentity("U001", "gov-platform", "320102199001011234", "张三",
                Map.of("userType", "citizen"));

        // 李四：OA经理
        bindIdentity("U002", "oa-system", "li.si@company.com", "李四",
                Map.of("role", "manager", "deptId", "tech-001", "deptName", "技术部"));

        // 王五：OA大领导，政务平台分管领导
        bindIdentity("U003", "oa-system", "wang.wu@company.com", "王五",
                Map.of("role", "director"));
        bindIdentity("U003", "gov-platform", "leader_001", "王五",
                Map.of("userType", "leader", "bureauId", "civil-affairs", "bureauName", "民政局"));

        // 赵六：政务平台业务人员
        bindIdentity("U004", "gov-platform", "staff_001", "赵六",
                Map.of("userType", "staff", "bureauId", "civil-affairs", "bureauName", "民政局", "level", 2));

        // 孙七：OA员工（销售部）
        bindIdentity("U005", "oa-system", "sun.qi@company.com", "孙七",
                Map.of("role", "employee", "deptId", "sales-001", "deptName", "销售部"));

        logger.info("InMemoryIdentityMappingService initialized with demo data");
    }

    @Override
    public Optional<ExternalIdentity> getExternalIdentity(String platformUserId, String systemId) {
        Map<String, UserIdentityMapping> userMappings = mappings.get(platformUserId);
        if (userMappings == null) {
            return Optional.empty();
        }

        UserIdentityMapping mapping = userMappings.get(systemId);
        if (mapping == null) {
            return Optional.empty();
        }

        return Optional.of(mapping.toExternalIdentity());
    }

    @Override
    public List<AccessibleSystem> getAccessibleSystems(String platformUserId) {
        Map<String, UserIdentityMapping> userMappings = mappings.getOrDefault(platformUserId, Collections.emptyMap());

        return systems.values().stream()
                .filter(ExternalSystemConfig::isEnabled)
                .map(system -> {
                    UserIdentityMapping mapping = userMappings.get(system.getSystemId());
                    AccessibleSystem.Builder builder = AccessibleSystem.builder()
                            .systemId(system.getSystemId())
                            .systemName(system.getSystemName())
                            .systemType(system.getSystemType())
                            .iconUrl(system.getIconUrl())
                            .description(system.getDescription());

                    if (mapping != null) {
                        builder.bound(true)
                                .externalUserId(mapping.getExternalUserId())
                                .externalUsername(mapping.getExternalUsername());
                    } else {
                        builder.bound(false);
                    }

                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<UserIdentityMapping> getMappingsByUser(String platformUserId) {
        Map<String, UserIdentityMapping> userMappings = mappings.get(platformUserId);
        if (userMappings == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(userMappings.values());
    }

    @Override
    public void bindIdentity(String platformUserId, String systemId,
                             String externalUserId, String externalUsername,
                             Map<String, Object> extraInfo) {
        UserIdentityMapping mapping = new UserIdentityMapping();
        mapping.setId(UUID.randomUUID().toString());
        mapping.setPlatformUserId(platformUserId);
        mapping.setSystemId(systemId);
        mapping.setExternalUserId(externalUserId);
        mapping.setExternalUsername(externalUsername);
        mapping.setExtraInfo(extraInfo != null ? new HashMap<>(extraInfo) : new HashMap<>());
        mapping.setBindType(UserIdentityMapping.BIND_TYPE_MANUAL);
        mapping.setBindTime(LocalDateTime.now());

        mappings.computeIfAbsent(platformUserId, k -> new ConcurrentHashMap<>())
                .put(systemId, mapping);

        logger.info("InMemoryIdentityMappingService#bindIdentity - bound: platformUser={}, system={}, externalUser={}",
                platformUserId, systemId, externalUserId);
    }

    @Override
    public void unbindIdentity(String platformUserId, String systemId) {
        Map<String, UserIdentityMapping> userMappings = mappings.get(platformUserId);
        if (userMappings != null) {
            UserIdentityMapping removed = userMappings.remove(systemId);
            if (removed != null) {
                logger.info("InMemoryIdentityMappingService#unbindIdentity - unbound: platformUser={}, system={}",
                        platformUserId, systemId);
            }
        }
    }

    @Override
    public boolean isBound(String platformUserId, String systemId) {
        Map<String, UserIdentityMapping> userMappings = mappings.get(platformUserId);
        return userMappings != null && userMappings.containsKey(systemId);
    }

    @Override
    public void updateExtraInfo(String platformUserId, String systemId, Map<String, Object> extraInfo) {
        Map<String, UserIdentityMapping> userMappings = mappings.get(platformUserId);
        if (userMappings != null) {
            UserIdentityMapping mapping = userMappings.get(systemId);
            if (mapping != null) {
                mapping.setExtraInfo(extraInfo != null ? new HashMap<>(extraInfo) : new HashMap<>());
                logger.info("InMemoryIdentityMappingService#updateExtraInfo - updated: platformUser={}, system={}",
                        platformUserId, systemId);
            }
        }
    }

    @Override
    public Optional<String> findPlatformUserByExternalId(String systemId, String externalUserId) {
        return mappings.entrySet().stream()
                .filter(entry -> {
                    UserIdentityMapping mapping = entry.getValue().get(systemId);
                    return mapping != null && externalUserId.equals(mapping.getExternalUserId());
                })
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Add a new external system configuration.
     *
     * @param config the system configuration
     */
    public void addSystem(ExternalSystemConfig config) {
        systems.put(config.getSystemId(), config);
    }

    /**
     * Remove an external system configuration.
     *
     * @param systemId the system ID
     */
    public void removeSystem(String systemId) {
        systems.remove(systemId);
    }

    /**
     * Get system configuration.
     *
     * @param systemId the system ID
     * @return the system configuration, or empty if not found
     */
    public Optional<ExternalSystemConfig> getSystem(String systemId) {
        return Optional.ofNullable(systems.get(systemId));
    }
}
