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
package com.alibaba.assistant.agent.common.constant;

/**
 * Hook 优先级常量定义（框架层）
 *
 * <p>用于控制同一 HookPosition 下多个 Hook 的执行顺序。
 * 数值越小，优先级越高，越先执行。
 *
 * <p>设计原则：
 * <ul>
 *   <li>快速意图 Hook 优先级最高（50），用于快速路径判断</li>
 *   <li>评估 Hook 次之（100），命中快速意图时可跳过</li>
 *   <li>Prompt 注入等其他 Hook 更后（200+）</li>
 * </ul>
 *
 * <p>上层应用可在此基础上定义更细粒度的优先级，
 * 只需确保不与框架层的核心优先级冲突。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public final class HookPriorityConstants {

    private HookPriorityConstants() {
        // Utility class
    }

    // ==================== beforeAgent 阶段 ====================

    /**
     * 快速意图 Hook 优先级
     * 
     * <p>在 beforeAgent 阶段最先执行，用于判断是否命中快速意图
     * 命中后设置 fast_intent 状态，后续 Hook 可据此跳过
     */
    public static final int FAST_INTENT_HOOK = 50;

    /**
     * 评估 Hook 默认优先级
     * 
     * <p>在快速意图判断之后执行
     * 如果快速意图命中，评估 Hook 可选择跳过以节省资源
     */
    public static final int EVALUATION_HOOK = 100;

    // ==================== beforeModel 阶段 ====================

    /**
     * beforeModel 阶段评估 Hook 默认优先级
     */
    public static final int BEFORE_MODEL_EVALUATION_HOOK = 100;

    // ==================== 通用 ====================

    /**
     * Prompt 贡献 Hook 优先级
     * 
     * <p>在评估 Hook 之后执行，可读取评估结果
     */
    public static final int PROMPT_CONTRIBUTOR_HOOK = 200;

    /**
     * 默认 Hook 优先级
     * 
     * <p>未指定优先级的 Hook 使用此默认值
     */
    public static final int DEFAULT_HOOK_PRIORITY = 500;

    // ==================== 状态键 ====================

    /**
     * 快速意图状态键
     * 
     * <p>存储在 OverAllState 中，用于标记快速意图命中状态
     * 类型：Map&lt;String, Object&gt;，包含：
     * <ul>
     *   <li>hit: Boolean - 是否命中快速意图</li>
     *   <li>experience_id: String - 命中的经验 ID</li>
     *   <li>experience_title: String - 命中的经验标题</li>
     *   <li>experience_type: String - 命中的经验类型</li>
     * </ul>
     */
    public static final String FAST_INTENT_STATE_KEY = "fast_intent";
}
