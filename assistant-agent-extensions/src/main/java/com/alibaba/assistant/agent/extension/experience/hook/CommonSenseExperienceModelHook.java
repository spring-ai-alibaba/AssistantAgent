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
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
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
 * 常识经验提示模型Hook
 * 参考记忆模块实现，在BEFORE_MODEL阶段直接修改messages列表，注入常识经验
 *
 * 核心设计：
 * 1. 从ExperienceProvider查询COMMON类型的经验
 * 2. 格式化为SystemMessage内容
 * 3. 查找现有SystemMessage并追加，或添加新的SystemMessage
 * 4. 通过返回Map.of("messages", newMessages)更新OverAllState
 *
 * @author Assistant Agent Team
 */
@HookPhases(AgentPhase.REACT)
@HookPositions(HookPosition.BEFORE_MODEL)
public class CommonSenseExperienceModelHook extends ModelHook {

    private static final Logger log = LoggerFactory.getLogger(CommonSenseExperienceModelHook.class);

    private final ExperienceProvider experienceProvider;
    private final ExperienceExtensionProperties properties;

    public CommonSenseExperienceModelHook(ExperienceProvider experienceProvider,
                                          ExperienceExtensionProperties properties) {
        this.experienceProvider = experienceProvider;
        this.properties = properties;
    }

    @Override
    public String getName() {
        return "CommonSenseExperienceModelHook";
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        log.info("CommonSenseExperienceModelHook#beforeModel - reason=开始注入常识经验");

        try {
            // 检查模块是否启用
            if (!properties.isEnabled() || !properties.isCommonExperienceEnabled()) {
                log.info("CommonSenseExperienceModelHook#beforeModel - reason=常识经验模块未启用，跳过");
                return CompletableFuture.completedFuture(Map.of());
            }

            // 构造查询上下文
            ExperienceQueryContext context = buildQueryContext(state, config);

            // 查询常识经验
            ExperienceQuery query = new ExperienceQuery(ExperienceType.COMMON);
            query.setLimit(Math.min(properties.getMaxItemsPerQuery(), 3));

            List<Experience> experiences = experienceProvider.query(query, context);

            if (CollectionUtils.isEmpty(experiences)) {
                log.info("CommonSenseExperienceModelHook#beforeModel - reason=未找到常识经验");
                return CompletableFuture.completedFuture(Map.of());
            }

            log.info("CommonSenseExperienceModelHook#beforeModel - reason=找到常识经验: {}", JSON.toJSONString(experiences));

            // 🔥 核心：参考记忆模块，直接修改messages列表
            CompletableFuture<Map<String, Object>> result = injectExperienceToMessages(state, experiences);

            // 添加日志确认返回值
            result.thenAccept(updates -> {
                log.info("CommonSenseExperienceModelHook#beforeModel - reason=Hook执行完成，返回updates: keys={}, messagesCount={}",
                    updates.keySet(),
                    updates.containsKey("messages") ? ((List<?>)updates.get("messages")).size() : "N/A");
            });

            return result;

        } catch (Exception e) {
            log.error("CommonSenseExperienceModelHook#beforeModel - reason=注入常识经验失败", e);
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    /**
     * 🔥 核心方法：注入常识经验到messages
     * 使用 AssistantMessage + ToolResponseMessage 配对方式
     */
    @SuppressWarnings("unchecked")
    private CompletableFuture<Map<String, Object>> injectExperienceToMessages(OverAllState state, List<Experience> experiences) {
        log.info("CommonSenseExperienceModelHook#injectExperienceToMessages - reason=开始处理messages");

        try {
            Optional<Object> messagesOpt = state.value("messages");
            if (messagesOpt.isEmpty()) {
                log.warn("CommonSenseExperienceModelHook#injectExperienceToMessages - reason=state中没有messages，跳过");
                return CompletableFuture.completedFuture(Map.of());
            }

            List<Message> messages = (List<Message>) messagesOpt.get();
            log.debug("CommonSenseExperienceModelHook#injectExperienceToMessages - reason=当前messages数量={}", messages.size());

            // 🔥 检查是否已经注入过常识经验
            for (Message msg : messages) {
                if (msg instanceof ToolResponseMessage toolMsg) {
                    for (ToolResponseMessage.ToolResponse response : toolMsg.getResponses()) {
                        if ("common_sense_injection".equals(response.name())) {
                            log.info("CommonSenseExperienceModelHook#injectExperienceToMessages - reason=检测到已注入常识经验，跳过");
                            return CompletableFuture.completedFuture(Map.of());
                        }
                    }
                }
            }

            // 构建经验内容
            String experienceContent = buildExperienceContent(experiences);
            log.debug("CommonSenseExperienceModelHook#injectExperienceToMessages - reason=经验内容构建完成，长度={}", experienceContent.length());

            // 🔥 构造 AssistantMessage + ToolResponseMessage 配对
            String toolCallId = "common_sense_" + UUID.randomUUID().toString().substring(0, 8);

            // 1. AssistantMessage with toolCall
            AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(
                    new AssistantMessage.ToolCall(
                        toolCallId,
                        "function",
                        "common_sense_injection",
                        "{}"  // 空参数
                    )
                ))
                .build();

            // 2. ToolResponseMessage with response
            ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(
                toolCallId,
                "common_sense_injection",
                experienceContent
            );

            ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build();

            log.info("CommonSenseExperienceModelHook#injectExperienceToMessages - reason=准备注入常识经验（AssistantMessage + ToolResponseMessage）");

            // 🔥 返回配对的两条消息
            Map<String, Object> updates = Map.of("messages", List.of(assistantMessage, toolResponseMessage));
            log.info("CommonSenseExperienceModelHook#injectExperienceToMessages - reason=准备返回updates，keys={}", updates.keySet());

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("CommonSenseExperienceModelHook#injectExperienceToMessages - reason=修改messages失败", e);
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    /**
     * 构建常识经验内容，格式化为SystemMessage文本
     */
    private String buildExperienceContent(List<Experience> experiences) {
        StringBuilder content = new StringBuilder();

        content.append("=== 补充的常识 ===\n\n");

        for (Experience experience : experiences) {
            // 通用格式化，不做特殊判断
            content.append("📋 ").append(experience.getTitle()).append("\n");

            if (StringUtils.hasText(experience.getContent())) {
                String trimmedContent = experience.getContent();
                if (trimmedContent.length() > properties.getMaxContentLength()) {
                    trimmedContent = trimmedContent.substring(0, properties.getMaxContentLength()) + "...";
                }
                content.append(trimmedContent).append("\n\n");
            }
        }

        content.append("请在回答中遵循以上规范。");

        return content.toString();
    }

    /**
     * 从State和Config构造查询上下文
     */
    private ExperienceQueryContext buildQueryContext(OverAllState state, RunnableConfig config) {
        ExperienceQueryContext context = new ExperienceQueryContext();

        // 从state提取上下文
        if (state != null) {
            state.value("user_id", String.class).ifPresent(context::setUserId);
            state.value("project_id", String.class).ifPresent(context::setProjectId);
        }

        // 从config提取Agent信息
        if (config != null) {
            config.metadata("user_id").ifPresent(id -> context.setUserId(id.toString()));
            config.metadata("agent_name").ifPresent(name -> context.setAgentName(name.toString()));
        }

        return context;
    }
}
