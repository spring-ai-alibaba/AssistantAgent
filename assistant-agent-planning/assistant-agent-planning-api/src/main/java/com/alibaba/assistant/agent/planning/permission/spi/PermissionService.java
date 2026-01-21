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
package com.alibaba.assistant.agent.planning.permission.spi;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.permission.model.StandardPermission;

import java.util.Map;

/**
 * Permission service interface.
 * <p>
 * Provides permission checking and data permission injection capabilities.
 * This service is the core of the permission system, responsible for:
 * <ul>
 *   <li>Checking if a user can perform an action (functional permission)</li>
 *   <li>Injecting data filters into action parameters (data permission)</li>
 *   <li>Getting standardized permissions for a user in a system</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * // Check functional permission
 * PermissionCheckResult result = permissionService.checkActionPermission(permission, "oa:attendance:query-late");
 * if (result.isDenied()) {
 *     throw new AccessDeniedException(result.getMessage());
 * }
 *
 * // Inject data permission
 * Map&lt;String, Object&gt; userParams = Map.of("date", "2024-01-20");
 * Map&lt;String, Object&gt; finalParams = permissionService.injectDataPermission(permission, action, userParams);
 * // finalParams now contains: { date: "2024-01-20", departmentId: "tech-001" }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface PermissionService {

    /**
     * Check if the user has permission to perform the specified action.
     *
     * @param permission the user's standard permission
     * @param actionId the action ID to check
     * @return the check result
     */
    PermissionCheckResult checkActionPermission(StandardPermission permission, String actionId);

    /**
     * Inject data permission filters into action parameters.
     * <p>
     * Based on the action's DataPermissionConfig, this method adds
     * appropriate filter conditions from the user's permission to
     * the action parameters.
     *
     * @param permission the user's standard permission
     * @param action the action definition
     * @param userParams the original user parameters
     * @return parameters with injected data filters
     */
    Map<String, Object> injectDataPermission(StandardPermission permission,
                                              ActionDefinition action,
                                              Map<String, Object> userParams);

    /**
     * Get standardized permission for a user in a specific system.
     * <p>
     * This method:
     * 1. Gets the user's external identity in the system
     * 2. Fetches permission context (from cache, external API, or request)
     * 3. Uses the appropriate adapter to convert to StandardPermission
     *
     * @param platformUserId the platform user ID
     * @param systemId the external system ID
     * @param context additional context (may contain permission data)
     * @return the standardized permission
     */
    StandardPermission getPermission(String platformUserId, String systemId, Map<String, Object> context);

    /**
     * Get permission directly from context using adapter.
     * <p>
     * This method skips identity mapping lookup and directly uses
     * the provided context with the appropriate adapter.
     *
     * @param systemId the external system ID
     * @param context the permission context
     * @return the standardized permission
     */
    StandardPermission adaptPermission(String systemId, Map<String, Object> context);

    /**
     * Check if an action requires permission enforcement.
     *
     * @param action the action definition
     * @return true if permission check is required
     */
    default boolean requiresPermissionCheck(ActionDefinition action) {
        return action.getDataPermissionConfig() != null &&
                action.getDataPermissionConfig().isEnforced();
    }
}
