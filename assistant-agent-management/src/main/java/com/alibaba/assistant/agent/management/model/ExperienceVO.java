package com.alibaba.assistant.agent.management.model;

import com.alibaba.assistant.agent.extension.experience.model.DisclosureStrategy;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceMetadata;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.model.FastIntentConfig;
import com.alibaba.assistant.agent.extension.experience.model.ReferenceEntry;
import com.alibaba.assistant.agent.extension.experience.model.AssetEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExperienceVO {

    private static final String GLOBAL_TENANT_ID = "global";

    private String id;
    private ExperienceType type;
    private String name;
    private String description;
    private String content;
    private DisclosureStrategy disclosureStrategy;
    private Set<String> tags = new HashSet<>();
    private List<String> tenantIdList = new ArrayList<>();
    private String sourceInfo;
    private List<String> associatedTools = new ArrayList<>();
    private List<String> relatedExperiences = new ArrayList<>();
    private ExperienceArtifact artifact;
    private FastIntentConfig fastIntentConfig;
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, Object> properties = new HashMap<>();
    private String toolInvocationPath;
    private List<ReferenceEntry> references = new ArrayList<>();
    private List<AssetView> assets = new ArrayList<>();

    public static ExperienceVO fromExperience(Experience exp) {
        ExperienceVO vo = new ExperienceVO();
        vo.setId(exp.getId());
        vo.setType(exp.getType());
        vo.setName(exp.getName());
        vo.setDescription(exp.getDescription());
        vo.setContent(exp.getContent());
        vo.setDisclosureStrategy(exp.getDisclosureStrategy());
        vo.setTags(exp.getTags() != null ? new HashSet<>(exp.getTags()) : new HashSet<>());
        vo.setAssociatedTools(exp.getAssociatedTools() != null ? new ArrayList<>(exp.getAssociatedTools()) : new ArrayList<>());
        vo.setRelatedExperiences(exp.getRelatedExperiences() != null ? new ArrayList<>(exp.getRelatedExperiences()) : new ArrayList<>());
        vo.setArtifact(exp.getArtifact());
        vo.setFastIntentConfig(exp.getFastIntentConfig());
        vo.setCreatedAt(exp.getCreatedAt());
        vo.setUpdatedAt(exp.getUpdatedAt());
        if (exp.getReferences() != null) {
            vo.setReferences(new ArrayList<>(exp.getReferences()));
        }
        if (exp.getAssets() != null) {
            List<AssetView> assetViews = new ArrayList<>();
            for (AssetEntry asset : exp.getAssets()) {
                assetViews.add(AssetView.fromAsset(asset));
            }
            vo.setAssets(assetViews);
        }

        ExperienceMetadata metadata = exp.getMetadata();
        if (metadata != null) {
            vo.setSourceInfo(metadata.getSource());
            vo.setTenantIdList(toViewTenantIdList(metadata.getTenantIdList()));
            if (metadata.getProperties() != null) {
                Map<String, Object> properties = new HashMap<>(metadata.getProperties());
                properties.remove("tenantIdList");
                vo.setProperties(properties);
                Object toolInvocationPath = properties.get("toolInvocationPath");
                if (toolInvocationPath != null) {
                    vo.setToolInvocationPath(String.valueOf(toolInvocationPath));
                }
            }
        }
        return vo;
    }

    public Experience toExperience() {
        Experience exp = new Experience();
        exp.setId(this.id);
        exp.setType(this.type);
        exp.setName(this.name);
        exp.setDescription(this.description);
        exp.setContent(this.content);
        exp.setDisclosureStrategy(this.disclosureStrategy);
        exp.setTags(this.tags != null ? new HashSet<>(this.tags) : new HashSet<>());
        exp.setAssociatedTools(this.associatedTools != null ? new ArrayList<>(this.associatedTools) : new ArrayList<>());
        exp.setRelatedExperiences(this.relatedExperiences != null ? new ArrayList<>(this.relatedExperiences) : new ArrayList<>());
        exp.setArtifact(this.artifact);
        exp.setFastIntentConfig(this.fastIntentConfig);
        exp.setCreatedAt(this.createdAt);
        exp.setUpdatedAt(this.updatedAt);

        ExperienceMetadata metadata = exp.getMetadata();
        metadata.setSource(this.sourceInfo);
        if (this.properties != null) {
            metadata.setProperties(new HashMap<>(this.properties));
        }
        metadata.setTenantIdList(this.tenantIdList);
        return exp;
    }

    public void applyTo(Experience exp) {
        if (this.type != null) {
            exp.setType(this.type);
        }
        if (this.name != null) {
            exp.setName(this.name);
        }
        if (this.description != null) {
            exp.setDescription(this.description);
        }
        if (this.content != null) {
            exp.setContent(this.content);
        }
        exp.setDisclosureStrategy(this.disclosureStrategy);
        if (this.tags != null) {
            exp.setTags(new HashSet<>(this.tags));
        }
        exp.getMetadata().setTenantIdList(this.tenantIdList);
        if (this.associatedTools != null) {
            exp.setAssociatedTools(new ArrayList<>(this.associatedTools));
        }
        if (this.relatedExperiences != null) {
            exp.setRelatedExperiences(new ArrayList<>(this.relatedExperiences));
        }
        exp.setArtifact(this.artifact);
        exp.setFastIntentConfig(this.fastIntentConfig);

        ExperienceMetadata metadata = exp.getMetadata();
        if (this.sourceInfo != null) {
            metadata.setSource(this.sourceInfo);
        }
        if (this.properties != null && !this.properties.isEmpty()) {
            for (Map.Entry<String, Object> entry : this.properties.entrySet()) {
                metadata.putProperty(entry.getKey(), entry.getValue());
            }
        }
        exp.touch();
    }

    // --- Getters and Setters ---

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

    public DisclosureStrategy getDisclosureStrategy() {
        return disclosureStrategy;
    }

    public void setDisclosureStrategy(DisclosureStrategy disclosureStrategy) {
        this.disclosureStrategy = disclosureStrategy;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public List<String> getTenantIdList() {
        return tenantIdList;
    }

    public void setTenantIdList(List<String> tenantIdList) {
        this.tenantIdList = tenantIdList;
    }

    public String getSourceInfo() {
        return sourceInfo;
    }

    public void setSourceInfo(String sourceInfo) {
        this.sourceInfo = sourceInfo;
    }

    public List<String> getAssociatedTools() {
        return associatedTools;
    }

    public void setAssociatedTools(List<String> associatedTools) {
        this.associatedTools = associatedTools;
    }

    public List<String> getRelatedExperiences() {
        return relatedExperiences;
    }

    public void setRelatedExperiences(List<String> relatedExperiences) {
        this.relatedExperiences = relatedExperiences;
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

    private static List<String> toViewTenantIdList(List<String> tenantIdList) {
        if (tenantIdList == null || tenantIdList.isEmpty()) {
            return new ArrayList<>(List.of(GLOBAL_TENANT_ID));
        }
        return new ArrayList<>(tenantIdList);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public String getToolInvocationPath() {
        return toolInvocationPath;
    }

    public void setToolInvocationPath(String toolInvocationPath) {
        this.toolInvocationPath = toolInvocationPath;
    }

    public List<ReferenceEntry> getReferences() {
        return references;
    }

    public void setReferences(List<ReferenceEntry> references) {
        this.references = references;
    }

    public List<AssetView> getAssets() {
        return assets;
    }

    public void setAssets(List<AssetView> assets) {
        this.assets = assets;
    }

    /** Public asset projection that hides raw content by default. */
    public static class AssetView {
        private String path;
        private String mediaType;
        private String role;
        private String description;
        private Long size;
        private boolean contentAvailable;

        public static AssetView fromAsset(AssetEntry entry) {
            AssetView v = new AssetView();
            v.path = entry.getPath();
            v.mediaType = entry.getMediaType();
            v.role = entry.getRole();
            v.description = entry.getDescription();
            v.size = entry.getSize();
            v.contentAvailable = (entry.getContent() != null && !entry.getContent().isEmpty())
                    || (entry.getContentRef() != null && !entry.getContentRef().isEmpty());
            return v;
        }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getMediaType() { return mediaType; }
        public void setMediaType(String mediaType) { this.mediaType = mediaType; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Long getSize() { return size; }
        public void setSize(Long size) { this.size = size; }
        public boolean isContentAvailable() { return contentAvailable; }
        public void setContentAvailable(boolean contentAvailable) { this.contentAvailable = contentAvailable; }
    }
}
