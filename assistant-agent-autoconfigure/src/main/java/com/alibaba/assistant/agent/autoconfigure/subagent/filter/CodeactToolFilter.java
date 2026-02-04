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
package com.alibaba.assistant.agent.autoconfigure.subagent.filter;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CodeactTool 筛选器
 * 
 * <p>根据 OverAllState 中的白名单配置筛选可用工具。
 * 支持按工具名称和工具组进行筛选，多种白名单模式可选。
 * 
 * <p>使用方式：
 * <pre>{@code
 * List<CodeactTool> filtered = CodeactToolFilter.filter(allTools, state, Language.PYTHON);
 * }</pre>
 * 
 * @author Assistant Agent Team
 * @since 1.0.0
 * @see WhitelistMode
 */
public final class CodeactToolFilter {

    private static final Logger logger = LoggerFactory.getLogger(CodeactToolFilter.class);

    private CodeactToolFilter() {
        // 工具类，禁止实例化
    }

    /**
     * 根据 OverAllState 中的白名单配置筛选工具
     * 
     * @param allTools 全部工具列表
     * @param state    OverAllState 实例
     * @param language 目标编程语言（用于语言兼容性过滤）
     * @return 筛选后的工具列表
     */
    public static List<CodeactTool> filter(List<CodeactTool> allTools, OverAllState state, Language language) {
        if (allTools == null || allTools.isEmpty()) {
            logger.debug("CodeactToolFilter#filter - 输入工具列表为空，返回空列表");
            return Collections.emptyList();
        }

        // 1. 先按语言兼容性过滤
        List<CodeactTool> languageCompatible = filterByLanguage(allTools, language);
        logger.debug("CodeactToolFilter#filter - 语言过滤后工具数: {}/{}", 
                languageCompatible.size(), allTools.size());

        // 2. 读取白名单配置
        List<String> toolNameWhitelist = extractStringList(state, CodeactStateKeys.AVAILABLE_TOOL_NAMES);
        List<String> toolGroupWhitelist = extractStringList(state, CodeactStateKeys.AVAILABLE_TOOL_GROUPS);
        WhitelistMode mode = extractWhitelistMode(state);

        logger.info("CodeactToolFilter#filter - 白名单配置: mode={}, toolNames={}, toolGroups={}", 
                mode, toolNameWhitelist, toolGroupWhitelist);

        // 3. 如果没有任何白名单配置，返回语言兼容的全部工具
        if (isEmpty(toolNameWhitelist) && isEmpty(toolGroupWhitelist)) {
            logger.info("CodeactToolFilter#filter - 无白名单配置，使用全部工具: count={}", 
                    languageCompatible.size());
            return languageCompatible;
        }

        // 4. 根据模式执行筛选
        List<CodeactTool> filtered = applyWhitelist(languageCompatible, toolNameWhitelist, toolGroupWhitelist, mode);

        logger.info("CodeactToolFilter#filter - 筛选完成: input={}, output={}, filteredTools={}", 
                languageCompatible.size(), filtered.size(),
                filtered.stream().map(CodeactTool::getName).collect(Collectors.toList()));

        return filtered;
    }

    /**
     * 按语言兼容性过滤
     */
    private static List<CodeactTool> filterByLanguage(List<CodeactTool> tools, Language language) {
        if (language == null) {
            return new ArrayList<>(tools);
        }
        return tools.stream()
                .filter(tool -> {
                    CodeactToolMetadata meta = tool.getCodeactMetadata();
                    return meta.supportedLanguages().contains(language);
                })
                .collect(Collectors.toList());
    }

    /**
     * 应用白名单筛选
     */
    private static List<CodeactTool> applyWhitelist(
            List<CodeactTool> tools,
            List<String> nameWhitelist,
            List<String> groupWhitelist,
            WhitelistMode mode) {

        Set<String> nameSet = isEmpty(nameWhitelist) ? null : new HashSet<>(nameWhitelist);
        Set<String> groupSet = isEmpty(groupWhitelist) ? null : new HashSet<>(groupWhitelist);

        return tools.stream()
                .filter(tool -> matchesWhitelist(tool, nameSet, groupSet, mode))
                .collect(Collectors.toList());
    }

    /**
     * 判断工具是否匹配白名单
     */
    private static boolean matchesWhitelist(
            CodeactTool tool,
            Set<String> nameSet,
            Set<String> groupSet,
            WhitelistMode mode) {

        String toolName = tool.getName();
        String toolGroup = tool.getCodeactMetadata().targetClassName();

        boolean matchesName = nameSet == null || nameSet.contains(toolName);
        boolean matchesGroup = groupSet == null || 
                (toolGroup != null && groupSet.contains(toolGroup));

        switch (mode) {
            case INTERSECTION:
                // 两个白名单都存在时取交集，只有一个时只检查那一个
                if (nameSet != null && groupSet != null) {
                    return matchesName && matchesGroup;
                } else if (nameSet != null) {
                    return matchesName;
                } else if (groupSet != null) {
                    return matchesGroup;
                }
                return true;

            case UNION:
                // 取并集：满足任一即可
                if (nameSet != null && groupSet != null) {
                    return matchesName || matchesGroup;
                } else if (nameSet != null) {
                    return matchesName;
                } else if (groupSet != null) {
                    return matchesGroup;
                }
                return true;

            case NAME_ONLY:
                return nameSet == null || matchesName;

            case GROUP_ONLY:
                return groupSet == null || matchesGroup;

            default:
                return true;
        }
    }

    /**
     * 从 state 提取字符串列表
     */
    @SuppressWarnings("unchecked")
    private static List<String> extractStringList(OverAllState state, String key) {
        if (state == null) {
            return null;
        }
        return state.value(key, List.class).orElse(null);
    }

    /**
     * 从 state 提取白名单模式
     */
    private static WhitelistMode extractWhitelistMode(OverAllState state) {
        if (state == null) {
            return WhitelistMode.INTERSECTION;
        }
        return state.value(CodeactStateKeys.WHITELIST_MODE, String.class)
                .map(s -> {
                    try {
                        return WhitelistMode.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        logger.warn("CodeactToolFilter#extractWhitelistMode - 无效的模式值: {}, 使用默认值", s);
                        return WhitelistMode.INTERSECTION;
                    }
                })
                .orElse(WhitelistMode.INTERSECTION);
    }

    /**
     * 判断列表是否为空
     */
    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
