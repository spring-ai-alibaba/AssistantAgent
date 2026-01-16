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
 * 动作类型枚举
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public enum ActionType {

    /**
     * API 调用 - 调用外部 REST/HTTP 接口
     */
    API_CALL,

    /**
     * 页面跳转 - 导航到指定页面
     */
    PAGE_NAVIGATION,

    /**
     * 表单预填 - 预填表单字段
     */
    FORM_PREFILL,

    /**
     * 工作流触发 - 触发后台工作流
     */
    WORKFLOW_TRIGGER,

    /**
     * 多步骤动作 - 包含多个执行步骤的复合动作
     */
    MULTI_STEP,

    /**
     * 内部服务调用 - 调用内部 Spring Bean
     */
    INTERNAL_SERVICE,

    /**
     * MCP 工具调用 - 调用 MCP 协议工具
     */
    MCP_TOOL
}
