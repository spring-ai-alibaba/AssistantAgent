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
package com.alibaba.assistant.agent.planning.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 动作注册表实体类
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("action_registry")
public class ActionRegistryEntity {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 动作唯一标识
     */
    @TableField("action_id")
    private String actionId;

    /**
     * 动作名称
     */
    @TableField("action_name")
    private String actionName;

    /**
     * 动作描述
     */
    @TableField("description")
    private String description;

    /**
     * 动作类型: API_CALL, PAGE_NAVIGATION, FORM_PREFILL, WORKFLOW_TRIGGER, MULTI_STEP, INTERNAL_SERVICE, MCP_TOOL
     */
    @TableField("action_type")
    private String actionType;

    /**
     * 分类
     */
    @TableField("category")
    private String category;

    /**
     * 标签列表（JSON 数组）
     */
    @TableField("tags")
    private String tags;

    /**
     * 触发关键词（JSON 数组）
     * 注意：数据库字段名为 keywords
     */
    @TableField("keywords")
    private String triggerKeywords;

    /**
     * 同义词列表（JSON 数组）
     */
    @TableField("synonyms")
    private String synonyms;

    /**
     * 示例输入（JSON 数组）
     */
    @TableField("example_inputs")
    private String exampleInputs;

    /**
     * 参数定义（JSON）
     */
    @TableField("parameters")
    private String parameters;

    /**
     * 步骤定义（JSON，多步骤动作）
     */
    @TableField("steps")
    private String steps;

    /**
     * 状态 Schema（JSON）
     */
    @TableField("state_schema")
    private String stateSchema;

    /**
     * 处理器类名
     */
    @TableField("handler")
    private String handler;

    /**
     * 接口绑定配置（JSON）
     */
    @TableField("interface_binding")
    private String interfaceBinding;

    /**
     * 优先级
     */
    @TableField("priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * 超时时间（分钟）
     */
    @TableField("timeout_minutes")
    @Builder.Default
    private Integer timeoutMinutes = 30;

    /**
     * 是否启用
     */
    @TableField("enabled")
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 权限要求（JSON 数组）
     */
    @TableField("required_permissions")
    private String requiredPermissions;

    /**
     * 元数据（JSON）
     */
    @TableField("metadata")
    private String metadata;

    /**
     * 使用次数
     */
    @TableField("usage_count")
    @Builder.Default
    private Long usageCount = 0L;

    /**
     * 成功率
     */
    @TableField("success_rate")
    @Builder.Default
    private Double successRate = 0.0;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 创建者
     */
    @TableField("creator")
    private String creator;

    private String systemId;
}
