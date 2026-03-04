package com.alibaba.assistant.agent.extension.experience.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 经验核心领域对象
 *
 * @author Assistant Agent Team
 */
public class Experience {

    /**
     * 经验唯一ID
     */
    private String id;

    /**
     * 经验类型
     */
    private ExperienceType type;

    /**
     * 简要标题或名称
     */
    private String title;

    /**
     * 经验主体内容
     */
    private String content;

    /**
     * 可执行产物（FastPath Intent 使用）；不影响既有 prompt 注入逻辑
     */
    private ExperienceArtifact artifact;

    /**
     * FastPath Intent 配置（每条经验可选）
     */
    private FastIntentConfig fastIntentConfig;

    /**
     * 经验生效范围
     */
    private ExperienceScope scope;

    /**
     * 经验所属用户或主体标识
     */
    private String ownerId;

    /**
     * 经验所属的项目或仓库
     */
    private String projectId;

    /**
     * 仓库ID
     */
    private String repoId;

    /**
     * 编程语言或自然语言
     */
    private String language;

    /**
     * 标签
     */
    private Set<String> tags = new HashSet<>();

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;

    /**
     * 附加元信息
     */
    private ExperienceMetadata metadata;

    public Experience() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.metadata = new ExperienceMetadata();
    }

    public Experience(ExperienceType type, String title, String content, ExperienceScope scope) {
        this();
        this.type = type;
        this.title = title;
        this.content = content;
        this.scope = scope;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ExperienceType getType() {
        return type;
    }

    public void setType(ExperienceType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    /**
     * 获取有效内容：合并 content 与 artifact.code，两者均有值时拼接返回
     * <p>
     * 先拼接 content（如有），再拼接从 artifact.code 生成的内容（如有）
     */
    public String getEffectiveContent() {
        StringBuilder result = new StringBuilder();

        // 1. 追加 content
        if (content != null && !content.isBlank()) {
            result.append(content);
        }

        // 2. 追加 artifact.code 生成的内容
        if (artifact != null && artifact.getCode() != null) {
            ExperienceArtifact.CodeArtifact codeArtifact = artifact.getCode();
            if (codeArtifact.getCode() != null && !codeArtifact.getCode().isBlank()) {
                if (!result.isEmpty()) {
                    result.append("\n\n");
                }
                result.append(buildContentFromCodeArtifact(codeArtifact));
            }
        }

        return result.isEmpty() ? content : result.toString();
    }

    /**
     * 从 CodeArtifact 自动生成 content
     */
    private String buildContentFromCodeArtifact(ExperienceArtifact.CodeArtifact codeArtifact) {
        StringBuilder sb = new StringBuilder();

        // 添加描述
        if (codeArtifact.getDescription() != null && !codeArtifact.getDescription().isBlank()) {
            sb.append(codeArtifact.getDescription()).append("\n\n");
        }

        // 添加代码块
        String lang = codeArtifact.getLanguage() != null ? codeArtifact.getLanguage() : "python";
        sb.append("```").append(lang).append("\n");
        sb.append(codeArtifact.getCode());
        if (!codeArtifact.getCode().endsWith("\n")) {
            sb.append("\n");
        }
        sb.append("```");

        return sb.toString();
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ExperienceArtifact getArtifact() {
        return artifact;
    }

    public void setArtifact(ExperienceArtifact artifact) {
        this.artifact = artifact;
    }

    public FastIntentConfig getFastIntentConfig() {
        return fastIntentConfig;
    }

    public void setFastIntentConfig(FastIntentConfig fastIntentConfig) {
        this.fastIntentConfig = fastIntentConfig;
    }

    public ExperienceScope getScope() {
        return scope;
    }

    public void setScope(ExperienceScope scope) {
        this.scope = scope;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getRepoId() {
        return repoId;
    }

    public void setRepoId(String repoId) {
        this.repoId = repoId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags != null ? tags : new HashSet<>();
    }

    public void addTag(String tag) {
        this.tags.add(tag);
    }

    public void removeTag(String tag) {
        this.tags.remove(tag);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public ExperienceMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ExperienceMetadata metadata) {
        this.metadata = metadata != null ? metadata : new ExperienceMetadata();
    }

    /**
     * 更新经验时自动更新时间戳
     */
    public void touch() {
        this.updatedAt = Instant.now();
    }
}
