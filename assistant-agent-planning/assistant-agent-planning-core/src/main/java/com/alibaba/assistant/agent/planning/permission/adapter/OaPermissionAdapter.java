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
package com.alibaba.assistant.agent.planning.permission.adapter;

import com.alibaba.assistant.agent.planning.permission.model.DataScopeType;
import com.alibaba.assistant.agent.planning.permission.model.StandardPermission;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * OA System Permission Adapter.
 * <p>
 * Converts OA system's role-based permissions to standard permission format.
 * <p>
 * Supported roles:
 * <ul>
 *   <li>employee - Basic employee, can only operate on own data</li>
 *   <li>manager - Department manager, can view department data</li>
 *   <li>director - Company director, can view all data</li>
 * </ul>
 * <p>
 * Permission matrix:
 * <pre>
 * +----------+------------------------+------------------+
 * | Role     | Allowed Actions        | Data Scope       |
 * +----------+------------------------+------------------+
 * | employee | apply-leave,           | SELF             |
 * |          | update-status          |                  |
 * +----------+------------------------+------------------+
 * | manager  | apply-leave,           | SELF/DEPARTMENT  |
 * |          | query-late,            |                  |
 * |          | update-status,         |                  |
 * |          | query-progress         |                  |
 * +----------+------------------------+------------------+
 * | director | query-late,            | ORGANIZATION     |
 * |          | query-statistics,      |                  |
 * |          | query-progress,        |                  |
 * |          | query-statistics       |                  |
 * +----------+------------------------+------------------+
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class OaPermissionAdapter implements PermissionAdapter {

    private static final Logger logger = LoggerFactory.getLogger(OaPermissionAdapter.class);

    public static final String SYSTEM_ID = "oa-system";

    // Role constants
    public static final String ROLE_EMPLOYEE = "employee";
    public static final String ROLE_MANAGER = "manager";
    public static final String ROLE_DIRECTOR = "director";

    @Override
    public String getSystemId() {
        return SYSTEM_ID;
    }

    @Override
    public StandardPermission adapt(Map<String, Object> rawContext) {
        if (rawContext == null) {
            logger.warn("OaPermissionAdapter#adapt - rawContext is null");
            return createEmptyPermission();
        }

        String userId = getString(rawContext, "userId");
        String role = getString(rawContext, "role");
        String deptId = getString(rawContext, "deptId");
        String deptName = getString(rawContext, "deptName");

        logger.debug("OaPermissionAdapter#adapt - userId={}, role={}, deptId={}", userId, role, deptId);

        StandardPermission permission = new StandardPermission();
        permission.setUserId(userId);
        permission.setSystemId(SYSTEM_ID);

        // Store context for later use
        if (deptName != null) {
            permission.addContext("deptName", deptName);
        }

        if (role == null) {
            logger.warn("OaPermissionAdapter#adapt - role is null, returning empty permission");
            return createEmptyPermission();
        }

        switch (role.toLowerCase()) {
            case ROLE_EMPLOYEE:
                adaptEmployeePermission(permission, userId);
                break;

            case ROLE_MANAGER:
                adaptManagerPermission(permission, userId, deptId);
                break;

            case ROLE_DIRECTOR:
                adaptDirectorPermission(permission);
                break;

            default:
                logger.warn("OaPermissionAdapter#adapt - unknown role: {}", role);
                permission.setAllowedActions(Collections.emptyList());
                permission.setDataScope(DataScopeType.SELF);
        }

        logger.info("OaPermissionAdapter#adapt - adapted permission: allowedActions={}, dataScope={}",
                permission.getAllowedActions(), permission.getDataScope());

        return permission;
    }

    /**
     * Adapt employee permissions.
     * <p>
     * Employees can only operate on their own data.
     */
    private void adaptEmployeePermission(StandardPermission permission, String userId) {
        permission.setAllowedActions(Arrays.asList(
                "oa:attendance:apply-leave",
                "oa:task:update-status"
        ));
        permission.setDataScope(DataScopeType.SELF);
        permission.addFilter("userId", "eq", userId);
    }

    /**
     * Adapt manager permissions.
     * <p>
     * Managers can view their department's data.
     */
    private void adaptManagerPermission(StandardPermission permission, String userId, String deptId) {
        permission.setAllowedActions(Arrays.asList(
                "oa:attendance:apply-leave",
                "oa:attendance:query-late",
                "oa:task:update-status",
                "oa:task:query-progress"
        ));
        permission.setDataScope(DataScopeType.DEPARTMENT);
        permission.addFilter("departmentId", "eq", deptId);
        // For personal operations, also add userId filter
        permission.addContext("selfUserId", userId);
    }

    /**
     * Adapt director permissions.
     * <p>
     * Directors can view all organization data.
     */
    private void adaptDirectorPermission(StandardPermission permission) {
        permission.setAllowedActions(Arrays.asList(
                "oa:attendance:query-late",
                "oa:attendance:query-statistics",
                "oa:task:query-progress",
                "oa:task:query-statistics"
        ));
        permission.setDataScope(DataScopeType.ORGANIZATION);
        // No filters for organization-wide access
    }

    /**
     * Create an empty permission (no access).
     */
    private StandardPermission createEmptyPermission() {
        StandardPermission permission = new StandardPermission();
        permission.setSystemId(SYSTEM_ID);
        permission.setAllowedActions(Collections.emptyList());
        permission.setDataScope(DataScopeType.SELF);
        return permission;
    }

    /**
     * Safely get string from map.
     */
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    @Override
    public String getName() {
        return "OA办公系统权限适配器";
    }
}
