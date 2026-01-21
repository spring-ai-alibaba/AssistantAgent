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
 * External system configuration model.
 * <p>
 * Stores configuration for external systems that can be integrated
 * with the Assistant Agent platform.
 * <p>
 * Example:
 * <pre>
 * ExternalSystemConfig config = new ExternalSystemConfig();
 * config.setSystemId("oa-system");
 * config.setSystemName("OA办公系统");
 * config.setSystemType("OA");
 * config.setApiBaseUrl("http://localhost:8081/api");
 * config.setAuthType("API_KEY");
 * config.setAdapterClass("com.alibaba.assistant.agent.planning.permission.adapter.OaPermissionAdapter");
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ExternalSystemConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * System type constants.
     */
    public static final String TYPE_OA = "OA";
    public static final String TYPE_GOV = "GOV";
    public static final String TYPE_ERP = "ERP";
    public static final String TYPE_CRM = "CRM";

    /**
     * Auth type constants.
     */
    public static final String AUTH_API_KEY = "API_KEY";
    public static final String AUTH_OAUTH = "OAUTH";
    public static final String AUTH_BASIC = "BASIC";

    /**
     * System ID (unique identifier).
     */
    private String systemId;

    /**
     * System display name.
     */
    private String systemName;

    /**
     * System type: OA, GOV, ERP, CRM, etc.
     */
    private String systemType;

    /**
     * API base URL for the external system.
     */
    private String apiBaseUrl;

    /**
     * Authentication type: API_KEY, OAUTH, BASIC.
     */
    private String authType;

    /**
     * Authentication configuration.
     * <p>
     * For API_KEY: { "apiKey": "xxx", "headerName": "X-API-Key" }
     * For OAUTH: { "clientId": "xxx", "clientSecret": "xxx", "tokenUrl": "xxx" }
     * For BASIC: { "username": "xxx", "password": "xxx" }
     */
    private Map<String, Object> authConfig;

    /**
     * Permission adapter class name.
     */
    private String adapterClass;

    /**
     * Whether this system is enabled.
     */
    private boolean enabled;

    /**
     * System icon URL (for UI display).
     */
    private String iconUrl;

    /**
     * System description.
     */
    private String description;

    /**
     * Creation time.
     */
    private LocalDateTime createdAt;

    /**
     * Last update time.
     */
    private LocalDateTime updatedAt;

    public ExternalSystemConfig() {
        this.authConfig = new HashMap<>();
        this.enabled = true;
    }

    /**
     * Get auth config value by key.
     *
     * @param key the key
     * @return the value, or null if not found
     */
    public Object getAuthConfigValue(String key) {
        return authConfig != null ? authConfig.get(key) : null;
    }

    /**
     * Get auth config value by key with default.
     *
     * @param key the key
     * @param defaultValue the default value
     * @return the value, or defaultValue if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getAuthConfigValue(String key, T defaultValue) {
        if (authConfig == null) {
            return defaultValue;
        }
        Object value = authConfig.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Add auth config.
     *
     * @param key the key
     * @param value the value
     */
    public void addAuthConfig(String key, Object value) {
        if (authConfig == null) {
            authConfig = new HashMap<>();
        }
        authConfig.put(key, value);
    }

    /**
     * Convert to AccessibleSystem (for display without binding info).
     *
     * @return the AccessibleSystem
     */
    public AccessibleSystem toAccessibleSystem() {
        return AccessibleSystem.builder()
                .systemId(systemId)
                .systemName(systemName)
                .systemType(systemType)
                .iconUrl(iconUrl)
                .description(description)
                .bound(false)
                .build();
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

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public Map<String, Object> getAuthConfig() {
        return authConfig;
    }

    public void setAuthConfig(Map<String, Object> authConfig) {
        this.authConfig = authConfig;
    }

    public String getAdapterClass() {
        return adapterClass;
    }

    public void setAdapterClass(String adapterClass) {
        this.adapterClass = adapterClass;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExternalSystemConfig that = (ExternalSystemConfig) o;
        return Objects.equals(systemId, that.systemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(systemId);
    }

    @Override
    public String toString() {
        return "ExternalSystemConfig{" +
                "systemId='" + systemId + '\'' +
                ", systemName='" + systemName + '\'' +
                ", systemType='" + systemType + '\'' +
                ", apiBaseUrl='" + apiBaseUrl + '\'' +
                ", authType='" + authType + '\'' +
                ", adapterClass='" + adapterClass + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
