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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * External system identity.
 * <p>
 * Represents a user's identity in an external system.
 * This is used after resolving the identity mapping from platform user
 * to external system user.
 * <p>
 * Example:
 * <pre>
 * ExternalIdentity identity = ExternalIdentity.builder()
 *     .systemId("oa-system")
 *     .userId("zhang.san@company.com")
 *     .username("张三")
 *     .build();
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ExternalIdentity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * External system ID.
     */
    private String systemId;

    /**
     * User ID in the external system.
     */
    private String userId;

    /**
     * Username in the external system (display name).
     */
    private String username;

    /**
     * Extra information from the external system.
     * <p>
     * Can include: role, department, position, etc.
     */
    private Map<String, Object> extraInfo;

    public ExternalIdentity() {
        this.extraInfo = new HashMap<>();
    }

    /**
     * Create a builder for ExternalIdentity.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get extra info value by key.
     *
     * @param key the key
     * @return the value, or null if not found
     */
    public Object getExtraInfo(String key) {
        return extraInfo != null ? extraInfo.get(key) : null;
    }

    /**
     * Get extra info value by key with default.
     *
     * @param key the key
     * @param defaultValue the default value
     * @return the value, or defaultValue if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtraInfo(String key, T defaultValue) {
        if (extraInfo == null) {
            return defaultValue;
        }
        Object value = extraInfo.get(key);
        return value != null ? (T) value : defaultValue;
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

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Map<String, Object> getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(Map<String, Object> extraInfo) {
        this.extraInfo = extraInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExternalIdentity that = (ExternalIdentity) o;
        return Objects.equals(systemId, that.systemId) &&
                Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(systemId, userId);
    }

    @Override
    public String toString() {
        return "ExternalIdentity{" +
                "systemId='" + systemId + '\'' +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", extraInfo=" + extraInfo +
                '}';
    }

    /**
     * Builder for ExternalIdentity.
     */
    public static class Builder {
        private String systemId;
        private String userId;
        private String username;
        private Map<String, Object> extraInfo = new HashMap<>();

        public Builder systemId(String systemId) {
            this.systemId = systemId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder extraInfo(Map<String, Object> extraInfo) {
            this.extraInfo = extraInfo;
            return this;
        }

        public Builder addExtraInfo(String key, Object value) {
            this.extraInfo.put(key, value);
            return this;
        }

        public ExternalIdentity build() {
            ExternalIdentity identity = new ExternalIdentity();
            identity.setSystemId(systemId);
            identity.setUserId(userId);
            identity.setUsername(username);
            identity.setExtraInfo(extraInfo);
            return identity;
        }
    }
}
