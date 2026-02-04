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
package com.alibaba.assistant.agent.extension.dynamic.mcp;

import org.springframework.ai.tool.ToolCallback;

/**
 * 服务器感知的 MCP 工具回调接口。
 *
 * <p>实现此接口的 ToolCallback 可以提供 MCP Server 的元数据信息，
 * 使得 {@link McpDynamicToolFactory} 能够正确地将工具分组到不同的类中。
 *
 * <p>使用场景：
 * <ul>
 *   <li>多 MCP Server 环境下，每个 Server 的工具需要归属到不同的 Python 类</li>
 *   <li>需要自定义 Server 显示名称或描述</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface McpServerAwareToolCallback extends ToolCallback {

	/**
	 * 获取 MCP Server 名称。
	 *
	 * <p>此名称将用于生成 Python 类名（经过归一化处理）。
	 * 例如 "server-center" 会被转换为 "ServerCenter" 类。
	 *
	 * @return MCP Server 名称，不能为 null
	 */
	String getServerName();

	/**
	 * 获取 MCP Server 显示名称（可选）。
	 *
	 * <p>此名称用于生成类的描述文档。如果返回 null，将使用 serverName。
	 *
	 * @return MCP Server 显示名称，可以为 null
	 */
	default String getServerDisplayName() {
		return getServerName();
	}

	/**
	 * 获取原始工具名称。
	 *
	 * <p>MCP 协议中定义的原始工具名称，如 "getCodeInfo"、"searchApplications" 等。
	 *
	 * @return 原始工具名称，不能为 null
	 */
	String getToolName();

}

