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
import java.util.Objects;

/**
 * Represents an external system that a user can access.
 * <p>
 * Used to display the list of systems a platform user has bound to.
 * <p>
 * Example:
 * <pre>
 * AccessibleSystem system = AccessibleSystem.builder()
 *     .systemId("oa-system")
 *     .systemName("OA办公系统")
 *     .externalUserId("zhang.san@company.com")
 *     .externalUsername("张三")
 *     .bound(true)
 *     .build();
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class AccessibleSystem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * System ID.
     */
    private String systemId;

    /**
     * System display name.
     */
    private String systemName;

    /**
     * System type (OA, GOV, ERP, CRM, etc.).
     */
    private String systemType;

    /**
     * User ID in the external system (if bound).
     */
    private String externalUserId;

    /**
     * Username in the external system (if bound).
     */
    private String externalUsername;

    /**
     * Whether the platform user has bound to this system.
     */
    private boolean bound;

    /**
     * System icon URL (for UI display).
     */
    private String iconUrl;

    /**
     * System description.
     */
    private String description;

    public AccessibleSystem() {
    }

    /**
     * Create a builder for AccessibleSystem.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
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

    public boolean isBound() {
        return bound;
    }

    public void setBound(boolean bound) {
        this.bound = bound;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessibleSystem that = (AccessibleSystem) o;
        return Objects.equals(systemId, that.systemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(systemId);
    }

    @Override
    public String toString() {
        return "AccessibleSystem{" +
                "systemId='" + systemId + '\'' +
                ", systemName='" + systemName + '\'' +
                ", systemType='" + systemType + '\'' +
                ", externalUserId='" + externalUserId + '\'' +
                ", externalUsername='" + externalUsername + '\'' +
                ", bound=" + bound +
                '}';
    }

    /**
     * Builder for AccessibleSystem.
     */
    public static class Builder {
        private String systemId;
        private String systemName;
        private String systemType;
        private String externalUserId;
        private String externalUsername;
        private boolean bound;
        private String iconUrl;
        private String description;

        public Builder systemId(String systemId) {
            this.systemId = systemId;
            return this;
        }

        public Builder systemName(String systemName) {
            this.systemName = systemName;
            return this;
        }

        public Builder systemType(String systemType) {
            this.systemType = systemType;
            return this;
        }

        public Builder externalUserId(String externalUserId) {
            this.externalUserId = externalUserId;
            return this;
        }

        public Builder externalUsername(String externalUsername) {
            this.externalUsername = externalUsername;
            return this;
        }

        public Builder bound(boolean bound) {
            this.bound = bound;
            return this;
        }

        public Builder iconUrl(String iconUrl) {
            this.iconUrl = iconUrl;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public AccessibleSystem build() {
            AccessibleSystem system = new AccessibleSystem();
            system.setSystemId(systemId);
            system.setSystemName(systemName);
            system.setSystemType(systemType);
            system.setExternalUserId(externalUserId);
            system.setExternalUsername(externalUsername);
            system.setBound(bound);
            system.setIconUrl(iconUrl);
            system.setDescription(description);
            return system;
        }
    }
}
