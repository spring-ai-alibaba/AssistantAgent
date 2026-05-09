package com.alibaba.assistant.agent.extension.experience.tool;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.DefaultCodeactToolMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactDirectCodeactToolCallbackWrapperTest {

    @Test
    void shouldWrapToolUsingQualifiedReactToolName() {
        CodeactTool tool = new FakeCodeactTool(
                DefaultToolDefinition.builder()
                        .name("liushuixianzhenduan")
                        .description("流水线诊断")
                        .inputSchema("{}")
                        .build(),
                DefaultCodeactToolMetadata.builder()
                        .addSupportedLanguage(Language.PYTHON)
                        .targetClassName("pipeline_diagnose_agent_tools")
                        .codeInvocationTemplate("liushuixianzhenduan()")
                        .build()
        );

        FunctionToolCallback<Map<String, Object>, String> callback = ReactDirectCodeactToolCallbackWrapper.wrap(tool);

        assertEquals("pipeline_diagnose_agent_tools.liushuixianzhenduan",
                callback.getToolDefinition().name());
    }

    @Test
    void shouldOnlyWrapToolsWithQualifiedReactToolName() {
        CodeactTool toolWithoutTargetClass = new FakeCodeactTool(
                DefaultToolDefinition.builder()
                        .name("send_message")
                        .description("reply")
                        .inputSchema("{}")
                        .build(),
                DefaultCodeactToolMetadata.builder()
                        .addSupportedLanguage(Language.PYTHON)
                        .build()
        );

        assertFalse(ReactDirectCodeactToolCallbackWrapper.hasReactToolName(toolWithoutTargetClass));
        assertTrue(ReactDirectCodeactToolCallbackWrapper.wrapAll(List.of(toolWithoutTargetClass)).isEmpty());
    }

    private static final class FakeCodeactTool implements CodeactTool {
        private final ToolDefinition toolDefinition;
        private final CodeactToolMetadata metadata;

        private FakeCodeactTool(ToolDefinition toolDefinition, CodeactToolMetadata metadata) {
            this.toolDefinition = toolDefinition;
            this.metadata = metadata;
        }

        @Override
        public String call(String toolInput) {
            return toolInput;
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return toolInput;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public CodeactToolMetadata getCodeactMetadata() {
            return metadata;
        }
    }
}
