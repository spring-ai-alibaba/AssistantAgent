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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 Hook 适用的 Agent 执行阶段
 * 
 * <p>此注解用于声明一个 Hook 应该在 CodeactAgent 的哪个阶段执行。
 * 与 spring-ai-alibaba 中的 {@code @HookPositions} 注解配合使用，
 * 形成完整的 Hook 元数据体系：
 * <ul>
 *     <li>{@code @HookPositions} - 指定 Hook 的执行时机（BEFORE_AGENT, AFTER_AGENT, BEFORE_MODEL, AFTER_MODEL）</li>
 *     <li>{@code @HookPhases} - 指定 Hook 的执行阶段（REACT, CODEACT, ALL）</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // React 阶段的 Hook（用于主 Agent）
 * @HookPhases(AgentPhase.REACT)
 * @HookPositions(HookPosition.BEFORE_AGENT)
 * public class FastIntentReactHook extends AgentHook {
 *     // ...
 * }
 * 
 * // Codeact 阶段的 Hook（用于代码生成子 Agent）
 * @HookPhases(AgentPhase.CODEACT)
 * @HookPositions(HookPosition.BEFORE_MODEL)
 * public class CodeGenerationExperienceHook extends ModelHook {
 *     // ...
 * }
 * 
 * // 两个阶段都适用的 Hook
 * @HookPhases({AgentPhase.REACT, AgentPhase.CODEACT})
 * @HookPositions(HookPosition.AFTER_MODEL)
 * public class CommonEvaluationHook extends ModelHook {
 *     // ...
 * }
 * 
 * // 使用 ALL 简化声明
 * @HookPhases(AgentPhase.ALL)
 * public class UniversalLoggingHook extends AgentHook {
 *     // ...
 * }
 * }</pre>
 *
 * <h2>默认行为</h2>
 * <p>如果 Hook 类没有添加此注解，默认被视为 {@link AgentPhase#REACT} 阶段的 Hook，
 * 以保持向后兼容性。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 * @see AgentPhase
 * @see HookPhaseUtils
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HookPhases {

    /**
     * 指定 Hook 适用的 Agent 阶段
     * 
     * <p>可以指定一个或多个阶段。如果指定多个阶段，Hook 会被注册到所有指定的阶段。
     * 
     * @return Hook 适用的 Agent 阶段数组，默认为 {@link AgentPhase#REACT}
     */
    AgentPhase[] value() default { AgentPhase.REACT };
}
