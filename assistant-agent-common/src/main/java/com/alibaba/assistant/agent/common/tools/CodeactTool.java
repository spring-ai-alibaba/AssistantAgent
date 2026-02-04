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
package com.alibaba.assistant.agent.common.tools;

import com.alibaba.assistant.agent.common.tools.definition.CodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.common.tools.definition.ReturnSchema;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;

import java.util.Optional;

/**
 * CodeAct 工具接口 - 扩展 Spring AI 的 ToolCallback。
 *
 * <p>CodeAct 工具体系的核心接口，关联：
 * <ul>
 * <li>CodeactToolDefinition - 结构化的工具定义</li>
 * <li>CodeactToolMetadata - 代码生成阶段的元数据</li>
 * </ul>
 *
 * <h2>核心特性</h2>
 * <ul>
 * <li>继承自 Spring AI 的 ToolCallback，保持与 Spring AI 生态的兼容性</li>
 * <li>提供 CodeAct 扩展元数据，支持代码生成和执行阶段的额外语义</li>
 * <li>支持结构化参数树和返回值 schema</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface CodeactTool extends ToolCallback {

	// ============ 继承自 ToolCallback ============
	// String call(String toolInput);
	// String call(String toolInput, ToolContext toolContext);
	// ToolDefinition getToolDefinition();

	/**
	 * 获取 CodeAct 结构化工具定义。
	 *
	 * <p>返回 CodeactToolDefinition，包含结构化的参数树和返回值 schema。
	 * 如果子类未实现此方法，返回 null 表示需要从 ToolDefinition 自动适配。
	 * @return CodeAct 结构化工具定义，未实现时返回 null
	 */
	default CodeactToolDefinition getCodeactDefinition() {
		return null;
	}

	/**
	 * 获取 CodeAct 工具元数据。
	 *
	 * <p>该元数据包含了代码生成和执行阶段所需的额外信息，例如：
	 * <ul>
	 * <li>支持的编程语言列表</li>
	 * <li>目标语言下的类/模块名</li>
	 * <li>Few-shot 示例</li>
	 * </ul>
	 * @return CodeAct 扩展元数据
	 */
	CodeactToolMetadata getCodeactMetadata();

	// ============ 便捷方法 ============

	/**
	 * 获取工具名。
	 * @return 工具名
	 */
	default String getName() {
		return getCodeactDefinition().name();
	}

	/**
	 * 获取工具描述。
	 * @return 工具描述
	 */
	default String getDescription() {
		return getCodeactDefinition().description();
	}

	/**
	 * 获取结构化参数树。
	 * @return 参数树，未定义时返回空的 ParameterTree
	 */
	default ParameterTree getParameterTree() {
		CodeactToolDefinition definition = getCodeactDefinition();
		if (definition != null) {
			return definition.parameterTree();
		}
		return ParameterTree.empty();
	}

	/**
	 * 获取声明的返回值 schema。
	 * @return 返回值 schema，未声明时返回 null
	 */
	default ReturnSchema getDeclaredReturnSchema() {
		CodeactToolDefinition definition = getCodeactDefinition();
		if (definition != null) {
			return definition.declaredReturnSchema();
		}
		return null;
	}

	// ============ ToolContext 便捷方法 ============

	/**
	 * 从 ToolContext 中获取 threadId。
	 *
	 * <p>threadId 来自 RunnableConfig.threadId()。
	 *
	 * @param toolContext 工具上下文
	 * @return threadId，不存在时返回 empty
	 */
	default Optional<String> getThreadId(@Nullable ToolContext toolContext) {
		return ToolContextHelper.getThreadId(toolContext);
	}

	/**
	 * 从 ToolContext 的 metadata 中获取指定值。
	 *
	 * <p>从 RunnableConfig.metadata(key) 获取。
	 *
	 * @param toolContext 工具上下文
	 * @param key metadata key
	 * @return 值，不存在时返回 empty
	 */
	default Optional<String> getFromMetadata(@Nullable ToolContext toolContext, String key) {
		return ToolContextHelper.getFromMetadata(toolContext, key);
	}

}

