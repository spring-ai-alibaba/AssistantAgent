package com.alibaba.assistant.agent.extension.experience.model;

/**
 * 经验披露策略枚举
 * 对齐 SKILLS 标准（agentskills.io）的渐进式披露机制
 *
 * @author Assistant Agent Team
 */
public enum DisclosureStrategy {

    /**
     * 渐进式披露 - 搜索返回摘要(name+description)，需要显式读取才获取全文
     * 对应 SKILLS 标准 Level 1→Level 2 模式
     */
    PROGRESSIVE,

    /**
     * 直接披露 - 高置信、短内容经验可直接注入 prompt，无需再次 read
     */
    DIRECT
}
