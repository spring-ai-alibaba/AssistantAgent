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
import com.alibaba.assistant.agent.extension.trigger.model.ScheduleMode;
import com.alibaba.assistant.agent.extension.trigger.model.SourceType;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerDefinition;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订阅触发器 CodeactTool 实现 - 创建和激活新的触发器。
 *
 * <p>将现有的 SubscribeTriggerTool 改造为实现 TriggerCodeactTool 接口，
 * 支持 React 和 CodeAct 两个阶段。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SubscribeTriggerCodeactTool implements TriggerCodeactTool {

	private static final Logger log = LoggerFactory.getLogger(SubscribeTriggerCodeactTool.class);

	@JsonIgnore
	private final TriggerManager triggerManager;

	private final CodeactToolMetadata codeactMetadata;

	private final ToolDefinition toolDefinition;

	private final CodeactToolDefinition codeactDefinition;

	@JsonIgnore
	private final ObjectMapper objectMapper;

	/**
	 * 构造 Trigger CodeactTool 实例。
	 * @param triggerManager 触发器管理器
	 */
	public SubscribeTriggerCodeactTool(TriggerManager triggerManager) {
		this.triggerManager = triggerManager;
		this.objectMapper = new ObjectMapper();

		// 构建 ToolDefinition
		this.toolDefinition = buildToolDefinition();

		// 构建 CodeactToolDefinition（包含 ParameterTree）
		this.codeactDefinition = buildCodeactDefinition();

		// 构建 CodeactToolMetadata
		this.codeactMetadata = buildCodeactMetadata();
	}

	@Override
	public String call(String toolInput) {
		return call(toolInput, null);
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		log.debug("SubscribeTriggerCodeactTool#call - reason=开始创建触发器, toolInput={}", toolInput);

		try {
			// 解析 JSON 输入参数
			Map<String, Object> params = objectMapper.readValue(toolInput, Map.class);

			// 构建触发器定义
			TriggerDefinition definition = buildTriggerDefinition(params);

			// 调用 TriggerManager 订阅触发器
			String triggerId = triggerManager.subscribe(definition);

			log.info("SubscribeTriggerCodeactTool#call - reason=触发器创建成功, triggerId={}", triggerId);

			// 返回结果
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("trigger_id", triggerId);
			result.put("message", "Trigger created successfully");

			return objectMapper.writeValueAsString(result);
		}
		catch (Exception e) {
			log.error("SubscribeTriggerCodeactTool#call - reason=触发器创建失败, error={}", e.getMessage(), e);

			try {
				Map<String, Object> errorResult = new LinkedHashMap<>();
				errorResult.put("success", false);
				errorResult.put("message", "Failed to create trigger: " + e.getMessage());
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
		// 订阅触发器通常是用户手动触发的操作
		return TriggerSourceType.MANUAL;
	}

	/**
	 * 构建 ToolDefinition。
	 */
	private ToolDefinition buildToolDefinition() {
		String inputSchema = """
				{
				    "type": "object",
				    "properties": {
				        "name": {
				            "type": "string",
				            "description": "触发器名称"
				        },
				        "description": {
				            "type": "string",
				            "description": "触发器描述"
				        },
				        "schedule_mode": {
				            "type": "string",
				            "description": "调度模式: CRON, FIXED_DELAY, FIXED_RATE, ONE_TIME, TRIGGER",
				            "enum": ["CRON", "FIXED_DELAY", "FIXED_RATE", "ONE_TIME", "TRIGGER"]
				        },
				        "schedule_value": {
				            "type": "string",
				            "description": "调度值：CRON模式为cron表达式；FIXED_DELAY/FIXED_RATE模式为毫秒数(如3分钟=180000)；ONE_TIME模式为ISO时间戳"
				        },
				        "condition_function": {
				            "type": "string",
				            "description": "条件判断函数代码"
				        },
				        "execute_function": {
				            "type": "string",
				            "description": "执行函数代码"
				        }
				    },
				    "required": ["name", "schedule_mode", "schedule_value"]
				}
				""";

		return ToolDefinition.builder()
			.name("subscribe_trigger")
			.description("创建并订阅一个新的触发器")
			.inputSchema(inputSchema)
			.build();
	}

	/**
	 * 构建 CodeactToolDefinition（包含结构化 ParameterTree）。
	 */
	private CodeactToolDefinition buildCodeactDefinition() {
		String inputSchema = toolDefinition.inputSchema();

		// 构建 ParameterTree
		ParameterTree parameterTree = ParameterTree.builder()
			.rawInputSchema(inputSchema)
			.addParameter(ParameterNode.builder()
				.name("name")
				.type(ParameterType.STRING)
				.description("触发器名称")
				.required(true)
				.build())
			.addParameter(ParameterNode.builder()
				.name("description")
				.type(ParameterType.STRING)
				.description("触发器描述")
				.required(false)
				.build())
			.addParameter(ParameterNode.builder()
				.name("schedule_mode")
				.type(ParameterType.STRING)
				.description("调度模式: CRON, FIXED_DELAY, FIXED_RATE, ONE_TIME, TRIGGER")
				.required(true)
				.enumValues(Arrays.asList("CRON", "FIXED_DELAY", "FIXED_RATE", "ONE_TIME", "TRIGGER"))
				.build())
			.addParameter(ParameterNode.builder()
				.name("schedule_value")
				.type(ParameterType.STRING)
				.description("调度值：CRON模式为cron表达式；FIXED_DELAY/FIXED_RATE模式为毫秒数(如3分钟=180000)；ONE_TIME模式为ISO时间戳")
				.required(true)
				.build())
			.addParameter(ParameterNode.builder()
				.name("condition_function")
				.type(ParameterType.STRING)
				.description("条件判断函数代码")
				.required(false)
				.build())
			.addParameter(ParameterNode.builder()
				.name("execute_function")
				.type(ParameterType.STRING)
				.description("执行函数代码")
				.required(false)
				.build())
			.addRequiredName("name")
			.addRequiredName("schedule_mode")
			.addRequiredName("schedule_value")
			.build();

		return DefaultCodeactToolDefinition.builder()
			.name("subscribe_trigger")
			.description("创建并订阅一个新的触发器")
			.inputSchema(inputSchema)
			.parameterTree(parameterTree)
			.returnDescription("创建结果，包含触发器 ID")
			.returnTypeHint("Dict[str, Any]")
			.build();
	}

	/**
	 * 构建 CodeactToolMetadata。
	 */
	private CodeactToolMetadata buildCodeactMetadata() {
		return DefaultCodeactToolMetadata.builder()
			.addSupportedLanguage(Language.PYTHON)
			.targetClassName("trigger_tools")
			.targetClassDescription("触发器管理工具集合")
			.addFewShot(new CodeExample("创建一个每天执行的触发器", """
					result = trigger_tools.subscribe_trigger(
					    name="daily_task",
					    schedule_mode="CRON",
					    schedule_value="0 0 * * *",
					    execute_function="print('Daily execution')"
					)
					print(f"Trigger ID: {result['trigger_id']}")
					""", "创建一个每天午夜执行的触发器，并打印触发器 ID"))
			.addFewShot(new CodeExample("创建一个3分钟后提醒的触发器", """
					# 3分钟 = 3 * 60 * 1000 = 180000毫秒
					delay_ms = 3 * 60 * 1000  # 分钟 * 秒 * 毫秒
					result = trigger_tools.subscribe_trigger(
					    name="reminder_3min",
					    schedule_mode="FIXED_DELAY",
					    schedule_value=str(delay_ms),
					    execute_function="reply_tools.send_message('时间到了！')"
					)
					""", "使用FIXED_DELAY模式，schedule_value单位是毫秒，可用 分钟*60*1000 计算"))
			.displayName("订阅触发器")
			.returnDirect(true)
			.build();
	}

	/**
	 * 从参数构建 TriggerDefinition。
	 */
	private TriggerDefinition buildTriggerDefinition(Map<String, Object> params) {
		TriggerDefinition definition = new TriggerDefinition();

		// 基本信息
		definition.setName((String) params.get("name"));
		definition.setDescription((String) params.getOrDefault("description", ""));

		// 来源信息
		definition.setSourceType(SourceType.USER);
		definition.setSourceId((String) params.getOrDefault("source_id", ""));
		definition.setCreatedBy((String) params.getOrDefault("created_by", ""));

		// 调度信息
		String scheduleMode = (String) params.get("schedule_mode");
		definition.setScheduleMode(ScheduleMode.valueOf(scheduleMode.toUpperCase()));
		definition.setScheduleValue((String) params.get("schedule_value"));

		// 执行信息
		definition.setConditionFunction((String) params.getOrDefault("condition_function", ""));
		definition.setExecuteFunction((String) params.getOrDefault("execute_function", ""));
		definition.setParameters((Map<String, Object>) params.getOrDefault("parameters", new HashMap<>()));

		// 上下文信息
		definition.setSessionSnapshotId((String) params.getOrDefault("session_snapshot_id", ""));
		definition.setGraphName((String) params.getOrDefault("graph_name", ""));
		definition.setAgentName((String) params.getOrDefault("agent_name", ""));

		// 重试配置
		if (params.containsKey("max_retries")) {
			definition.setMaxRetries(((Number) params.get("max_retries")).intValue());
		}
		if (params.containsKey("retry_delay")) {
			definition.setRetryDelay(((Number) params.get("retry_delay")).longValue());
		}

		return definition;
	}

}

