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
 * 执行步骤类型枚举
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public enum StepType {

    /**
     * 查询步骤 - 查询数据，返回选项供用户选择
     */
    QUERY,

    /**
     * 输入步骤 - 收集用户输入
     */
    INPUT,

    /**
     * 执行步骤 - 执行具体动作
     */
    EXECUTE,

    /**
     * API 调用步骤 - 调用外部 HTTP 接口
     */
    API_CALL,

    /**
     * 内部服务步骤 - 调用内部 Spring Bean
     */
    INTERNAL_SERVICE,

    /**
     * 通知步骤 - 发送通知（邮件、消息等）
     */
    NOTIFICATION,

    /**
     * 决策步骤 - 根据条件分支执行
     */
    DECISION,

    /**
     * 等待步骤 - 等待外部事件或用户输入
     */
    WAIT,

    /**
     * 转换步骤 - 数据转换和映射
     */
    TRANSFORM,

    /**
     * 校验步骤 - 参数校验和业务规则检查
     */
    VALIDATION
}
