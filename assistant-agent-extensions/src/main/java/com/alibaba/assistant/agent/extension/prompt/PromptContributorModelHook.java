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

import com.alibaba.assistant.agent.prompt.PromptContribution;
import com.alibaba.assistant.agent.prompt.PromptContributor;
import com.alibaba.assistant.agent.prompt.PromptContributorContext;
import com.alibaba.assistant.agent.prompt.PromptContributorManager;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Prioritized;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 将 PromptContributor 机制接入 ModelHook 的抽象基类
 * 在 BEFORE_MODEL 阶段执行，将 PromptContribution 注入到 messages
 *
 * <p>去重策略：将已注入贡献的 guidanceId（contributorName + content MD5）存储在
 * OverAllState 的 {@value #STATE_KEY_INJECTED_IDS} 中，每次注入前检查是否已存在，
 * 注入内容保持纯文本格式，避免 XML 标签干扰 LLM 工具调用的 JSON 生成。
 *
 * @author Assistant Agent Team
 * @see ReactPromptContributorModelHook
 */
@HookPositions(HookPosition.BEFORE_MODEL)
public abstract class PromptContributorModelHook extends ModelHook implements Prioritized {

    private static final Logger log = LoggerFactory.getLogger(PromptContributorModelHook.class);

    /**
     * 注入工具名称
     */
    private static final String INJECTION_TOOL_NAME = "__get_system_guidance__";

    /**
     * 存储已注入 guidance id 的 state key
     */
    static final String STATE_KEY_INJECTED_IDS = "__prompt_contribution_injected_ids__";

    /**
     * 默认的 order 值，设置为较大值以确保在评估 Hook（order=10）之后执行
     */
    protected static final int DEFAULT_ORDER = 200;

    private final PromptContributorManager contributorManager;
    private final int order;

    protected PromptContributorModelHook(PromptContributorManager contributorManager) {
        this(contributorManager, DEFAULT_ORDER);
    }

    protected PromptContributorModelHook(PromptContributorManager contributorManager, int order) {
        this.contributorManager = contributorManager;
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public String getName() {
        return "PromptContributorModelHook";
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return List.of();
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        String hookName = getName();
        log.debug("{}#beforeModel - reason=开始执行 Prompt 贡献", hookName);

        try {
            // 1. 提取已注入的 guidance id（从 state 中读取）
            Set<String> existingGuidanceIds = getInjectedIds(state);

            // 2. 构造上下文
            PromptContributorContext context = new OverAllStatePromptContributorContext(
                    state, null, "REACT");

            // 3. 逐个处理 contributor，收集去重后的新贡献
            List<GuidanceEntry> newEntries = collectAndFilterGuidance(context, existingGuidanceIds);

            if (newEntries.isEmpty()) {
                log.debug("{}#beforeModel - reason=无新 Prompt 贡献（全部被去重或为空）", hookName);
                return CompletableFuture.completedFuture(Map.of());
            }

            // 4. 注入纯文本内容并更新已注入 id 集合
            Map<String, Object> updates = injectContributions(newEntries, existingGuidanceIds);

            log.info("{}#beforeModel - reason=已注入 Prompt 贡献, newCount={}, existingSkipped={}",
                    hookName, newEntries.size(), existingGuidanceIds.size());
            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("{}#beforeModel - reason=执行失败", hookName, e);
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    // ─── Deduplication (state-based) ──────────────────────────────────

    /**
     * 从 OverAllState 中获取已注入的 guidance id 集合
     */
    @SuppressWarnings("unchecked")
    private Set<String> getInjectedIds(OverAllState state) {
        try {
            Object idsObj = state.value(STATE_KEY_INJECTED_IDS).orElse(null);
            if (idsObj instanceof Set) {
                return new HashSet<>((Set<String>) idsObj);
            }
        } catch (Exception e) {
            log.debug("PromptContributorModelHook#getInjectedIds - reason=读取失败", e);
        }
        return new HashSet<>();
    }

    /**
     * 生成 guidance id（Contributor 名称 + 内容的 MD5 前8位）
     */
    private String generateGuidanceId(String contributorName, String content) {
        String toHash = contributorName + ":" + (content != null ? content : "");
        String md5Hex = md5Hex(toHash);
        return contributorName + "_" + md5Hex.substring(0, 8);
    }

    // ─── Collection & filtering ───────────────────────────────────────

    private List<GuidanceEntry> collectAndFilterGuidance(PromptContributorContext context,
                                                         Set<String> existingGuidanceIds) {
        List<PromptContributor> contributors = contributorManager.getContributors();
        if (contributors == null || contributors.isEmpty()) {
            return List.of();
        }

        List<GuidanceEntry> newEntries = new ArrayList<>();
        for (PromptContributor contributor : contributors) {
            try {
                if (!contributor.shouldContribute(context)) {
                    continue;
                }

                PromptContribution contribution = contributor.contribute(context);
                if (contribution == null || contribution.isEmpty()) {
                    continue;
                }

                String content = extractContributionText(contribution);
                if (content == null || content.isBlank()) {
                    continue;
                }

                String guidanceId = generateGuidanceId(contributor.getName(), content);
                if (existingGuidanceIds.contains(guidanceId)) {
                    log.debug("PromptContributorModelHook - reason=去重跳过, contributor={}, guidanceId={}",
                            contributor.getName(), guidanceId);
                    continue;
                }

                newEntries.add(new GuidanceEntry(guidanceId, contributor.getName(), content));

            } catch (Exception e) {
                log.error("PromptContributorModelHook - reason=contributor 执行失败, name={}",
                        contributor.getName(), e);
            }
        }
        return newEntries;
    }

    // ─── Injection (XML tag format, consistent with meow-agent-server) ─

    private Map<String, Object> injectContributions(List<GuidanceEntry> entries,
                                                     Set<String> existingGuidanceIds) {
        if (entries.isEmpty()) {
            return Map.of();
        }

        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("<additional_system_guidance>\n");
        for (GuidanceEntry entry : entries) {
            contentBuilder.append("<guidance id=\"").append(entry.guidanceId).append("\">\n");
            contentBuilder.append(entry.content).append("\n");
            contentBuilder.append("</guidance>\n");
        }
        contentBuilder.append("</additional_system_guidance>");

        String content = contentBuilder.toString();
        String toolCallId = "guidance_" + UUID.randomUUID().toString().substring(0, 8);

        AssistantMessage assistantMsg = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        toolCallId, "function", INJECTION_TOOL_NAME, "{}")))
                .build();

        ToolResponseMessage toolMsg = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        toolCallId, INJECTION_TOOL_NAME, content)))
                .build();

        // Update injected ids set (merge old + new)
        Set<String> updatedIds = new HashSet<>(existingGuidanceIds);
        for (GuidanceEntry entry : entries) {
            updatedIds.add(entry.guidanceId);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("messages", List.of(assistantMsg, toolMsg));
        updates.put(STATE_KEY_INJECTED_IDS, updatedIds);
        return updates;
    }

    // ─── Utilities ────────────────────────────────────────────────────

    private String extractContributionText(PromptContribution contribution) {
        StringBuilder sb = new StringBuilder();
        if (contribution.messagesToPrepend() != null) {
            for (Message msg : contribution.messagesToPrepend()) {
                if (msg.getText() != null && !msg.getText().isBlank()) {
                    sb.append(msg.getText()).append("\n");
                }
            }
        }
        if (contribution.messagesToAppend() != null) {
            for (Message msg : contribution.messagesToAppend()) {
                if (msg.getText() != null && !msg.getText().isBlank()) {
                    sb.append(msg.getText()).append("\n");
                }
            }
        }
        if (contribution.systemTextToPrepend() != null && !contribution.systemTextToPrepend().isBlank()) {
            log.warn("PromptContributorModelHook - reason=systemTextToPrepend 不会生效, 请改用 messagesToAppend");
        }
        if (contribution.systemTextToAppend() != null && !contribution.systemTextToAppend().isBlank()) {
            log.warn("PromptContributorModelHook - reason=systemTextToAppend 不会生效, 请改用 messagesToAppend");
        }
        return sb.toString().trim();
    }

    static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 is always available in JDK
            return Integer.toHexString(input.hashCode());
        }
    }

    record GuidanceEntry(String guidanceId, String contributorName, String content) {}
}

