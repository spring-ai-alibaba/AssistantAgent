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
package com.alibaba.assistant.agent.planning.context;

import java.util.Objects;
import java.util.Optional;

/**
 * 租户上下文管理器
 *
 * <p>使用 ThreadLocal 存储当前请求的租户信息，在整个请求处理过程中可以随时访问。
 *
 * <h3>使用场景</h3>
 * <ul>
 * <li>在 ActionProvider 中过滤租户相关的 Action</li>
 * <li>在 ParamCollectionService 中隔离不同租户的会话</li>
 * <li>在 PermissionProvider 中检查租户级别的权限</li>
 * <li>在 ActionExecutor 中传递租户上下文到外部系统</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 在请求开始时设置上下文（通常在 Filter 或 Interceptor 中）
 * TenantContext.set(1L, 2L, 100L);
 *
 * // 在业务代码中获取上下文
 * Long tenantId = TenantContext.getTenantId();  // 1L
 * Long systemId = TenantContext.getSystemId();  // 2L
 * Long userId = TenantContext.getUserId();      // 100L
 *
 * // 检查上下文是否存在
 * if (TenantContext.isPresent()) {
 *     // 执行需要租户上下文的操作
 * }
 *
 * // 在请求结束时清理上下文（通常在 Filter 或 Interceptor 中）
 * TenantContext.clear();
 * </pre>
 *
 * <h3>重要提示</h3>
 * <strong>必须在使用完毕后调用 {@link #clear()} 清理上下文</strong>，否则可能导致内存泄漏
 * 或在 Web 容器环境下导致上下文混乱。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class TenantContext {

    /**
     * ThreadLocal 存储租户信息
     */
    private static final ThreadLocal<TenantInfo> CONTEXT = new ThreadLocal<>();

    /**
     * 私有构造函数，防止实例化
     */
    private TenantContext() {
    }

    /**
     * 设置租户上下文
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @param userId 用户 ID
     * @throws IllegalArgumentException 如果任一参数为 null
     */
    public static void set(Long tenantId, Long systemId, Long userId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(systemId, "systemId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");

        CONTEXT.set(new TenantInfo(tenantId, systemId, userId));
    }

    /**
     * 设置租户上下文（仅租户和系统，无用户）
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @throws IllegalArgumentException 如果任一参数为 null
     */
    public static void set(Long tenantId, Long systemId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(systemId, "systemId cannot be null");

        CONTEXT.set(new TenantInfo(tenantId, systemId, null));
    }

    /**
     * 获取租户 ID
     *
     * @return 租户 ID，如果未设置则抛出异常
     * @throws IllegalStateException 如果上下文未设置
     */
    public static Long getTenantId() {
        TenantInfo info = CONTEXT.get();
        if (info == null) {
            throw new IllegalStateException("Tenant context not set. Call TenantContext.set() first.");
        }
        return info.tenantId;
    }

    /**
     * 获取系统 ID
     *
     * @return 系统 ID，如果未设置则抛出异常
     * @throws IllegalStateException 如果上下文未设置
     */
    public static Long getSystemId() {
        TenantInfo info = CONTEXT.get();
        if (info == null) {
            throw new IllegalStateException("Tenant context not set. Call TenantContext.set() first.");
        }
        return info.systemId;
    }

    /**
     * 获取用户 ID
     *
     * @return 用户 ID，如果未设置则返回 null
     * @throws IllegalStateException 如果上下文未设置
     */
    public static Long getUserId() {
        TenantInfo info = CONTEXT.get();
        if (info == null) {
            throw new IllegalStateException("Tenant context not set. Call TenantContext.set() first.");
        }
        return info.userId;
    }

    /**
     * 获取用户 ID（Optional）
     *
     * @return Optional 包装的用户 ID
     */
    public static Optional<Long> getUserIdOptional() {
        TenantInfo info = CONTEXT.get();
        if (info == null) {
            throw new IllegalStateException("Tenant context not set. Call TenantContext.set() first.");
        }
        return Optional.ofNullable(info.userId);
    }

    /**
     * 检查上下文是否已设置
     *
     * @return 如果上下文已设置则返回 true，否则返回 false
     */
    public static boolean isPresent() {
        return CONTEXT.get() != null;
    }

    /**
     * 清除租户上下文
     *
     * <p><strong>重要：</strong>在请求处理完成后必须调用此方法，否则可能导致内存泄漏。
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 获取完整的租户信息
     *
     * @return 租户信息，如果未设置则返回 null
     */
    public static TenantInfo getTenantInfo() {
        return CONTEXT.get();
    }

    /**
     * 租户信息
     */
    public static class TenantInfo {
        private final Long tenantId;
        private final Long systemId;
        private final Long userId;

        private TenantInfo(Long tenantId, Long systemId, Long userId) {
            this.tenantId = tenantId;
            this.systemId = systemId;
            this.userId = userId;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public Long getSystemId() {
            return systemId;
        }

        public Long getUserId() {
            return userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TenantInfo that = (TenantInfo) o;
            return Objects.equals(tenantId, that.tenantId) &&
                    Objects.equals(systemId, that.systemId) &&
                    Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, systemId, userId);
        }

        @Override
        public String toString() {
            return "TenantInfo{" +
                    "tenantId=" + tenantId +
                    ", systemId=" + systemId +
                    ", userId=" + userId +
                    '}';
        }
    }
}
