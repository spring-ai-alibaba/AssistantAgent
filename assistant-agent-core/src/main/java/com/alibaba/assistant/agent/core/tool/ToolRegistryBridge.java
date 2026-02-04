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
package com.alibaba.assistant.agent.core.tool;

import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.core.tool.schema.ReturnSchemaRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

/**
 * ToolRegistry Bridge - 供 Python 调用的 Java 对象。
 *
 * <p>这个类被注入到 GraalVM Python 环境中，Python 代码通过调用它来执行 CodeactTool。
 * 同时负责在工具调用完成后触发返回值结构的观测。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ToolRegistryBridge {

	private static final Logger logger = LoggerFactory.getLogger(ToolRegistryBridge.class);

	private final CodeactToolRegistry registry;

	private final ToolContext toolContext;

	/**
	 * 构造函数。
	 * @param registry 工具注册表
	 * @param toolContext 工具上下文
	 */
	public ToolRegistryBridge(CodeactToolRegistry registry, ToolContext toolContext) {
		this.registry = registry;
		this.toolContext = toolContext;
		logger.debug("ToolRegistryBridge#<init> - reason=Bridge对象创建完成");
	}

	/**
	 * 调用工具 - 供 Python 代码调用。
	 * @param toolName 工具名称
	 * @param argsJson 参数（JSON 字符串）
	 * @return 工具执行结果（JSON 字符串）
	 */
	public String callTool(String toolName, String argsJson) {
		logger.info("ToolRegistryBridge#callTool - reason=Python调用工具开始, toolName={}, argsJsonLength={}, hasToolContext={}, toolContextKeys={}",
				toolName, argsJson != null ? argsJson.length() : 0,
				toolContext != null,
				toolContext != null && toolContext.getContext() != null ? toolContext.getContext().keySet() : "null");

		try {
			// 从注册表获取工具
			CodeactTool tool = registry.getTool(toolName)
				.orElseThrow(() -> new IllegalArgumentException("Tool not found: " + toolName));

			// 调用工具
			String result = tool.call(argsJson, toolContext);

			logger.info("ToolRegistryBridge#callTool - reason=工具调用成功，准备观测返回值, toolName={}, resultLength={}",
					toolName, result != null ? result.length() : 0);

			// 观测返回值结构
			observeReturnSchema(toolName, result, true);

			logger.info("ToolRegistryBridge#callTool - reason=工具调用完成, toolName={}", toolName);

			return result;
		}
		catch (Exception e) {
			logger.error("ToolRegistryBridge#callTool - reason=工具调用失败, toolName=" + toolName, e);
			String errorResult = "{\"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}";

			// 观测错误返回值结构
			observeReturnSchema(toolName, errorResult, false);

			return errorResult;
		}
	}

	/**
	 * 观测工具返回值结构。
	 * @param toolName 工具名称
	 * @param resultJson 返回值 JSON
	 * @param success 是否成功
	 */
	private void observeReturnSchema(String toolName, String resultJson, boolean success) {
		try {
			ReturnSchemaRegistry schemaRegistry = registry.getReturnSchemaRegistry();
			if (schemaRegistry != null) {
				logger.info("ToolRegistryBridge#observeReturnSchema - reason=开始观测返回值结构, registryHashCode={}, toolName={}, success={}, resultJsonLength={}",
						System.identityHashCode(schemaRegistry), toolName, success, resultJson != null ? resultJson.length() : 0);
				schemaRegistry.observe(toolName, resultJson, success);
				logger.info("ToolRegistryBridge#observeReturnSchema - reason=观测工具返回值结构完成, toolName={}, success={}",
						toolName, success);
			}
			else {
				logger.warn("ToolRegistryBridge#observeReturnSchema - reason=ReturnSchemaRegistry为null，跳过观测, toolName={}",
						toolName);
			}
		}
		catch (Exception e) {
			// 观测失败不影响工具调用结果
			logger.warn("ToolRegistryBridge#observeReturnSchema - reason=观测返回值结构失败, toolName={}, error={}", toolName,
					e.getMessage(), e);
		}
	}

}

