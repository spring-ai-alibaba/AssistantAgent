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
}
