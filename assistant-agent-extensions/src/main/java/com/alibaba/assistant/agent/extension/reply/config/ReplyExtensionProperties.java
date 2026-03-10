package com.alibaba.assistant.agent.extension.reply.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 回复模块配置属性
 *
 * <p>Convention over Configuration: 默认提供一个 send_message 工具
 *
 * @author Assistant Agent Team
 */
@ConfigurationProperties(prefix = "spring.ai.alibaba.codeact.extension.reply")
public class ReplyExtensionProperties {

	/**
	 * 是否启用回复模块
	 */
	private boolean enabled = true;

	/**
	 * 是否允许@Bean配置覆盖YAML配置
	 */
	private boolean allowConfigOverride = true;

	/**
	 * 回复工具配置列表（默认提供 send_message 工具）
	 */
	private List<ReplyToolConfig> tools = createDefaultTools();

	/**
	 * 创建默认工具列表
	 */
	private static List<ReplyToolConfig> createDefaultTools() {
		List<ReplyToolConfig> defaultTools = new ArrayList<>();

		// 默认的 send_message 工具
		ReplyToolConfig sendMessage = new ReplyToolConfig();
		sendMessage.setToolName("send_message");
		sendMessage.setChannelCode("IDE_TEXT");
		sendMessage.setDescription("向用户发送消息");
		sendMessage.setReactEnabled(true);
		sendMessage.setCodeActEnabled(true);

		ReplyToolConfig.ParameterConfig textParam = new ReplyToolConfig.ParameterConfig();
		textParam.setName("text");
		textParam.setType("STRING");
		textParam.setRequired(true);
		textParam.setDescription("要发送的消息文本");
		sendMessage.setParameters(List.of(textParam));

		defaultTools.add(sendMessage);
		return defaultTools;
	}

	// Getters and Setters
	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isAllowConfigOverride() {
		return allowConfigOverride;
	}

	public void setAllowConfigOverride(boolean allowConfigOverride) {
		this.allowConfigOverride = allowConfigOverride;
	}

	public List<ReplyToolConfig> getTools() {
		return tools;
	}

	public void setTools(List<ReplyToolConfig> tools) {
		this.tools = tools;
	}

}

