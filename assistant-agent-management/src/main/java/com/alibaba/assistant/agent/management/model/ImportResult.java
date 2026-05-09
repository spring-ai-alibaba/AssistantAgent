package com.alibaba.assistant.agent.management.model;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {

    private List<String> importedIds = new ArrayList<>();
    private List<String> failures = new ArrayList<>();

    public ImportResult() {
    }

    public ImportResult(List<String> importedIds, List<String> failures) {
        this.importedIds = importedIds != null ? importedIds : new ArrayList<>();
        this.failures = failures != null ? failures : new ArrayList<>();
    }

    public List<String> getImportedIds() {
        return importedIds;
    }

    public void setImportedIds(List<String> importedIds) {
        this.importedIds = importedIds;
    }

    public List<String> getFailures() {
        return failures;
    }

    public void setFailures(List<String> failures) {
        this.failures = failures;
    }
}
