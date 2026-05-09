package com.alibaba.assistant.agent.management.model;

public class ToolInfo {

    private String name;
    private String description;
    private String parametersSchema;
    private boolean imported;

    public ToolInfo() {
    }

    public ToolInfo(String name, String description, String parametersSchema, boolean imported) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema;
        this.imported = imported;
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

    public String getParametersSchema() {
        return parametersSchema;
    }

    public void setParametersSchema(String parametersSchema) {
        this.parametersSchema = parametersSchema;
    }

    public boolean isImported() {
        return imported;
    }

    public void setImported(boolean imported) {
        this.imported = imported;
    }
}
