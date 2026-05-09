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
 * Placeholder ToolCallback for the {@code __prompt_contribution__} internal tool.
 *
 * <p>{@link PromptContributorModelHook} injects fake AssistantMessage + ToolResponseMessage
 * pairs using this tool name. The agent framework needs a registered ToolCallback so that:
 * <ol>
 *   <li>The tool appears in the LLM's tool list, preventing confusion</li>
 *   <li>If the LLM mistakenly calls it, a clear error message is returned</li>
 * </ol>
 *
 * <p>This tool is never meant to be called directly. The description explicitly tells
 * the LLM not to invoke it.
 *
 * @author Assistant Agent Team
 */
public class PromptContributionToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(PromptContributionToolCallback.class);

    public static final String TOOL_NAME = "__get_system_guidance__";

    private static final String TOOL_DESCRIPTION =
            "[INTERNAL SYSTEM TOOL - DO NOT CALL] "
            + "This is an internal system tool that is automatically invoked by the framework. "
            + "You must NEVER call this tool directly. "
            + "If you need system guidance, it will be provided automatically in <additional_system_guidance> tags. "
            + "Calling this tool will result in an error.";

    private static final String INPUT_SCHEMA = """
            {
                "type": "object",
                "properties": {},
                "required": []
            }
            """;

    private final ToolDefinition toolDefinition;

    public PromptContributionToolCallback() {
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
        log.warn("PromptContributionToolCallback#call - reason=LLM incorrectly called internal tool, input={}", input);
        return "{\"error\": true, \"message\": \"ERROR: __get_system_guidance__ is an internal system tool. "
                + "Do NOT call it. The guidance is already provided in <additional_system_guidance> tags. "
                + "Proceed with your task using the appropriate tools.\"}";
    }

    public static PromptContributionToolCallback getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final PromptContributionToolCallback INSTANCE = new PromptContributionToolCallback();
    }
}
