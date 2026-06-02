package com.alibaba.assistant.agent.extension.experience.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
     * 简要名称（原 title 字段，重命名对齐 SKILLS 标准）
     */
    private String name;

    /**
     * 经验摘要描述（用于搜索结果预览，对齐 SKILLS 标准 Level 1）
     */
    private String description;

    /**
     * 经验主体内容
     */
    private String content;

    /**
     * 披露策略（对齐 SKILLS 标准渐进式披露机制）
     */
    private DisclosureStrategy disclosureStrategy;

    /**
     * 关联工具名列表（CodeactTool 名称）
     */
    private List<String> associatedTools = new ArrayList<>();

    /**
     * 关联经验ID列表
     */
    private List<String> relatedExperiences = new ArrayList<>();

    /**
     * 可执行产物（FastPath Intent 使用）；不影响既有 prompt 注入逻辑
     */
    private ExperienceArtifact artifact;

    /**
     * FastPath Intent 配置（每条经验可选）
     */
    private FastIntentConfig fastIntentConfig;

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

    /**
     * 渐进披露第三层（L3）面向 LLM 的参考文档。仅通过 {@code read_exp_doc} 按路径读取。
     */
    private List<ReferenceEntry> references = new ArrayList<>();

    /**
     * 仅在沙箱内使用的附件（脚本/静态资源/评估数据）。不会通过 {@code read_exp_doc} 披露。
     */
    private List<AssetEntry> assets = new ArrayList<>();

    public Experience() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.metadata = new ExperienceMetadata();
    }

    public Experience(ExperienceType type, String name, String content) {
        this();
        this.type = type;
        this.name = name;
        this.content = content;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * 兼容别名：返回 name 字段值
     */
    public String getTitle() {
        return name;
    }

    /**
     * 兼容别名：设置 name 字段值
     */
    public void setTitle(String title) {
        this.name = title;
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

    /**
     * 获取有效内容：直接返回 content
     */
    public String getEffectiveContent() {
        return content;
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

    public DisclosureStrategy getDisclosureStrategy() {
        return disclosureStrategy;
    }

    public void setDisclosureStrategy(DisclosureStrategy disclosureStrategy) {
        this.disclosureStrategy = disclosureStrategy;
    }

    public List<String> getAssociatedTools() {
        return associatedTools;
    }

    public void setAssociatedTools(List<String> associatedTools) {
        this.associatedTools = associatedTools != null ? associatedTools : new ArrayList<>();
    }

    public List<String> getRelatedExperiences() {
        return relatedExperiences;
    }

    public void setRelatedExperiences(List<String> relatedExperiences) {
        this.relatedExperiences = relatedExperiences != null ? relatedExperiences : new ArrayList<>();
    }

    public ExperienceMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ExperienceMetadata metadata) {
        this.metadata = metadata != null ? metadata : new ExperienceMetadata();
    }

    public List<ReferenceEntry> getReferences() {
        return references;
    }

    public void setReferences(List<ReferenceEntry> references) {
        this.references = references != null ? references : new ArrayList<>();
    }

    public List<AssetEntry> getAssets() {
        return assets;
    }

    public void setAssets(List<AssetEntry> assets) {
        this.assets = assets != null ? assets : new ArrayList<>();
    }

    /**
     * 更新经验时自动更新时间戳
     */
    public void touch() {
        this.updatedAt = Instant.now();
    }
}
