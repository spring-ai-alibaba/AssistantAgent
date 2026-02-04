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
package com.alibaba.assistant.agent.autoconfigure.subagent;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.core.context.CodeContext;
import com.alibaba.assistant.agent.core.executor.RuntimeEnvironmentManager;
import com.alibaba.assistant.agent.core.tool.schema.ReturnSchemaRegistry;
import com.alibaba.assistant.agent.autoconfigure.subagent.spec.CodeactSubAgentSpec;
import com.alibaba.assistant.agent.autoconfigure.tools.WriteCodeTool;
import com.alibaba.assistant.agent.autoconfigure.tools.WriteConditionCodeTool;
import com.alibaba.assistant.agent.extension.experience.config.ExperienceExtensionProperties;
import com.alibaba.assistant.agent.extension.experience.fastintent.CodeFastIntentSupport;
import com.alibaba.assistant.agent.extension.experience.fastintent.FastIntentService;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.cloud.ai.graph.agent.BaseAgent;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CodeactSubAgentInterceptor - Codeact子Agent拦截器（完全对标SubAgentInterceptor）
 *
 * <p>唯一区别：使用Map&lt;String, BaseAgent&gt;而不是Map&lt;String, ReactAgent&gt;
 * <p>暴露write_code和write_condition_code两个工具，内部委托给TaskTool
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class CodeactSubAgentInterceptor extends ModelInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(CodeactSubAgentInterceptor.class);

	private final List<ToolCallback> tools;
	private final String systemPrompt;
	private final Map<String, BaseAgent> subAgents;
	private final boolean includeDefaultCodeGenerator;

	// CodeGen特有的上下文
	private final CodeContext codeContext;
	private final RuntimeEnvironmentManager environmentManager;
	private final List<com.alibaba.cloud.ai.graph.agent.hook.Hook> hooks;

	// Experience / fast-intent (optional)
	private final ExperienceProvider experienceProvider;
	private final ExperienceExtensionProperties experienceExtensionProperties;
	private final FastIntentService fastIntentService;

	/**
	 * 需要从父 agent 传递给子 agent 的 state keys。
	 * 通过此配置，业务方可以指定需要跨 agent 传递的状态。
	 */
	private final List<String> stateKeysToPropagate;

	/**
	 * 子 Agent 的系统提示词（用于代码生成阶段）。
	 * 如果设置了此字段，将传递给 CodeGeneratorSubAgent 用于定制代码生成行为。
	 */
	private final String subAgentSystemPrompt;

	private CodeactSubAgentInterceptor(Builder builder) {
		this.systemPrompt = builder.systemPrompt != null ? builder.systemPrompt : DEFAULT_SYSTEM_PROMPT;
		this.subAgents = new HashMap<>(builder.subAgents);
		this.includeDefaultCodeGenerator = builder.includeDefaultCodeGenerator;
		this.codeContext = builder.codeContext;
		this.environmentManager = builder.environmentManager;
		this.hooks = builder.hooks;
		this.experienceProvider = builder.experienceProvider;
		this.experienceExtensionProperties = builder.experienceExtensionProperties;
		this.fastIntentService = builder.fastIntentService;
		this.stateKeysToPropagate = builder.stateKeysToPropagate != null
				? new ArrayList<>(builder.stateKeysToPropagate)
				: Collections.emptyList();
		this.subAgentSystemPrompt = builder.subAgentSystemPrompt;

		// 添加默认code-generator和condition-code-generator（对标general-purpose）
		if (includeDefaultCodeGenerator && builder.defaultModel != null) {
			// 创建普通代码生成子Agent
			BaseAgent codeGenAgent = createDefaultCodeGeneratorAgent(
					builder.defaultModel,
					builder.defaultCodeactTools,
					builder.defaultInterceptors,
					builder.defaultLanguage,
					false,  // 不是条件判断函数
					builder.hooks,
					builder.returnSchemaRegistry,
					builder.subAgentSystemPrompt  // 传递自定义系统提示词
			);
			this.subAgents.put("code-generator", codeGenAgent);

			// 创建条件判断代码生成子Agent
			BaseAgent conditionCodeGenAgent = createDefaultCodeGeneratorAgent(
					builder.defaultModel,
					builder.defaultCodeactTools,
					builder.defaultInterceptors,
					builder.defaultLanguage,
					true,  // 是条件判断函数
					builder.hooks,
					builder.returnSchemaRegistry,
					builder.subAgentSystemPrompt
			);
			this.subAgents.put("condition-code-generator", conditionCodeGenAgent);

			logger.info("CodeactSubAgentInterceptor#<init> 创建默认代码生成子Agent");
		}

		// 创建内部 BaseAgentTaskTool（对标 SubAgentInterceptor 创建 TaskTool）
		// 传入 stateKeysToPropagate，支持将父 agent 的状态传递给子 agent
		BaseAgentTaskTool taskTool = new BaseAgentTaskTool(this.subAgents, this.stateKeysToPropagate);

		if (!this.stateKeysToPropagate.isEmpty()) {
			logger.info("CodeactSubAgentInterceptor#<init> 配置状态传递: keys={}", this.stateKeysToPropagate);
		}

		// 创建WriteCodeTool和WriteConditionCodeTool（委托给 BaseAgentTaskTool）
		CodeFastIntentSupport codeFastIntentSupport =
				(experienceProvider != null && experienceExtensionProperties != null && fastIntentService != null)
						? new CodeFastIntentSupport(experienceProvider, experienceExtensionProperties, fastIntentService)
						: null;
		List<ToolCallback> toolList = new ArrayList<>();
		toolList.add(WriteCodeTool.createWriteCodeToolCallback(taskTool, codeContext, environmentManager, codeFastIntentSupport));
		toolList.add(WriteConditionCodeTool.createWriteConditionCodeToolCallback(taskTool, codeContext, environmentManager, codeFastIntentSupport));
		this.tools = Collections.unmodifiableList(toolList);

		logger.info("CodeactSubAgentInterceptor#<init> 初始化完成: subAgentCount={}", this.subAgents.size());
	}


	/**
	 * 创建默认代码生成子Agent（对标createGeneralPurposeAgent）
	 *
	 * @param model ChatModel 实例
	 * @param tools CodeactTool 列表
	 * @param interceptors 拦截器列表
	 * @param language 编程语言
	 * @param isCondition 是否为条件判断函数
	 * @param hooks Hook 列表
	 * @param returnSchemaRegistry 返回值 Schema 注册表
	 * @param customSystemPrompt 自定义系统提示词（可选，为 null 时使用默认提示词）
	 */
	private BaseAgent createDefaultCodeGeneratorAgent(
			ChatModel model,
			List<CodeactTool> tools,
			List<? extends Interceptor> interceptors,
			Language language,
			boolean isCondition,
			List<com.alibaba.cloud.ai.graph.agent.hook.Hook> hooks,
			ReturnSchemaRegistry returnSchemaRegistry,
			String customSystemPrompt) {

		List<ModelInterceptor> modelInterceptors = new ArrayList<>();
		if (interceptors != null) {
			for (Interceptor interceptor : interceptors) {
				if (interceptor instanceof ModelInterceptor) {
					modelInterceptors.add((ModelInterceptor) interceptor);
				}
			}
		}

		if (isCondition) {
			CodeGeneratorSubAgent.Builder builder = CodeGeneratorSubAgent.builder()
					.name("condition-code-generator")
					.description("Generate condition function code that returns boolean")
					.chatModel(model)
					.codeactTools(tools)
					.language(language)
					.modelInterceptors(modelInterceptors)
					.hooks(hooks)
					.isCondition(true)
					.returnSchemaRegistry(returnSchemaRegistry);

			if (customSystemPrompt != null && !customSystemPrompt.isEmpty()) {
				builder.customSystemPrompt(customSystemPrompt);
			}
			return builder.build();
		} else {
			CodeGeneratorSubAgent.Builder builder = CodeGeneratorSubAgent.builder()
					.name("code-generator")
					.description("Generate function code based on requirements")
					.chatModel(model)
					.codeactTools(tools)
					.language(language)
					.modelInterceptors(modelInterceptors)
					.hooks(hooks)
					.isCondition(false)
					.returnSchemaRegistry(returnSchemaRegistry);

			if (customSystemPrompt != null && !customSystemPrompt.isEmpty()) {
				builder.customSystemPrompt(customSystemPrompt);
			}
			return builder.build();
		}
	}

	@Override
	public List<ToolCallback> getTools() {
		return tools;
	}

	@Override
	public String getName() {
		return "CodeactSubAgent";
	}

	@Override
	public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
		logger.info("CodeactSubAgentInterceptor#interceptModel 拦截器被调用");

		// 完全对标SubAgentInterceptor.interceptModel - 追加而非覆盖系统提示词
		SystemMessage enhancedSystemMessage;

		if (request.getSystemMessage() == null) {
			// 原始请求没有系统提示词，使用拦截器的默认提示词
			enhancedSystemMessage = new SystemMessage(this.systemPrompt);
			logger.info("CodeactSubAgentInterceptor#interceptModel 原始请求无系统提示词，使用拦截器默认提示词");
		} else {
			// 原始请求有系统提示词，追加拦截器的提示词（保留原有内容）
			enhancedSystemMessage = new SystemMessage(
					request.getSystemMessage().getText() + "\n\n" + systemPrompt
			);
			logger.info("CodeactSubAgentInterceptor#interceptModel 追加拦截器提示词到原有系统提示词");
		}

		ModelRequest enhancedRequest = ModelRequest.builder(request)
				.systemMessage(enhancedSystemMessage)
				.build();

		logger.info("CodeactSubAgentInterceptor#interceptModel 系统提示词已增强，继续调用链");
		return handler.call(enhancedRequest);
	}

	/**
	 * Builder（完全对标SubAgentInterceptor.Builder）
	 */
	public static class Builder {
		private String systemPrompt;
		private ChatModel defaultModel;
		private List<CodeactTool> defaultCodeactTools;
		private List<Interceptor> defaultInterceptors;
		private Language defaultLanguage = Language.PYTHON;
		private Map<String, BaseAgent> subAgents = new HashMap<>();
		private boolean includeDefaultCodeGenerator = true;
		private CodeContext codeContext;
		private RuntimeEnvironmentManager environmentManager;
		private List<com.alibaba.cloud.ai.graph.agent.hook.Hook> hooks;
		private ReturnSchemaRegistry returnSchemaRegistry;

		private ExperienceProvider experienceProvider;
		private ExperienceExtensionProperties experienceExtensionProperties;
		private FastIntentService fastIntentService;

		/**
		 * 需要从父 agent 传递给子 agent 的 state keys。
		 * 通过此配置，业务方可以指定需要跨 agent 传递的状态。
		 */
		private List<String> stateKeysToPropagate;

		/**
		 * 子 Agent 的自定义系统提示词（用于代码生成阶段）。
		 * 如果设置了此字段，将传递给 CodeGeneratorSubAgent 用于定制代码生成行为。
		 */
		private String subAgentSystemPrompt;

		public Builder systemPrompt(String systemPrompt) {
			this.systemPrompt = systemPrompt;
			return this;
		}

		public Builder subAgentSystemPrompt(String systemPrompt) {
			this.subAgentSystemPrompt = systemPrompt;
			return this;
		}

		public Builder defaultModel(ChatModel model) {
			this.defaultModel = model;
			return this;
		}

		public Builder defaultCodeactTools(List<CodeactTool> tools) {
			this.defaultCodeactTools = tools;
			return this;
		}

		public Builder defaultInterceptors(Interceptor... interceptors) {
			this.defaultInterceptors = Arrays.asList(interceptors);
			return this;
		}

		public Builder defaultLanguage(Language language) {
			this.defaultLanguage = language;
			return this;
		}

		public Builder codeContext(CodeContext codeContext) {
			this.codeContext = codeContext;
			return this;
		}

		public Builder environmentManager(RuntimeEnvironmentManager environmentManager) {
			this.environmentManager = environmentManager;
			return this;
		}

		public Builder returnSchemaRegistry(ReturnSchemaRegistry returnSchemaRegistry) {
			this.returnSchemaRegistry = returnSchemaRegistry;
			return this;
		}

		public Builder experienceProvider(ExperienceProvider experienceProvider) {
			this.experienceProvider = experienceProvider;
			return this;
		}

		public Builder experienceExtensionProperties(ExperienceExtensionProperties props) {
			this.experienceExtensionProperties = props;
			return this;
		}

		public Builder fastIntentService(FastIntentService fastIntentService) {
			this.fastIntentService = fastIntentService;
			return this;
		}

		public Builder hooks(List<com.alibaba.cloud.ai.graph.agent.hook.Hook> hooks) {
			this.hooks = hooks;
			return this;
		}

		/**
		 * 配置需要从父 agent 传递给子 agent 的 state keys。
		 * <p>
		 * 这是一个通用扩展点，允许业务方将父 agent 的状态传递给 Codeact 子 agent。
		 *
		 * @param keys 需要传递的 state key 列表
		 * @return this builder
		 */
		public Builder stateKeysToPropagate(List<String> keys) {
			this.stateKeysToPropagate = keys;
			return this;
		}

		/**
		 * 配置需要从父 agent 传递给子 agent 的 state keys（可变参数形式）。
		 *
		 * @param keys 需要传递的 state keys
		 * @return this builder
		 */
		public Builder stateKeysToPropagate(String... keys) {
			this.stateKeysToPropagate = Arrays.asList(keys);
			return this;
		}

		/**
		 * 添加自定义子Agent（对标addSubAgent）
		 */
		public Builder addSubAgent(String name, BaseAgent agent) {
			this.subAgents.put(name, agent);
			return this;
		}

		/**
		 * 通过Spec添加子Agent（对标addSubAgent(SubAgentSpec)）
		 */
		public Builder addSubAgent(CodeactSubAgentSpec spec) {
			BaseAgent agent = createSubAgentFromSpec(spec);
			this.subAgents.put(spec.getName(), agent);
			return this;
		}

		/**
		 * 是否包含默认代码生成器（对标includeGeneralPurpose）
		 */
		public Builder includeDefaultCodeGenerator(boolean include) {
			this.includeDefaultCodeGenerator = include;
			return this;
		}

		/**
		 * 从Spec创建子Agent（对标createSubAgentFromSpec）
		 */
		private BaseAgent createSubAgentFromSpec(CodeactSubAgentSpec spec) {
			ChatModel model = spec.getModel() != null ? spec.getModel() : defaultModel;
			List<CodeactTool> tools = spec.getCodeactTools() != null ?
					spec.getCodeactTools() : defaultCodeactTools;
			Language language = spec.getLanguage() != null ?
					spec.getLanguage() : defaultLanguage;

			List<ModelInterceptor> allInterceptors = new ArrayList<>();
			if (defaultInterceptors != null) {
				for (Interceptor interceptor : defaultInterceptors) {
					if (interceptor instanceof ModelInterceptor) {
						allInterceptors.add((ModelInterceptor) interceptor);
					}
				}
			}
			if (spec.getInterceptors() != null) {
				allInterceptors.addAll(spec.getInterceptors());
			}

			return CodeGeneratorSubAgent.builder()
					.name(spec.getName())
					.description(spec.getDescription())
					.chatModel(model)
					.codeactTools(tools)
					.language(language)
					.modelInterceptors(allInterceptors)
					.customSystemPrompt(spec.getSystemPrompt())
					.isCondition(spec.isCondition())
					.returnSchemaRegistry(returnSchemaRegistry)
					.build();
		}

		public CodeactSubAgentInterceptor build() {
			if (codeContext == null) {
				throw new IllegalStateException("codeContext is required");
			}
			if (environmentManager == null) {
				throw new IllegalStateException("environmentManager is required");
			}
			return new CodeactSubAgentInterceptor(this);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	private static final String DEFAULT_SYSTEM_PROMPT = """
		## `write_code` 和 `write_condition_code` (代码生成工具)
		
		你可以使用write_code工具生成函数代码，使用write_condition_code工具生成条件判断函数。
		
		使用场景：
		- 当需要实现一个新功能时，使用write_code
		- 当需要实现条件判断逻辑时，使用write_condition_code
		- 生成的代码会自动注册并可通过execute_code调用
		
		注意事项：
		1. 提供清晰的需求描述
		2. 明确指定函数名和参数
		3. 生成后的函数可以直接使用
		""";
}

