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

import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.extension.dynamic.tool.AbstractDynamicCodeactTool;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;

import java.util.Map;

/**
 * MCP ToolCallback 适配器。
 *
 * <p>将 MCP Client Boot Starter 提供的 ToolCallback 适配为 CodeactTool，
 * 补齐 CodeactToolMetadata。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class McpToolCallbackAdapter extends AbstractDynamicCodeactTool {

	private static final Logger logger = LoggerFactory.getLogger(McpToolCallbackAdapter.class);

	/**
	 * 被适配的原始 ToolCallback。
	 */
	@JsonIgnore
	private final ToolCallback delegate;

	/**
	 * MCP 工具的原始名称（用于调用时传递给 delegate）。
	 */
	private final String rawToolName;

	/**
	 * 构造适配器。
	 *
	 * @param objectMapper ObjectMapper 实例
	 * @param delegate 被适配的 ToolCallback
	 * @param codeactMetadata CodeAct 元数据
	 */
	public McpToolCallbackAdapter(ObjectMapper objectMapper, ToolCallback delegate,
			CodeactToolMetadata codeactMetadata) {
		super(objectMapper, delegate.getToolDefinition(), codeactMetadata);
		this.delegate = delegate;
		this.rawToolName = delegate.getToolDefinition().name();
	}

	@Override
	protected String doCall(Map<String, Object> args, @Nullable ToolContext toolContext) throws Exception {
		logger.debug("McpToolCallbackAdapter#doCall - reason=委托给原始ToolCallback, rawToolName={}",
				rawToolName);

		// 将参数序列化回 JSON，传给 delegate
		String argsJson = objectMapper.writeValueAsString(args);

		// 调用原始 ToolCallback
		String result;
		if (toolContext != null) {
			result = delegate.call(argsJson, toolContext);
		}
		else {
			result = delegate.call(argsJson);
		}

        if (result.startsWith("[")) {
            JSONArray array = JSONArray.parseArray(result);
            if (!array.isEmpty()) {
                JSONObject data = array.getJSONObject(0);
                if (data.containsKey("text") && data.getString("text") != null && !data.getString("text").isEmpty()) {
                    result = data.getString("text");
                }
            }
        }

		logger.debug("McpToolCallbackAdapter#doCall - reason=原始ToolCallback调用完成, rawToolName={}, resultLength={}",
				rawToolName, result != null ? result.length() : 0);

		return result;
	}

	/**
	 * 获取原始工具名。
	 *
	 * @return 原始工具名
	 */
	public String getRawToolName() {
		return rawToolName;
	}

	/**
	 * 获取被适配的原始 ToolCallback。
	 *
	 * @return 原始 ToolCallback
	 */
	public ToolCallback getDelegate() {
		return delegate;
	}

}

