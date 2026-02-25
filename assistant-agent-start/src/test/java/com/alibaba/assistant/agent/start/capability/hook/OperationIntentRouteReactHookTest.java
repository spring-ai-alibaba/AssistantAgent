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
package com.alibaba.assistant.agent.start.capability.hook;

import com.alibaba.assistant.agent.start.capability.config.CapabilityRegistrationProperties;
import com.alibaba.assistant.agent.start.capability.intent.CapabilityIntentClassifier;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationIntentRouteReactHookTest {

    @Test
    void shouldRouteToCapabilityToolWhenOperationIntentMatchesConfiguredKeyword() {
        CapabilityRegistrationProperties properties = buildCapabilityProperties();
        OperationIntentRouteReactHook hook = new OperationIntentRouteReactHook(properties,
                new CapabilityIntentClassifier());
        OverAllState state = mockState(new LinkedHashMap<>(), "发起工作汇报");

        Map<String, Object> updates = hook.beforeAgent(state, null).join();

        assertThat(updates.get("capability_intent_type")).isEqualTo("操作");
        assertThat(updates.get("capability_intent_route_tool")).isEqualTo("submit_office_work_report");
        assertThat(updates.get("jump_to")).isEqualTo(JumpTo.tool);
        AssistantMessage assistantMessage = (AssistantMessage) ((List<?>) updates.get("messages")).get(0);
        assertThat(assistantMessage.getToolCalls()).hasSize(1);
        assertThat(assistantMessage.getToolCalls().get(0).name()).isEqualTo("submit_office_work_report");
    }

    @Test
    void shouldNotRouteWhenIntentTypeIsQuery() {
        CapabilityRegistrationProperties properties = buildCapabilityProperties();
        OperationIntentRouteReactHook hook = new OperationIntentRouteReactHook(properties,
                new CapabilityIntentClassifier());
        OverAllState state = mockState(new LinkedHashMap<>(), "查询本周工作汇报记录");

        Map<String, Object> updates = hook.beforeAgent(state, null).join();

        assertThat(updates.get("capability_intent_type")).isEqualTo("查询");
        assertThat(updates).doesNotContainKey("jump_to");
        assertThat(updates).doesNotContainKey("messages");
    }

    @Test
    void shouldSkipRoutingWhenDraftAlreadyActive() {
        CapabilityRegistrationProperties properties = buildCapabilityProperties();
        OperationIntentRouteReactHook hook = new OperationIntentRouteReactHook(properties,
                new CapabilityIntentClassifier());
        Map<String, Object> stateData = new LinkedHashMap<>();
        stateData.put("capability_draft_submit_office_work_report_status", "SLOT_MISSING");
        OverAllState state = mockState(stateData, "发起工作汇报");

        Map<String, Object> updates = hook.beforeAgent(state, null).join();

        assertThat(updates.get("capability_intent_type")).isEqualTo("操作");
        assertThat(updates).doesNotContainKey("jump_to");
        assertThat(updates).doesNotContainKey("messages");
    }

    private CapabilityRegistrationProperties buildCapabilityProperties() {
        CapabilityRegistrationProperties properties = new CapabilityRegistrationProperties();
        CapabilityRegistrationProperties.HttpFormCapability capability =
                new CapabilityRegistrationProperties.HttpFormCapability();
        capability.setEnabled(true);
        capability.setToolName("submit_office_work_report");
        capability.setDescription("Submit OA daily work report");
        capability.setOperationIntentKeywords(List.of("工作汇报", "OA系统工作汇报", "发起工作汇报"));
        properties.setRegistrations(List.of(capability));
        return properties;
    }

    private OverAllState mockState(Map<String, Object> stateData, String input) {
        OverAllState state = mock(OverAllState.class);
        when(state.data()).thenReturn(stateData);
        when(state.value("input", String.class)).thenReturn(Optional.ofNullable(input));
        when(state.value("messages")).thenReturn(Optional.of(List.of()));
        when(state.value("jump_to")).thenReturn(Optional.empty());
        return state;
    }

}
