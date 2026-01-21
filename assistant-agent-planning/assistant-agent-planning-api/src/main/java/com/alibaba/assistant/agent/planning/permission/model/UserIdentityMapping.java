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
package com.alibaba.assistant.agent.planning.permission.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * User identity mapping model.
 * <p>
 * Maps a platform user to an external system user.
 * This is the core model for multi-system integration,
 * allowing one platform user to have identities in multiple external systems.
 * <p>
 * Example:
 * <pre>
 * Platform User "张三" (U001) maps to:
 *   - OA System: zhang.san@company.com
 *   - Gov Platform: 320102199001011234
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class UserIdentityMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Bind type constants.
     */
    public static final String BIND_TYPE_MANUAL = "MANUAL";
    public static final String BIND_TYPE_AUTO = "AUTO";
    public static final String BIND_TYPE_OAUTH = "OAUTH";

    /**
     * Mapping ID.
     */
    private String id;

    /**
     * Platform user ID.
     */
    private String platformUserId;

    /**
     * External system ID.
     */
    private String systemId;

    /**
     * User ID in the external system.
     */
    private String externalUserId;

    /**
     * Username in the external system (for display).
     */
    private String externalUsername;

    /**
     * Extra information from the external system.
     * <p>
     * Can include: role, department, position, etc.
     */
    private Map<String, Object> extraInfo;

    /**
     * How this mapping was created.
     * <p>
     * Values: MANUAL, AUTO, OAUTH
     */
    private String bindType;

    /**
     * When this mapping was created.
     */
    private LocalDateTime bindTime;

    public UserIdentityMapping() {
        this.extraInfo = new HashMap<>();
        this.bindTime = LocalDateTime.now();
    }

    /**
     * Create a new mapping with required fields.
     *
     * @param platformUserId the platform user ID
     * @param systemId the external system ID
     * @param externalUserId the external user ID
     * @return a new UserIdentityMapping instance
     */
    public static UserIdentityMapping create(String platformUserId, String systemId, String externalUserId) {
        UserIdentityMapping mapping = new UserIdentityMapping();
        mapping.setPlatformUserId(platformUserId);
        mapping.setSystemId(systemId);
        mapping.setExternalUserId(externalUserId);
        mapping.setBindType(BIND_TYPE_MANUAL);
        return mapping;
    }

    /**
     * Convert this mapping to an ExternalIdentity.
     *
     * @return the ExternalIdentity
     */
    public ExternalIdentity toExternalIdentity() {
        return ExternalIdentity.builder()
                .systemId(systemId)
                .userId(externalUserId)
                .username(externalUsername)
                .extraInfo(extraInfo)
                .build();
    }

    /**
     * Get extra info value by key.
     *
     * @param key the key
     * @return the value, or null if not found
     */
    public Object getExtraInfoValue(String key) {
        return extraInfo != null ? extraInfo.get(key) : null;
    }

    /**
     * Add extra info.
     *
     * @param key the key
     * @param value the value
     */
    public void addExtraInfo(String key, Object value) {
        if (extraInfo == null) {
            extraInfo = new HashMap<>();
        }
        extraInfo.put(key, value);
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlatformUserId() {
        return platformUserId;
    }

    public void setPlatformUserId(String platformUserId) {
        this.platformUserId = platformUserId;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getExternalUserId() {
        return externalUserId;
    }

    public void setExternalUserId(String externalUserId) {
        this.externalUserId = externalUserId;
    }

    public String getExternalUsername() {
        return externalUsername;
    }

    public void setExternalUsername(String externalUsername) {
        this.externalUsername = externalUsername;
    }

    public Map<String, Object> getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(Map<String, Object> extraInfo) {
        this.extraInfo = extraInfo;
    }

    public String getBindType() {
        return bindType;
    }

    public void setBindType(String bindType) {
        this.bindType = bindType;
    }

    public LocalDateTime getBindTime() {
        return bindTime;
    }

    public void setBindTime(LocalDateTime bindTime) {
        this.bindTime = bindTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserIdentityMapping that = (UserIdentityMapping) o;
        return Objects.equals(platformUserId, that.platformUserId) &&
                Objects.equals(systemId, that.systemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(platformUserId, systemId);
    }

    @Override
    public String toString() {
        return "UserIdentityMapping{" +
                "id='" + id + '\'' +
                ", platformUserId='" + platformUserId + '\'' +
                ", systemId='" + systemId + '\'' +
                ", externalUserId='" + externalUserId + '\'' +
                ", externalUsername='" + externalUsername + '\'' +
                ", bindType='" + bindType + '\'' +
                ", bindTime=" + bindTime +
                '}';
    }
}
