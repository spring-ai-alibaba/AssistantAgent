package com.alibaba.assistant.agent.management.controller;

import com.alibaba.assistant.agent.management.model.ExperienceVO;
import com.alibaba.assistant.agent.management.model.ImportResult;
import com.alibaba.assistant.agent.management.model.ToolInfo;
import com.alibaba.assistant.agent.management.model.ToolSourceInfo;
import com.alibaba.assistant.agent.management.model.ToolSyncResult;
import com.alibaba.assistant.agent.management.spi.ToolSourceBrowser;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/exp-console/api/tool-sources")
public class ToolSourceController {

    private final ToolSourceBrowser browser;

    public ToolSourceController(ToolSourceBrowser browser) {
        this.browser = browser;
    }

    @GetMapping
    public ResponseEntity<List<ToolSourceInfo>> listSources() {
        return ResponseEntity.ok(browser.listSources());
    }

    @GetMapping("/{sourceId}/tools")
    public ResponseEntity<List<ToolInfo>> listTools(@PathVariable("sourceId") String sourceId) {
        return ResponseEntity.ok(browser.listTools(sourceId));
    }

    @PostMapping("/{sourceId}/import")
    public ResponseEntity<ImportResult> importTools(
            @PathVariable("sourceId") String sourceId,
            @RequestBody Map<String, List<String>> body) {
        List<String> toolNames = body.getOrDefault("toolNames", List.of());
        return ResponseEntity.ok(browser.importTools(sourceId, toolNames));
    }

    @GetMapping("/{sourceId}/imported")
    public ResponseEntity<List<ExperienceVO>> listImportedTools(@PathVariable("sourceId") String sourceId) {
        return ResponseEntity.ok(browser.listImportedTools(sourceId));
    }

    @PostMapping("/{sourceId}/sync")
    public ResponseEntity<ToolSyncResult> syncTools(@PathVariable("sourceId") String sourceId) {
        return ResponseEntity.ok(browser.syncTools(sourceId));
    }
}
