package com.alibaba.assistant.agent.extension.experience.model;

/**
 * 经验类型枚举
 *
 * @author Assistant Agent Team
 */
public enum ExperienceType {

    /**
     * React经验 - Agent行为经验与策略建议（流程经验）
     */
    REACT,

    /**
     * 工具经验 - 单个工具的使用经验（MCP/A2A/HTTP工具）
     */
    TOOL,

    /**
     * 通用常识经验 - 规范、注意事项、安全提示等
     */
    COMMON
}
