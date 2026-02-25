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
package com.alibaba.assistant.agent.extension.evaluation.config;

import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.assistant.agent.extension.evaluation.model.CodeactEvaluationTag;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodeactEvaluationContextFactoryTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldUseLatestUserMessageWhenLastMessageIsInjectedToolResponse() {
        CodeactEvaluationContextFactory factory = new CodeactEvaluationContextFactory();
        OverAllState state = mock(OverAllState.class);

        String toolCallId = "react_strategy_001";
        List<Message> messages = List.of(
                new UserMessage("发起工作汇报"),
                AssistantMessage.builder().toolCalls(List.of(
                        new AssistantMessage.ToolCall(toolCallId, "function", "react_strategy_injection", "{}")
                )).build(),
                ToolResponseMessage.builder().responses(List.of(
                        new ToolResponseMessage.ToolResponse(toolCallId, "react_strategy_injection", "经验内容")
                )).build()
        );

        when(state.value("messages")).thenReturn(Optional.of(messages));
        when(state.value("input", String.class)).thenReturn(Optional.empty());
        when(state.value("agentName", String.class)).thenReturn(Optional.empty());
        when(state.value("knowledgeSearchHits")).thenReturn(Optional.empty());
        when(state.value("evaluationExternalParams")).thenReturn(Optional.empty());

        EvaluationContext context = factory.createInputRoutingContext(state, null);

        assertThat(context.getInput().get(CodeactEvaluationTag.INPUT_USER_INPUT))
                .isEqualTo("发起工作汇报");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldFallbackToStateInputWhenNoUserMessageExists() {
        CodeactEvaluationContextFactory factory = new CodeactEvaluationContextFactory();
        OverAllState state = mock(OverAllState.class);

        List<Message> messages = List.of(
                AssistantMessage.builder().content("tool-only message").build()
        );

        when(state.value("messages")).thenReturn(Optional.of(messages));
        when(state.value("input", String.class)).thenReturn(Optional.of("发起工作汇报"));
        when(state.value("agentName", String.class)).thenReturn(Optional.empty());
        when(state.value("knowledgeSearchHits")).thenReturn(Optional.empty());
        when(state.value("evaluationExternalParams")).thenReturn(Optional.of(Map.of()));

        EvaluationContext context = factory.createInputRoutingContext(state, null);

        assertThat(context.getInput().get(CodeactEvaluationTag.INPUT_USER_INPUT))
                .isEqualTo("发起工作汇报");
    }
}

