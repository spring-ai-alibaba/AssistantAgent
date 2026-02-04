package com.alibaba.assistant.agent.extension.experience.hook;

import com.alibaba.assistant.agent.common.hook.AgentPhase;
import com.alibaba.assistant.agent.common.hook.HookPhases;
import com.alibaba.assistant.agent.extension.experience.config.ExperienceExtensionProperties;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * React经验Agent Hook
 * 在BEFORE_AGENT阶段注入React行为策略经验
 *
 * 核心设计：
 * 1. 在Agent启动前查询React策略经验
 * 2. 将策略经验注入到初始messages中
 * 3. 影响Agent的整体行为模式
 *
 * @author Assistant Agent Team
 */
@HookPhases(AgentPhase.REACT)
@HookPositions(HookPosition.BEFORE_AGENT)
public class ReactExperienceAgentHook extends AgentHook {

    private static final Logger log = LoggerFactory.getLogger(ReactExperienceAgentHook.class);

    private final ExperienceProvider experienceProvider;
    private final ExperienceExtensionProperties properties;

    public ReactExperienceAgentHook(ExperienceProvider experienceProvider,
                                   ExperienceExtensionProperties properties) {
        this.experienceProvider = experienceProvider;
        this.properties = properties;
    }

    @Override
    public String getName() {
        return "ReactExperienceAgentHook";
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        log.info("ReactExperienceAgentHook#beforeAgent - reason=开始注入React策略经验");

        try {
            // 检查模块是否启用
            if (!properties.isEnabled() || !properties.isReactExperienceEnabled()) {
                log.info("ReactExperienceAgentHook#beforeAgent - reason=React经验模块未启用，跳过");
                return CompletableFuture.completedFuture(Map.of());
            }

            // 构造查询上下文
            ExperienceQueryContext context = buildQueryContext(state, config);

            // 查询React经验
            ExperienceQuery query = new ExperienceQuery(ExperienceType.REACT);
            query.setLimit(Math.min(properties.getMaxItemsPerQuery(), 3));

            List<Experience> experiences = experienceProvider.query(query, context);

            if (CollectionUtils.isEmpty(experiences)) {
                log.info("ReactExperienceAgentHook#beforeAgent - reason=未找到React策略经验");
                return CompletableFuture.completedFuture(Map.of());
            }

            log.info("ReactExperienceAgentHook#beforeAgent - reason=找到React策略经验: {}", JSON.toJSONString(experiences));

            // 🔥 核心：注入策略经验到messages
            return injectReactExperienceToMessages(state, experiences);

        } catch (Exception e) {
            log.error("ReactExperienceAgentHook#beforeAgent - reason=注入React经验失败", e);
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    /**
     * 🔥 核心方法：注入React策略经验到messages
     * 使用 AssistantMessage + ToolResponseMessage 配对方式
     */
    @SuppressWarnings("unchecked")
    private CompletableFuture<Map<String, Object>> injectReactExperienceToMessages(OverAllState state, List<Experience> experiences) {
        log.info("ReactExperienceAgentHook#injectReactExperienceToMessages - reason=开始处理messages");

        try {
            Optional<Object> messagesOpt = state.value("messages");
            if (messagesOpt.isEmpty()) {
                log.warn("ReactExperienceAgentHook#injectReactExperienceToMessages - reason=state中没有messages，跳过");
                return CompletableFuture.completedFuture(Map.of());
            }

            List<Message> messages = (List<Message>) messagesOpt.get();
            log.debug("ReactExperienceAgentHook#injectReactExperienceToMessages - reason=当前messages数量={}", messages.size());

            // 🔥 检查是否已经注入过React策略经验
            for (Message msg : messages) {
                if (msg instanceof ToolResponseMessage toolMsg) {
                    for (ToolResponseMessage.ToolResponse response : toolMsg.getResponses()) {
                        if ("react_strategy_injection".equals(response.name())) {
                            log.info("ReactExperienceAgentHook#injectReactExperienceToMessages - reason=检测到已注入React策略经验，跳过");
                            return CompletableFuture.completedFuture(Map.of());
                        }
                    }
                }
            }

            // 构建React策略内容
            String reactStrategyContent = buildReactStrategyContent(experiences);
            log.debug("ReactExperienceAgentHook#injectReactExperienceToMessages - reason=经验内容构建完成，长度={}", reactStrategyContent.length());

            // 🔥 构造 AssistantMessage + ToolResponseMessage 配对
            String toolCallId = "react_strategy_" + UUID.randomUUID().toString().substring(0, 8);

            // 1. AssistantMessage with toolCall
            AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(
                    new AssistantMessage.ToolCall(
                        toolCallId,
                        "function",
                        "react_strategy_injection",
                        "{}"  // 空参数
                    )
                ))
                .build();

            // 2. ToolResponseMessage with response
            ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(
                toolCallId,
                "react_strategy_injection",
                reactStrategyContent
            );

            ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build();

            log.info("ReactExperienceAgentHook#injectReactExperienceToMessages - reason=准备注入React策略经验（AssistantMessage + ToolResponseMessage）");

            // 🔥 返回配对的两条消息
            Map<String, Object> updates = Map.of("messages", List.of(assistantMessage, toolResponseMessage));
            log.info("ReactExperienceAgentHook#injectReactExperienceToMessages - reason=准备返回updates，keys={}", updates.keySet());

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("ReactExperienceAgentHook#injectReactExperienceToMessages - reason=修改messages失败", e);
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    /**
     * 构建React策略内容
     */
    private String buildReactStrategyContent(List<Experience> experiences) {
        StringBuilder content = new StringBuilder();

        content.append("=== Agent行为策略指导 ===\n\n");

        for (Experience experience : experiences) {
            content.append("🎯 策略：").append(experience.getTitle()).append("\n");

            if (StringUtils.hasText(experience.getContent())) {
                String trimmedContent = experience.getContent();
                if (trimmedContent.length() > properties.getMaxContentLength()) {
                    trimmedContent = trimmedContent.substring(0, properties.getMaxContentLength()) + "...";
                }
                content.append(trimmedContent).append("\n\n");
            }
        }

        content.append("请在执行任务时遵循以上策略指导。");

        return content.toString();
    }

    /**
     * 构建查询上下文
     */
    private ExperienceQueryContext buildQueryContext(OverAllState state, RunnableConfig config) {
        ExperienceQueryContext context = new ExperienceQueryContext();

        // 从state提取
        if (state != null) {
            state.value("user_id", String.class).ifPresent(context::setUserId);
            state.value("project_id", String.class).ifPresent(context::setProjectId);
            state.value("task_type", String.class).ifPresent(context::setTaskType);
        }

        // 从config提取
        if (config != null) {
            config.metadata("agent_name").ifPresent(name -> context.setAgentName(name.toString()));
            config.metadata("task_type").ifPresent(type -> context.setTaskType(type.toString()));
        }

        return context;
    }
}
