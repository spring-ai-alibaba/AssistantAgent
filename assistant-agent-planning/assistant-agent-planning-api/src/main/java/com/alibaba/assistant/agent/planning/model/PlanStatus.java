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
 * 执行计划状态枚举
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public enum PlanStatus {

    /**
     * 待执行 - 计划已创建，等待执行
     */
    PENDING,

    /**
     * 执行中 - 计划正在执行
     */
    IN_PROGRESS,

    /**
     * 等待输入 - 等待用户输入或选择
     */
    WAITING_INPUT,

    /**
     * 已完成 - 计划执行成功完成
     */
    COMPLETED,

    /**
     * 已取消 - 计划被用户取消
     */
    CANCELLED,

    /**
     * 已失败 - 计划执行失败
     */
    FAILED,

    /**
     * 已超时 - 计划执行超时
     */
    TIMEOUT,

    /**
     * 部分完成 - 部分步骤完成，部分失败
     */
    PARTIAL
}
