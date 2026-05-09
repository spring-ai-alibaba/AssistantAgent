/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.extension.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.lang.NonNull;

/**
 * Placeholder ToolCallback for the fake tool injected by {@code CodeactToolSignatureAgentHook}.
 */
public class CodeactToolSignatureInjectionToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(CodeactToolSignatureInjectionToolCallback.class);

    public static final String TOOL_NAME = "codeact_tool_signature_injection";

    private static final String TOOL_DESCRIPTION =
            "[INTERNAL SYSTEM TOOL - DO NOT CALL] "
            + "This is an internal system tool that is automatically invoked by the framework. "
            + "You must NEVER call this tool directly. "
            + "Codeact tool signatures are injected automatically into the conversation for write_code usage. "
            + "Calling this tool will result in an error.";

    private static final String INPUT_SCHEMA = """
            {
                "type": "object",
                "properties": {},
                "required": []
            }
            """;

    private final ToolDefinition toolDefinition;

    public CodeactToolSignatureInjectionToolCallback() {
        this.toolDefinition = ToolDefinition.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(INPUT_SCHEMA)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(@NonNull String input) {
        return call(input, null);
    }

    @Override
    public String call(@NonNull String input, ToolContext toolContext) {
        log.warn("CodeactToolSignatureInjectionToolCallback#call - reason=LLM incorrectly called internal tool, input={}", input);
        return "{\"error\": true, \"message\": \"ERROR: codeact_tool_signature_injection is an internal system tool. "
                + "Do NOT call it. The tool signatures are already provided in the conversation for write_code usage. "
                + "Proceed with your task using the appropriate tools.\"}";
    }

    public static CodeactToolSignatureInjectionToolCallback getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final CodeactToolSignatureInjectionToolCallback INSTANCE =
                new CodeactToolSignatureInjectionToolCallback();
    }
}
