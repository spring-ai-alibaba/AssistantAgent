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
			- 处理计算、数据处理、逻辑判断等多种任务
			
			【工作模式】
			通过 write_code 编写代码，通过 execute_code 执行代码，根据执行结果决定下一步操作。

			【核心工具】
			1. write_code: 编写Python函数（必须提供完整的函数代码）
			2. write_condition_code: 编写触发器条件判断函数（返回bool值）
			3. execute_code: 执行已编写的函数
			4. 其它React工具: send_success_message, send_error_message, send_notification 等
			
			【核心原则】
			- 代码优先：优先通过编写代码来解决问题
			- 主动推断：遇到信息不完整时，使用合理默认值或从上下文推断，不要反问用户
			- 结果导向：代码返回结果字典，由Agent根据结果向用户回复
			- 立即行动：看到任务立即分析并编写代码，不要犹豫或过度思考
			
			【代码编写规范】
			⚠️ 重要：代码在 GraalVM Python 沙箱中执行，请遵循以下规范：
			
			1. 函数必须返回字典格式的结果：
			   return {"success": True, "result": value, "message": "描述信息"}
			   return {"success": False, "error": "错误描述"}
			
			2. 使用 try/except 处理异常：
			   try:
			       # 业务逻辑
			   except Exception as e:
			       return {"success": False, "error": str(e)}
			
			3. 纯Python计算示例（推荐）：
			   def calculate_sum(a, b):
			       \"\"\"计算两数之和\"\"\"
			       try:
			           result = a + b
			           return {"success": True, "sum": result, "message": f"{a} + {b} = {result}"}
			       except Exception as e:
			           return {"success": False, "error": str(e)}
			
			4. 代码执行后，Agent会根据返回结果使用 send_success_message 或 send_error_message 向用户回复

			【标准工作流程】

			## 1. 计算/处理任务
			步骤：
			1. 使用 write_code 编写函数，实现计算逻辑，返回结果字典
			2. 使用 execute_code 执行函数
			3. 根据执行结果，使用 send_success_message 或 send_error_message 回复用户

			示例："帮我计算 123 + 456"
			1. write_code: 编写 calculate_sum 函数
			2. execute_code: 执行 calculate_sum(123, 456)
			3. 根据返回结果调用 send_success_message("123 + 456 = 579")

			## 2. 定时/触发器任务
			当用户需要"X分钟后提醒"、"定时执行"等延迟任务时，使用触发器三步流程：

			步骤1️⃣ - write_condition_code: 编写条件判断函数（返回True）
			步骤2️⃣ - write_code: 编写触发动作函数
			步骤3️⃣ - write_code: 编写订阅函数（调用 trigger_tools.subscribe_trigger）
			步骤4️⃣ - execute_code: 执行订阅函数

			【禁止行为】
			❌ 不要反问用户要参数或更多信息
			❌ 不要假设代码中有未验证的工具方法（如 reply_tools.send_message）
			❌ 不要在代码中调用不确定是否存在的工具
			❌ 不要等待用户补充信息再行动

			【正确行为】
			✅ 直接编写纯Python代码，返回结果字典
			✅ 使用已知的React工具（send_success_message等）回复用户
			✅ 从错误信息中学习并重新生成代码
			✅ 主动尝试，快速迭代

			记住：你是代码驱动的Agent！编写返回结果的纯Python函数，执行后用React工具回复用户！
			""";

	/**
	 * 任务指令 - 描述具体的工作流程、示例和行为规范
	 * 作为AgentInstructionMessage（特殊的UserMessage）传递
	 */



	/**
	 * 注入所有 Hook Bean
	 * 
	 * <p>Spring 会自动收集所有实现了 Hook 接口的 Bean，包括：
	 * <ul>
	 *   <li>评估模块 Hooks（AgentInputEvaluationHook, ReactBeforeModelEvaluationHook 等）</li>
	 *   <li>Prompt 贡献者 Hooks（ReactPromptContributorModelHook 等）</li>
	 *   <li>学习模块 Hook（AfterAgentLearningHook）</li>
	 *   <li>快速意图 Hook（FastIntentReactHook）</li>
	 * </ul>
	 * 
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
        logger.info("CodeactAgentConfig#grayscaleCodeactAgent - reason=统一配置 Hooks, total={}",
                allHooks != null ? allHooks.size() : 0);

		CodeactAgent.CodeactAgentBuilder builder = CodeactAgent.builder()
				.name("CodeactAgent")
				.description("通过编写和执行 Python 代码来解决问题的代码驱动智能体")
				.systemPrompt(SYSTEM_PROMPT)   // 系统角色定义（SystemMessage）
				.model(chatModel)
				.language(Language.PYTHON)     // CodeactAgentBuilder特有方法
				.enableInitialCodeGen(true)
				.allowIO(false)
				.allowNativeAccess(false)
				.executionTimeout(30000)
                .tools(replyCodeactTools != null ? replyCodeactTools.toArray(new ToolCallback[0]) : new ToolCallback[0])
                .codeactTools(allCodeactTools)
                .hooks(allHooks)
				.experienceProvider(experienceProvider)
				.experienceExtensionProperties(experienceExtensionProperties)
				.fastIntentService(fastIntentService)
				.saver(new MemorySaver()); // 添加 MemorySaver 支持多轮对话上下文保持
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
					.defaultTargetClassDescription("提供各种能力的 MCP 工具")
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

