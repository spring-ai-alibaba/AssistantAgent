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

import com.alibaba.assistant.agent.planning.permission.model.AccessibleSystem;
import com.alibaba.assistant.agent.planning.permission.model.ExternalIdentity;
import com.alibaba.assistant.agent.planning.permission.model.UserIdentityMapping;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * User identity mapping service interface.
 * <p>
 * Manages the mapping between platform users and external system users.
 * This service is responsible for:
 * <ul>
 *   <li>Querying user's external identity in specific systems</li>
 *   <li>Listing all systems a user has access to</li>
 *   <li>Binding/unbinding external system identities</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * // Get user's identity in OA system
 * Optional&lt;ExternalIdentity&gt; identity = identityMappingService
 *     .getExternalIdentity("U001", "oa-system");
 *
 * if (identity.isPresent()) {
 *     String oaUserId = identity.get().getUserId();
 *     // Use oaUserId to call OA system APIs
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface IdentityMappingService {

    /**
     * Get user's external identity in a specific system.
     *
     * @param platformUserId the platform user ID
     * @param systemId the external system ID
     * @return the external identity if bound, empty otherwise
     */
    Optional<ExternalIdentity> getExternalIdentity(String platformUserId, String systemId);

    /**
     * Get all systems a user can access.
     * <p>
     * Returns both bound and unbound systems, with binding status indicated.
     *
     * @param platformUserId the platform user ID
     * @return list of accessible systems
     */
    List<AccessibleSystem> getAccessibleSystems(String platformUserId);

    /**
     * Get all identity mappings for a user.
     *
     * @param platformUserId the platform user ID
     * @return list of identity mappings
     */
    List<UserIdentityMapping> getMappingsByUser(String platformUserId);

    /**
     * Bind an external system identity to a platform user.
     *
     * @param platformUserId the platform user ID
     * @param systemId the external system ID
     * @param externalUserId the external user ID
     * @param externalUsername the external username (display name)
     * @param extraInfo additional information (role, department, etc.)
     */
    void bindIdentity(String platformUserId, String systemId,
                      String externalUserId, String externalUsername,
                      Map<String, Object> extraInfo);

    /**
     * Bind an external system identity to a platform user (simple version).
     *
     * @param platformUserId the platform user ID
     * @param systemId the external system ID
     * @param externalUserId the external user ID
     * @param extraInfo additional information
     */
    default void bindIdentity(String platformUserId, String systemId,
                              String externalUserId, Map<String, Object> extraInfo) {
        bindIdentity(platformUserId, systemId, externalUserId, null, extraInfo);
    }

    /**
     * Unbind an external system identity from a platform user.
     *
     * @param platformUserId the platform user ID
     * @param systemId the external system ID
     */
    void unbindIdentity(String platformUserId, String systemId);

    /**
     * Check if a user has bound to a specific system.
     *
     * @param platformUserId the platform user ID
     * @param systemId the external system ID
     * @return true if bound, false otherwise
     */
    boolean isBound(String platformUserId, String systemId);

    /**
     * Update extra info for an existing mapping.
     *
     * @param platformUserId the platform user ID
     * @param systemId the external system ID
     * @param extraInfo the new extra info
     */
    void updateExtraInfo(String platformUserId, String systemId, Map<String, Object> extraInfo);

    /**
     * Find platform user ID by external identity.
     *
     * @param systemId the external system ID
     * @param externalUserId the external user ID
     * @return the platform user ID if found, empty otherwise
     */
    Optional<String> findPlatformUserByExternalId(String systemId, String externalUserId);
}
