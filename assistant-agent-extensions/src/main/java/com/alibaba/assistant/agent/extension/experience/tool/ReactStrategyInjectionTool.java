package com.alibaba.assistant.agent.extension.experience.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

/**
 * React策略经验注入假工具
 *
 * <p>这个工具不执行任何实际操作，仅用于支持 ReactExperienceAgentHook
 * 通过 AssistantMessage + ToolResponseMessage 配对方式注入 React 策略经验。
 *
 * <p>注册这个工具可以让 ReactAgent 的路由逻辑正确识别和处理经验注入。
 *
 * <p><b>重要</b>：LLM 不应该主动调用这个工具。如果 LLM 尝试调用，
 * 会返回错误提示，引导 LLM 使用正确的工具。
 *
 * @author Assistant Agent Team
 */
public class ReactStrategyInjectionTool {

    private static final Logger log = LoggerFactory.getLogger(ReactStrategyInjectionTool.class);

    /**
     * 工具名称常量，需要与 ReactExperienceAgentHook 中使用的名称保持一致
     */
    public static final String TOOL_NAME = "react_strategy_injection";

    /**
     * React策略经验注入方法 - 这是一个内部系统工具，禁止 LLM 主动调用
     *
     * <p>Hook 会预先构造 AssistantMessage(toolCall) + ToolResponseMessage 配对，
     * 模拟已经完成的工具调用，所以这个方法不应该被真正执行。
     *
     * <p>如果 LLM 主动调用了这个工具，说明 LLM 没有遵循工具描述，
     * 此时返回错误提示，引导 LLM 正确行为。
     *
     * @return 错误提示信息
     */
    @Tool(name = TOOL_NAME,
          description = "[内部系统工具 - 请勿调用] " +
                  "这是一个由框架自动调用的内部系统工具，用于注入 React 策略指导。" +
                  "你绝对不能直接调用此工具。" +
                  "你需要的策略指导已经在对话上下文中提供。" +
                  "如果需要执行操作，请使用适当的工具，如 write_code、execute_code 等。" +
                  "调用此工具将导致错误。")
    public String inject() {
        log.error("ReactStrategyInjectionTool#inject - reason=LLM错误调用了内部系统工具, " +
                "这表明LLM没有遵循工具描述中的禁止调用说明");

        return """
            {
                "error": true,
                "error_type": "FORBIDDEN_TOOL_CALL",
                "message": "ERROR: You called react_strategy_injection which is a forbidden internal system tool. This tool is automatically invoked by the framework to provide you with React strategy guidance - you should NEVER call it directly. The strategy guidance you need is already provided in the conversation. Please proceed with your task using the appropriate tools (like write_code, execute_code, send_message, etc.). Do NOT attempt to call react_strategy_injection again."
            }
            """;
    }
}
