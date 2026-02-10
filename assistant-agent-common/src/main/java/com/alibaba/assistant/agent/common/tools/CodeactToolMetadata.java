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

import com.alibaba.assistant.agent.common.enums.Language;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Collections;
import java.util.List;

/**
 * CodeAct 工具扩展元数据接口 - 扩展 Spring AI 的 ToolMetadata。
 *
 * <p>在 returnDirect 基础上，提供代码生成阶段所需的元信息：
 * <ul>
 * <li>语言支持列表</li>
 * <li>工具分组（类名/命名空间）</li>
 * <li>Few-shot 示例</li>
 * <li>显示名称与别名</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface CodeactToolMetadata extends ToolMetadata {

	// ============ 继承自 ToolMetadata ============
	// boolean returnDirect();

	// ============ 运行时支持 ============

	/**
	 * 获取该工具支持的目标语言列表。
	 * @return 支持的语言列表
	 */
	List<Language> supportedLanguages();

	// ============ 工具分组（Grouping）============

	/**
	 * 获取工具所属的类/模块/命名空间名称。
	 *
	 * <p>用于在 prompt 中将工具按类分组展示。 例如：Python 下的 "search_tools"、"reply_tools"
	 * @return 目标类名，如果是全局工具则返回 null 或空字符串
	 */
	String targetClassName();

	/**
	 * 获取目标类/模块的自然语言描述。
	 * @return 目标类描述
	 */
	String targetClassDescription();

	// ============ Few-shot 示例 ============

	/**
	 * 获取 few-shot 示例列表。
	 * @return few-shot 示例列表
	 */
	List<CodeExample> fewShots();

	// ============ 显示名与别名 ============

	/**
	 * 获取工具的显示名称（用于 UI 或日志）。
	 * @return 显示名称，默认返回 null 表示使用工具名
	 */
	default String displayName() {
		return null;
	}

	/**
	 * 获取工具的别名列表。
	 * @return 别名列表
	 */
	default List<String> aliases() {
		return Collections.emptyList();
	}

	// ============ 基础工具标识 ============

	/**
	 * 工具是否始终可用
	 *
	 * <p>标记为 {@code true} 的工具是基础工具，无论前置环节筛选出哪些业务工具，
	 * 这些基础工具都会被包含在最终的可用工具列表中。
	 *
	 * <p>典型的基础工具包括：
	 * <ul>
	 *   <li>reply_tools（用户回复工具）- Agent 必须能够回复用户</li>
	 *   <li>其他交互必需的工具</li>
	 * </ul>
	 *
	 * @return {@code true} 表示始终可用，{@code false} 表示受前置筛选影响（默认）
	 */
	default boolean alwaysAvailable() {
		return false;
	}

	// ============ 废弃方法（保留兼容性）============

	/**
	 * 获取在目标语言下的代码调用签名模板。
	 *
	 * @return 代码调用签名模板
	 * @deprecated 使用 {@link com.alibaba.assistant.agent.common.tools.definition.ParameterTree}
	 * 替代， 从 CodeactToolDefinition.parameterTree() 获取结构化参数信息
	 */
	@Deprecated
	default String codeInvocationTemplate() {
		return null;
	}

	// ============ 构建器 ============

	/**
	 * 创建构建器实例。
	 * @return 构建器
	 */
	static DefaultCodeactToolMetadata.Builder builder() {
		return DefaultCodeactToolMetadata.builder();
	}

}
