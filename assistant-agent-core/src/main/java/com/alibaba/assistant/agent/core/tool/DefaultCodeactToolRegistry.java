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
import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.definition.CodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.DefaultCodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.common.tools.definition.ReturnSchema;
import com.alibaba.assistant.agent.core.tool.definition.ToolDefinitionParser;
import com.alibaba.assistant.agent.core.tool.schema.DefaultReturnSchemaRegistry;
import com.alibaba.assistant.agent.core.tool.schema.ReturnSchemaRegistry;
import com.alibaba.assistant.agent.core.tool.view.PythonToolViewRenderer;
import com.alibaba.assistant.agent.core.tool.view.ToolViewRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * CodeactTool 注册表的默认实现 - 新机制。
 *
 * <p>使用线程安全的 ConcurrentHashMap 存储工具，并支持结构化工具定义。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class DefaultCodeactToolRegistry implements CodeactToolRegistry {

	private static final Logger log = LoggerFactory.getLogger(DefaultCodeactToolRegistry.class);

	private final Map<String, CodeactTool> tools = new ConcurrentHashMap<>();

	private final Map<String, String> aliasToName = new ConcurrentHashMap<>();

	private final Map<String, CodeactToolDefinition> toolDefinitions = new ConcurrentHashMap<>();

	private final ReturnSchemaRegistry returnSchemaRegistry;

	private final ToolViewRenderer pythonRenderer = new PythonToolViewRenderer();

	/**
	 * 使用默认的 ReturnSchemaRegistry 创建注册表。
	 */
	public DefaultCodeactToolRegistry() {
		this(new DefaultReturnSchemaRegistry());
	}

	/**
	 * 使用指定的 ReturnSchemaRegistry 创建注册表。
	 *
	 * @param returnSchemaRegistry 返回值 schema 注册表（通常是 Spring Bean 单例）
	 */
	public DefaultCodeactToolRegistry(ReturnSchemaRegistry returnSchemaRegistry) {
		this.returnSchemaRegistry = returnSchemaRegistry;
		log.debug("DefaultCodeactToolRegistry#<init> - reason=创建注册表, returnSchemaRegistry={}",
				returnSchemaRegistry.getClass().getSimpleName());
	}

	@Override
	public void register(CodeactTool tool) {
		String toolName = tool.getToolDefinition().name();
		tools.put(toolName, tool);

		// 注册别名
		List<String> aliases = tool.getCodeactMetadata().aliases();
		if (aliases != null) {
			for (String alias : aliases) {
				aliasToName.put(alias, toolName);
			}
		}

		// 解析并缓存结构化定义
		CodeactToolDefinition structuredDef = resolveToolDefinition(tool);
		toolDefinitions.put(toolName, structuredDef);

		// 注册声明的返回值 schema
		ReturnSchema declaredSchema = tool.getDeclaredReturnSchema();
		if (declaredSchema != null) {
			returnSchemaRegistry.registerDeclared(toolName, declaredSchema);
		}

		log.info("DefaultCodeactToolRegistry#register - reason=工具注册成功, name={}", toolName);
	}

	/**
	 * 解析工具的结构化定义。
	 */
	private CodeactToolDefinition resolveToolDefinition(CodeactTool tool) {
		// 如果工具已经提供了 CodeactToolDefinition，直接使用
		CodeactToolDefinition codeactDef = tool.getCodeactDefinition();
		if (codeactDef != null) {
			return codeactDef;
		}

		// 否则从 ToolDefinition 自动构建
		org.springframework.ai.tool.definition.ToolDefinition springDef = tool.getToolDefinition();
		String inputSchema = springDef.inputSchema();

		// 解析参数树
		ParameterTree parameterTree = ToolDefinitionParser.parse(inputSchema);

		return DefaultCodeactToolDefinition.builder()
			.name(springDef.name())
			.description(springDef.description())
			.inputSchema(inputSchema)
			.parameterTree(parameterTree)
			.build();
	}

	@Override
	public Optional<CodeactTool> getTool(String name) {
		return Optional.ofNullable(tools.get(name));
	}

	@Override
	public Optional<CodeactTool> getToolByAlias(String alias) {
		String toolName = aliasToName.get(alias);
		if (toolName != null) {
			return getTool(toolName);
		}
		return Optional.empty();
	}

	@Override
	public List<CodeactTool> getAllTools() {
		return new ArrayList<>(tools.values());
	}

	@Override
	public List<CodeactTool> getToolsForLanguage(Language language) {
		return tools.values()
			.stream()
			.filter(tool -> tool.getCodeactMetadata().supportedLanguages().contains(language))
			.collect(Collectors.toList());
	}

	@Override
	public Optional<CodeactToolDefinition> getToolDefinition(String toolName) {
		return Optional.ofNullable(toolDefinitions.get(toolName));
	}

	@Override
	public Optional<ReturnSchema> getReturnSchema(String toolName) {
		return returnSchemaRegistry.getSchema(toolName);
	}

	@Override
	public String generateStructuredToolPrompt(Language language) {
		List<CodeactTool> languageTools = getToolsForLanguage(language);

		if (languageTools.isEmpty()) {
			return "";
		}

		StringBuilder prompt = new StringBuilder();
		prompt.append("# Available Tools\n\n");
		prompt.append("You can use the following tools in your code:\n\n");

		// 按 className 分组
		Map<String, List<CodeactTool>> toolsByClass = new HashMap<>();
		List<CodeactTool> globalTools = new ArrayList<>();

		for (CodeactTool tool : languageTools) {
			String className = tool.getCodeactMetadata().targetClassName();
			if (className != null && !className.isEmpty()) {
				toolsByClass.computeIfAbsent(className, k -> new ArrayList<>()).add(tool);
			}
			else {
				globalTools.add(tool);
			}
		}

		// 生成类工具的代码 stub
		for (Map.Entry<String, List<CodeactTool>> entry : toolsByClass.entrySet()) {
			String className = entry.getKey();
			List<CodeactTool> classTools = entry.getValue();

			// 获取类描述
			String classDescription = classTools.isEmpty() ? ""
					: classTools.get(0).getCodeactMetadata().targetClassDescription();

			String classStub = pythonRenderer.renderClassStub(className, classDescription, classTools,
					toolName -> returnSchemaRegistry.getSchema(toolName).orElse(null));
			prompt.append("```python\n");
			prompt.append(classStub);
			prompt.append("```\n\n");
		}

		// 生成全局函数的代码 stub
		if (!globalTools.isEmpty()) {
			prompt.append("## Global Functions\n\n");
			prompt.append("```python\n");
			for (CodeactTool tool : globalTools) {
				ReturnSchema schema = returnSchemaRegistry.getSchema(tool.getName()).orElse(null);
				String funcStub = pythonRenderer.renderToolStub(tool, schema);
				prompt.append(funcStub).append("\n");
			}
			prompt.append("```\n");
		}

		return prompt.toString();
	}

	@Override
	@Deprecated
	public String generateToolDescriptionPrompt(Language language) {
		List<CodeactTool> languageTools = getToolsForLanguage(language);

		if (languageTools.isEmpty()) {
			return "";
		}

		StringBuilder prompt = new StringBuilder();
		prompt.append("## 可用工具\n\n");
		prompt.append("你可以在代码中使用以下工具：\n\n");

		// Group by className
		Map<String, List<CodeactTool>> toolsByClass = new HashMap<>();
		List<CodeactTool> globalTools = new ArrayList<>();

		for (CodeactTool tool : languageTools) {
			String className = tool.getCodeactMetadata().targetClassName();
			if (className != null && !className.isEmpty()) {
				toolsByClass.computeIfAbsent(className, k -> new ArrayList<>()).add(tool);
			}
			else {
				globalTools.add(tool);
			}
		}

		// Generate description for class-based tools
		for (Map.Entry<String, List<CodeactTool>> entry : toolsByClass.entrySet()) {
			String className = entry.getKey();
			prompt.append(String.format("### Class: %s\n\n", className));

			for (CodeactTool tool : entry.getValue()) {
				appendToolDescription(prompt, tool);
			}
			prompt.append("\n");
		}

		// Generate description for global tools
		if (!globalTools.isEmpty()) {
			prompt.append("### Global Functions\n\n");
			for (CodeactTool tool : globalTools) {
				appendToolDescription(prompt, tool);
			}
		}

		return prompt.toString();
	}

	@SuppressWarnings("deprecation")
	private void appendToolDescription(StringBuilder prompt, CodeactTool tool) {
		// 优先使用结构化定义生成签名
		CodeactToolDefinition def = toolDefinitions.get(tool.getName());
		String signature;
		if (def != null && def.parameterTree().hasParameters()) {
			signature = tool.getName() + "(" + def.parameterTree().toPythonSignature() + ")";
		}
		else {
			// 回退到旧的 codeInvocationTemplate
			signature = tool.getCodeactMetadata().codeInvocationTemplate();
			if (signature == null || signature.isEmpty()) {
				signature = tool.getName() + "()";
			}
		}

		String description = tool.getToolDefinition().description();

		prompt.append(String.format("- `%s`", signature));
		if (description != null && !description.isEmpty()) {
			prompt.append(String.format(": %s", description));
		}
		prompt.append("\n");
	}

	@Override
	public ReturnSchemaRegistry getReturnSchemaRegistry() {
		return returnSchemaRegistry;
	}

}

