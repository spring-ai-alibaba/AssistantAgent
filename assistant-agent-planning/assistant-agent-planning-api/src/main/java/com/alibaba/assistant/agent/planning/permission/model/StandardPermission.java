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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Standard permission model for the platform.
 * <p>
 * All external system permissions are converted to this standard format
 * through permission adapters. This provides a unified permission model
 * for the platform to handle authorization and data filtering.
 * <p>
 * Contains:
 * <ul>
 *   <li>Functional permissions - which actions the user can perform</li>
 *   <li>Data permissions - what data scope the user can access</li>
 *   <li>Data filters - specific filter conditions for queries</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * StandardPermission permission = new StandardPermission();
 * permission.setUserId("zhang.san@company.com");
 * permission.setSystemId("oa-system");
 * permission.setAllowedActions(Arrays.asList("oa:attendance:query-late"));
 * permission.setDataScope(DataScopeType.DEPARTMENT);
 * permission.addFilter("departmentId", "eq", "tech-001");
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class StandardPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * User ID in the external system.
     */
    private String userId;

    /**
     * System ID this permission belongs to.
     */
    private String systemId;

    /**
     * List of allowed action IDs.
     * <p>
     * Example: ["oa:attendance:query-late", "oa:task:query-progress"]
     */
    private List<String> allowedActions;

    /**
     * Data scope type.
     * <p>
     * Defines the general range of data the user can access.
     */
    private DataScopeType dataScope;

    /**
     * Data filter conditions.
     * <p>
     * Key: filter name (e.g., "departmentId")
     * Value: DataFilter object with operator and value
     */
    private Map<String, DataFilter> filters;

    /**
     * Additional context information.
     * <p>
     * Can store extra permission-related data from external systems.
     */
    private Map<String, Object> context;

    public StandardPermission() {
        this.allowedActions = new ArrayList<>();
        this.filters = new HashMap<>();
        this.context = new HashMap<>();
    }

    /**
     * Check if the user has permission to perform the specified action.
     *
     * @param actionId the action ID to check
     * @return true if allowed, false otherwise
     */
    public boolean hasPermission(String actionId) {
        return allowedActions != null && allowedActions.contains(actionId);
    }

    /**
     * Add a filter condition.
     *
     * @param field the field name
     * @param operator the operator (eq, ne, in, etc.)
     * @param value the filter value
     */
    public void addFilter(String field, String operator, Object value) {
        if (filters == null) {
            filters = new HashMap<>();
        }
        filters.put(field, new DataFilter(field, operator, value));
    }

    /**
     * Add a filter condition using DataFilter object.
     *
     * @param filter the DataFilter object
     */
    public void addFilter(DataFilter filter) {
        if (filters == null) {
            filters = new HashMap<>();
        }
        filters.put(filter.getField(), filter);
    }

    /**
     * Get filter by field name.
     *
     * @param field the field name
     * @return the DataFilter, or null if not found
     */
    public DataFilter getFilter(String field) {
        return filters != null ? filters.get(field) : null;
    }

    /**
     * Check if data scope allows access to all data.
     *
     * @return true if organization-wide access
     */
    public boolean hasOrganizationScope() {
        return DataScopeType.ORGANIZATION == dataScope;
    }

    /**
     * Check if data scope is limited to self.
     *
     * @return true if self-only access
     */
    public boolean hasSelfScope() {
        return DataScopeType.SELF == dataScope;
    }

    /**
     * Add context value.
     *
     * @param key the context key
     * @param value the context value
     */
    public void addContext(String key, Object value) {
        if (context == null) {
            context = new HashMap<>();
        }
        context.put(key, value);
    }

    /**
     * Get context value.
     *
     * @param key the context key
     * @return the context value, or null if not found
     */
    public Object getContext(String key) {
        return context != null ? context.get(key) : null;
    }

    // Getters and Setters

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public List<String> getAllowedActions() {
        return allowedActions;
    }

    public void setAllowedActions(List<String> allowedActions) {
        this.allowedActions = allowedActions;
    }

    public DataScopeType getDataScope() {
        return dataScope;
    }

    public void setDataScope(DataScopeType dataScope) {
        this.dataScope = dataScope;
    }

    public Map<String, DataFilter> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, DataFilter> filters) {
        this.filters = filters;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StandardPermission that = (StandardPermission) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(systemId, that.systemId) &&
                Objects.equals(allowedActions, that.allowedActions) &&
                dataScope == that.dataScope &&
                Objects.equals(filters, that.filters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, systemId, allowedActions, dataScope, filters);
    }

    @Override
    public String toString() {
        return "StandardPermission{" +
                "userId='" + userId + '\'' +
                ", systemId='" + systemId + '\'' +
                ", allowedActions=" + allowedActions +
                ", dataScope=" + dataScope +
                ", filters=" + filters +
                '}';
    }
}
