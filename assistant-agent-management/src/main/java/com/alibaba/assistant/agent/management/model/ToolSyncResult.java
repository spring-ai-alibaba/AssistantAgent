package com.alibaba.assistant.agent.management.model;

import java.util.ArrayList;
import java.util.List;

public class ToolSyncResult {

    private List<String> createdIds = new ArrayList<>();
    private List<String> updatedIds = new ArrayList<>();
    private List<String> deletedIds = new ArrayList<>();
    private List<String> failures = new ArrayList<>();

    public ToolSyncResult() {
    }

    public ToolSyncResult(List<String> createdIds,
                          List<String> updatedIds,
                          List<String> deletedIds,
                          List<String> failures) {
        this.createdIds = createdIds != null ? createdIds : new ArrayList<>();
        this.updatedIds = updatedIds != null ? updatedIds : new ArrayList<>();
        this.deletedIds = deletedIds != null ? deletedIds : new ArrayList<>();
        this.failures = failures != null ? failures : new ArrayList<>();
    }

    public List<String> getCreatedIds() {
        return createdIds;
    }

    public void setCreatedIds(List<String> createdIds) {
        this.createdIds = createdIds;
    }

    public List<String> getUpdatedIds() {
        return updatedIds;
    }

    public void setUpdatedIds(List<String> updatedIds) {
        this.updatedIds = updatedIds;
    }

    public List<String> getDeletedIds() {
        return deletedIds;
    }

    public void setDeletedIds(List<String> deletedIds) {
        this.deletedIds = deletedIds;
    }

    public List<String> getFailures() {
        return failures;
    }

    public void setFailures(List<String> failures) {
        this.failures = failures;
    }
}
