package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperienceRuntimeModelInterceptorTest {

    @Test
    void shouldKeepOnlyDisclosedDirectToolsInModelRequest() {
        ExperienceRuntimeModelInterceptor interceptor = new ExperienceRuntimeModelInterceptor(null, null, null);
        ModelRequest request = ModelRequest.builder()
                .tools(List.of(
                        "send_message",
                        "search_exp",
                        "pipeline_diagnose_agent_tools.liushuixianzhenduan",
                        "deploy_agent_tools.publish",
                        "write_code"))
                .context(Map.of(
                        CodeactStateKeys.EXPERIENCE_ALLOWED_REACT_TOOL_NAMES,
                        List.of("pipeline_diagnose_agent_tools.liushuixianzhenduan")))
                .build();

        interceptor.interceptModel(request, modelRequest -> {
            assertEquals(
                    List.of("send_message", "search_exp", "pipeline_diagnose_agent_tools.liushuixianzhenduan", "write_code"),
                    modelRequest.getTools());
            return ModelResponse.of(new AssistantMessage("ok"));
        });
    }
}
