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

import com.alibaba.assistant.agent.prompt.DefaultPromptContributorManager;
import com.alibaba.assistant.agent.prompt.PromptContribution;
import com.alibaba.assistant.agent.prompt.PromptContributor;
import com.alibaba.assistant.agent.prompt.PromptContributorContext;
import com.alibaba.assistant.agent.prompt.PromptContributorManager;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PromptContributorModelHook} dedup logic.
 */
class PromptContributorModelHookTest {

    /**
     * First call should inject XML-wrapped content and store guidance ids in state.
     */
    @Test
    void firstCall_shouldInjectXmlWrappedContentAndStoreIds() throws Exception {
        PromptContributorManager manager = new DefaultPromptContributorManager(List.of(
                simpleContributor("TestContributor", "Hello guidance content")
        ));

        TestHook hook = new TestHook(manager);
        OverAllState state = new OverAllState(Map.of("messages", new ArrayList<>()));

        CompletableFuture<Map<String, Object>> future = hook.beforeModel(state, RunnableConfig.builder().build());
        Map<String, Object> updates = future.get();

        // Should produce messages
        assertNotNull(updates.get("messages"));
        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) updates.get("messages");
        assertEquals(2, messages.size());

        // Verify XML-wrapped content (matching meow-agent-server format)
        ToolResponseMessage toolMsg = (ToolResponseMessage) messages.get(1);
        String content = toolMsg.getResponses().get(0).responseData();
        assertTrue(content.contains("Hello guidance content"));
        assertTrue(content.contains("<additional_system_guidance>"), "Should contain XML wrapper");
        assertTrue(content.contains("<guidance id="), "Should contain XML guidance tags");
        assertTrue(content.contains("</guidance>"), "Should contain closing guidance tag");
        assertTrue(content.contains("</additional_system_guidance>"), "Should contain closing wrapper");

        // Verify injected ids stored in state update
        assertNotNull(updates.get(PromptContributorModelHook.STATE_KEY_INJECTED_IDS));
        @SuppressWarnings("unchecked")
        Set<String> ids = (Set<String>) updates.get(PromptContributorModelHook.STATE_KEY_INJECTED_IDS);
        assertEquals(1, ids.size());
    }

    /**
     * Second call with same content (ids in state) should produce no new injection.
     */
    @Test
    void secondCall_withSameContent_shouldBeDeduped() throws Exception {
        PromptContributorManager manager = new DefaultPromptContributorManager(List.of(
                simpleContributor("TestContributor", "Hello guidance content")
        ));

        TestHook hook = new TestHook(manager);

        // First call
        OverAllState state1 = new OverAllState(Map.of("messages", new ArrayList<>()));
        Map<String, Object> updates1 = hook.beforeModel(state1, RunnableConfig.builder().build()).get();

        @SuppressWarnings("unchecked")
        Set<String> injectedIds = (Set<String>) updates1.get(PromptContributorModelHook.STATE_KEY_INJECTED_IDS);

        // Second call: state now contains the injected ids
        Map<String, Object> state2Data = new HashMap<>();
        state2Data.put("messages", new ArrayList<>());
        state2Data.put(PromptContributorModelHook.STATE_KEY_INJECTED_IDS, injectedIds);
        OverAllState state2 = new OverAllState(state2Data);

        Map<String, Object> updates2 = hook.beforeModel(state2, RunnableConfig.builder().build()).get();

        assertTrue(updates2.isEmpty(), "Expected no new messages when same content already injected");
    }

    /**
     * If contributor content changes, new guidance should be injected.
     */
    @Test
    void secondCall_withDifferentContent_shouldInject() throws Exception {
        MutableContributor contributor = new MutableContributor("DynamicContributor", "First content");
        PromptContributorManager manager = new DefaultPromptContributorManager(List.of(contributor));

        TestHook hook = new TestHook(manager);

        // First call
        OverAllState state1 = new OverAllState(Map.of("messages", new ArrayList<>()));
        Map<String, Object> updates1 = hook.beforeModel(state1, RunnableConfig.builder().build()).get();

        @SuppressWarnings("unchecked")
        Set<String> injectedIds = (Set<String>) updates1.get(PromptContributorModelHook.STATE_KEY_INJECTED_IDS);

        // Change content
        contributor.setContent("Different content now");

        // Second call with prior ids in state
        Map<String, Object> state2Data = new HashMap<>();
        state2Data.put("messages", new ArrayList<>());
        state2Data.put(PromptContributorModelHook.STATE_KEY_INJECTED_IDS, injectedIds);
        OverAllState state2 = new OverAllState(state2Data);

        Map<String, Object> updates2 = hook.beforeModel(state2, RunnableConfig.builder().build()).get();

        assertNotNull(updates2.get("messages"), "Expected new injection for changed content");

        @SuppressWarnings("unchecked")
        List<Message> newMessages = (List<Message>) updates2.get("messages");
        ToolResponseMessage toolMsg = (ToolResponseMessage) newMessages.get(1);
        String content = toolMsg.getResponses().get(0).responseData();
        assertTrue(content.contains("Different content now"));

        // Verify ids set grows
        @SuppressWarnings("unchecked")
        Set<String> updatedIds = (Set<String>) updates2.get(PromptContributorModelHook.STATE_KEY_INJECTED_IDS);
        assertEquals(2, updatedIds.size(), "Should have both old and new guidance ids");
    }

    /**
     * Multiple contributors: only new ones get injected on second call.
     */
    @Test
    void multipleContributors_partialDedup() throws Exception {
        PromptContributor c1 = simpleContributor("Contributor1", "Content A");
        PromptContributor c2 = simpleContributor("Contributor2", "Content B");

        PromptContributorManager manager = new DefaultPromptContributorManager(List.of(c1));
        TestHook hook = new TestHook(manager);

        // First call: only c1
        OverAllState state1 = new OverAllState(Map.of("messages", new ArrayList<>()));
        Map<String, Object> updates1 = hook.beforeModel(state1, RunnableConfig.builder().build()).get();

        @SuppressWarnings("unchecked")
        Set<String> injectedIds = (Set<String>) updates1.get(PromptContributorModelHook.STATE_KEY_INJECTED_IDS);

        // Register c2
        manager.register(c2);

        // Second call: c1 deduped, c2 injected
        Map<String, Object> state2Data = new HashMap<>();
        state2Data.put("messages", new ArrayList<>());
        state2Data.put(PromptContributorModelHook.STATE_KEY_INJECTED_IDS, injectedIds);
        OverAllState state2 = new OverAllState(state2Data);

        Map<String, Object> updates2 = hook.beforeModel(state2, RunnableConfig.builder().build()).get();

        assertNotNull(updates2.get("messages"));

        @SuppressWarnings("unchecked")
        List<Message> newMessages = (List<Message>) updates2.get("messages");
        ToolResponseMessage toolMsg = (ToolResponseMessage) newMessages.get(1);
        String content = toolMsg.getResponses().get(0).responseData();

        assertTrue(content.contains("Content B"), "New contributor should be injected");
        assertFalse(content.contains("Content A"), "Old contributor should be deduped");
    }

    /**
     * Empty state should work without errors.
     */
    @Test
    void emptyState_shouldWorkCorrectly() throws Exception {
        PromptContributorManager manager = new DefaultPromptContributorManager(List.of(
                simpleContributor("Test", "Some content")
        ));

        TestHook hook = new TestHook(manager);
        OverAllState state = new OverAllState(Map.of());
        Map<String, Object> updates = hook.beforeModel(state, RunnableConfig.builder().build()).get();

        assertNotNull(updates.get("messages"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private static PromptContributor simpleContributor(String name, String content) {
        return new PromptContributor() {
            @Override public String getName() { return name; }
            @Override public boolean shouldContribute(PromptContributorContext context) { return true; }
            @Override public int getPriority() { return 100; }

            @Override
            public PromptContribution contribute(PromptContributorContext context) {
                return PromptContribution.builder()
                        .append(new UserMessage(content))
                        .build();
            }
        };
    }

    private static class MutableContributor implements PromptContributor {
        private final String name;
        private String content;

        MutableContributor(String name, String content) {
            this.name = name;
            this.content = content;
        }

        void setContent(String content) { this.content = content; }

        @Override public String getName() { return name; }
        @Override public boolean shouldContribute(PromptContributorContext ctx) { return true; }
        @Override public int getPriority() { return 100; }

        @Override
        public PromptContribution contribute(PromptContributorContext ctx) {
            return PromptContribution.builder()
                    .append(new UserMessage(content))
                    .build();
        }
    }

    private static class TestHook extends PromptContributorModelHook {
        TestHook(PromptContributorManager manager) {
            super(manager);
        }
    }
}
