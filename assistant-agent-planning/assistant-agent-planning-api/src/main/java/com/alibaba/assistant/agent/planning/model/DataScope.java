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
package com.alibaba.assistant.agent.planning.model;

/**
 * 数据权限范围
 *
 * <p>定义用户可以访问的数据行范围，用于实现行级数据安全。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public enum DataScope {

    /**
     * 全部数据
     *
     * <p>用户可以访问所有数据行，通常仅用于系统管理员。
     */
    ALL("全部数据"),

    /**
     * 本组织及下级组织的数据
     *
     * <p>用户可以访问所属组织及其所有下级组织的数据。
     */
    ORG("本组织及下级组织"),

    /**
     * 本部门的数据
     *
     * <p>用户仅可以访问所属部门的数据。
     */
    DEPT("本部门"),

    /**
     * 仅自己的数据
     *
     * <p>用户仅可以访问自己创建的数据。
     */
    SELF("仅自己"),

    /**
     * 自定义范围
     *
     * <p>用户的数据权限范围由自定义规则定义，可能涉及复杂的过滤条件。
     */
    CUSTOM("自定义"),

    /**
     * 无权限
     *
     * <p>用户没有数据访问权限。
     */
    NONE("无权限");

    private final String description;

    DataScope(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
