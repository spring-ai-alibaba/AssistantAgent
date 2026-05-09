package com.alibaba.assistant.agent.management.internal;

import com.alibaba.assistant.agent.management.model.ExperienceVO;
import com.alibaba.assistant.agent.management.model.ImportResult;
import com.alibaba.assistant.agent.management.model.ToolInfo;
import com.alibaba.assistant.agent.management.model.ToolSourceInfo;
import com.alibaba.assistant.agent.management.model.ToolSyncResult;
import com.alibaba.assistant.agent.management.spi.ToolSourceBrowser;

import java.util.Collections;
import java.util.List;

public class InMemoryToolSourceBrowser implements ToolSourceBrowser {

    @Override
    public List<ToolSourceInfo> listSources() {
        return Collections.emptyList();
    }

    @Override
    public List<ToolInfo> listTools(String sourceId) {
        return Collections.emptyList();
    }

    @Override
    public ImportResult importTools(String sourceId, List<String> toolNames) {
        return new ImportResult();
    }

    @Override
    public List<ExperienceVO> listImportedTools(String sourceId) {
        return Collections.emptyList();
    }

    @Override
    public ToolSyncResult syncTools(String sourceId) {
        return new ToolSyncResult();
    }
}
