package com.alibaba.assistant.agent.extension.experience.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

/**
 * 常识经验注入假工具
 *
 * <p>这个工具不执行任何实际操作，仅用于支持 CommonSenseExperienceModelHook
 * 通过 AssistantMessage + ToolResponseMessage 配对方式注入常识经验。
 *
 * <p>注册这个工具可以让 ReactAgent 的路由逻辑正确识别和处理经验注入。
 *
 * @author Assistant Agent Team
 */
public class CommonSenseInjectionTool {

    private static final Logger log = LoggerFactory.getLogger(CommonSenseInjectionTool.class);

    /**
     * 常识经验注入方法 - 这是一个占位工具，实际不会被调用
     *
     * <p>Hook 会预先构造 AssistantMessage(toolCall) + ToolResponseMessage 配对，
     * 模拟已经完成的工具调用，所以这个方法不会被真正执行。
     *
     * @return 空字符串（实际不会被调用）
     */
    @Tool(name = "common_sense_injection",
          description = "内部工具：用于注入常识经验到对话上下文。此工具由系统自动调用，无需用户手动触发。")
    public String inject() {
        log.warn("CommonSenseInjectionTool#inject - reason=此工具不应被直接调用，仅作为占位工具存在");
        return "";
    }
}

