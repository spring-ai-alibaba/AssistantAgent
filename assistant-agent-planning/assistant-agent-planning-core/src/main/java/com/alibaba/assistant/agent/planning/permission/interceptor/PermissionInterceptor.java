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
package com.alibaba.assistant.agent.planning.permission.interceptor;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.DataPermissionConfig;
import com.alibaba.assistant.agent.planning.permission.model.StandardPermission;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionCheckResult;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Permission interceptor for Action execution.
 * <p>
 * Provides two key functions:
 * <ol>
 *   <li>Check functional permissions before execution</li>
 *   <li>Inject data permissions into action parameters</li>
 * </ol>
 * <p>
 * This class is optional - only used when permission module is enabled.
 * When disabled, actions execute without permission checks.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class PermissionInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(PermissionInterceptor.class);

    private final PermissionService permissionService;

    public PermissionInterceptor(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * Check if user has permission to execute the action.
     * <p>
     * This is called before action execution. If permission is denied,
     * the action should not be executed.
     *
     * @param action the action to check
     * @param platformUserId platform user ID (from execution context)
     * @param systemId target system ID (from action definition or context)
     * @param context additional context
     * @return permission check result
     */
    public PermissionCheckResult checkPermission(ActionDefinition action,
                                                   String platformUserId,
                                                   String systemId,
                                                   Map<String, Object> context) {
        if (action == null) {
            return PermissionCheckResult.denied("Action cannot be null", "INVALID_ACTION");
        }

        // Skip permission check if action doesn't have permission config
        DataPermissionConfig permissionConfig = action.getDataPermissionConfig();
        if (permissionConfig == null || !permissionConfig.isEnforced()) {
            logger.debug("PermissionInterceptor#checkPermission - permission not enforced for actionId={}",
                    action.getActionId());
            return PermissionCheckResult.allowed();
        }

        // Check if we have required context
        if (platformUserId == null || platformUserId.isEmpty()) {
            logger.warn("PermissionInterceptor#checkPermission - missing platformUserId for actionId={}",
                    action.getActionId());
            return PermissionCheckResult.denied("缺少用户ID", "MISSING_USER_ID");
        }

        if (systemId == null || systemId.isEmpty()) {
            logger.warn("PermissionInterceptor#checkPermission - missing systemId for actionId={}",
                    action.getActionId());
            return PermissionCheckResult.denied("缺少系统ID", "MISSING_SYSTEM_ID");
        }

        logger.info("PermissionInterceptor#checkPermission - checking permission: userId={}, systemId={}, actionId={}",
                platformUserId, systemId, action.getActionId());

        try {
            // Get user's standardized permission
            StandardPermission permission = permissionService.getPermission(platformUserId, systemId, context);

            // Check action permission
            return permissionService.checkActionPermission(permission, action.getActionId());
        } catch (Exception e) {
            logger.error("PermissionInterceptor#checkPermission - error checking permission", e);
            return PermissionCheckResult.denied("权限检查失败: " + e.getMessage(), "PERMISSION_CHECK_ERROR");
        }
    }

    /**
     * Inject data permissions into action parameters.
     * <p>
     * This modifies the parameters by adding filter conditions based on
     * user's data permission scope. Called before action execution.
     *
     * @param action the action to execute
     * @param params original parameters from user
     * @param platformUserId platform user ID
     * @param systemId target system ID
     * @param context additional context
     * @return parameters with injected data permission filters
     */
    public Map<String, Object> injectDataPermission(ActionDefinition action,
                                                      Map<String, Object> params,
                                                      String platformUserId,
                                                      String systemId,
                                                      Map<String, Object> context) {
        if (action == null || params == null) {
            return params != null ? params : new HashMap<>();
        }

        // Skip injection if action doesn't have permission config
        DataPermissionConfig permissionConfig = action.getDataPermissionConfig();
        if (permissionConfig == null || !permissionConfig.isEnforced()) {
            logger.debug("PermissionInterceptor#injectDataPermission - permission not enforced for actionId={}",
                    action.getActionId());
            return params;
        }

        // Skip injection if missing required context
        if (platformUserId == null || platformUserId.isEmpty() || systemId == null || systemId.isEmpty()) {
            logger.warn("PermissionInterceptor#injectDataPermission - missing context for actionId={}, returning original params",
                    action.getActionId());
            return params;
        }

        logger.info("PermissionInterceptor#injectDataPermission - injecting data permission: userId={}, systemId={}, actionId={}",
                platformUserId, systemId, action.getActionId());

        try {
            // Get user's standardized permission
            StandardPermission permission = permissionService.getPermission(platformUserId, systemId, context);

            // Inject data permission into parameters
            return permissionService.injectDataPermission(permission, action, params);
        } catch (Exception e) {
            logger.error("PermissionInterceptor#injectDataPermission - error injecting data permission, returning original params", e);
            return params;
        }
    }
}
