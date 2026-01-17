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

import java.util.List;
import java.util.Map;

import com.alibaba.assistant.agent.planning.model.StepDefinition.MethodParam;

/**
 * 动作定义
 *
 * <p>完整的动作定义，包括基本信息、参数、步骤配置等。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionDefinition {

    /**
     * 动作唯一标识
     */
    private String actionId;

    /**
     * 动作名称
     */
    private String actionName;

    /**
     * 动作描述
     */
    private String description;

    /**
     * 动作类型
     */
    private ActionType actionType;

    /**
     * 动作分类
     */
    private String category;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 触发关键词
     */
    private List<String> triggerKeywords;

    /**
     * 同义词列表
     */
    private List<String> synonyms;

    /**
     * 示例输入（用于训练和匹配）
     */
    private List<String> exampleInputs;

    /**
     * 参数定义列表
     */
    private List<ActionParameter> parameters;

    /**
     * 步骤定义列表（多步骤动作）
     */
    private List<StepDefinition> steps;

    /**
     * 状态 Schema 定义（用于多步骤状态管理）
     */
    private Map<String, Object> stateSchema;

    /**
     * 处理器类名（单步骤动作）
     */
    private String handler;

    /**
     * 接口绑定配置（单步骤动作）
     */
    private StepDefinition.InterfaceBinding interfaceBinding;

    /**
     * 优先级
     */
    @Builder.Default
    private Integer priority = 0;

    /**
     * 默认超时时间（分钟）
     */
    @Builder.Default
    private Integer timeoutMinutes = 30;

    /**
     * 是否启用
     */
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 权限要求
     */
    private List<String> requiredPermissions;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    // ===== 多租户和权限字段 =====

    /**
     * 租户 ID（null 表示全局 Action，所有租户可见）
     */
    private Long tenantId;

    /**
     * 系统 ID（null 表示租户级 Action，租户下所有系统可见）
     */
    private Long systemId;

    /**
     * 模块 ID（null 表示系统级 Action，系统下所有模块可见）
     */
    private Long moduleId;

    /**
     * 允许执行此 Action 的角色列表
     *
     * <p>如果为 null 或空，表示所有角色都可以执行。
     * 如果不为空，只有列表中的角色可以执行此 Action。
     */
    private List<String> allowedRoles;

    /**
     * 数据权限配置
     *
     * <p>定义了执行此 Action 时需要应用的数据权限规则。
     * 例如，查询类 Action 可能需要根据用户的部门过滤数据。
     */
    private DataPermissionConfig dataPermissionConfig;

    /**
     * 判断是否为多步骤动作
     */
    public boolean isMultiStep() {
        return ActionType.MULTI_STEP.equals(actionType)
                || (steps != null && !steps.isEmpty());
    }

    /**
     * 获取必填参数
     */
    public List<ActionParameter> getRequiredParameters() {
        if (parameters == null) {
            return List.of();
        }
        return parameters.stream()
                .filter(p -> Boolean.TRUE.equals(p.getRequired()))
                .toList();
    }

    /**
     * 检查 Action 是否属于指定的租户和系统
     *
     * <p>匹配规则：
     * <ul>
     * <li>如果 tenantId 为 null，表示全局 Action，所有租户都可以访问</li>
     * <li>如果 tenantId 不为 null 但 systemId 为 null，表示租户级 Action，租户下所有系统都可以访问</li>
     * <li>如果 tenantId 和 systemId 都不为 null，必须精确匹配</li>
     * </ul>
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @return 如果 Action 属于指定的租户和系统则返回 true，否则返回 false
     */
    public boolean belongsToTenant(Long tenantId, Long systemId) {
        // 全局 Action
        if (this.tenantId == null) {
            return true;
        }

        // 租户级 Action（租户 ID 必须匹配）
        if (!this.tenantId.equals(tenantId)) {
            return false;
        }

        // 系统级 Action（系统 ID 必须匹配）
        if (this.systemId != null && !this.systemId.equals(systemId)) {
            return false;
        }

        return true;
    }

    /**
     * 检查 Action 是否属于指定的租户、系统和模块
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @return 如果 Action 属于指定的租户、系统和模块则返回 true，否则返回 false
     */
    public boolean belongsToTenant(Long tenantId, Long systemId, Long moduleId) {
        if (!belongsToTenant(tenantId, systemId)) {
            return false;
        }

        // 模块级 Action（模块 ID 必须匹配）
        if (this.moduleId != null && !this.moduleId.equals(moduleId)) {
            return false;
        }

        return true;
    }

    /**
     * 获取 Action 绑定配置
     *
     * <p>这是一个便捷方法，返回 interfaceBinding。
     *
     * @return 接口绑定配置
     */
    public StepDefinition.InterfaceBinding getBinding() {
        return interfaceBinding;
    }

    /**
     * Action 绑定配置（别名，用于便捷访问）
     */
    @Deprecated
    public ActionBinding getActionBinding() {
        return new ActionBinding(interfaceBinding);
    }

    /**
     * Action 绑定配置（兼容旧代码）
     *
     * @deprecated 使用 {@link #getBinding()} 代替
     */
    @Deprecated
    @Data
    public static class ActionBinding {
        private final String type;
        private final String url;
        private final String method;
        private final java.util.Map<String, String> headers;
        private final String serverName;
        private final String toolName;
        private final String beanName;
        private final String methodName;
        private final java.util.List<MethodParam> methodParams;
        private final String dataSourceId;

        public ActionBinding(StepDefinition.InterfaceBinding binding) {
            if (binding == null) {
                this.type = null;
                this.url = null;
                this.method = null;
                this.headers = null;
                this.serverName = null;
                this.toolName = null;
                this.beanName = null;
                this.methodName = null;
                this.methodParams = null;
                this.dataSourceId = null;
            } else {
                this.type = binding.getType();
                if (binding.getHttp() != null) {
                    this.url = binding.getHttp().getUrl();
                    this.method = binding.getHttp().getMethod();
                    this.headers = binding.getHttp().getHeaders();
                } else {
                    this.url = null;
                    this.method = null;
                    this.headers = null;
                }
                if (binding.getMcp() != null) {
                    this.serverName = binding.getMcp().getServerName();
                    this.toolName = binding.getMcp().getToolName();
                } else {
                    this.serverName = null;
                    this.toolName = null;
                }
                if (binding.getInternal() != null) {
                    this.beanName = binding.getInternal().getBeanName();
                    this.methodName = binding.getInternal().getMethodName();
                    this.methodParams = binding.getInternal().getMethodParams();
                } else {
                    this.beanName = null;
                    this.methodName = null;
                    this.methodParams = null;
                }
                if (binding.getDataAgent() != null) {
                    this.dataSourceId = binding.getDataAgent().getDataSourceId();
                } else {
                    this.dataSourceId = null;
                }
            }
        }
    }
}
