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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据权限配置
 *
 * <p>定义了执行 Action 时需要应用的数据权限规则。
 *
 * <h3>使用场景</h3>
 * <ul>
 * <li>查询类 Action：根据用户的部门过滤数据</li>
 * <li>统计类 Action：仅统计用户有权限的数据</li>
 * <li>导出类 Action：导出用户有权限的数据</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * DataPermissionConfig config = DataPermissionConfig.builder()
 *     .enabled(true)
 *     .resourceType("product")
 *     .scopeField("dept_id")
 *     .build();
 *
 * // 在执行 Action 时应用数据权限
 * if (config.isEnabled()) {
 *     DataScope scope = permissionProvider.getDataScope(userId, config.getResourceType());
 *     if (scope == DataScope.DEPT) {
 *         // 添加部门过滤条件
 *         query.addFilter("dept_id", user.getDeptId());
 *     }
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataPermissionConfig {

    /**
     * 是否启用数据权限
     *
     * <p>如果为 false，则不应用任何数据权限过滤。
     */
    @Builder.Default
    private boolean enabled = false;

    /**
     * 资源类型
     *
     * <p>用于标识需要应用数据权限的资源类型，例如 "product", "order", "customer"。
     */
    private String resourceType;

    /**
     * 权限范围字段名
     *
     * <p>数据库表中用于存储数据权限范围的字段名，例如 "dept_id", "org_id", "creator_id"。
     */
    private String scopeField;

    /**
     * 是否支持自定义范围
     *
     * <p>如果为 true，表示用户可以自定义数据权限范围。
     */
    @Builder.Default
    private boolean supportCustom = false;

    /**
     * 默认数据权限范围
     *
     * <p>当用户没有配置数据权限时使用的默认范围。
     */
    @Builder.Default
    private DataScope defaultScope = DataScope.SELF;
}
