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
 * 参数来源类型枚举
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public enum ParameterSource {

    /**
     * 用户输入 - 从用户的自然语言输入中提取
     */
    USER_INPUT,

    /**
     * 上下文 - 从会话上下文中获取（如 userId, sessionId）
     */
    CONTEXT,

    /**
     * 前序步骤 - 从前序步骤的输出中获取
     */
    PREVIOUS_STEP,

    /**
     * 步骤输出 - 从指定步骤的输出中获取
     */
    STEP_OUTPUT,

    /**
     * 环境变量 - 从系统环境变量中获取
     */
    ENVIRONMENT,

    /**
     * 默认值 - 使用预设的默认值
     */
    DEFAULT,

    /**
     * 计算值 - 通过表达式计算得出
     */
    COMPUTED,

    /**
     * 系统值 - 系统自动填充（如当前时间）
     */
    SYSTEM
}
