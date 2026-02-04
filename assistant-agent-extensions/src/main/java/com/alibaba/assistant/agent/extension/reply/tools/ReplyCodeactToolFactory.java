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
package com.alibaba.assistant.agent.extension.reply.tools;

import com.alibaba.assistant.agent.common.tools.ReplyCodeactTool;
import com.alibaba.assistant.agent.extension.reply.config.ReplyToolConfig;
import com.alibaba.assistant.agent.extension.reply.model.ParameterSchema;
import com.alibaba.assistant.agent.extension.reply.spi.ReplyChannelDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reply CodeactTool 工厂类。
 *
 * <p>负责根据配置创建 ReplyCodeactTool 实例。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ReplyCodeactToolFactory {

	private static final Logger log = LoggerFactory.getLogger(ReplyCodeactToolFactory.class);

	private final Map<String, ReplyChannelDefinition> channelMap;

	/**
	 * 构造工厂实例。
	 *
	 * @param channelDefinitions 渠道定义列表
	 */
	public ReplyCodeactToolFactory(List<ReplyChannelDefinition> channelDefinitions) {
		this.channelMap = new HashMap<>();
		for (ReplyChannelDefinition channel : channelDefinitions) {
			channelMap.put(channel.getChannelCode(), channel);
		}
		log.info("ReplyCodeactToolFactory#init - reason=工厂初始化完成, channels={}", channelMap.size());
	}

	/**
	 * 根据配置列表创建 ReplyCodeactTool 列表。
	 *
	 * @param configs 工具配置列表
	 * @return ReplyCodeactTool 列表
	 */
	public List<ReplyCodeactTool> createTools(List<ReplyToolConfig> configs) {
		List<ReplyCodeactTool> tools = new ArrayList<>();

		for (ReplyToolConfig config : configs) {
			try {
				ReplyCodeactTool tool = createTool(config);
				if (tool != null) {
					tools.add(tool);
					log.info("ReplyCodeactToolFactory#createTools - reason=创建工具成功, toolName={}, channelCode={}",
							config.getToolName(), config.getChannelCode());
				}
			}
			catch (Exception e) {
				log.error("ReplyCodeactToolFactory#createTools - reason=创建工具失败, toolName={}, error={}",
						config.getToolName(), e.getMessage(), e);
			}
		}

		log.info("ReplyCodeactToolFactory#createTools - reason=工具创建完成, totalCount={}", tools.size());

		return tools;
	}

	/**
	 * 根据单个配置创建 ReplyCodeactTool。
	 *
	 * @param config 工具配置
	 * @return ReplyCodeactTool 实例
	 */
	public ReplyCodeactTool createTool(ReplyToolConfig config) {
		// 查找对应的渠道定义
		ReplyChannelDefinition channel = channelMap.get(config.getChannelCode());
		if (channel == null) {
			log.error("ReplyCodeactToolFactory#createTool - reason=渠道不存在, channelCode={}", config.getChannelCode());
			return null;
		}

		// 构建参数模式
		ParameterSchema parameterSchema = buildParameterSchema(config, channel);

		// 确定渠道类型
		ReplyCodeactTool.ReplyChannelType channelType = determineChannelType(config);

		// 创建工具实例
		return new BaseReplyCodeactTool(config.getToolName(), config.getDescription(), channel, config, parameterSchema,
				channelType);
	}

	/**
	 * 构建参数模式。
	 * <p>优先使用配置中的参数定义，如果配置中没有，则从渠道定义中获取。
	 */
	private ParameterSchema buildParameterSchema(ReplyToolConfig config, ReplyChannelDefinition channel) {
		// 1. 优先使用配置中的参数定义
		if (config.getParameters() != null && !config.getParameters().isEmpty()) {
			ParameterSchema.Builder builder = ParameterSchema.builder();
			for (ReplyToolConfig.ParameterConfig paramConfig : config.getParameters()) {
				ParameterSchema.ParameterType type = convertToParameterType(paramConfig.getType());
				ParameterSchema.ParameterDef def = new ParameterSchema.ParameterDef(
						paramConfig.getName(),
						type,
						paramConfig.isRequired(),
						paramConfig.getDescription()
				);
				def.setDefaultValue(paramConfig.getDefaultValue());
				def.setEnumValues(paramConfig.getEnumValues());
				builder.parameter(def);
			}
			log.debug("ReplyCodeactToolFactory#buildParameterSchema - reason=使用配置中的参数定义, toolName={}, paramCount={}",
					config.getToolName(), config.getParameters().size());
			return builder.build();
		}

		// 2. 如果配置中没有参数定义，从渠道定义中获取
		ParameterSchema channelSchema = channel.getSupportedParameters();
		if (channelSchema != null) {
			log.debug("ReplyCodeactToolFactory#buildParameterSchema - reason=使用渠道定义的参数, toolName={}, channelCode={}",
					config.getToolName(), config.getChannelCode());
			return channelSchema;
		}

		// 3. 如果都没有，返回空的参数模式
		log.warn("ReplyCodeactToolFactory#buildParameterSchema - reason=未找到参数定义将使用空参数模式, toolName={}, channelCode={}",
				config.getToolName(), config.getChannelCode());
		return ParameterSchema.builder().build();
	}

	/**
	 * 将字符串类型转换为 ParameterType。
	 */
	private ParameterSchema.ParameterType convertToParameterType(String type) {
		if (type == null) {
			return ParameterSchema.ParameterType.STRING;
		}
		String lowerType = type.toLowerCase();
		switch (lowerType) {
			case "integer":
			case "int":
			case "long":
				return ParameterSchema.ParameterType.INTEGER;
			case "boolean":
			case "bool":
				return ParameterSchema.ParameterType.BOOLEAN;
			case "array":
			case "list":
				return ParameterSchema.ParameterType.ARRAY;
			case "object":
			case "map":
				return ParameterSchema.ParameterType.OBJECT;
			case "string":
			default:
				return ParameterSchema.ParameterType.STRING;
		}
	}

	/**
	 * 确定渠道类型。
	 */
	private ReplyCodeactTool.ReplyChannelType determineChannelType(ReplyToolConfig config) {
		// 可以从配置中读取，或根据渠道代码推断
		// 这里简单返回 PRIMARY，实际应该从配置中获取
		return ReplyCodeactTool.ReplyChannelType.PRIMARY;
	}

}

