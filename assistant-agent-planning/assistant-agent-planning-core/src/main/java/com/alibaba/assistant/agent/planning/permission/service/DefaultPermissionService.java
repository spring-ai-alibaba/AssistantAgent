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

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.DataPermissionConfig;
import com.alibaba.assistant.agent.planning.permission.adapter.PermissionAdapterRegistry;
import com.alibaba.assistant.agent.planning.permission.model.DataFilter;
import com.alibaba.assistant.agent.planning.permission.model.ExternalIdentity;
import com.alibaba.assistant.agent.planning.permission.model.StandardPermission;
import com.alibaba.assistant.agent.planning.permission.spi.IdentityMappingService;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionAdapter;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionCheckResult;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of PermissionService.
 * <p>
 * Provides permission checking and data permission injection.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Service
public class DefaultPermissionService implements PermissionService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPermissionService.class);

    private final PermissionAdapterRegistry adapterRegistry;
    private final IdentityMappingService identityMappingService;

    public DefaultPermissionService(PermissionAdapterRegistry adapterRegistry,
                                    IdentityMappingService identityMappingService) {
        this.adapterRegistry = adapterRegistry;
        this.identityMappingService = identityMappingService;
    }

    @Override
    public PermissionCheckResult checkActionPermission(StandardPermission permission, String actionId) {
        if (permission == null) {
            logger.warn("DefaultPermissionService#checkActionPermission - permission is null");
            return PermissionCheckResult.denied("权限信息为空");
        }

        if (actionId == null || actionId.isEmpty()) {
            logger.warn("DefaultPermissionService#checkActionPermission - actionId is empty");
            return PermissionCheckResult.actionNotFound(actionId);
        }

        boolean hasPermission = permission.hasPermission(actionId);

        if (hasPermission) {
            logger.debug("DefaultPermissionService#checkActionPermission - allowed: user={}, action={}",
                    permission.getUserId(), actionId);
            return PermissionCheckResult.allowed().withActionId(actionId);
        } else {
            logger.info("DefaultPermissionService#checkActionPermission - denied: user={}, action={}, allowedActions={}",
                    permission.getUserId(), actionId, permission.getAllowedActions());
            return PermissionCheckResult.noPermission(actionId).withActionId(actionId);
        }
    }

    @Override
    public Map<String, Object> injectDataPermission(StandardPermission permission,
                                                     ActionDefinition action,
                                                     Map<String, Object> userParams) {
        Map<String, Object> result = new HashMap<>(userParams != null ? userParams : new HashMap<>());

        if (permission == null || action == null) {
            logger.warn("DefaultPermissionService#injectDataPermission - permission or action is null");
            return result;
        }

        DataPermissionConfig config = action.getDataPermissionConfig();
        if (config == null || !config.isEnabled()) {
            logger.debug("DefaultPermissionService#injectDataPermission - data permission not enabled for action: {}",
                    action.getActionId());
            return result;
        }

        Map<String, DataFilter> filters = permission.getFilters();
        if (filters == null || filters.isEmpty()) {
            logger.debug("DefaultPermissionService#injectDataPermission - no filters to inject for action: {}",
                    action.getActionId());
            return result;
        }

        // Inject filter values into parameters
        for (Map.Entry<String, DataFilter> entry : filters.entrySet()) {
            String filterField = entry.getKey();
            DataFilter filter = entry.getValue();

            // Get mapped parameter name
            String paramField = config.getMappedParamField(filterField);

            // Only inject if the filter uses 'eq' operator and parameter is not already set
            if ("eq".equals(filter.getOperator()) && !result.containsKey(paramField)) {
                result.put(paramField, filter.getValue());
                logger.debug("DefaultPermissionService#injectDataPermission - injected: {}={} for action: {}",
                        paramField, filter.getValue(), action.getActionId());
            }
        }

        logger.info("DefaultPermissionService#injectDataPermission - injected {} filters for action: {}",
                filters.size(), action.getActionId());

        return result;
    }

    @Override
    public StandardPermission getPermission(String platformUserId, String systemId, Map<String, Object> context) {
        // Step 1: Get external identity
        Optional<ExternalIdentity> identityOpt = identityMappingService.getExternalIdentity(platformUserId, systemId);

        if (identityOpt.isEmpty()) {
            logger.warn("DefaultPermissionService#getPermission - user not bound to system: platformUser={}, system={}",
                    platformUserId, systemId);
            // Return empty permission
            StandardPermission emptyPermission = new StandardPermission();
            emptyPermission.setSystemId(systemId);
            return emptyPermission;
        }

        ExternalIdentity identity = identityOpt.get();

        // Step 2: Build context for adapter
        Map<String, Object> adapterContext = new HashMap<>();
        adapterContext.put("userId", identity.getUserId());
        adapterContext.put("username", identity.getUsername());

        // Merge extra info from identity mapping
        if (identity.getExtraInfo() != null) {
            adapterContext.putAll(identity.getExtraInfo());
        }

        // Merge additional context (may override)
        if (context != null) {
            adapterContext.putAll(context);
        }

        // Step 3: Use adapter to convert to standard permission
        return adaptPermission(systemId, adapterContext);
    }

    @Override
    public StandardPermission adaptPermission(String systemId, Map<String, Object> context) {
        PermissionAdapter adapter = adapterRegistry.getAdapter(systemId);

        if (adapter == null) {
            logger.warn("DefaultPermissionService#adaptPermission - no adapter found for system: {}", systemId);
            StandardPermission emptyPermission = new StandardPermission();
            emptyPermission.setSystemId(systemId);
            return emptyPermission;
        }

        StandardPermission permission = adapter.adapt(context);

        logger.debug("DefaultPermissionService#adaptPermission - adapted permission for system: {}, user={}, actions={}",
                systemId, permission.getUserId(), permission.getAllowedActions());

        return permission;
    }
}
