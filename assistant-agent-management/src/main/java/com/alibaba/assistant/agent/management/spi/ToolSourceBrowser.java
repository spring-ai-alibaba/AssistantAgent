package com.alibaba.assistant.agent.management.spi;

import com.alibaba.assistant.agent.management.model.ExperienceVO;
import com.alibaba.assistant.agent.management.model.ImportResult;
import com.alibaba.assistant.agent.management.model.ToolInfo;
import com.alibaba.assistant.agent.management.model.ToolSourceInfo;
import com.alibaba.assistant.agent.management.model.ToolSyncResult;

import java.util.List;

public interface ToolSourceBrowser {

    List<ToolSourceInfo> listSources();

    List<ToolInfo> listTools(String sourceId);

    ImportResult importTools(String sourceId, List<String> toolNames);

    List<ExperienceVO> listImportedTools(String sourceId);

    ToolSyncResult syncTools(String sourceId);
}
