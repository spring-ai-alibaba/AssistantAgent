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
package com.alibaba.assistant.agent.common.hook;

/**
 * Agent 执行阶段枚举
 * 
 * <p>用于标识 Hook 应该在哪个 Agent 阶段执行。CodeactAgent 架构中有两个主要阶段：
 * <ul>
 *     <li>React 阶段：主 Agent 决策层，LLM 决定使用什么工具完成任务</li>
 *     <li>Codeact 阶段：代码生成子 Agent 层，LLM 生成具体代码</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * @HookPhases(AgentPhase.REACT)
 * public class FastIntentReactHook extends AgentHook { ... }
 * 
 * @HookPhases(AgentPhase.CODEACT)
 * public class CodeGenExperienceHook extends ModelHook { ... }
 * 
 * @HookPhases({AgentPhase.REACT, AgentPhase.CODEACT})
 * public class CommonEvaluationHook extends ModelHook { ... }
 * }</pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 * @see HookPhases
 * @see HookPhaseUtils
 */
public enum AgentPhase {

    /**
     * React 阶段 - 主 Agent 决策层
     * 
     * <p>对应 CodeactAgent.builder().hooks() 参数。
     * 在此阶段，主 Agent（ReactAgent/CodeactAgent）负责：
     * <ul>
     *     <li>理解用户意图</li>
     *     <li>决定调用哪些工具（write_code, execute_code, reply 等）</li>
     *     <li>协调整体任务执行流程</li>
     * </ul>
     */
    REACT,

    /**
     * Codeact 阶段 - 代码生成子 Agent 层
     * 
     * <p>对应 CodeactAgent.builder().subAgentHooks() 参数。
     * 在此阶段，CodeGeneratorSubAgent 负责：
     * <ul>
     *     <li>根据需求描述生成 Python 函数代码</li>
     *     <li>调用 LLM 进行代码生成</li>
     *     <li>代码质量评估和优化</li>
     * </ul>
     */
    CODEACT,

    /**
     * 两个阶段都适用
     * 
     * <p>标记为 ALL 的 Hook 会同时注册到 React 阶段和 Codeact 阶段。
     * 适用于通用的评估、日志、监控等跨阶段的 Hook。
     */
    ALL
}
