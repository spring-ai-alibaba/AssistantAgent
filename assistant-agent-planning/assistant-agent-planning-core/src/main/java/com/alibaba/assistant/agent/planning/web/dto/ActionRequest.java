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
package com.alibaba.assistant.agent.planning.web.dto;

import com.alibaba.assistant.agent.planning.model.ActionParameter;
import com.alibaba.assistant.agent.planning.model.ActionType;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 动作创建/更新请求
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionRequest {

    /**
     * 动作ID（更新时必填）
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
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 触发关键词
     */
    private List<String> triggerKeywords;

    /**
     * 同义词
     */
    private List<String> synonyms;

    /**
     * 示例输入
     */
    private List<String> exampleInputs;

    /**
     * 参数定义
     */
    private List<ActionParameter> parameters;

    /**
     * 执行步骤
     */
    private List<StepDefinition> steps;

    /**
     * 状态模式
     */
    private Map<String, Object> stateSchema;

    /**
     * 处理器
     */
    private String handler;

    /**
     * 接口绑定
     */
    private StepDefinition.InterfaceBinding interfaceBinding;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 超时时间（分钟）
     */
    private Integer timeoutMinutes;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 所需权限
     */
    private List<String> requiredPermissions;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;
}
