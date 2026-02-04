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
package com.alibaba.assistant.agent.extension.trigger.tools;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeExample;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.DefaultCodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.TriggerCodeactTool;
import com.alibaba.assistant.agent.common.tools.definition.CodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.DefaultCodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.ParameterNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.common.tools.definition.ParameterType;
import com.alibaba.assistant.agent.extension.trigger.manager.TriggerManager;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerDefinition;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 获取触发器详情 CodeactTool 实现 - 获取指定触发器的详细信息。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class GetTriggerDetailCodeactTool implements TriggerCodeactTool {

	private static final Logger log = LoggerFactory.getLogger(GetTriggerDetailCodeactTool.class);

	@JsonIgnore
	private final TriggerManager triggerManager;

	private final CodeactToolMetadata codeactMetadata;

	private final ToolDefinition toolDefinition;

	private final CodeactToolDefinition codeactDefinition;

	@JsonIgnore
	private final ObjectMapper objectMapper;

	public GetTriggerDetailCodeactTool(TriggerManager triggerManager) {
		this.triggerManager = triggerManager;
		this.objectMapper = new ObjectMapper();
		this.toolDefinition = buildToolDefinition();
		this.codeactDefinition = buildCodeactDefinition();
		this.codeactMetadata = buildCodeactMetadata();
	}

	@Override
	public String call(String toolInput) {
		return call(toolInput, null);
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		log.debug("GetTriggerDetailCodeactTool#call - reason=开始获取触发器详情, toolInput={}", toolInput);

		try {
			Map<String, Object> params = objectMapper.readValue(toolInput, Map.class);
			String triggerId = (String) params.get("trigger_id");

			if (triggerId == null || triggerId.isEmpty()) {
				throw new IllegalArgumentException("trigger_id is required");
			}

			// 获取触发器详情
			Optional<TriggerDefinition> triggerOpt = triggerManager.getDetail(triggerId);

			if (!triggerOpt.isPresent()) {
				Map<String, Object> notFoundResult = new LinkedHashMap<>();
				notFoundResult.put("success", false);
				notFoundResult.put("message", "Trigger not found: " + triggerId);
				return objectMapper.writeValueAsString(notFoundResult);
			}

			TriggerDefinition trigger = triggerOpt.get();
			log.info("GetTriggerDetailCodeactTool#call - reason=成功获取触发器详情, triggerId={}", triggerId);

			// 构建详细信息
			Map<String, Object> detail = new LinkedHashMap<>();
			detail.put("id", trigger.getTriggerId());
			detail.put("name", trigger.getName());
			detail.put("description", trigger.getDescription());
			detail.put("schedule_mode", trigger.getScheduleMode());
			detail.put("schedule_value", trigger.getScheduleValue());
			detail.put("status", trigger.getStatus());
			detail.put("condition_function", trigger.getConditionFunction());
			detail.put("execute_function", trigger.getExecuteFunction());
			detail.put("created_at", trigger.getCreatedAt());

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("trigger", detail);

			return objectMapper.writeValueAsString(result);
		}
		catch (Exception e) {
			log.error("GetTriggerDetailCodeactTool#call - reason=获取触发器详情失败, error={}", e.getMessage(), e);

			try {
				Map<String, Object> errorResult = new LinkedHashMap<>();
				errorResult.put("success", false);
				errorResult.put("message", "Failed to get trigger detail: " + e.getMessage());
				return objectMapper.writeValueAsString(errorResult);
			}
			catch (Exception ex) {
				return "{\"success\":false,\"message\":\"Failed to serialize error result\"}";
			}
		}
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return toolDefinition;
	}

	@Override
	public CodeactToolDefinition getCodeactDefinition() {
		return codeactDefinition;
	}

	@Override
	public CodeactToolMetadata getCodeactMetadata() {
		return codeactMetadata;
	}

	@Override
	public TriggerSourceType getSourceType() {
		return TriggerSourceType.MANUAL;
	}

	private ToolDefinition buildToolDefinition() {
		return ToolDefinition.builder()
			.name("get_trigger_detail")
			.description("获取指定触发器的详细信息")
			.inputSchema("""
				{
				    "type": "object",
				    "properties": {
				        "trigger_id": {
				            "type": "string",
				            "description": "触发器 ID"
				        }
				    },
				    "required": ["trigger_id"]
				}
				""")
			.build();
	}

	private CodeactToolDefinition buildCodeactDefinition() {
		String inputSchema = toolDefinition.inputSchema();

		ParameterTree parameterTree = ParameterTree.builder()
			.rawInputSchema(inputSchema)
			.addParameter(ParameterNode.builder()
				.name("trigger_id")
				.type(ParameterType.STRING)
				.description("触发器 ID")
				.required(true)
				.build())
			.addRequiredName("trigger_id")
			.build();

		return DefaultCodeactToolDefinition.builder()
			.name("get_trigger_detail")
			.description("获取指定触发器的详细信息")
			.inputSchema(inputSchema)
			.parameterTree(parameterTree)
			.returnDescription("触发器详细信息")
			.returnTypeHint("Dict[str, Any]")
			.build();
	}

	private CodeactToolMetadata buildCodeactMetadata() {
		return DefaultCodeactToolMetadata.builder()
			.addSupportedLanguage(Language.PYTHON)
			.targetClassName("trigger_tools")
			.targetClassDescription("触发器管理工具集合")
			.addFewShot(new CodeExample("获取触发器详情",
					"result = get_trigger_detail(trigger_id=\"trigger_123\")\nif result['success']:\n    print(result['trigger']['name'])",
					"获取并打印触发器名称"))
			.displayName("获取触发器详情")
			.returnDirect(false)
			.build();
	}

}

