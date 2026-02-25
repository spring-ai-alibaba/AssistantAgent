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
package com.alibaba.assistant.agent.core.executor;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Codeact 上下文变量提供者 SPI
 * 
 * <p>业务方实现此接口，定义需要注入到 Python 执行环境的变量。
 * 框架层负责将这些变量注入到执行上下文中。
 * 
 * <h2>设计说明</h2>
 * <ul>
 *   <li>采用单一 Provider 模式，一个业务应用只需实现一个 Provider</li>
 *   <li>如果没有配置 Provider，则不注入任何自定义变量（向后兼容）</li>
 *   <li>{@link #getVariables(OverAllState, ToolContext)} 返回运行时实际存在的变量</li>
 *   <li>{@link #getVariableMetadata()} 返回所有可能变量的元数据定义（静态）</li>
 *   <li>Prompt 构建时，根据 getVariables() 的 key 去 metadata 中查找描述</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 * @see VariableMetadata
 * @see GraalCodeExecutor
 */
public interface CodeactVariableProvider {

    /**
     * 获取需要注入到 Python 执行环境的自定义变量（运行时）
     * 
     * <p>设计原则：有什么就返回什么，没有的数据不要返回 null 值。
     *
     * <p>变量会被注入到 Python 代码执行环境中，代码可以直接使用这些变量名访问数据。
     * 
     * @param state 当前 Agent 状态（包含 inputs 中写入的所有数据）
     * @param toolContext 工具上下文
     * @return 变量名到变量值的映射，这些变量会被注入到 Python 执行环境中。
     *         建议使用 LinkedHashMap 以保持变量顺序。
     */
    Map<String, Object> getVariables(OverAllState state, ToolContext toolContext);

    /**
     * 获取所有可能变量的元数据定义（静态）
     * 
     * <p>返回所有可能出现的变量的元数据，包括必有变量和可选变量。
     * 这是一个静态定义，不依赖运行时状态。
     * 
     * <h3>Prompt 构建时的使用方式</h3>
     * <ol>
     *   <li>调用 {@link #getVariables(OverAllState, ToolContext)} 获取当前实际存在的变量</li>
     *   <li>遍历 getVariableMetadata()，只展示 getVariables() 中存在的变量的描述</li>
     * </ol>
     * 
     * <p>默认返回空列表，子类可覆盖以提供元数据。
     * 
     * @return 所有可能变量的元数据列表
     */
    default List<VariableMetadata> getVariableMetadata() {
        return Collections.emptyList();
    }

    /**
     * 格式化变量说明为 Prompt 友好的字符串（便捷方法）
     * 
     * <p>根据实际存在的变量和元数据定义，生成用于 Prompt 的变量说明。
     * 只展示当前实际存在的变量的描述（不展示空值变量）。
     * 
     * <h3>输出格式示例</h3>
     * <pre>
     * - user_id(str): 用户工号
     * - session_id(str): 会话ID
     * </pre>
     * 
     * @param actualVariables 当前实际存在的变量（通过 getVariables 获取）
     * @return 格式化后的变量说明，每行一个变量
     */
    default String formatVariablesForPrompt(Map<String, Object> actualVariables) {
        if (actualVariables == null || actualVariables.isEmpty()) {
            return "";
        }

        List<VariableMetadata> allMetadata = getVariableMetadata();
        Map<String, VariableMetadata> metadataMap = new java.util.HashMap<>();
        for (VariableMetadata metadata : allMetadata) {
            metadataMap.put(metadata.getName(), metadata);
        }

        StringBuilder sb = new StringBuilder();
        for (String varName : actualVariables.keySet()) {
            VariableMetadata metadata = metadataMap.get(varName);
            if (metadata != null) {
                sb.append("- ").append(metadata.formatForPrompt()).append("\n");
            } else {
                sb.append("- ").append(varName).append("\n");
            }
        }

        return sb.toString();
    }
}
