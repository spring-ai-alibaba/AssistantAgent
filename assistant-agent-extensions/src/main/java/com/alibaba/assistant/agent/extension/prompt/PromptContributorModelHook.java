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

import com.alibaba.assistant.agent.common.hook.AgentPhase;
import com.alibaba.assistant.agent.common.hook.HookPhaseUtils;
import com.alibaba.assistant.agent.prompt.PromptContribution;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 将 PromptContributor 机制接入 ModelHook 的抽象基类
 * 在 BEFORE_MODEL 阶段执行，将 PromptContribution 注入到 messages
 * 
 * <p>子类只需使用 {@code @HookPhases} 注解指定适用的 Agent 阶段，
 * 基类会自动从注解中读取阶段信息。
 *
 * @author Assistant Agent Team
 * @see ReactPromptContributorModelHook
 * @see CodeactPromptContributorModelHook
 */
@HookPositions(HookPosition.BEFORE_MODEL)
public abstract class PromptContributorModelHook extends ModelHook implements Prioritized {

    private static final Logger log = LoggerFactory.getLogger(PromptContributorModelHook.class);

    /**
     * 注入工具名称
     */
    private static final String INJECTION_TOOL_NAME = "__prompt_contribution__";

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
        AgentPhase[] phases = HookPhaseUtils.getHookPhases(this);
        String phaseName = phases.length > 0 ? phases[0].name() : "UNKNOWN";
        return "PromptContributorModelHook-" + phaseName;
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
            // 1. 构造上下文（从注解中获取阶段名称）
            // 注意：systemMessage 传 null，因为框架中 AgentLlmNode 的 systemMessage 
            // 来自构建时的固定值 this.systemPrompt，不会存入 OverAllState，
            // 所以无法从 state 中获取。如需获取 systemMessage，应改用 ModelInterceptor。
            AgentPhase[] phases = HookPhaseUtils.getHookPhases(this);
            String phaseName = phases.length > 0 ? phases[0].name() : "REACT";
            PromptContributorContext context = new OverAllStatePromptContributorContext(
                    state, null, phaseName);

            // 2. 组装所有贡献
            PromptContribution contribution = contributorManager.assemble(context);

            if (contribution == null || contribution.isEmpty()) {
                log.debug("{}#beforeModel - reason=无 Prompt 贡献内容", hookName);
                return CompletableFuture.completedFuture(Map.of());
            }

            // 3. 将贡献内容注入到 messages
            Map<String, Object> updates = injectContribution(contribution);

            log.info("{}#beforeModel - reason=已注入 Prompt 贡献", hookName);
            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("{}#beforeModel - reason=执行失败", hookName, e);
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private Map<String, Object> injectContribution(PromptContribution contribution) {
        Map<String, Object> updates = new HashMap<>();

        // spring-ai-alibaba框架中 AgentLlmNode 的 systemMessage 来自构建时的固定值 this.systemPrompt，hook中无法修改它
        // 当前只能通过 messagesToAppend 注入内容

        if (contribution.systemTextToAppend() != null || contribution.systemTextToPrepend() != null) {
            // System 文本通过特殊的 state key 传递（注意：当前不会生效，见上方说明）
            if (contribution.systemTextToPrepend() != null) {
                log.warn("PromptContributorModelHook#injectContribution - " +
                        "reason=systemTextToPrepend 设置了但不会生效, 请改用 messagesToAppend");
            }
            if (contribution.systemTextToAppend() != null) {
                log.warn("PromptContributorModelHook#injectContribution - " +
                        "reason=systemTextToAppend 设置了但不会生效, 请改用 messagesToAppend");
            }
        }

        // 处理 Messages
        List<Message> messagesToAdd = new ArrayList<>();
        if (contribution.messagesToPrepend() != null) {
            messagesToAdd.addAll(contribution.messagesToPrepend());
        }
        if (contribution.messagesToAppend() != null) {
            messagesToAdd.addAll(contribution.messagesToAppend());
        }

        if (!messagesToAdd.isEmpty()) {
            // 使用 Tool Response 方式注入消息内容
            String content = buildInjectionContent(messagesToAdd);
            String toolCallId = "contrib_" + UUID.randomUUID().toString().substring(0, 8);

            AssistantMessage assistantMsg = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            toolCallId, "function", INJECTION_TOOL_NAME, "{}")))
                    .build();

            ToolResponseMessage toolMsg = ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            toolCallId, INJECTION_TOOL_NAME, content)))
                    .build();

            updates.put("messages", List.of(assistantMsg, toolMsg));
        }

        return updates;
    }

    private String buildInjectionContent(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            if (msg.getText() != null && !msg.getText().isBlank()) {
                sb.append(msg.getText()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }
}

