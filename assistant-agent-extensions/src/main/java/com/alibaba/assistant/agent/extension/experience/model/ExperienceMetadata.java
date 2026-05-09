package com.alibaba.assistant.agent.extension.experience.model;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 经验附加元信息
 *
 * @author Assistant Agent Team
 */
public class ExperienceMetadata {

    /**
     * 经验来源
     */
    private String source;

    /**
     * 置信度或质量评分 (0.0-1.0)
     */
    private Double confidence;

    /**
     * 版本信息
     */
    private String version;

    /**
     * 扩展属性
     */
    private Map<String, Object> properties = new HashMap<>();

    public ExperienceMetadata() {
    }

    public ExperienceMetadata(String source, Double confidence, String version) {
        this.source = source;
        this.confidence = confidence;
        this.version = version;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public void putProperty(String key, Object value) {
        this.properties.put(key, value);
    }

    public Object getProperty(String key) {
        return this.properties.get(key);
    }

    public List<String> getTenantIdList() {
        Object val = properties.get("tenantIdList");
        if (val instanceof Collection<?> collection) {
            return normalizeTenantIdList(collection);
        }
        return new ArrayList<>();
    }

    public void setTenantIdList(Collection<String> tenantIdList) {
        List<String> normalized = normalizeTenantIdList(tenantIdList);
        if (normalized.isEmpty()) {
            properties.remove("tenantIdList");
            return;
        }
        properties.put("tenantIdList", normalized);
    }

    public void addTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return;
        }
        List<String> current = new ArrayList<>(getTenantIdList());
        current.add(tenantId);
        setTenantIdList(current);
    }

    public void clearTenantIdList() {
        properties.remove("tenantIdList");
    }

    public boolean isGlobal() {
        return getTenantIdList().isEmpty();
    }

    public boolean matchesTenantId(String tenantId) {
        return matchesTenantId(tenantId, true);
    }

    public boolean matchesTenantId(String tenantId, boolean includeGlobal) {
        List<String> experienceTenantIdList = getTenantIdList();
        if ("global".equalsIgnoreCase(tenantId != null ? tenantId.trim() : null)) {
            return experienceTenantIdList.isEmpty();
        }
        if (experienceTenantIdList.isEmpty()) {
            return includeGlobal;
        }
        if (!StringUtils.hasText(tenantId)) {
            return false;
        }
        return experienceTenantIdList.contains(tenantId.trim());
    }

    private List<String> normalizeTenantIdList(Collection<?> rawTenantIds) {
        if (rawTenantIds == null || rawTenantIds.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object rawTenantId : rawTenantIds) {
            if (rawTenantId == null) {
                continue;
            }
            String tenantId = String.valueOf(rawTenantId).trim();
            if (!StringUtils.hasText(tenantId) || "global".equalsIgnoreCase(tenantId)) {
                continue;
            }
            normalized.add(tenantId);
        }
        return new ArrayList<>(normalized);
    }
}
