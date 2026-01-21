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

/**
 * Data scope type enumeration.
 * <p>
 * Defines the range of data a user can access:
 * <ul>
 *   <li>SELF - Only own data</li>
 *   <li>DEPARTMENT - Data within own department</li>
 *   <li>DEPARTMENT_TREE - Data within own department and sub-departments</li>
 *   <li>ORGANIZATION - All data within the organization</li>
 *   <li>CUSTOM - Custom data scope defined by filters</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public enum DataScopeType {

    /**
     * Only own data.
     * User can only see data they created or owned.
     */
    SELF("self", "仅自己"),

    /**
     * Department level data.
     * User can see data within their department.
     */
    DEPARTMENT("department", "本部门"),

    /**
     * Department tree data.
     * User can see data within their department and all sub-departments.
     */
    DEPARTMENT_TREE("department_tree", "本部门及下级"),

    /**
     * Organization level data.
     * User can see all data within the organization.
     */
    ORGANIZATION("organization", "全组织"),

    /**
     * Custom data scope.
     * Data scope is defined by custom filters.
     */
    CUSTOM("custom", "自定义");

    private final String code;
    private final String description;

    DataScopeType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get DataScopeType by code.
     *
     * @param code the code
     * @return the DataScopeType, or null if not found
     */
    public static DataScopeType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DataScopeType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
