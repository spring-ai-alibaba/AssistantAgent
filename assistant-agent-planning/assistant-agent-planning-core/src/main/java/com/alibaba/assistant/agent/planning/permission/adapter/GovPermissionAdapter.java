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
 * Government Service Platform Permission Adapter.
 * <p>
 * Converts government platform's user type-based permissions to standard permission format.
 * <p>
 * Supported user types:
 * <ul>
 *   <li>citizen - Public citizens, can query and make appointments</li>
 *   <li>staff - Government staff, can manage appointments and content</li>
 *   <li>leader - Department leaders, can view statistics</li>
 * </ul>
 * <p>
 * Permission matrix:
 * <pre>
 * +----------+------------------------+------------------+
 * | UserType | Allowed Actions        | Data Scope       |
 * +----------+------------------------+------------------+
 * | citizen  | query-process,         | SELF/PUBLIC      |
 * |          | appointment:create     |                  |
 * +----------+------------------------+------------------+
 * | staff    | appointment:set-limit, | DEPARTMENT       |
 * |          | content:publish        |                  |
 * +----------+------------------------+------------------+
 * | leader   | stats:query-appointment| DEPARTMENT_TREE  |
 * |          | stats:query-satisfaction                  |
 * +----------+------------------------+------------------+
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class GovPermissionAdapter implements PermissionAdapter {

    private static final Logger logger = LoggerFactory.getLogger(GovPermissionAdapter.class);

    public static final String SYSTEM_ID = "gov-platform";

    // User type constants
    public static final String USER_TYPE_CITIZEN = "citizen";
    public static final String USER_TYPE_STAFF = "staff";
    public static final String USER_TYPE_LEADER = "leader";

    @Override
    public String getSystemId() {
        return SYSTEM_ID;
    }

    @Override
    public StandardPermission adapt(Map<String, Object> rawContext) {
        if (rawContext == null) {
            logger.warn("GovPermissionAdapter#adapt - rawContext is null");
            return createEmptyPermission();
        }

        String userId = getString(rawContext, "userId");
        String userType = getString(rawContext, "userType");
        String bureauId = getString(rawContext, "bureauId");
        String bureauName = getString(rawContext, "bureauName");
        Integer level = getInteger(rawContext, "level");

        logger.debug("GovPermissionAdapter#adapt - userId={}, userType={}, bureauId={}", userId, userType, bureauId);

        StandardPermission permission = new StandardPermission();
        permission.setUserId(userId);
        permission.setSystemId(SYSTEM_ID);

        // Store context for later use
        if (bureauName != null) {
            permission.addContext("bureauName", bureauName);
        }

        if (userType == null) {
            logger.warn("GovPermissionAdapter#adapt - userType is null, returning empty permission");
            return createEmptyPermission();
        }

        switch (userType.toLowerCase()) {
            case USER_TYPE_CITIZEN:
                adaptCitizenPermission(permission, userId);
                break;

            case USER_TYPE_STAFF:
                adaptStaffPermission(permission, bureauId, level);
                break;

            case USER_TYPE_LEADER:
                adaptLeaderPermission(permission, bureauId);
                break;

            default:
                logger.warn("GovPermissionAdapter#adapt - unknown userType: {}", userType);
                permission.setAllowedActions(Collections.emptyList());
                permission.setDataScope(DataScopeType.SELF);
        }

        logger.info("GovPermissionAdapter#adapt - adapted permission: allowedActions={}, dataScope={}",
                permission.getAllowedActions(), permission.getDataScope());

        return permission;
    }

    /**
     * Adapt citizen permissions.
     * <p>
     * Citizens can query public information and manage their own appointments.
     */
    private void adaptCitizenPermission(StandardPermission permission, String citizenId) {
        permission.setAllowedActions(Arrays.asList(
                "gov:service:query-process",
                "gov:appointment:create"
        ));
        permission.setDataScope(DataScopeType.SELF);
        permission.addFilter("citizenId", "eq", citizenId);
    }

    /**
     * Adapt staff permissions.
     * <p>
     * Staff can manage appointments and content within their bureau.
     */
    private void adaptStaffPermission(StandardPermission permission, String bureauId, Integer level) {
        // Level 2+ staff can manage appointments and content
        if (level != null && level >= 2) {
            permission.setAllowedActions(Arrays.asList(
                    "gov:appointment:set-limit",
                    "gov:content:publish-article"
            ));
        } else {
            // Lower level staff has limited permissions
            permission.setAllowedActions(Arrays.asList(
                    "gov:appointment:set-limit"
            ));
        }
        permission.setDataScope(DataScopeType.DEPARTMENT);
        permission.addFilter("bureauId", "eq", bureauId);
    }

    /**
     * Adapt leader permissions.
     * <p>
     * Leaders can view statistics within their department tree.
     */
    private void adaptLeaderPermission(StandardPermission permission, String bureauId) {
        permission.setAllowedActions(Arrays.asList(
                "gov:stats:query-appointment",
                "gov:stats:query-satisfaction"
        ));
        permission.setDataScope(DataScopeType.DEPARTMENT_TREE);
        permission.addFilter("bureauId", "eq", bureauId);

        // Mark satisfaction query as potentially unavailable
        permission.addContext("satisfactionAvailable", false);
        permission.addContext("satisfactionMessage", "满意度查询功能尚未接入");
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

    /**
     * Safely get integer from map.
     */
    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getName() {
        return "政务服务平台权限适配器";
    }
}
