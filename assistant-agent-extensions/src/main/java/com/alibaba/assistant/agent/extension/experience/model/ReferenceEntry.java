package com.alibaba.assistant.agent.extension.experience.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * 经验中渐进披露第三层（L3）可供模型读取的参考文档条目。
 *
 * <p>{@link Experience#getReferences()} 中的条目在 {@code read_exp} 响应里仅以
 * {@code path/mediaType/description/size} 这些字段暴露为 manifest；完整内容
 * 仅能通过 {@code read_exp_doc} 按需读取。
 *
 * <p>典型来源：{@code references/**}、根目录的 {@code *.md}/{@code *.yaml}、
 * agents 目录下的 markdown 等面向 LLM 的文档。
 */
public class ReferenceEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * skill 包内的相对路径，如 {@code references/domains/session.md}。
     */
    private String path;

    /**
     * MIME 类型，如 {@code text/markdown}、{@code application/yaml}。
     */
    private String mediaType;

    /**
     * 文档描述（由 H1 标题 / frontmatter / LLM 总结 / 回退文案生成）。
     */
    private String description;

    /**
     * 文档正文（UTF-8 文本）。
     */
    private String content;

    /**
     * 正文的 SHA-256 摘要，用于复用 description 缓存。
     */
    private String contentHash;

    /**
     * 原始字节数。
     */
    private Long size;

    public ReferenceEntry() {
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }
}
