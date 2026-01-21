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

import com.alibaba.assistant.agent.planning.permission.model.StandardPermission;

import java.util.Map;

/**
 * Permission adapter interface.
 * <p>
 * Each external system should implement this interface to convert its
 * native permission model to the platform's standard permission format.
 * <p>
 * The adapter is responsible for:
 * <ul>
 *   <li>Converting external roles to allowed actions</li>
 *   <li>Determining data scope based on user's role/position</li>
 *   <li>Building filter conditions for data permission</li>
 * </ul>
 * <p>
 * Example implementation:
 * <pre>
 * &#64;Component
 * public class OaPermissionAdapter implements PermissionAdapter {
 *
 *     &#64;Override
 *     public String getSystemId() {
 *         return "oa-system";
 *     }
 *
 *     &#64;Override
 *     public StandardPermission adapt(Map&lt;String, Object&gt; rawContext) {
 *         String role = (String) rawContext.get("role");
 *         StandardPermission permission = new StandardPermission();
 *
 *         switch (role) {
 *             case "employee":
 *                 permission.setAllowedActions(Arrays.asList("apply-leave"));
 *                 permission.setDataScope(DataScopeType.SELF);
 *                 break;
 *             case "manager":
 *                 permission.setAllowedActions(Arrays.asList("apply-leave", "query-late"));
 *                 permission.setDataScope(DataScopeType.DEPARTMENT);
 *                 break;
 *         }
 *         return permission;
 *     }
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface PermissionAdapter {

    /**
     * Get the system ID this adapter supports.
     * <p>
     * This should match the systemId in ExternalSystemConfig.
     *
     * @return the system ID (e.g., "oa-system", "gov-platform")
     */
    String getSystemId();

    /**
     * Convert external system's raw permission context to standard permission format.
     * <p>
     * The rawContext typically contains:
     * <ul>
     *   <li>userId - external user ID</li>
     *   <li>role - user's role in the external system</li>
     *   <li>department/bureau - organizational unit</li>
     *   <li>Any other system-specific permission data</li>
     * </ul>
     *
     * @param rawContext raw permission context from external system
     * @return standardized permission object
     */
    StandardPermission adapt(Map<String, Object> rawContext);

    /**
     * Get the adapter priority.
     * <p>
     * Higher priority adapters are tried first when multiple adapters
     * support the same system. Default is 0.
     *
     * @return priority value (higher = more priority)
     */
    default int getOrder() {
        return 0;
    }

    /**
     * Check if this adapter supports the given system ID.
     * <p>
     * Default implementation compares with {@link #getSystemId()}.
     * Override for adapters that support multiple systems.
     *
     * @param systemId the system ID to check
     * @return true if this adapter can handle the system
     */
    default boolean supports(String systemId) {
        return getSystemId() != null && getSystemId().equals(systemId);
    }

    /**
     * Get display name for this adapter.
     * <p>
     * Used for logging and debugging.
     *
     * @return adapter display name
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
