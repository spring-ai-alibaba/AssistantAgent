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

import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hook 阶段过滤工具类
 * 
 * <p>提供按 {@link AgentPhase} 过滤和分组 Hook 的工具方法。
 * 用于在 Agent 注册时自动将 Hook 分配到正确的阶段。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 在 AgentRegistry 中使用
 * @Autowired
 * public AgentRegistry(List<Hook> allHooks, ...) {
 *     // 自动按阶段分组
 *     Map<AgentPhase, List<Hook>> grouped = HookPhaseUtils.groupByPhase(allHooks);
 *     this.reactHooks = grouped.get(AgentPhase.REACT);
 *     this.codeactHooks = grouped.get(AgentPhase.CODEACT);
 * }
 * 
 * // 或者分别过滤
 * List<Hook> reactHooks = HookPhaseUtils.filterByPhase(allHooks, AgentPhase.REACT);
 * List<Hook> codeactHooks = HookPhaseUtils.filterByPhase(allHooks, AgentPhase.CODEACT);
 * }</pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 * @see AgentPhase
 * @see HookPhases
 */
public final class HookPhaseUtils {

    private static final Logger log = LoggerFactory.getLogger(HookPhaseUtils.class);

    private HookPhaseUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 获取 Hook 适用的阶段
     * 
     * <p>读取 Hook 类上的 {@link HookPhases} 注解。如果没有注解，默认返回 {@link AgentPhase#REACT}。
     *
     * @param hook Hook 实例
     * @return Hook 适用的阶段数组
     */
    public static AgentPhase[] getHookPhases(Hook hook) {
        if (hook == null) {
            return new AgentPhase[]{ AgentPhase.REACT };
        }

        HookPhases annotation = hook.getClass().getAnnotation(HookPhases.class);
        if (annotation != null) {
            return annotation.value();
        }

        // 默认返回 REACT（向后兼容）
        return new AgentPhase[]{ AgentPhase.REACT };
    }

    /**
     * 判断 Hook 是否适用于指定阶段
     *
     * @param hook Hook 实例
     * @param phase 目标阶段
     * @return 如果 Hook 适用于指定阶段返回 true
     */
    public static boolean isApplicableToPhase(Hook hook, AgentPhase phase) {
        if (hook == null || phase == null) {
            return false;
        }

        AgentPhase[] phases = getHookPhases(hook);
        for (AgentPhase p : phases) {
            if (p == AgentPhase.ALL || p == phase) {
                return true;
            }
        }
        return false;
    }

    /**
     * 过滤出指定阶段的 Hook 列表
     *
     * @param hooks 所有 Hook 列表
     * @param phase 目标阶段
     * @return 适用于指定阶段的 Hook 列表（不可变）
     */
    public static List<Hook> filterByPhase(List<Hook> hooks, AgentPhase phase) {
        if (hooks == null || hooks.isEmpty() || phase == null) {
            return Collections.emptyList();
        }

        List<Hook> filtered = hooks.stream()
                .filter(hook -> isApplicableToPhase(hook, phase))
                .collect(Collectors.toList());

        log.debug("HookPhaseUtils#filterByPhase phase={}, total={}, filtered={}",
                phase, hooks.size(), filtered.size());

        return filtered;
    }

    /**
     * 将 Hook 列表按阶段分组
     * 
     * <p>注意：如果一个 Hook 标记为 {@link AgentPhase#ALL}，
     * 它会同时出现在 REACT 和 CODEACT 两个分组中。
     *
     * @param hooks 所有 Hook 列表
     * @return 按阶段分组的 Map，key 为 AgentPhase，value 为该阶段的 Hook 列表
     */
    public static Map<AgentPhase, List<Hook>> groupByPhase(List<Hook> hooks) {
        Map<AgentPhase, List<Hook>> result = new EnumMap<>(AgentPhase.class);
        result.put(AgentPhase.REACT, new ArrayList<>());
        result.put(AgentPhase.CODEACT, new ArrayList<>());
        result.put(AgentPhase.ALL, new ArrayList<>());

        if (hooks == null || hooks.isEmpty()) {
            return result;
        }

        for (Hook hook : hooks) {
            AgentPhase[] phases = getHookPhases(hook);
            for (AgentPhase phase : phases) {
                if (phase == AgentPhase.ALL) {
                    // ALL 表示两个阶段都适用
                    result.get(AgentPhase.REACT).add(hook);
                    result.get(AgentPhase.CODEACT).add(hook);
                    result.get(AgentPhase.ALL).add(hook);
                } else {
                    result.get(phase).add(hook);
                }
            }
        }

        log.info("HookPhaseUtils#groupByPhase 分组完成: total={}, react={}, codeact={}, all={}",
                hooks.size(),
                result.get(AgentPhase.REACT).size(),
                result.get(AgentPhase.CODEACT).size(),
                result.get(AgentPhase.ALL).size());

        return result;
    }

    /**
     * 获取 React 阶段的 Hook 列表
     * 
     * <p>便捷方法，等同于 {@code filterByPhase(hooks, AgentPhase.REACT)}
     *
     * @param hooks 所有 Hook 列表
     * @return React 阶段的 Hook 列表
     */
    public static List<Hook> getReactHooks(List<Hook> hooks) {
        return filterByPhase(hooks, AgentPhase.REACT);
    }

    /**
     * 获取 Codeact 阶段的 Hook 列表
     * 
     * <p>便捷方法，等同于 {@code filterByPhase(hooks, AgentPhase.CODEACT)}
     *
     * @param hooks 所有 Hook 列表
     * @return Codeact 阶段的 Hook 列表
     */
    public static List<Hook> getCodeactHooks(List<Hook> hooks) {
        return filterByPhase(hooks, AgentPhase.CODEACT);
    }

    /**
     * 打印 Hook 分组信息（用于调试）
     *
     * @param hooks 所有 Hook 列表
     */
    public static void logHookPhases(List<Hook> hooks) {
        if (hooks == null || hooks.isEmpty()) {
            log.info("HookPhaseUtils#logHookPhases 无 Hook 需要分组");
            return;
        }

        log.info("HookPhaseUtils#logHookPhases 开始打印 Hook 阶段信息，总数: {}", hooks.size());
        
        for (Hook hook : hooks) {
            AgentPhase[] phases = getHookPhases(hook);
            StringBuilder phaseStr = new StringBuilder();
            for (int i = 0; i < phases.length; i++) {
                if (i > 0) {
                    phaseStr.append(", ");
                }
                phaseStr.append(phases[i].name());
            }
            log.info("  - Hook: {}, Phases: [{}]", hook.getName(), phaseStr);
        }
    }
}
