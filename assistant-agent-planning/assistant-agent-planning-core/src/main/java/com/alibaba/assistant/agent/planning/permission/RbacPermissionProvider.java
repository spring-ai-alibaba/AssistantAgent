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
package com.alibaba.assistant.agent.planning.permission;

import com.alibaba.assistant.agent.planning.model.DataScope;
import com.alibaba.assistant.agent.planning.model.PermissionCheckResult;
import com.alibaba.assistant.agent.planning.spi.PermissionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 基于角色的访问控制（RBAC）权限提供者
 *
 * <p>此实现提供基本的 RBAC 功能，通过 SPI 接口集成到用户权限系统。
 *
 * <h3>使用方式</h3>
 * <p>用户可以通过继承此类并重写 {@link #getUserRoles(Long)} 和 {@link #getDataScope(Long, String)}
 * 方法来集成到自己的权限系统。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Component
 * public class MyPermissionProvider extends RbacPermissionProvider {
 *     &#64;Autowired
 *     private RoleService roleService;
 *
 *     &#64;Override
 *     protected List&lt;String&gt; getUserRoles(Long userId) {
 *         return roleService.getRoleCodesByUserId(userId);
 *     }
 *
 *     &#64;Override
 *     protected DataScope getUserDataScope(Long userId, String resourceType) {
 *         return roleService.getDataScope(userId, resourceType);
 *     }
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class RbacPermissionProvider implements PermissionProvider {

    private static final Logger logger = LoggerFactory.getLogger(RbacPermissionProvider.class);

    @Override
    public PermissionCheckResult checkPermission(
            Long tenantId,
            Long systemId,
            Long userId,
            String actionId,
            List<String> requiredRoles) {

        if (userId == null) {
            return PermissionCheckResult.denied("用户 ID 不能为空");
        }

        if (requiredRoles == null || requiredRoles.isEmpty()) {
            // 没有角色要求，默认允许
            logger.debug("RbacPermissionProvider#checkPermission - no role requirement, userId={}, actionId={}",
                    userId, actionId);
            return PermissionCheckResult.granted();
        }

        try {
            // 获取用户角色
            List<String> userRoles = getUserRoles(userId);

            if (userRoles == null || userRoles.isEmpty()) {
                logger.debug("RbacPermissionProvider#checkPermission - user has no roles, userId={}", userId);
                return PermissionCheckResult.denied("用户没有分配角色");
            }

            // 检查是否有所需角色
            boolean hasPermission = userRoles.stream()
                    .anyMatch(requiredRoles::contains);

            if (hasPermission) {
                logger.debug("RbacPermissionProvider#checkPermission - permission granted, userId={}, actionId={}, roles={}",
                        userId, actionId, userRoles);
                return PermissionCheckResult.granted();
            } else {
                logger.debug("RbacPermissionProvider#checkPermission - permission denied, userId={}, actionId={}, requiredRoles={}, userRoles={}",
                        userId, actionId, requiredRoles, userRoles);
                return PermissionCheckResult.denied("缺少必需的角色：" + requiredRoles);
            }
        } catch (Exception e) {
            logger.error("RbacPermissionProvider#checkPermission - error, userId={}, actionId={}",
                    userId, actionId, e);
            return PermissionCheckResult.denied("权限检查失败：" + e.getMessage());
        }
    }

    @Override
    public DataScope getDataScope(Long userId, String resourceType) {
        if (userId == null) {
            return DataScope.NONE;
        }

        try {
            DataScope scope = getUserDataScope(userId, resourceType);
            logger.debug("RbacPermissionProvider#getDataScope - userId={}, resourceType={}, scope={}",
                    userId, resourceType, scope);
            return scope;
        } catch (Exception e) {
            logger.error("RbacPermissionProvider#getDataScope - error, userId={}, resourceType={}",
                    userId, resourceType, e);
            return DataScope.SELF; // 出错时默认返回最小权限
        }
    }

    @Override
    public boolean hasResourcePermission(
            Long userId,
            String resourceType,
            String resourceId,
            String permission) {
        // 检查数据权限范围
        DataScope scope = getDataScope(userId, resourceType);
        return scope != DataScope.NONE;
    }

    @Override
    public String getProviderType() {
        return "RBAC";
    }

    // ===== 需要子类实现的方法 =====

    /**
     * 获取用户的角色列表
     *
     * <p>子类需要重写此方法以集成到自己的权限系统。
     *
     * @param userId 用户 ID
     * @return 用户的角色编码列表，如果用户没有角色则返回空列表
     */
    protected List<String> getUserRoles(Long userId) {
        // 默认实现：返回空列表
        logger.warn("RbacPermissionProvider#getUserRoles - not implemented, returning empty list, userId={}", userId);
        return List.of();
    }

    /**
     * 获取用户对指定资源的数据权限范围
     *
     * <p>子类需要重写此方法以集成到自己的权限系统。
     *
     * @param userId 用户 ID
     * @param resourceType 资源类型
     * @return 数据权限范围，如果无法确定则返回 SELF
     */
    protected DataScope getUserDataScope(Long userId, String resourceType) {
        // 默认实现：返回 SELF
        logger.debug("RbacPermissionProvider#getUserDataScope - using default SELF, userId={}, resourceType={}",
                userId, resourceType);
        return DataScope.SELF;
    }
}
