package com.alibaba.assistant.agent.extension.experience.hook;

import com.alibaba.assistant.agent.extension.experience.config.ExperienceExtensionProperties;
import com.alibaba.assistant.agent.extension.experience.fastintent.FastIntentContext;
import com.alibaba.assistant.agent.extension.experience.fastintent.FastIntentService;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.assistant.agent.common.hook.AgentPhase;
import com.alibaba.assistant.agent.common.hook.HookPhases;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * FastIntentReactHook - REACT FastPath Intent
 *
 * <p>命中后：
 * <ul>
 *     <li>追加一个 AssistantMessage(toolCalls=...) 到 messages</li>
 *     <li>设置 jump_to=tool 以跳过本轮 model 调用，直接进入 tool 执行</li>
 * </ul>
 */
@HookPhases(AgentPhase.REACT)
@HookPositions(HookPosition.BEFORE_AGENT)
public class FastIntentReactHook extends AgentHook {

    private static final Logger log = LoggerFactory.getLogger(FastIntentReactHook.class);

    private final ExperienceProvider experienceProvider;
    private final ExperienceExtensionProperties properties;
    private final FastIntentService fastIntentService;

    public FastIntentReactHook(ExperienceProvider experienceProvider,
                               ExperienceExtensionProperties properties,
                               FastIntentService fastIntentService) {
        this.experienceProvider = experienceProvider;
        this.properties = properties;
        this.fastIntentService = fastIntentService;
    }

    @Override
    public String getName() {
        return "FastIntentReactHook";
    }

    @Override
    public List<JumpTo> canJumpTo() {
        // 必须声明所有可能的跳转目标：
        // - JumpTo.tool: 当快速意图命中时，跳过 model 直接执行 tool
        // - JumpTo.model: 当没有匹配到经验时，继续正常流程进入 model 调用
        return List.of(JumpTo.tool, JumpTo.model);
    }

    @Override
    public Map<String, KeyStrategy> getKeyStrategys() {
        // 确保 jump_to 字段使用 REPLACE 策略，避免类型混淆
        return Map.of("jump_to", new ReplaceStrategy());
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        log.info("FastIntentReactHook#beforeAgent - reason=try react fast-intent match");

        try {
            if (!properties.isEnabled() || !properties.isReactExperienceEnabled()) {
                return CompletableFuture.completedFuture(Map.of());
            }
            if (!properties.isFastIntentEnabled() || !properties.isFastIntentReactEnabled()) {
                log.debug("FastIntentReactHook#beforeAgent - reason=fast-intent disabled");
                return CompletableFuture.completedFuture(Map.of());
            }

            ExperienceQueryContext queryContext = buildQueryContext(state, config);

            ExperienceQuery query = new ExperienceQuery(ExperienceType.REACT);
            query.setLimit(Math.max(10, properties.getMaxItemsPerQuery())); // fastpath needs enough candidates
            List<Experience> experiences = experienceProvider.query(query, queryContext);

            if (CollectionUtils.isEmpty(experiences)) {
                return CompletableFuture.completedFuture(Map.of());
            }

            List<Message> messages = state != null ? (List<Message>) state.value("messages").orElse(List.of()) : List.of();
            Map<String, Object> md = config != null ? config.metadata().orElse(Collections.emptyMap()) : Collections.emptyMap();
            String userInput = state != null ? state.value("input", String.class).orElse(null) : null;
            if (!StringUtils.hasText(userInput)) {
                // fallback: find last UserMessage text if present
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Message m = messages.get(i);
                    if (m instanceof org.springframework.ai.chat.messages.UserMessage um) {
                        userInput = um.getText();
                        break;
                    }
                }
            }

            FastIntentContext ctx = new FastIntentContext(userInput, messages, md, state, null);

            Optional<Experience> bestOpt = fastIntentService.selectBestMatch(experiences, ctx);
            if (bestOpt.isEmpty()) {
                log.debug("FastIntentReactHook#beforeAgent - reason=no matched experience");
                return CompletableFuture.completedFuture(Map.of(
                        "jump_to", JumpTo.model
                ));
            }

            Experience best = bestOpt.get();
            ExperienceArtifact artifact = best.getArtifact();
            ExperienceArtifact.ReactArtifact react = artifact != null ? artifact.getReact() : null;
            List<ExperienceArtifact.ToolCallSpec> toolCalls = react != null && react.getPlan() != null ? react.getPlan().getToolCalls() : List.of();

            if (CollectionUtils.isEmpty(toolCalls)) {
                log.warn("FastIntentReactHook#beforeAgent - reason=matched but react toolCalls empty, expId={}", best.getId());
                return CompletableFuture.completedFuture(Map.of());
            }

            // whitelist check
            List<String> allowed = properties.getFastIntentAllowedTools();
            if (!CollectionUtils.isEmpty(allowed)) {
                for (ExperienceArtifact.ToolCallSpec callSpec : toolCalls) {
                    if (callSpec == null || !StringUtils.hasText(callSpec.getToolName())) {
                        continue;
                    }
                    if (!allowed.contains(callSpec.getToolName())) {
                        log.warn("FastIntentReactHook#beforeAgent - reason=tool not allowed: tool={}, expId={}",
                                callSpec.getToolName(), best.getId());
                        return CompletableFuture.completedFuture(Map.of());
                    }
                }
            }

            List<AssistantMessage.ToolCall> assistantToolCalls = new ArrayList<>();
            for (ExperienceArtifact.ToolCallSpec callSpec : toolCalls) {
                if (callSpec == null || !StringUtils.hasText(callSpec.getToolName())) {
                    continue;
                }
                String toolCallId = "fast_intent_" + UUID.randomUUID().toString().substring(0, 8);
                String argsJson = callSpec.getArguments() != null ? JSON.toJSONString(callSpec.getArguments()) : "{}";
                assistantToolCalls.add(new AssistantMessage.ToolCall(
                        toolCallId,
                        "function",
                        callSpec.getToolName(),
                        argsJson
                ));
            }

            if (assistantToolCalls.isEmpty()) {
                log.warn("FastIntentReactHook#beforeAgent - reason=toolCalls invalid after build, expId={}", best.getId());
                return CompletableFuture.completedFuture(Map.of());
            }

            AssistantMessage assistantMessage = AssistantMessage.builder()
                    .content(react != null ? react.getAssistantText() : null)
                    .toolCalls(assistantToolCalls)
                    .build();

            Map<String, Object> fastIntentState = Map.of(
                    "hit", true,
                    "experience_id", best.getId(),
                    "experience_title", best.getTitle(),
                    "experience_type", String.valueOf(best.getType())
            );

            log.info("FastIntentReactHook#beforeAgent - reason=fast-intent HIT, expId={}, toolCalls={}",
                    best.getId(), assistantToolCalls.size());

            return CompletableFuture.completedFuture(Map.of(
                    "messages", List.of(assistantMessage),
                    "jump_to", JumpTo.tool,
                    "fast_intent", fastIntentState
            ));

        } catch (Exception e) {
            log.error("FastIntentReactHook#beforeAgent - reason=fast-intent failed", e);
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private ExperienceQueryContext buildQueryContext(OverAllState state, RunnableConfig config) {
        ExperienceQueryContext context = new ExperienceQueryContext();
        if (state != null) {
            state.value("user_id", String.class).ifPresent(context::setUserId);
            state.value("project_id", String.class).ifPresent(context::setProjectId);
            state.value("task_type", String.class).ifPresent(context::setTaskType);
        }
        if (config != null) {
            config.metadata("agent_name").ifPresent(name -> context.setAgentName(name.toString()));
            config.metadata("task_type").ifPresent(type -> context.setTaskType(type.toString()));
        }
        return context;
    }
}


