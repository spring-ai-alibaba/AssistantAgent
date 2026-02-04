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
package com.alibaba.assistant.agent.core.model;

import java.io.Serializable;

/**
 * 工具调用记录，用于追踪代码执行过程中调用的工具。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ToolCallRecord implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 调用顺序（从1开始）
	 */
	private int order;

	/**
	 * 工具名称（格式：targetClassName.methodName 或 直接 toolName）
	 */
	private String tool;

	public ToolCallRecord() {
	}

	public ToolCallRecord(int order, String tool) {
		this.order = order;
		this.tool = tool;
	}

	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public String getTool() {
		return tool;
	}

	public void setTool(String tool) {
		this.tool = tool;
	}

	@Override
	public String toString() {
		return "{\"order\": " + order + ", \"tool\": \"" + tool + "\"}";
	}
}

