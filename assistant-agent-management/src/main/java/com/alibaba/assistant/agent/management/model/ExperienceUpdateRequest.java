package com.alibaba.assistant.agent.management.model;

import com.alibaba.assistant.agent.extension.experience.model.DisclosureStrategy;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.FastIntentConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExperienceUpdateRequest {

    private String name;
    private String description;
    private String content;
    private DisclosureStrategy disclosureStrategy;
    private Set<String> tags;
    private List<String> tenantIdList = new ArrayList<>();
    private List<String> associatedTools = new ArrayList<>();
    private List<String> relatedExperiences = new ArrayList<>();
    private ExperienceArtifact artifact;
    private FastIntentConfig fastIntentConfig;
    private Map<String, Object> properties = new HashMap<>();

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

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
