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
import com.alibaba.assistant.agent.core.model.ToolCallRecord;
import com.alibaba.assistant.agent.core.tool.schema.ReturnSchemaRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.List;

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
	 * 工具调用追踪记录
	 */
	private final List<ToolCallRecord> callTrace = new ArrayList<>();

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

			// 处理 null 或空字符串返回值
			boolean isEmptyResult = (result == null || result.isEmpty());
			if (isEmptyResult) {
				logger.warn("ToolRegistryBridge#callTool - reason=工具返回null或空字符串，使用默认空对象, toolName={}", toolName);
				result = "{}";
			}

			logger.info("ToolRegistryBridge#callTool - reason=工具调用成功，准备观测返回值, toolName={}, resultLength={}, isEmptyResult={}",
					toolName, result.length(), isEmptyResult);

			// 解析返回结果，检查repliedToUser标志
			boolean repliedToUser = checkRepliedToUser(result);

			// 记录工具调用到追踪列表（包含repliedToUser标志）
			recordToolCall(toolName, repliedToUser);

			// 观测返回值结构（仅当有实际数据时才观测，避免空返回值污染 schema）
			if (!isEmptyResult) {
				observeReturnSchema(toolName, result, true);
			} else {
				logger.debug("ToolRegistryBridge#callTool - reason=跳过schema观测因为返回值为空, toolName={}", toolName);
			}

			logger.info("ToolRegistryBridge#callTool - reason=工具调用完成, toolName={}, repliedToUser={}", toolName, repliedToUser);

			return result;
		}
		catch (Exception e) {
			logger.error("ToolRegistryBridge#callTool - reason=工具调用失败, toolName=" + toolName, e);
			String errorMessage = e.getMessage();
			if (errorMessage == null) {
				errorMessage = e.getClass().getSimpleName();
			}
			String errorResult = "{\"error\": \"" + errorMessage.replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\"}";

			// 记录工具调用（失败情况，repliedToUser=false）
			recordToolCall(toolName, false);

			// 观测错误返回值结构
			observeReturnSchema(toolName, errorResult, false);

			// 直接抛出异常，让Python层能够捕获并正确处理错误信息
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	/**
	 * 检查工具返回结果中是否包含repliedToUser=true标志。
	 * @param resultJson 返回值JSON
	 * @return 如果包含repliedToUser=true则返回true，否则返回false
	 */
	private boolean checkRepliedToUser(String resultJson) {
		if (resultJson == null || resultJson.isEmpty()) {
			return false;
		}
		try {
			// 简单的JSON解析检查repliedToUser字段
			// 使用简单的字符串匹配来避免引入额外依赖
			if (resultJson.contains("\"repliedToUser\"")) {
				// 更精确的检查：查找 "repliedToUser": true 或 "repliedToUser":true
				return resultJson.contains("\"repliedToUser\":true") ||
					   resultJson.contains("\"repliedToUser\": true") ||
					   resultJson.contains("\"repliedToUser\" : true");
			}
		} catch (Exception e) {
			logger.debug("ToolRegistryBridge#checkRepliedToUser - reason=解析repliedToUser标志失败, error={}", e.getMessage());
		}
		return false;
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

	/**
	 * 记录工具调用。
	 * @param toolName 工具名称
	 * @param repliedToUser 工具调用是否已回复用户
	 */
	private void recordToolCall(String toolName, boolean repliedToUser) {
		// 获取工具的targetClassName来构建完整的工具标识
		String toolIdentifier = toolName;
		try {
			CodeactTool tool = registry.getTool(toolName).orElse(null);
			if (tool != null && tool.getCodeactMetadata() != null) {
				String targetClassName = tool.getCodeactMetadata().targetClassName();
				if (targetClassName != null && !targetClassName.isEmpty()) {
					toolIdentifier = targetClassName + "." + toolName;
				}
			}
		} catch (Exception e) {
			logger.warn("ToolRegistryBridge#recordToolCall - reason=获取工具元数据失败, toolName={}", toolName);
		}

		ToolCallRecord record = new ToolCallRecord(callTrace.size() + 1, toolIdentifier, repliedToUser);
		callTrace.add(record);
		logger.info("ToolRegistryBridge#recordToolCall - reason=记录工具调用, order={}, tool={}, repliedToUser={}",
				record.getOrder(), record.getTool(), record.isRepliedToUser());
	}

	/**
	 * 获取工具调用追踪记录。
	 * @return 工具调用记录列表
	 */
	public List<ToolCallRecord> getCallTrace() {
		return new ArrayList<>(callTrace);
	}

	/**
	 * 获取已回复用户的工具调用追踪记录（replyToUserTrace）。
	 * @return 已回复用户的工具调用记录列表
	 */
	public List<ToolCallRecord> getReplyToUserTrace() {
		List<ToolCallRecord> replyToUserTrace = new ArrayList<>();
		for (ToolCallRecord record : callTrace) {
			if (record.isRepliedToUser()) {
				replyToUserTrace.add(record);
			}
		}
		return replyToUserTrace;
	}

	/**
	 * 清空工具调用追踪记录。
	 */
	public void clearCallTrace() {
		callTrace.clear();
		logger.debug("ToolRegistryBridge#clearCallTrace - reason=清空调用追踪记录");
	}

}

