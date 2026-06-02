package com.alibaba.assistant.agent.management.spi;

/**
 * 为 skill 包导入阶段提供 reference 文档描述生成能力的 SPI。
 *
 * <p>调用方按以下优先级选择 description：
 * <ol>
 *     <li>.md 首个非空 H1 标题</li>
 *     <li>YAML frontmatter 中的 {@code description:} 字段</li>
 *     <li>本 SPI：调用 LLM/默认实现对正文做总结</li>
 *     <li>回退文案：{@code "(no description) " + path}</li>
 * </ol>
 */
public interface ReferenceSummarizer {

    /**
     * @param path        skill 包内的相对路径，仅用于日志/提示
     * @param content     文档正文（UTF-8）
     * @return 一段简短描述；返回 null/空串表示无法总结，调用方应使用回退文案
     */
    String summarize(String path, String content);
}
