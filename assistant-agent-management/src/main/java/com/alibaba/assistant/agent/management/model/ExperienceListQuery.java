package com.alibaba.assistant.agent.management.model;

import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;

public class ExperienceListQuery {

    private ExperienceType type;
    private String keyword;
    private String tenantId;
    private boolean includeGlobal = true;
    private int page = 1;
    private int size = 20;

    public ExperienceType getType() {
        return type;
    }

    public void setType(ExperienceType type) {
        this.type = type;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isIncludeGlobal() {
        return includeGlobal;
    }

    public void setIncludeGlobal(boolean includeGlobal) {
        this.includeGlobal = includeGlobal;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
