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
package com.alibaba.assistant.agent.planning.spi;

import com.alibaba.assistant.agent.planning.model.PermissionCheckResult;
import com.alibaba.assistant.agent.planning.model.DataScope;

import java.util.List;

/**
 * 权限检查提供者 SPI
 *
 * <p>此接口定义了动作执行前的权限检查机制。实现类可以连接到不同的权限系统：
 * <ul>
 * <li>基于角色的访问控制（RBAC）</li>
 * <li>基于属性的访问控制（ABAC）</li>
 * <li>自定义权限系统</li>
 * </ul>
 *
 * <h3>权限检查流程</h3>
 * <ol>
 * <li>检查用户是否有执行 Action 的权限（功能权限）</li>
 * <li>获取用户的数据权限范围（数据权限）</li>
 * <li>在执行 Action 时应用数据权限过滤</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Component
 * public class MyRbacPermissionProvider implements PermissionProvider {
 *     &#64;Autowired
 *     private RoleService roleService;
 *
 *     &#64;Override
 *     public PermissionCheckResult checkPermission(
 *             Long tenantId, Long systemId, Long userId,
 *             String actionId, List&lt;String&gt; requiredRoles) {
 *
 *         // 获取用户角色
 *         List&lt;String&gt; userRoles = roleService.getUserRoles(userId);
 *
 *         // 检查是否有所需角色
 *         boolean hasPermission = userRoles.stream()
 *             .anyMatch(requiredRoles::contains);
 *
 *         if (hasPermission) {
 *             return PermissionCheckResult.granted();
 *         } else {
 *             return PermissionCheckResult.denied("缺少必需的角色");
 *         }
 *     }
 *
 *     &#64;Override
 *     public DataScope getDataScope(Long userId, String resourceType) {
 *         // 获取用户的数据权限范围
 *         return roleService.getDataScope(userId, resourceType);
 *     }
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface PermissionProvider {

    /**
     * 检查用户是否有执行指定 Action 的权限
     *
     * <p>此方法应该在 Action 执行之前被调用，用于验证用户是否有权限执行该操作。
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @param userId 用户 ID
     * @param actionId 动作 ID
     * @param requiredRoles 执行该动作所需的角色列表
     * @return 权限检查结果
     * @throws IllegalArgumentException 如果任一参数为 null
     */
    PermissionCheckResult checkPermission(
            Long tenantId,
            Long systemId,
            Long userId,
            String actionId,
            List<String> requiredRoles);

    /**
     * 获取用户对指定资源的数据权限范围
     *
     * <p>数据权限用于控制用户可以访问哪些数据行。常见的范围包括：
     * <ul>
     * <li>ALL - 全部数据</li>
     * <li>ORG - 本组织及下级组织的数据</li>
     * <li>DEPT - 本部门的数据</li>
     * <li>SELF - 仅自己的数据</li>
     * <li>CUSTOM - 自定义范围</li>
     * </ul>
     *
     * @param userId 用户 ID
     * @param resourceType 资源类型，例如 "product", "order", "customer"
     * @return 数据权限范围
     * @throws IllegalArgumentException 如果任一参数为 null 或空
     */
    DataScope getDataScope(Long userId, String resourceType);

    /**
     * 检查用户是否有指定资源的访问权限
     *
     * <p>此方法用于细粒度的资源访问控制。
     *
     * @param userId 用户 ID
     * @param resourceType 资源类型
     * @param resourceId 资源 ID
     * @param permission 权限类型，例如 "read", "write", "delete"
     * @return 是否有权限
     * @throws IllegalArgumentException 如果任一参数为 null 或空
     */
    default boolean hasResourcePermission(
            Long userId,
            String resourceType,
            String resourceId,
            String permission) {
        // 默认实现：检查数据权限范围
        DataScope dataScope = getDataScope(userId, resourceType);
        return dataScope != DataScope.NONE;
    }

    /**
     * 获取权限提供者的类型名称
     *
     * <p>用于日志记录和监控。
     *
     * @return 类型名称，例如 "RBAC", "ABAC", "Custom"
     */
    default String getProviderType() {
        return "Unknown";
    }
}
