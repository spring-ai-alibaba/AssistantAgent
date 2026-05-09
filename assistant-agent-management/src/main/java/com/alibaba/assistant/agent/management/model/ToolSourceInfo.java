package com.alibaba.assistant.agent.management.model;

public class ToolSourceInfo {

    private String id;
    private String name;
    private String type;
    private int importedCount;
    private int totalCount;

    public ToolSourceInfo() {
    }

    public ToolSourceInfo(String id, String name, String type, int importedCount, int totalCount) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.importedCount = importedCount;
        this.totalCount = totalCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public void setImportedCount(int importedCount) {
        this.importedCount = importedCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
