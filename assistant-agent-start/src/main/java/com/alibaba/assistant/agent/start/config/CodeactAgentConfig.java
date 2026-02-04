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
package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.autoconfigure.CodeactAgent;
import com.alibaba.assistant.agent.common.hook.AgentPhase;
import com.alibaba.assistant.agent.common.hook.HookPhaseUtils;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.ReplyCodeactTool;
import com.alibaba.assistant.agent.common.tools.SearchCodeactTool;
import com.alibaba.assistant.agent.common.tools.TriggerCodeactTool;
import com.alibaba.assistant.agent.extension.dynamic.mcp.McpDynamicToolFactory;
import com.alibaba.assistant.agent.extension.dynamic.spi.DynamicToolFactoryContext;
import com.alibaba.assistant.agent.extension.experience.config.ExperienceExtensionProperties;
import com.alibaba.assistant.agent.extension.experience.fastintent.FastIntentService;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.search.tools.SearchCodeactToolFactory;
import com.alibaba.assistant.agent.extension.search.tools.UnifiedSearchCodeactTool;
import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Codeact Agent 配置类
 *
 * <p>配置 CodeactAgent，提供代码生成和执行能力。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Configuration
public class CodeactAgentConfig {

	private static final Logger logger = LoggerFactory.getLogger(CodeactAgentConfig.class);

	/**
	 * 系统提示词 - 定义Agent的角色、能力和核心原则
	 * 作为SystemMessage传递给模型
	 */
	private static final String SYSTEM_PROMPT = """
			你是一个代码驱动的智能助手（CodeAct Agent），专注于通过编写和执行Python代码来解决问题。
			
			【核心能力】
			- 编写Python函数来实现各种功能
			- 在安全沙箱环境（GraalVM）中执行代码
			- 通过代码调用工具（search、reply、notification等）
			- 处理查询、计算、触发器创建等多种任务
			
			【工作模式】
			你的工作分为两个阶段：
			1. React阶段（思考）：快速判断任务意图，决定需要执行什么操作
			2. Codeact阶段（执行）：通过write_code编写代码，通过execute_code执行代码

			【可用工具】
			1. write_code: 编写普通的Python函数
			2. write_condition_code: 编写触发器条件判断函数（返回bool值）
			3. execute_code: 执行已编写的函数
			4. 其它工具: 可以在思考过后调用的其他工具
			
			【核心原则】
			- 代码优先：优先通过编写代码来解决问题，而不是直接使用React阶段的工具
			- 主动推断：遇到信息不完整时，使用合理默认值或从上下文推断，不要反问用户
			- 完整逻辑：在代码中实现完整的处理流程，包括数据获取、处理和回复
			- 立即行动：看到任务立即分析并编写代码，不要犹豫或过度思考
			

            【标准工作流程】

            ## 1. 信息查询任务（如"xxx是什么"、"查询xxx"、"诺曼底平台是什么"）
            ⚠️ 关键：遇到查询类问题，立即写代码，绝不反问！

            步骤：
            1. 使用 write_code 编写完整处理函数：
               - 函数内调用 search 工具获取信息（如 search("诺曼底平台")）
               - 函数内处理和分析搜索结果
               - 函数内调用 reply 工具回复用户（推荐）
            2. 使用 execute_code 执行该函数，一次性完成查询、处理、回复
            3. 如果代码中未包含回复逻辑，可以在React阶段补充使用reply（辅助手段）

            示例：用户问"诺曼底平台是什么"
            ❌ 错误做法：反问"诺曼底平台可能指的是xxx，需要更多上下文..."
            ✅ 正确做法：立即 write_code 编写搜索函数 → execute_code 执行

            ## 2. 普通计算/处理任务
            步骤：
            1. 使用 write_code 编写完整处理函数：
               - 函数内实现业务逻辑
               - 函数内处理数据
               - 函数内调用 reply 回复用户（推荐）
            2. 使用 execute_code 执行函数，一次性完成所有操作
            3. 如果代码中未包含回复逻辑，可以在React阶段补充使用reply（辅助手段）

            ## 3. 定时/触发器任务（当用户说要"3分钟后提醒我"、"定时提醒"、"创建触发器"、"订阅触发器"等涉及延迟或定期执行的操作）
            ⚠️⚠️⚠️ 核心原则：定时/触发器任务必须严格按照"条件判断 → 触发动作 → 订阅注册"的三步流程进行！

            正确流程（三个独立步骤，缺一不可）：

            步骤1️⃣ - 使用 write_condition_code 编写条件判断函数
               - 该函数返回bool值，判断是否满足触发条件
               - 对于定时任务（如"3分钟后提醒"），条件函数简单返回True，使用cron表达式控制触发时间
               - 函数名示例：check_medicine_reminder_condition
               - 示例代码：
                 def check_medicine_reminder_condition():
                     # 定时任务的条件函数直接返回True
                     # 触发时间由subscribe_trigger的cron参数控制
                     return True

            步骤2️⃣ - 使用 write_code 编写触发动作函数
               - 该函数定义触发后要执行的具体操作
               - 可以在函数内调用 reply/notification 通知用户
               - 函数名示例：handle_medicine_reminder_action
               - 示例代码：
                 def handle_medicine_reminder_action():
                     notification("提醒：该吃药了！")

            步骤3️⃣ - 使用 write_code 编写订阅函数并执行
               - 调用 subscribe_trigger 将前两个函数注册到触发器系统
               - 使用delay参数设置延迟时间（单位：秒）
               - 在函数内调用 reply 告知用户注册成功
               - 函数名示例：subscribe_medicine_reminder
               - 示例代码：
                 def subscribe_medicine_reminder():
                     # delay参数：延迟多少秒后触发（3分钟 = 180秒）
                     result = subscribe_trigger(
                         condition_func='check_medicine_reminder_condition',
                         action_func='handle_medicine_reminder_action',
                         delay=180
                     )
                     reply(f"定时提醒触发器注册成功！3分钟后将提醒您吃药。")

            步骤4️⃣ - 使用 execute_code 执行订阅函数
               - 执行第3步编写的订阅函数，完成触发器注册

            🚨 常见错误示例（请避免）：
            ❌ 错误1：跳过步骤，直接使用 write_code 和 execute_code
               - 这会导致无法创建触发器，因为缺少条件判断函数

            ❌ 错误2：只写一个函数就执行
               - 触发器需要条件判断+触发动作两个独立函数

            ❌ 错误3：步骤顺序错误（如先write_code再write_condition_code）
               - 必须按照：条件 → 动作 → 订阅 的顺序进行

            ✅ 正确示例："3分钟后提醒我吃药"
               1. write_condition_code: check_medicine_reminder_condition（返回True）
               2. write_code: handle_medicine_reminder_action（发送提醒）
               3. write_code: subscribe_medicine_reminder（注册触发器+回复用户，使用delay=180）
               4. execute_code: subscribe_medicine_reminder（执行注册）

            【关键理念】
            💡 代码包含完整逻辑：
            - 检索信息回答用户问题
            - 设置定时、触发的延时、定期任务
            - 通过工具调用完成用户的需求
            - 通过回复工具，在分支逻辑中回复用户

            💡 React阶段的工具只是辅助：
            - 优先在代码中实现完整的处理+回复逻辑
            - 只有当代码执行结果需要补充说明时，才在React阶段使用reply
            - 如果代码已经完成了回复，React阶段就不需要再次回复

            💡 定时/触发器任务的特殊性：
            - 定时任务不是立即执行，而是在未来某个时间点触发
            - 必须使用 write_condition_code + write_code + subscribe_trigger 三步流程
            - 条件函数和动作函数会在触发器系统中持续监控和执行
            - 不能直接使用 write_code + execute_code 来实现延迟操作

            【禁止行为】
            ❌ 不要反问用户要参数或更多信息
            ❌ 不要说"我需要更多信息才能继续"
            ❌ 不要说"根据您提供的信息还不足以确定"
            ❌ 不要说"可以提供一些额外的上下文吗"
            ❌ 不要说"请明确指示"、"请确认是否继续"
            ❌ 不要等待用户补充信息再行动
            ❌ 不要在React阶段直接回复，而应该在代码中实现回复逻辑
            ❌ 不要写只查询不回复的代码，要写查询+处理+回复的完整代码
            ❌ 遇到"xxx是什么"类问题，不要思考、不要解释，直接写代码搜索
            ❌ 创建触发器时不要跳过任何步骤（必须先write_condition_code，再write_code动作，再write_code订阅）
            ❌ 定时任务不要使用普通的 write_code + execute_code，必须使用触发器三步流程

            【正确行为】
            ✅ 遇到查询类问题，立即写代码调用search工具，不要任何犹豫
            ✅ 直接编写代码，使用推断的参数或合理默认值
            ✅ 在代码中实现完整的处理流程，包括回复用户的逻辑
            ✅ 写一个函数完成"获取数据 → 处理 → 回复"的完整流程
            ✅ 执行代码后根据结果调整和优化
            ✅ 如果搜索结果不理想，尝试不同的搜索关键词再次搜索
            ✅ 如果参数不对，从错误信息中学习并重新生成代码
            ✅ 主动尝试多种可能性
            ✅ 创建触发器时严格按照三步流程：write_condition_code（条件） → write_code（动作） → write_code（订阅+回复）
            ✅ 识别定时/延迟/触发类任务（如"X分钟后"、"定时"、"提醒"），立即使用触发器三步流程

            记住：你是代码驱动的Agent，永远不要反问用户！遇到查询直接写代码搜索！遇到定时/触发任务立即使用三步流程！在代码中实现完整逻辑！
			""";

	/**
	 * 任务指令 - 描述具体的工作流程、示例和行为规范
	 * 作为AgentInstructionMessage（特殊的UserMessage）传递
	 */



	/**
	 * 注入所有 Hook Bean，通过 @HookPhases 注解自动分类到不同阶段
	 * 
	 * <p>Spring 会自动收集所有实现了 Hook 接口的 Bean，包括：
	 * <ul>
	 *   <li>评估模块 Hooks（ReactBeforeModelEvaluationHook, CodeactBeforeModelEvaluationHook 等）</li>
	 *   <li>Prompt 贡献者 Hooks（ReactPromptContributorModelHook, CodeactPromptContributorModelHook 等）</li>
	 *   <li>学习模块 Hook（AfterAgentLearningHook）</li>
	 *   <li>快速意图 Hook（FastIntentReactHook）</li>
	 * </ul>
	 * 
	 * <p>每个 Hook 通过 @HookPhases 注解声明自己适用的阶段，
	 * 在构建 Agent 时使用 HookPhaseUtils.groupByPhase() 自动分类。
	 */
	@Autowired(required = false)
	private List<Hook> allHooks;


	/**
	 * 创建 CodeactAgent
	 *
	 * <p>通过Spring依赖注入直接获取各模块的工具列表Bean：
	 * <ul>
	 * <li>replyCodeactTools - Reply模块的工具列表</li>
	 * <li>searchCodeactTools - Search模块的工具列表</li>
	 * <li>triggerCodeactTools - Trigger模块的工具列表</li>
	 * <li>unifiedSearchCodeactTool - 统一搜索工具（单独注入）</li>
	 * <li>mcpToolCallbackProvider - MCP工具提供者（由MCP Client Boot Starter自动注入）</li>
	 * </ul>
	 *
	 * <p>这种方式确保了Spring先创建这些依赖Bean，再创建CodeactAgent
	 *
	 * @param chatModel Spring AI的ChatModel
	 * @param replyCodeactTools Reply模块的工具列表（可选）
	 * @param searchCodeactToolFactory Search模块的工具工厂（可选）
	 * @param triggerCodeactTools Trigger模块的工具列表（可选）
	 * @param unifiedSearchCodeactTool 统一搜索工具（可选）
	 * @param mcpToolCallbackProvider MCP工具提供者（由MCP Client Boot Starter自动注入，可选）
	 */
	@Bean
	public CodeactAgent grayscaleCodeactAgent(
			ChatModel chatModel,
			@Autowired(required = false) List<ReplyCodeactTool> replyCodeactTools,
			@Autowired(required = false) SearchCodeactToolFactory searchCodeactToolFactory,
			@Autowired(required = false) List<TriggerCodeactTool> triggerCodeactTools,
			@Autowired(required = false) UnifiedSearchCodeactTool unifiedSearchCodeactTool,
			@Autowired(required = false) ToolCallbackProvider mcpToolCallbackProvider,
            @Autowired(required = false) ExperienceProvider experienceProvider,
            @Autowired(required = false) ExperienceExtensionProperties experienceExtensionProperties,
            @Autowired(required = false) FastIntentService fastIntentService) {

		logger.info("CodeactAgentConfig#grayscaleCodeactAgent - reason=创建 CodeactAgent");
		logger.info("CodeactAgentConfig#grayscaleCodeactAgent - reason=配置 MemorySaver 以支持多轮对话上下文保持");
		logger.warn("CodeactAgentConfig#grayscaleCodeactAgent - reason=临时禁用 streaming 模式以排查循环问题");

		/*-----------准备工具-----------*/
		List<CodeactTool> allCodeactTools = new ArrayList<>();

		// 添加UnifiedSearchCodeactTool
		if (unifiedSearchCodeactTool != null) {
			allCodeactTools.add(unifiedSearchCodeactTool);
			logger.info("CodeactAgentConfig#grayscaleCodeactAgent - reason=添加UnifiedSearchCodeactTool");
		}

		// 添加Search工具
		if (searchCodeactToolFactory != null) {
			List<SearchCodeactTool> searchTools = searchCodeactToolFactory.createTools();
			if (!searchTools.isEmpty()) {
				allCodeactTools.addAll(searchTools);
				logger.info("CodeactAgentConfig#grayscaleCodeactAgent - reason=添加SearchCodeactTools, count={}", searchTools.size());
			}
		}

		// 添加Reply工具
		if (replyCodeactTools != null && !replyCodeactTools.isEmpty()) {
			allCodeactTools.addAll(replyCodeactTools);
			logger.info("CodeactAgentConfig#grayscaleCodeactAgent - reason=添加ReplyCodeactTools, count={}", replyCodeactTools.size());
		}

		// 添加Trigger工具
		if (triggerCodeactTools != null && !triggerCodeactTools.isEmpty()) {
			allCodeactTools.addAll(triggerCodeactTools);
			logger.info("CodeactAgentConfig#grayscaleCodeactAgent - reason=添加TriggerCodeactTools, count={}", triggerCodeactTools.size());
		}

		// 添加 MCP 动态工具（通过 MCP Client Boot Starter 注入的 ToolCallbackProvider）
		// 配置方式参考 mcp-client-spring-boot.md，在 application.properties 中配置：
		// spring.ai.mcp.client.streamable-http.connections.my-server.url=https://mcp.example.com
		// spring.ai.mcp.client.streamable-http.connections.my-server.endpoint=/mcp
		if (mcpToolCallbackProvider != null) {
			List<CodeactTool> mcpTools = createMcpDynamicTools(mcpToolCallbackProvider);
			allCodeactTools.addAll(mcpTools);
			logger.info("CodeactAgentConfig#grayscaleCodeactAgent - reason=Added MCP dynamic tools, count={}", mcpTools.size());
		} else {
			logger.warn("CodeactAgentConfig#grayscaleCodeactAgent - reason=ToolCallbackProvider not found, MCP dynamic tools disabled. " +
					"Check: 1. spring-ai-starter-mcp-client dependency; 2. MCP connection config in application.yml");
		}

		// 添加 HTTP 动态工具
		List<CodeactTool> httpTools = createHttpDynamicTools();
		if (!httpTools.isEmpty()) {
			allCodeactTools.addAll(httpTools);
			logger.info("CodeactAgentConfig#grayscaleCodeactAgent - reason=添加HTTP动态工具, count={}", httpTools.size());
		}

		logger.info("CodeactAgentConfig#grayscaleCodeactAgent - reason=合并后CodeactTool总数, count={}", allCodeactTools.size());

		// React阶段不需要外部工具，write_code/execute_code/write_condition_code会在CodeactAgent内部自动添加
		logger.info("CodeactAgentConfig#grayscaleCodeactAgent - reason=React阶段使用内置工具(write_code, execute_code, write_condition_code)");


        /*---------------------准备hooks-------------------*/
        // 使用 HookPhaseUtils.groupByPhase() 根据 @HookPhases 注解自动分类
        // 这样 Hook 的阶段由注解自己声明，而不是配置类手动指定
        Map<AgentPhase, List<Hook>> hooksByPhase = HookPhaseUtils.groupByPhase(allHooks);
        List<Hook> reactHooks = hooksByPhase.get(AgentPhase.REACT);
        List<Hook> codeactHooks = hooksByPhase.get(AgentPhase.CODEACT);

        logger.info("CodeactAgentConfig#grayscaleCodeactAgent - reason=按 @HookPhases 注解自动分类 Hooks, " +
                "total={}, reactPhase={}, codeactPhase={}",
                allHooks != null ? allHooks.size() : 0,
                reactHooks.size(),
                codeactHooks.size());

        // 打印各阶段 Hook 详情（调试用）
        if (logger.isDebugEnabled()) {
            HookPhaseUtils.logHookPhases(allHooks);
        }

		CodeactAgent.CodeactAgentBuilder builder = CodeactAgent.builder()
				.name("CodeactAgent")
				.description("A code-driven agent that solves problems by writing and executing Python code")
				.systemPrompt(SYSTEM_PROMPT)   // 系统角色定义（SystemMessage）
				.model(chatModel)
                .codingChatModel(chatModel)
				.language(Language.PYTHON)     // CodeactAgentBuilder特有方法
				// 使用 qwen-coder-plus 模型进行代码生成
				.codeGenerationModelName("qwen3-coder-plus")
				.enableInitialCodeGen(true)
				.allowIO(false)
				.allowNativeAccess(false)
				.executionTimeout(30000)
                .tools(replyCodeactTools != null ? replyCodeactTools.toArray(new ToolCallback[0]) : new ToolCallback[0])
                .codeactTools(allCodeactTools)
                .hooks(reactHooks)
                .subAgentHooks(codeactHooks)
				.experienceProvider(experienceProvider)
				.experienceExtensionProperties(experienceExtensionProperties)
				.fastIntentService(fastIntentService)
				.saver(new MemorySaver()); // 🔥 添加 MemorySaver 支持多轮对话上下文保持（放在最后）
		return builder.build();
	}

	/**
	 * Create MCP dynamic tools.
	 *
	 * <p>Uses MCP Client Boot Starter auto-wired ToolCallbackProvider,
	 * adapted to CodeactTool via McpDynamicToolFactory.
	 *
	 * <p>Configure MCP connections in application.properties:
	 * <pre>
	 * # Streamable HTTP Transport
	 * spring.ai.mcp.client.streamable-http.connections.my-server.url=https://your-mcp-server.example.com
	 * spring.ai.mcp.client.streamable-http.connections.my-server.endpoint=/mcp
	 * </pre>
	 *
	 * @param toolCallbackProvider MCP ToolCallbackProvider (auto-wired by MCP Client Boot Starter)
	 * @return MCP dynamic tools list
	 */
	private List<CodeactTool> createMcpDynamicTools(ToolCallbackProvider toolCallbackProvider) {
		logger.info("CodeactAgentConfig#createMcpDynamicTools - reason=Creating MCP dynamic tools");

		try {
			// Use MCP Server name as class name prefix (corresponds to mcp-servers.json config name)
			McpDynamicToolFactory factory = McpDynamicToolFactory.builder()
					.toolCallbackProvider(toolCallbackProvider)
					.defaultTargetClassNamePrefix("mcp-server")  // MCP Server name
					.defaultTargetClassDescription("MCP tools providing various capabilities")
					.build();

			// Create factory context and generate tools
			DynamicToolFactoryContext context = DynamicToolFactoryContext.builder().build();
			List<CodeactTool> tools = factory.createTools(context);

			logger.info("CodeactAgentConfig#createMcpDynamicTools - reason=MCP dynamic tools created, count={}", tools.size());

			// Log created tool names
			for (CodeactTool tool : tools) {
				logger.info("CodeactAgentConfig#createMcpDynamicTools - reason=Created MCP tool, toolName={}, targetClass={}",
						tool.getToolDefinition().name(), tool.getCodeactMetadata().targetClassName());
			}

			return tools;
		}
		catch (Exception e) {
			logger.error("CodeactAgentConfig#createMcpDynamicTools - reason=MCP dynamic tool creation failed, error={}", e.getMessage(), e);
			return new ArrayList<>();
		}
	}

	/**
	 * Create HTTP dynamic tools.
	 *
	 * <p>Example of creating HTTP-based dynamic tools from OpenAPI spec.
	 * This method is disabled by default - customize it for your own HTTP APIs.
	 *
	 * @return HTTP dynamic tools list (empty by default)
	 */
	private List<CodeactTool> createHttpDynamicTools() {
		logger.info("CodeactAgentConfig#createHttpDynamicTools - reason=HTTP dynamic tools disabled by default");
		// HTTP dynamic tools are disabled by default.
		// To enable, provide your own OpenAPI spec and endpoint configuration.
		// Example:
		// String openApiSpec = "{ ... your OpenAPI spec ... }";
		// OpenApiSpec spec = OpenApiSpec.builder(openApiSpec).baseUrl("https://api.example.com").build();
		// HttpDynamicToolFactory factory = HttpDynamicToolFactory.builder().openApiSpec(spec).build();
		// return factory.createTools(DynamicToolFactoryContext.builder().build());
		return new ArrayList<>();
	}
}

