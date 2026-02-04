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
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.extension.trigger.manager.TriggerManager;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerDefinition;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 列出触发器 CodeactTool 实现 - 获取所有触发器列表。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ListTriggersCodeactTool implements TriggerCodeactTool {

	private static final Logger log = LoggerFactory.getLogger(ListTriggersCodeactTool.class);

	@JsonIgnore
	private final TriggerManager triggerManager;

	private final CodeactToolMetadata codeactMetadata;

	private final ToolDefinition toolDefinition;

	private final CodeactToolDefinition codeactDefinition;

	@JsonIgnore
	private final ObjectMapper objectMapper;

	public ListTriggersCodeactTool(TriggerManager triggerManager) {
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
		log.debug("ListTriggersCodeactTool#call - reason=开始列出触发器");

		try {
			// 获取所有触发器
			List<TriggerDefinition> triggers = triggerManager.listAll();

			log.info("ListTriggersCodeactTool#call - reason=成功获取触发器列表, count={}", triggers.size());

			// 构建返回结果
			List<Map<String, Object>> triggerList = new ArrayList<>();
			for (TriggerDefinition trigger : triggers) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("id", trigger.getTriggerId());
				item.put("name", trigger.getName());
				item.put("description", trigger.getDescription());
				item.put("schedule_mode", trigger.getScheduleMode());
				item.put("status", trigger.getStatus());
				triggerList.add(item);
			}

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("triggers", triggerList);
			result.put("count", triggers.size());

			return objectMapper.writeValueAsString(result);
		}
		catch (Exception e) {
			log.error("ListTriggersCodeactTool#call - reason=列出触发器失败, error={}", e.getMessage(), e);

			try {
				Map<String, Object> errorResult = new LinkedHashMap<>();
				errorResult.put("success", false);
				errorResult.put("message", "Failed to list triggers: " + e.getMessage());
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
			.name("list_triggers")
			.description("列出所有触发器")
			.inputSchema("{\"type\":\"object\",\"properties\":{}}")
			.build();
	}

	private CodeactToolDefinition buildCodeactDefinition() {
		String inputSchema = toolDefinition.inputSchema();

		ParameterTree parameterTree = ParameterTree.builder().rawInputSchema(inputSchema).build();

		return DefaultCodeactToolDefinition.builder()
			.name("list_triggers")
			.description("列出所有触发器")
			.inputSchema(inputSchema)
			.parameterTree(parameterTree)
			.returnDescription("触发器列表")
			.returnTypeHint("Dict[str, Any]")
			.build();
	}

	private CodeactToolMetadata buildCodeactMetadata() {
		return DefaultCodeactToolMetadata.builder()
			.addSupportedLanguage(Language.PYTHON)
			.targetClassName("trigger_tools")
			.targetClassDescription("触发器管理工具集合")
			.addFewShot(new CodeExample("列出所有触发器",
					"result = list_triggers()\nfor trigger in result['triggers']:\n    print(f\"{trigger['name']}: {trigger['enabled']}\")",
					"打印所有触发器的名称和状态"))
			.displayName("列出触发器")
			.returnDirect(false)
			.build();
	}

}

