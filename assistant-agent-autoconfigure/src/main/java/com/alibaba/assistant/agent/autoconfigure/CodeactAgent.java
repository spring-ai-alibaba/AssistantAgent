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
package com.alibaba.assistant.agent.autoconfigure;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.core.context.CodeContext;
import com.alibaba.assistant.agent.core.executor.GraalCodeExecutor;
import com.alibaba.assistant.agent.core.executor.RuntimeEnvironmentManager;
import com.alibaba.assistant.agent.core.executor.python.PythonEnvironmentManager;
import com.alibaba.assistant.agent.core.tool.CodeactToolRegistry;
import com.alibaba.assistant.agent.core.tool.DefaultCodeactToolRegistry;
import com.alibaba.assistant.agent.core.tool.ToolRegistryBridgeFactory;
import com.alibaba.assistant.agent.core.tool.schema.ReturnSchemaRegistry;
import com.alibaba.assistant.agent.extension.experience.config.ExperienceExtensionProperties;
import com.alibaba.assistant.agent.extension.experience.fastintent.FastIntentService;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.autoconfigure.subagent.CodeactSubAgentInterceptor;
import com.alibaba.assistant.agent.autoconfigure.tools.ExecuteCodeTool;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.alibaba.cloud.ai.graph.agent.node.AgentLlmNode;
import com.alibaba.cloud.ai.graph.agent.node.AgentToolNode;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * CodeactAgent - Code as Action Agent.
 *
 * <p>This agent extends ReactAgent to provide code generation and execution capabilities.
 * It generates Python code as functions and executes them using GraalVM.
 *
 * <h2>Key Features:</h2>
 * <ul>
 * <li>Initial code generation before entering agent loop</li>
 * <li>Write code via LLM (using SubAgent pattern)</li>
 * <li>Execute code in GraalVM sandbox</li>
 * <li>Store code in persistent Store for cross-session reuse</li>
 * <li>Full integration with Spring AI Alibaba framework</li>
 * </ul>
 *
 * <h2>Architecture:</h2>
 * <pre>
 * User Input → InitialCodeGenHook → ReactAgent Loop
 *                                     ↓
 *                         [WriteCode | ExecuteCode | Think | Reply]
 *                                     ↓
 *                              GraalVM Execution
 *                                     ↓
 *                            Store Persistence
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class CodeactAgent extends ReactAgent {

	private static final Logger logger = LoggerFactory.getLogger(CodeactAgent.class);

	// CodeAct specific components
	private final CodeContext codeContext;
	private final RuntimeEnvironmentManager environmentManager;
	private final GraalCodeExecutor executor;

	/**
	 * Private constructor for CodeactAgent.
	 *
	 * @param llmNode The LLM node for chat interactions
	 * @param toolNode The tool node for tool execution
	 * @param compileConfig Compilation configuration for the agent graph
	 * @param builder The builder containing configuration parameters (required by ReactAgent framework)
	 * @param codeContext Code context for managing generated functions
	 * @param environmentManager Runtime environment manager for code execution
	 * @param executor GraalVM code executor for safe code execution
	 */
	private CodeactAgent(
			AgentLlmNode llmNode,
			AgentToolNode toolNode,
			CompileConfig compileConfig,
			CodeactAgentBuilder builder,
			CodeContext codeContext,
			RuntimeEnvironmentManager environmentManager,
			GraalCodeExecutor executor) {
		super(llmNode, toolNode, compileConfig, builder);
		this.codeContext = codeContext;
		this.environmentManager = environmentManager;
		this.executor = executor;

		logger.info("CodeactAgent#<init> 初始化完成: language={}", codeContext.getLanguage());
	}

	/**
	 * Create a new builder for CodeactAgent
	 */
	public static CodeactAgentBuilder builder() {
		return new CodeactAgentBuilder();
	}


	/**
	 * Get the code context
	 */
	public CodeContext getCodeContext() {
		return codeContext;
	}

	/**
	 * Get the runtime environment manager
	 */
	public RuntimeEnvironmentManager getEnvironmentManager() {
		return environmentManager;
	}

	/**
	 * Get the code executor
	 */
	public GraalCodeExecutor getExecutor() {
		return executor;
	}

	/**
	 * Builder for CodeactAgent that extends ReactAgent.Builder.
	 */
	public static class CodeactAgentBuilder extends Builder {

		// CodeAct specific fields
		private Language language = Language.PYTHON;
		private CodeContext codeContext;
		private RuntimeEnvironmentManager environmentManager;
		private GraalCodeExecutor executor;
		private BiFunction<String, ToolContext, String> codeGenerator;
		private boolean enableInitialCodeGen = true;
		private boolean allowIO = false;
		private boolean allowNativeAccess = false;
		private long executionTimeoutMs = 30000;

		// CodeactTool Registry (新机制)
		private CodeactToolRegistry codeactToolRegistry;

		// ReturnSchemaRegistry (进程内单例)
		private ReturnSchemaRegistry returnSchemaRegistry;

		// ToolRegistryBridgeFactory (用于自定义工具调用桥接，如可观测性)
		private ToolRegistryBridgeFactory toolRegistryBridgeFactory;

		// CodeactTool support (新机制)
		private List<CodeactTool> codeactTools = new ArrayList<>();

		// SubAgent Hooks
		private List<Hook> subAgentHooks = new ArrayList<>();

		// Experience / FastIntent (optional, for WriteCodeTool fastpath)
		private ExperienceProvider experienceProvider;
		private ExperienceExtensionProperties experienceExtensionProperties;
		private FastIntentService fastIntentService;

		// Code generation model name
		private String codeGenerationModelName;

		// Keep reference to ChatModel for code generation
		private ChatModel chatModel;

		// SubAgent system prompt (for Codeact phase code generation)
		private String subAgentSystemPrompt;

		// state keys to propagate from parent agent (react phase) to child agent (codeact phase)
		private List<String> stateKeysToPropagate = new ArrayList<>();

		// GraphLifecycleListeners (用于可观测性)
		private List<com.alibaba.cloud.ai.graph.GraphLifecycleListener> lifecycleListeners = new ArrayList<>();

		public CodeactAgentBuilder() {
			super();
			// Set default name
			this.name("CodeactAgent");
            this.enableLogging(true);
		}

		/**
		 * Set the programming language (default: Python)
		 */
		public CodeactAgentBuilder language(Language language) {
			this.language = language;
			return this;
		}

		/**
		 * Set custom code context
		 */
		public CodeactAgentBuilder codeContext(CodeContext codeContext) {
			this.codeContext = codeContext;
			return this;
		}

		/**
		 * Set custom environment manager
		 */
		public CodeactAgentBuilder environmentManager(RuntimeEnvironmentManager environmentManager) {
			this.environmentManager = environmentManager;
			return this;
		}

		/**
		 * Set custom code generator (SubAgent)
		 */
		public CodeactAgentBuilder codeGenerator(BiFunction<String, ToolContext, String> codeGenerator) {
			this.codeGenerator = codeGenerator;
			return this;
		}

		/**
		 * Set hooks for sub-agents (e.g. CodeGeneratorSubAgent)
		 */
		public CodeactAgentBuilder subAgentHooks(List<Hook> hooks) {
			this.subAgentHooks = hooks;
			return this;
		}

		/**
		 * Set state keys to propagate from parent agent (react phase) to child agent (codeact phase)
		 */
		public CodeactAgentBuilder stateKeysToPropagate(List<String> keys) {
			this.stateKeysToPropagate = keys != null ? new ArrayList<>(keys) : new ArrayList<>();
			return this;
		}

		/**
		 * Set state keys to propagate from parent agent (react phase) to child agent (codeact phase), in variable argument form
		 */
		public CodeactAgentBuilder stateKeysToPropagate(String... keys) {
			this.stateKeysToPropagate = new ArrayList<>(Arrays.asList(keys));
			return this;
		}

		public CodeactAgentBuilder experienceProvider(ExperienceProvider experienceProvider) {
			this.experienceProvider = experienceProvider;
			return this;
		}

		public CodeactAgentBuilder experienceExtensionProperties(ExperienceExtensionProperties props) {
			this.experienceExtensionProperties = props;
			return this;
		}

		public CodeactAgentBuilder fastIntentService(FastIntentService fastIntentService) {
			this.fastIntentService = fastIntentService;
			return this;
		}

		/**
		 * Set custom system prompt for the Codeact sub-agent (code generation phase).
		 */
		public CodeactAgentBuilder subAgentSystemPrompt(String systemPrompt) {
			this.subAgentSystemPrompt = systemPrompt;
			return this;
		}

		/**
		 * Enable/disable initial code generation hook
		 */
		public CodeactAgentBuilder enableInitialCodeGen(boolean enable) {
			this.enableInitialCodeGen = enable;
			return this;
		}

		/**
		 * Allow IO operations in GraalVM (security)
		 */
		public CodeactAgentBuilder allowIO(boolean allow) {
			this.allowIO = allow;
			return this;
		}

		/**
		 * Allow native access in GraalVM (security)
		 */
		public CodeactAgentBuilder allowNativeAccess(boolean allow) {
			this.allowNativeAccess = allow;
			return this;
		}

		/**
		 * Set execution timeout in milliseconds
		 */
		public CodeactAgentBuilder executionTimeout(long timeoutMs) {
			this.executionTimeoutMs = timeoutMs;
			return this;
		}
		/**
		 * Register a CodeactTool (新机制)
		 */
		public CodeactAgentBuilder codeactTool(CodeactTool tool) {
			this.codeactTools.add(tool);
			return this;
		}

		/**
		 * Register multiple CodeactTools (新机制)
		 */
		public CodeactAgentBuilder codeactTools(CodeactTool... tools) {
			this.codeactTools.addAll(Arrays.asList(tools));
			return this;
		}

		/**
		 * Register a list of CodeactTools (新机制)
		 */
		public CodeactAgentBuilder codeactTools(List<CodeactTool> tools) {
			this.codeactTools.addAll(tools);
			return this;
		}

		/**
		 * Set custom CodeactTool registry (新机制)
		 */
		public CodeactAgentBuilder codeactToolRegistry(CodeactToolRegistry registry) {
			this.codeactToolRegistry = registry;
			return this;
		}

		/**
		 * Set the ReturnSchemaRegistry (进程内单例，用于收集工具返回值结构)
		 *
		 * <p>如果不设置，将使用 codeactToolRegistry 内部的 registry。
		 * 建议注入 Spring Bean 单例以在整个应用生命周期内持续累积观测数据。
		 */
		public CodeactAgentBuilder returnSchemaRegistry(ReturnSchemaRegistry registry) {
			this.returnSchemaRegistry = registry;
			return this;
		}

		/**
		 * Set the ToolRegistryBridgeFactory for customizing ToolRegistryBridge creation.
		 *
		 * <p>If not set, the default factory will be used which creates standard
		 * ToolRegistryBridge instances.
		 *
		 * @param factory the ToolRegistryBridgeFactory to use
		 * @return CodeactAgentBuilder instance for chaining
		 */
		public CodeactAgentBuilder toolRegistryBridgeFactory(ToolRegistryBridgeFactory factory) {
			this.toolRegistryBridgeFactory = factory;
			return this;
		}

		/**
		 * Add a GraphLifecycleListener for observability.
		 *
		 * <p>用于监听 Agent Graph 执行的关键阶段，如 React 阶段开始/结束、节点执行前后等。
		 * 可以通过此接口实现日志记录、指标收集、分布式追踪等可观测性功能。
		 *
		 * @param listener the GraphLifecycleListener to add
		 * @return CodeactAgentBuilder instance for chaining
		 */
		public CodeactAgentBuilder lifecycleListener(com.alibaba.cloud.ai.graph.GraphLifecycleListener listener) {
			if (listener != null) {
				this.lifecycleListeners.add(listener);
			}
			return this;
		}

		/**
		 * Add multiple GraphLifecycleListeners for observability.
		 *
		 * @param listeners the list of GraphLifecycleListeners to add
		 * @return CodeactAgentBuilder instance for chaining
		 */
		public CodeactAgentBuilder lifecycleListeners(List<? extends com.alibaba.cloud.ai.graph.GraphLifecycleListener> listeners) {
			if (listeners != null) {
				this.lifecycleListeners.addAll(listeners);
			}
			return this;
		}

		/**
		 * Set the model name for code generation
		 * For example: "qwen-coder-plus", "qwen-max", etc.
		 */
		public CodeactAgentBuilder codeGenerationModelName(String modelName) {
			this.codeGenerationModelName = modelName;
			return this;
		}

		// Override parent methods to provide CodeactAgentBuilder return type
		@Override
		public CodeactAgentBuilder name(String name) {
			super.name(name);
			return this;
		}

		@Override
		public CodeactAgentBuilder description(String description) {
			super.description(description);
			return this;
		}

		@Override
		public CodeactAgentBuilder instruction(String instruction) {
			super.instruction(instruction);
			return this;
		}

		@Override
		public CodeactAgentBuilder systemPrompt(String systemPrompt) {
			super.systemPrompt(systemPrompt);
			return this;
		}

		public CodeactAgentBuilder codingChatModel(ChatModel chatModel) {
			this.chatModel = chatModel;
			return this;
		}

        @Override
		public CodeactAgentBuilder model(ChatModel model) {
			super.model(model);
			return this;
		}

		/**
		 * Set checkpoint saver for conversation memory
		 * @param saver the checkpoint saver
		 * @return CodeactBuilder instance
		 */
		public CodeactAgentBuilder saver(BaseCheckpointSaver saver) {
			super.saver(saver);
			return this;
		}

		// Hook and Interceptor support methods

		/**
		 * 注册多个Hook到Agent
		 * @param hooks Hook实例数组
		 * @return CodeactAgentBuilder实例，支持链式调用
		 */
		@Override
		public CodeactAgentBuilder hooks(Hook... hooks) {
			super.hooks(hooks);
			return this;
		}

		/**
		 * 注册Hook列表到Agent
		 * @param hooks Hook列表
		 * @return CodeactAgentBuilder实例，支持链式调用
		 */
		@Override
		public CodeactAgentBuilder hooks(List<? extends Hook> hooks) {
			super.hooks(hooks);
			return this;
		}

		/**
		 * 注册单个Interceptor到Agent
		 * @param interceptor Interceptor实例
		 * @return CodeactAgentBuilder实例，支持链式调用
		 */
		public CodeactAgentBuilder interceptor(Interceptor interceptor) {
			super.interceptors(interceptors);
			return this;
		}

		/**
		 * 注册多个Interceptor到Agent
		 * @param interceptors Interceptor实例数组
		 * @return CodeactAgentBuilder实例，支持链式调用
		 */
		@Override
		public CodeactAgentBuilder interceptors(Interceptor... interceptors) {
			super.interceptors(interceptors);
			return this;
		}

		/**
		 * 注册Interceptor列表到Agent
		 * @param interceptors Interceptor列表
		 * @return CodeactAgentBuilder实例，支持链式调用
		 */
		@Override
		public CodeactAgentBuilder interceptors(List<? extends Interceptor> interceptors) {
			super.interceptors(interceptors);
			return this;
		}

		/**
		 * 注册工具相关的方法 - 委托给ReactAgent.Builder
		 */
		@Override
		public CodeactAgentBuilder tools(ToolCallback... tools) {
			super.tools(tools);
			return this;
		}

		@Override
		public CodeactAgentBuilder tools(List<ToolCallback> tools) {
			super.tools(tools);
			return this;
		}

		@Override
		public CodeactAgentBuilder methodTools(Object... toolObjects) {
			super.methodTools(toolObjects);
			return this;
		}

		/**
		 * Override buildConfig to include lifecycleListeners for observability.
		 *
		 * @return CompileConfig with lifecycleListeners included
		 */
		@Override
		protected CompileConfig buildConfig() {
			// If compileConfig is already set, use it
			if (compileConfig != null) {
				// Add additional lifecycleListeners to existing config
				if (!lifecycleListeners.isEmpty()) {
					for (com.alibaba.cloud.ai.graph.GraphLifecycleListener listener : lifecycleListeners) {
						compileConfig.lifecycleListeners().offer(listener);
					}
					logger.info("CodeactAgentBuilder#buildConfig - reason=添加LifecycleListeners到已有CompileConfig, count={}",
							lifecycleListeners.size());
				}
				return compileConfig;
			}

			// Build new CompileConfig with saver and lifecycleListeners
			com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig saverConfig =
					com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig.builder()
							.register(saver)
							.build();

			CompileConfig.Builder builder = CompileConfig.builder()
					.saverConfig(saverConfig)
					.recursionLimit(Integer.MAX_VALUE)
					.releaseThread(releaseThread);

			// Add ObservationRegistry if available
			if (observationRegistry != null) {
				builder.observationRegistry(observationRegistry);
			}

			// Add all lifecycleListeners
			for (com.alibaba.cloud.ai.graph.GraphLifecycleListener listener : lifecycleListeners) {
				builder.withLifecycleListener(listener);
			}

			if (!lifecycleListeners.isEmpty()) {
				logger.info("CodeactAgentBuilder#buildConfig - reason=创建带有LifecycleListeners的CompileConfig, count={}",
						lifecycleListeners.size());
			}

			return builder.build();
		}

		/**
		 * Build the CodeactAgent
		 */
		@Override
		public CodeactAgent build() {
			logger.info("CodeactAgentBuilder#build 开始构建CodeactAgent");

			// Initialize CodeactToolRegistry if not provided
			if (this.codeactToolRegistry == null) {
				// 如果有注入 returnSchemaRegistry，使用它来创建 registry
				if (this.returnSchemaRegistry != null) {
					this.codeactToolRegistry = new DefaultCodeactToolRegistry(this.returnSchemaRegistry);
					logger.debug("CodeactAgentBuilder#build - reason=使用注入的ReturnSchemaRegistry创建CodeactToolRegistry");
				} else {
					this.codeactToolRegistry = new DefaultCodeactToolRegistry();
					logger.debug("CodeactAgentBuilder#build - reason=创建默认CodeactToolRegistry");
				}
			}

			if (this.toolRegistryBridgeFactory != null) {
				logger.info("CodeactAgentBuilder#build 使用自定义ToolRegistryBridgeFactory: {}",
						this.toolRegistryBridgeFactory.getClass().getSimpleName());
			}

			// 处理 CodeactTool (新机制)
			if (!this.codeactTools.isEmpty()) {
				logger.info("CodeactAgentBuilder#build - reason=开始注册CodeactTool, count={}", this.codeactTools.size());
				for (CodeactTool codeactTool : this.codeactTools) {
					this.codeactToolRegistry.register(codeactTool);
					logger.debug("CodeactAgentBuilder#build - reason=CodeactTool注册成功, name={}",
						codeactTool.getToolDefinition().name());
				}
				logger.info("CodeactAgentBuilder#build - reason=CodeactTool注册完成, count={}", this.codeactTools.size());
			}


			// Initialize CodeContext if not provided
			if (this.codeContext == null) {
				this.codeContext = new CodeContext(this.language);
				logger.debug("CodeactAgentBuilder#build 创建默认CodeContext: language={}", this.language);
			}

			// Initialize RuntimeEnvironmentManager if not provided
			if (this.environmentManager == null) {
				this.environmentManager = createDefaultEnvironmentManager(this.language);
				logger.debug("CodeactAgentBuilder#build 创建默认RuntimeEnvironmentManager: language={}", this.language);
			}

			// For executor, create with placeholder state
			this.executor = new GraalCodeExecutor(
				this.environmentManager,
				this.codeContext,
				null, // Will be set by ReactAgent
				new OverAllState(), // Placeholder
				this.codeactToolRegistry,  // Pass CodeactTool registry
				this.toolRegistryBridgeFactory,  // Pass custom factory (null will use default)
				this.allowIO,
				this.allowNativeAccess,
				this.executionTimeoutMs
			);

			// 创建 CodeactSubAgentInterceptor（替代旧的 codeGenerator 方式）
			Interceptor codeactSubAgentInterceptor = createCodeactSubAgentInterceptor();

			// 将 CodeactSubAgentInterceptor 添加到拦截器列表
			super.interceptors(codeactSubAgentInterceptor);
            super.modelInterceptors.add((ModelInterceptor) codeactSubAgentInterceptor);

			ExecuteCodeTool executeCodeTool = new ExecuteCodeTool(this.executor, this.codeContext);

			// Note: InitialCodeGenHook 已废弃，代码生成通过 SubAgent 机制实现

			// Manually create the components like DefaultBuilder does
			// but return CodeactAgent instead

			// Validate required fields like DefaultBuilder does
			if (!StringUtils.hasText(this.name)) {
				throw new IllegalArgumentException("Agent name must not be empty");
			}

			if (chatClient == null && model == null) {
				throw new IllegalArgumentException("Either chatClient or model must be provided");
			}

			if (chatClient == null) {
				ChatClient.Builder clientBuilder = ChatClient.builder(model);
				if (chatOptions != null) {
					clientBuilder.defaultOptions(chatOptions);
				}
				chatClient = clientBuilder.build();
			}

			// Create AgentLlmNode
			AgentLlmNode.Builder llmNodeBuilder = AgentLlmNode.builder()
					.agentName(this.name)
					.chatClient(chatClient);

			if (outputKey != null && !outputKey.isEmpty()) {
				llmNodeBuilder.outputKey(outputKey);
			}

			if (systemPrompt != null) {
				llmNodeBuilder.systemPrompt(systemPrompt);
			}

			// Separate unified interceptors by type (like DefaultBuilder does)
			// 重要：使用父类的 this.modelInterceptors 和 this.toolInterceptors 字段
			if (!interceptors.isEmpty()) {
				this.modelInterceptors.clear();  // 清空现有列表
				this.toolInterceptors.clear();

				for (Interceptor interceptor : interceptors) {
					if (interceptor instanceof ModelInterceptor) {
						this.modelInterceptors.add((ModelInterceptor) interceptor);
					}
					if (interceptor instanceof ToolInterceptor) {
						this.toolInterceptors.add((ToolInterceptor) interceptor);
					}
				}
				logger.info("CodeactAgentBuilder#build 拦截器分类完成: modelInterceptors={}, toolInterceptors={}",
					this.modelInterceptors.size(), this.toolInterceptors.size());
			}

			// Extract tools from interceptors (like DefaultBuilder does)
			List<ToolCallback> interceptorTools = new ArrayList<>();
			if (!this.modelInterceptors.isEmpty()) {
				for (ModelInterceptor interceptor : this.modelInterceptors) {
					List<ToolCallback> toolsFromInterceptor = interceptor.getTools();
					if (toolsFromInterceptor != null && !toolsFromInterceptor.isEmpty()) {
						interceptorTools.addAll(toolsFromInterceptor);
						logger.info("CodeactAgentBuilder#build 从拦截器提取工具: interceptor={}, toolCount={}",
							interceptor.getName(), toolsFromInterceptor.size());
					}
				}
			}

			// Combine all tools: interceptorTools + regularTools
			// Note: write_code 和 write_condition_code 由 CodeactSubAgentInterceptor 提供
			List<ToolCallback> allTools = new ArrayList<>();
			allTools.addAll(interceptorTools);  // 从拦截器提取的工具（包括write_code, write_condition_code）
			if (tools != null) {
				allTools.addAll(tools);  // 用户注册的工具
			}

			// 添加 execute_code 工具
			allTools.add(
				FunctionToolCallback.builder("execute_code", executeCodeTool)
					.description("Execute a previously generated function by its exact name with matching parameters. " +
						"CRITICAL: The functionName MUST exactly match the name used in write_code. " +
						"The 'args' parameter names MUST exactly match the 'parameters' specified in write_code. " +
						"Example: If you called write_code with functionName='calculate_sum' and parameters=['a', 'b'], " +
						"then you must call execute_code with functionName='calculate_sum' and args={'a': 10, 'b': 20}. " +
						"Parameter value types supported: String, Number, Boolean, List, Map/Object.")
					.inputType(ExecuteCodeTool.Request.class)
					.build()
			);

			logger.info("CodeactAgentBuilder#build 工具收集完成: interceptorTools={}, regularTools={}, total={}",
				interceptorTools.size(), (tools != null ? tools.size() : 0), allTools.size());

            llmNodeBuilder.toolCallbacks(allTools);

            AgentLlmNode llmNode = llmNodeBuilder.build();

			// Set interceptors to llmNode (like ReactAgent constructor does)
			if (!this.modelInterceptors.isEmpty()) {
				llmNode.setModelInterceptors(this.modelInterceptors);
				logger.info("CodeactAgentBuilder#build 设置ModelInterceptors到LlmNode: count={}", this.modelInterceptors.size());
			}

			// Create AgentToolNode
			AgentToolNode.Builder toolBuilder = AgentToolNode.builder()
					.agentName(this.name);

			if (!allTools.isEmpty()) {
				toolBuilder.toolCallbacks(allTools);
			}

			// Set toolContext if available (like DefaultBuilder does)
			if (toolContext != null && !toolContext.isEmpty()) {
				toolBuilder.toolContext(toolContext);
			}

			// Enable logging if enabled
			if (enableLogging) {
				toolBuilder.enableActingLog(true);
			}

			// Set exception processor (like DefaultBuilder does)
			if (toolExecutionExceptionProcessor == null) {
				toolBuilder.toolExecutionExceptionProcessor(
					org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor.builder()
						.alwaysThrow(false)
						.build());
			} else {
				toolBuilder.toolExecutionExceptionProcessor(toolExecutionExceptionProcessor);
			}

        llmNode.setInstruction(instruction);
		AgentToolNode toolNode = toolBuilder.build();

			// Set interceptors to toolNode (like ReactAgent constructor does)
			if (!this.toolInterceptors.isEmpty()) {
				toolNode.setToolInterceptors(this.toolInterceptors);
				logger.info("CodeactAgentBuilder#build 设置ToolInterceptors到ToolNode: count={}", this.toolInterceptors.size());
			}

		logger.info("CodeactAgentBuilder#build CodeactAgent构建完成");

		// Log registered tools
		logRegisteredTools(allTools);

		return new CodeactAgent(
			llmNode,
			toolNode,
			buildConfig(),
			this,
			this.codeContext,
			this.environmentManager,
			this.executor);
	}

	/**
	 * Log all registered CodeAct tools and React tools
	 */
	private void logRegisteredTools(List<ToolCallback> reactTools) {
		logger.info("CodeactAgentBuilder#logRegisteredTools - reason=开始罗列已注册的工具");

		// Log CodeAct phase tools (from CodeactToolRegistry)
		if (this.codeactToolRegistry != null) {
			List<CodeactTool> codeactTools =
				this.codeactToolRegistry.getAllTools();

			if (codeactTools.isEmpty()) {
				logger.info("CodeactAgentBuilder#logRegisteredTools - reason=CodeAct阶段工具数量, count=0");
			} else {
				logger.info("CodeactAgentBuilder#logRegisteredTools - reason=CodeAct阶段工具数量, count={}",
					codeactTools.size());

				for (int i = 0; i < codeactTools.size(); i++) {
					CodeactTool tool = codeactTools.get(i);
					logger.info("CodeactAgentBuilder#logRegisteredTools - reason=CodeAct工具详情, " +
						"index={}, name={}, description={}",
						i + 1,
						tool.getToolDefinition().name(),
						tool.getToolDefinition().description());
				}
			}
		} else {
			logger.info("CodeactAgentBuilder#logRegisteredTools - reason=CodeactToolRegistry未初始化");
		}

		// Log React phase tools (Spring AI ToolCallbacks)
		if (reactTools == null || reactTools.isEmpty()) {
			logger.info("CodeactAgentBuilder#logRegisteredTools - reason=React阶段工具数量, count=0");
		} else {
			logger.info("CodeactAgentBuilder#logRegisteredTools - reason=React阶段工具数量, count={}",
				reactTools.size());

			for (int i = 0; i < reactTools.size(); i++) {
				ToolCallback tool = reactTools.get(i);
				logger.info("CodeactAgentBuilder#logRegisteredTools - reason=React工具详情, " +
					"index={}, name={}, description={}",
					i + 1,
					tool.getToolDefinition().name(),
					tool.getToolDefinition().description());
			}
		}

		logger.info("CodeactAgentBuilder#logRegisteredTools - reason=工具罗列完成");
	}

		private RuntimeEnvironmentManager createDefaultEnvironmentManager(Language language) {
			return switch (language) {
				case PYTHON -> new PythonEnvironmentManager();
				case JAVASCRIPT, JAVA -> throw new UnsupportedOperationException(
					"Language not yet supported: " + language);
			};
		}

		private BiFunction<String, ToolContext, String> createDefaultCodeGenerator() {
			// Use LLM to generate Python code
			return (requirement, toolContext) -> {
				if (this.chatModel == null) {
					throw new IllegalStateException(
						"ChatModel is required for code generation. " +
						"Please set a ChatModel using .model(chatModel) before building the agent."
					);
				}

				// Extract function specification from context
				@SuppressWarnings("unchecked")
                Map<String, Object> codeGenSpec = (Map<String, Object>) toolContext.getContext().get("CODE_GEN_SPEC");
				String functionName = codeGenSpec != null ? (String) codeGenSpec.get("functionName") : null;
				@SuppressWarnings("unchecked")
				List<String> parameters = codeGenSpec != null ? (List<String>) codeGenSpec.get("parameters") : null;

				logger.info("CodeactAgentBuilder#createDefaultCodeGenerator 生成代码: functionName={}, parameters={}, requirement={}",
					functionName, parameters, requirement);

				// Build a detailed prompt for code generation
				StringBuilder promptBuilder = new StringBuilder();
				promptBuilder.append("请根据以下需求生成一个Python函数:\n\n");
				promptBuilder.append("需求: ").append(requirement).append("\n\n");

				// Add strict function name requirement
				if (functionName != null && !functionName.trim().isEmpty()) {
					promptBuilder.append("** 重要 **: 函数名必须是: ").append(functionName).append("\n");
				}

				// Add strict parameter requirements
				if (parameters != null && !parameters.isEmpty()) {
					promptBuilder.append("** 重要 **: 函数必须接受以下参数（按此顺序，参数名必须完全匹配）: ")
						.append(String.join(", ", parameters)).append("\n\n");
				} else {
					promptBuilder.append("** 重要 **: 由于未指定参数列表，函数必须使用 **kwargs 接收可变关键字参数\n\n");
				}

				promptBuilder.append("代码生成要求:\n");
				promptBuilder.append("1. 只生成一个函数定义，不要包含任何测试代码、示例调用或 if __name__ == '__main__' 块\n");

				if (functionName != null) {
					promptBuilder.append("2. 函数名必须严格使用: ").append(functionName).append("\n");
				} else {
					promptBuilder.append("2. 函数名要有意义，使用小写字母和下划线（snake_case）\n");
				}

				if (parameters != null && !parameters.isEmpty()) {
					promptBuilder.append("3. 函数参数必须严格按照指定的参数列表: def ")
						.append(functionName != null ? functionName : "function_name")
						.append("(").append(String.join(", ", parameters)).append("):\n");
				} else {
					promptBuilder.append("3. 函数必须接受**kwargs作为参数: def ")
						.append(functionName != null ? functionName : "function_name")
						.append("(**kwargs):\n");
				}

				promptBuilder.append("4. 函数必须有清晰的文档字符串(docstring)，说明功能、参数和返回值\n");
				promptBuilder.append("5. 使用类型提示(type hints)标注参数和返回值类型（如果明确类型的话）\n");
				promptBuilder.append("6. 代码要简洁、高效、可读性强\n");
				promptBuilder.append("7. 不要使用 markdown 代码块标记(```python 或 ```)，直接返回纯 Python 代码\n");
				promptBuilder.append("8. 不要包含任何 import 语句，系统会自动添加常用的导入\n\n");

				promptBuilder.append("示例格式:\n");
				if (parameters != null && !parameters.isEmpty()) {
					promptBuilder.append("def ").append(functionName != null ? functionName : "example_function")
						.append("(").append(String.join(", ", parameters)).append("):\n");
					promptBuilder.append("    \"\"\"函数功能描述。\n");
					promptBuilder.append("    \n");
					promptBuilder.append("    Args:\n");
					for (String param : parameters) {
						promptBuilder.append("        ").append(param).append(": 参数描述\n");
					}
					promptBuilder.append("    \n");
					promptBuilder.append("    Returns:\n");
					promptBuilder.append("        返回值的描述\n");
					promptBuilder.append("    \"\"\"\n");
					promptBuilder.append("    # 实现代码\n");
					promptBuilder.append("    return result\n\n");
				} else {
					promptBuilder.append("def ").append(functionName != null ? functionName : "example_function")
						.append("(**kwargs):\n");
					promptBuilder.append("    \"\"\"函数功能描述。\n");
					promptBuilder.append("    \n");
					promptBuilder.append("    Args:\n");
					promptBuilder.append("        **kwargs: 可变关键字参数\n");
					promptBuilder.append("    \n");
					promptBuilder.append("    Returns:\n");
					promptBuilder.append("        返回值的描述\n");
					promptBuilder.append("    \"\"\"\n");
					promptBuilder.append("    # 实现代码\n");
					promptBuilder.append("    return result\n\n");
				}

				promptBuilder.append("现在请严格按照上述要求生成代码:");

				String prompt = promptBuilder.toString();

				try {
					String response;
					if (this.codeGenerationModelName != null && !this.codeGenerationModelName.isEmpty()) {
						// Use specified model for code generation
						logger.info("CodeactAgentBuilder#createDefaultCodeGenerator 使用指定模型生成代码: {}", this.codeGenerationModelName);
						response = this.chatModel.call(
							Prompt.builder()
								.messages(new UserMessage(prompt))
                                    .chatOptions(DashScopeChatOptions.builder()
                                            .withModel(this.codeGenerationModelName)
									.build())
								.build()
						).getResult().getOutput().getText();
					} else {
						// Use default model
						response = this.chatModel.call(prompt);
					}

					// Clean up the response (remove markdown code blocks if present)
					String cleanedCode = cleanUpGeneratedCode(response);

					logger.info("CodeactAgentBuilder#createDefaultCodeGenerator 代码生成成功，代码长度: {} 字符", cleanedCode.length());
					logger.debug("CodeactAgentBuilder#createDefaultCodeGenerator 生成的代码:\n{}", cleanedCode);

					return cleanedCode;

				} catch (Exception e) {
					logger.error("CodeactAgentBuilder#createDefaultCodeGenerator 代码生成失败", e);
					throw new RuntimeException("Code generation failed: " + e.getMessage(), e);
				}
			};
		}

		private String cleanUpGeneratedCode(String code) {
			// Remove markdown code blocks
			String cleaned = code.replaceAll("```python\\s*", "");
			cleaned = cleaned.replaceAll("```\\s*$", "");
			cleaned = cleaned.replaceAll("```\\s*", "");
			cleaned = cleaned.trim();

			return cleaned;
		}

		/**
		 * 创建 CodeactSubAgentInterceptor（对标 DeepResearchAgent.subAgentAsInterceptors）
		 */
		private ModelInterceptor createCodeactSubAgentInterceptor() {
			logger.info("CodeactAgentBuilder#createCodeactSubAgentInterceptor 创建子Agent拦截器");

			// 使用当前配置的 model 和 codeactTools
			ChatModel codeGenModel = this.chatModel != null ?
				this.chatModel : this.model;

			if (codeGenModel == null) {
				throw new IllegalStateException("ChatModel is required for CodeactSubAgentInterceptor. " +
					"Please set a ChatModel using .model(chatModel) before building the agent.");
			}

			return CodeactSubAgentInterceptor.builder()
				.defaultModel(codeGenModel)
				.defaultCodeactTools(this.codeactTools)
				.defaultLanguage(language)
				.codeContext(this.codeContext)
				.environmentManager(this.environmentManager)
				.experienceProvider(this.experienceProvider)
				.experienceExtensionProperties(this.experienceExtensionProperties)
				.fastIntentService(this.fastIntentService)
				.includeDefaultCodeGenerator(true)  // 使用默认代码生成器
				.hooks(this.subAgentHooks) // Pass sub-agent hooks
				.returnSchemaRegistry(this.codeactToolRegistry != null ? this.codeactToolRegistry.getReturnSchemaRegistry() : null)
				.stateKeysToPropagate(this.stateKeysToPropagate) // 传递需要跨 agent 传递的 state keys
				.subAgentSystemPrompt(this.subAgentSystemPrompt)
				.build();
		}
	}
}

