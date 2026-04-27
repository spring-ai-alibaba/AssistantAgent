package com.alibaba.assistant.agent.extension.experience.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * 经验中仅在沙箱内被使用的附件条目（脚本、静态资源、评估数据等）。
 *
 * <p>{@link Experience#getAssets()} 中的条目 <strong>不会</strong> 被 {@code read_exp_doc}
 * 披露给模型；CLI 执行路径会把它们写入沙箱 {@code /workspace/<path>}，由脚本/命令
 * 自行消费。{@code read_exp} 只返回 manifest（不含 {@code content}）。
 *
 * <p>典型来源：{@code scripts/**}、{@code assets/**}、{@code evals/**}、
 * {@code package.json} 等。
 */
public class AssetEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * skill 包内的相对路径，如 {@code scripts/analyze.py}。
     */
    private String path;

    /**
     * MIME 类型。
     */
    private String mediaType;

    /**
     * 角色分类：{@code script} / {@code asset} / {@code eval} / {@code metadata}。
     */
    private String role;

    /**
     * 简要描述（由 H1 / LLM 总结 / fallback 产生）。
     */
    private String description;

    /**
     * 原始字节数。
     */
    private Long size;

    /**
     * 内容；文本类以 UTF-8 字符串存储，二进制类以 Base64 编码。null 表示需要通过
     * {@link #contentRef} 从外部存储拉取。
     */
    private String content;

    /**
     * 外部存储（如 OSS）引用；目前预留字段，未启用。
     */
    private String contentRef;

    /**
     * 内容 SHA-256 摘要，便于缓存 description。
     */
    private String contentHash;

    public AssetEntry() {
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentRef() {
        return contentRef;
    }

    public void setContentRef(String contentRef) {
        this.contentRef = contentRef;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
}
