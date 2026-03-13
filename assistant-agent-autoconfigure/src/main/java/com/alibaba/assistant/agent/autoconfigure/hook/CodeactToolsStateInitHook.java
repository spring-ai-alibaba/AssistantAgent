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
package com.alibaba.assistant.agent.autoconfigure.hook;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.common.constant.HookPriorityConstants;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.core.tool.CodeactToolRegistry;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Prioritized;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * CodeactTools 状态初始化 Hook
 *
 * <p>在 BEFORE_AGENT 阶段最先执行，将 {@link CodeactToolRegistry} 中注册的所有
 * 工具的可序列化元数据注入到 {@link OverAllState} 中。
 *
 * <p><b>重要</b>：为避免序列化问题，不再将完整的 {@link CodeactTool} 对象写入 State。
 * 而是将工具的元数据（名称、描述、参数签名等）以可序列化的形式写入。
 * 
 * <p>写入 State 的数据：
 * <ul>
 *   <li>{@code codeact_tool_names} - 所有工具名称列表 (List&lt;String&gt;)</li>
 *   <li>{@code codeact_tool_metadata_list} - 所有工具的元数据列表 (List&lt;Map&lt;String, Object&gt;&gt;)</li>
 * </ul>
 *
 * <p>优先级为 {@value #ORDER}，确保在所有其他 BEFORE_AGENT Hook 之前执行。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@HookPositions(HookPosition.BEFORE_AGENT)
public class CodeactToolsStateInitHook extends AgentHook implements Prioritized {

    private static final Logger log = LoggerFactory.getLogger(CodeactToolsStateInitHook.class);

    /**
     * 优先级设置为 {@link HookPriorityConstants#CODEACT_TOOLS_STATE_INIT_HOOK}，
     * 确保在所有其他 BEFORE_AGENT Hook 之前执行。
     * <p>
     * 执行顺序：
     * <ol>
     *   <li>CodeactToolsStateInitHook (5) - 注入工具元数据到 state</li>
     *   <li>ReactExperienceHook (20) - React 经验注入</li>
     *   <li>TaskTreeInitHook (30) - 任务树初始化</li>
     *   <li>FastIntentHook (50) - 快速意图判断</li>
     *   <li>EvaluationHook (100) - 评估（从 state 读取工具元数据）</li>
     *   <li>PromptContributorHook (200) - Prompt 注入</li>
     * </ol>
     */
    private static final int ORDER = HookPriorityConstants.CODEACT_TOOLS_STATE_INIT_HOOK;

    private final CodeactToolRegistry codeactToolRegistry;

    public CodeactToolsStateInitHook(CodeactToolRegistry codeactToolRegistry) {
        this.codeactToolRegistry = codeactToolRegistry;
    }

    @Override
    public String getName() {
        return "CodeactToolsStateInitHook";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * 获取 CodeactToolRegistry 实例
     * <p>
     * 供其他组件使用，以便从 Registry 中获取完整的工具对象。
     * 
     * @return CodeactToolRegistry 实例
     */
    public CodeactToolRegistry getCodeactToolRegistry() {
        return codeactToolRegistry;
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        List<CodeactTool> allTools = codeactToolRegistry.getAllTools();
        
        // 提取工具名称列表
        List<String> toolNames = allTools.stream()
                .map(CodeactTool::getName)
                .collect(Collectors.toList());

        // 提取工具元数据列表（可序列化的形式）
        List<Map<String, Object>> toolMetadataList = new ArrayList<>();
        for (CodeactTool tool : allTools) {
            Map<String, Object> metadata = extractSerializableMetadata(tool);
            if (metadata != null) {
                toolMetadataList.add(metadata);
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(CodeactStateKeys.CODEACT_TOOL_NAMES, toolNames);
        updates.put(CodeactStateKeys.CODEACT_TOOL_METADATA_LIST, toolMetadataList);

        log.info("CodeactToolsStateInitHook#beforeAgent - reason=注入工具元数据到state, toolCount={}, metadataCount={}",
                toolNames.size(), toolMetadataList.size());

        return CompletableFuture.completedFuture(updates);
    }

    /**
     * 从 CodeactTool 中提取可序列化的元数据
     * 
     * @param tool CodeactTool 实例
     * @return 包含工具元数据的 Map（可序列化）
     */
    private Map<String, Object> extractSerializableMetadata(CodeactTool tool) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            
            // 基本信息
            metadata.put("name", tool.getName());
            metadata.put("description", tool.getDescription());
            
            // 参数签名
            ParameterTree paramTree = tool.getParameterTree();
            if (paramTree != null && paramTree.hasParameters()) {
                metadata.put("parameters", paramTree.toPythonSignature());
            } else {
                metadata.put("parameters", "");
            }
            
            // CodeactToolMetadata 中的关键信息
            CodeactToolMetadata codeactMetadata = tool.getCodeactMetadata();
            if (codeactMetadata != null) {
                metadata.put("targetClassName", codeactMetadata.targetClassName());
                metadata.put("alwaysAvailable", codeactMetadata.alwaysAvailable());
            }
            
            return metadata;
        } catch (Exception e) {
            log.warn("CodeactToolsStateInitHook#extractSerializableMetadata - 提取工具元数据失败, toolName={}", 
                    tool.getName(), e);
            return null;
        }
    }
}

