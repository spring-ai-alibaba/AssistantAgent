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

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * ToolContext 辅助工具类。
 *
 * <p>提供从 ToolContext 中提取信息的便捷方法：
 * <ul>
 *   <li>threadId - 来自 RunnableConfig.threadId()</li>
 *   <li>其他字段 - 通过 getFromMetadata(key) 从 RunnableConfig.metadata 获取</li>
 * </ul>
 *
 * <p>数据链路说明：
 * <ol>
 *   <li>框架通过 ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY 将 RunnableConfig 注入到 ToolContext</li>
 *   <li>threadId 通过 RunnableConfig.threadId() 获取</li>
 *   <li>其他自定义字段通过 RunnableConfig.metadata(key) 获取</li>
 * </ol>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public final class ToolContextHelper {

	private ToolContextHelper() {
		// 禁止实例化
	}

	/**
	 * 从 ToolContext 中获取 threadId。
	 *
	 * <p>从 RunnableConfig.threadId() 获取。
	 *
	 * @param toolContext 工具上下文
	 * @return threadId，不存在时返回 empty
	 */
	public static Optional<String> getThreadId(@Nullable ToolContext toolContext) {
		return getRunnableConfig(toolContext)
				.flatMap(RunnableConfig::threadId);
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
	public static Optional<String> getFromMetadata(@Nullable ToolContext toolContext, String key) {
		return getRunnableConfig(toolContext)
				.flatMap(config -> config.metadata(key))
				.map(String::valueOf);
	}

	/**
	 * 从 ToolContext 的 metadata 中获取所有元数据。
	 *
	 * @param toolContext 工具上下文
	 * @return metadata Map，不存在时返回 empty
	 */
	public static Optional<Map<String, Object>> getAllMetadata(@Nullable ToolContext toolContext) {
		return getRunnableConfig(toolContext)
				.flatMap(RunnableConfig::metadata);
	}

	/**
	 * 从 ToolContext 中获取 RunnableConfig 对象。
	 *
	 * @param toolContext 工具上下文
	 * @return RunnableConfig 对象，不存在时返回 empty
	 */
	public static Optional<RunnableConfig> getRunnableConfig(@Nullable ToolContext toolContext) {
		if (toolContext == null || toolContext.getContext() == null) {
			return Optional.empty();
		}
		Object config = toolContext.getContext().get(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY);
		if (config instanceof RunnableConfig runnableConfig) {
			return Optional.of(runnableConfig);
		}
		return Optional.empty();
	}

	/**
	 * 从 ToolContext 中获取 OverAllState 对象。
	 *
	 * @param toolContext 工具上下文
	 * @return OverAllState 对象，不存在时返回 empty
	 */
	public static Optional<Object> getAgentState(@Nullable ToolContext toolContext) {
		if (toolContext == null || toolContext.getContext() == null) {
			return Optional.empty();
		}
		Object state = toolContext.getContext().get(ToolContextConstants.AGENT_STATE_CONTEXT_KEY);
		return Optional.ofNullable(state);
	}


}

