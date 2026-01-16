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
package com.alibaba.assistant.agent.planning.intent;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.planning.config.PlanningExtensionProperties;
import com.alibaba.assistant.agent.planning.model.*;
import com.alibaba.assistant.agent.planning.spi.ActionProvider;
import com.alibaba.assistant.agent.planning.spi.PlanExecutor;
import com.alibaba.assistant.agent.planning.spi.PlanGenerator;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 统一意图识别 Hook - 整合 Planning 和 Experience
 *
 * <p>整合策略：
 * <ol>
 *     <li>第一层：关键词快速过滤（Planning KeywordMatcher）</li>
 *     <li>第二层：语义匹配（Planning ActionProvider）</li>
 *     <li>第三层：置信度分流</li>
 * </ol>
 *
 * <p>执行策略（根据置信度）：
 * <ul>
 *     <li>>= 0.95（高置信度）：
 *         <ul>
 *             <li>1. 检查 Experience：有 → FastIntent 快速执行</li>
 *             <li>2. 无 Experience：Planning 直接执行</li>
 *         </ul>
 *     </li>
 *     <li>>= 0.7（中等置信度）：注入提示，让 LLM 决策</li>
 *     <li>< 0.7（低置信度）：不干预，走正常 ReAct 流程</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@HookPositions(HookPosition.BEFORE_AGENT)
public class UnifiedIntentRecognitionHook extends AgentHook {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedIntentRecognitionHook.class);

    private final ActionProvider actionProvider;
    private final PlanGenerator planGenerator;
    private final PlanExecutor planExecutor;
    private final KeywordMatcher keywordMatcher;
    private final ExperienceProvider experienceProvider;  // 可选，Experience 模块启用时注入
    private final PlanningExtensionProperties properties;

    private final double directExecuteThreshold;
    private final double hintThreshold;

    public UnifiedIntentRecognitionHook(ActionProvider actionProvider,
                                        PlanGenerator planGenerator,
                                        PlanExecutor planExecutor,
                                        KeywordMatcher keywordMatcher,
                                        ExperienceProvider experienceProvider,
                                        PlanningExtensionProperties properties) {
        this.actionProvider = actionProvider;
        this.planGenerator = planGenerator;
        this.planExecutor = planExecutor;
        this.keywordMatcher = keywordMatcher;
        this.experienceProvider = experienceProvider;
        this.properties = properties;

        // 从配置读取阈值
        PlanningExtensionProperties.IntentConfig intentConfig = properties.getIntent();
        this.directExecuteThreshold = intentConfig != null ? intentConfig.getDirectExecuteThreshold() : 0.95;
        this.hintThreshold = intentConfig != null ? intentConfig.getHintThreshold() : 0.7;

        // 初始化关键词匹配器
        initKeywordMatcher();
    }

    /**
     * 初始化关键词匹配器
     */
    private void initKeywordMatcher() {
        try {
            List<ActionDefinition> allActions = actionProvider.getAllActions();
            for (ActionDefinition action : allActions) {
                if (Boolean.TRUE.equals(action.getEnabled())) {
                    keywordMatcher.registerAction(action);
                }
            }
            logger.info("UnifiedIntentRecognitionHook#initKeywordMatcher - reason=initialized, actionCount={}, keywordIndexSize={}",
                    keywordMatcher.getRegisteredActionCount(), keywordMatcher.getKeywordIndexSize());
        } catch (Exception e) {
            logger.error("UnifiedIntentRecognitionHook#initKeywordMatcher - reason=failed to init", e);
        }
    }

    @Override
    public String getName() {
        return "UnifiedIntentRecognitionHook";
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return List.of(JumpTo.tool, JumpTo.end, JumpTo.model);
    }

    @Override
    public Map<String, KeyStrategy> getKeyStrategys() {
        return Map.of(
                "jump_to", new ReplaceStrategy(),
                "unified_intent", new ReplaceStrategy()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        logger.debug("UnifiedIntentRecognitionHook#beforeAgent - reason=checking unified intent");

        try {
            // 检查是否启用
            if (!isEnabled()) {
                return CompletableFuture.completedFuture(Map.of());
            }

            // 获取用户输入
            String userInput = extractUserInput(state);
            if (!StringUtils.hasText(userInput)) {
                return CompletableFuture.completedFuture(Map.of());
            }

            // 第一层：关键词快速过滤
            if (!keywordMatcher.mayMatch(userInput)) {
                logger.debug("UnifiedIntentRecognitionHook#beforeAgent - reason=keyword filter: no match");
                return CompletableFuture.completedFuture(Map.of());
            }

            // 第二层：语义匹配（Planning）
            Map<String, Object> context = buildMatchContext(state, config);
            List<ActionMatch> matches = actionProvider.matchActions(userInput, context);

            if (CollectionUtils.isEmpty(matches)) {
                logger.debug("UnifiedIntentRecognitionHook#beforeAgent - reason=semantic match: no match");
                return CompletableFuture.completedFuture(Map.of());
            }

            // 获取最佳匹配
            ActionMatch bestMatch = matches.get(0);
            double confidence = bestMatch.getConfidence() != null ? bestMatch.getConfidence() : 0.0;

            logger.info("UnifiedIntentRecognitionHook#beforeAgent - reason=match found, actionId={}, confidence={}, matchType={}",
                    bestMatch.getAction().getActionId(), confidence, bestMatch.getMatchType());

            // 第三层：置信度分流
            if (confidence >= directExecuteThreshold) {
                // 高置信度（>=0.95）：检查 Experience，决定执行方式（Hook层处理）
                return handleHighConfidence(bestMatch, userInput, context, state, config);
            } else {
                // 中低置信度（<0.95）：不在Hook层处理，放行到评估模块
                // 评估模块会根据置信度注入不同的提示
                logger.debug("UnifiedIntentRecognitionHook#beforeAgent - reason=confidence < 0.95, defer to evaluation module, confidence={}", confidence);
                return CompletableFuture.completedFuture(Map.of());
            }

        } catch (Exception e) {
            logger.error("UnifiedIntentRecognitionHook#beforeAgent - reason=error occurred", e);
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    /**
     * 高置信度处理：检查 Experience，决定执行方式
     */
    private CompletableFuture<Map<String, Object>> handleHighConfidence(
            ActionMatch match,
            String userInput,
            Map<String, Object> context,
            OverAllState state,
            RunnableConfig config) {

        ActionDefinition action = match.getAction();

        // 1. 检查是否有相关 Experience
        Optional<Experience> experienceOpt = findMatchingExperience(action, userInput, state, config);

        if (experienceOpt.isPresent()) {
            // 有 Experience：使用 FastIntent 快速执行
            logger.info("UnifiedIntentRecognitionHook#handleHighConfidence - reason=found experience, using FastIntent, actionId={}, expId={}",
                    action.getActionId(), experienceOpt.get().getId());
            return handleFastIntentExecution(experienceOpt.get(), action, match);
        } else {
            // 无 Experience：使用 Planning 直接执行
            logger.info("UnifiedIntentRecognitionHook#handleHighConfidence - reason=no experience, using Planning direct execution, actionId={}",
                    action.getActionId());
            return handlePlanningDirectExecution(match, userInput, context);
        }
    }

    /**
     * 查找匹配的 Experience
     */
    private Optional<Experience> findMatchingExperience(
            ActionDefinition action,
            String userInput,
            OverAllState state,
            RunnableConfig config) {

        if (experienceProvider == null) {
            return Optional.empty();
        }

        try {
            // 构建查询上下文
            ExperienceQueryContext queryContext = buildExperienceQueryContext(state, config);

            // 查询 REACT 类型的 Experience
            ExperienceQuery query = new ExperienceQuery(ExperienceType.REACT);
            query.setLimit(10);
            List<Experience> experiences = experienceProvider.query(query, queryContext);

            if (CollectionUtils.isEmpty(experiences)) {
                return Optional.empty();
            }

            // 简单匹配：检查 experience 的 intent 或 title 是否与动作相关
            for (Experience exp : experiences) {
                if (isExperienceRelatedToAction(exp, action, userInput)) {
                    return Optional.of(exp);
                }
            }

            return Optional.empty();

        } catch (Exception e) {
            logger.warn("UnifiedIntentRecognitionHook#findMatchingExperience - reason=failed to query experience, error={}",
                    e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 判断 Experience 是否与动作相关
     */
    private boolean isExperienceRelatedToAction(Experience exp, ActionDefinition action, String userInput) {
        // 简单策略：检查 title 或 content 是否包含动作名称或关键词
        String title = exp.getTitle() != null ? exp.getTitle().toLowerCase() : "";
        String content = exp.getContent() != null ? exp.getContent().toLowerCase() : "";
        String actionName = action.getActionName() != null ? action.getActionName().toLowerCase() : "";
        String actionId = action.getActionId() != null ? action.getActionId().toLowerCase() : "";

        return title.contains(actionName) || content.contains(actionName) ||
                title.contains(actionId) || content.contains(actionId);
    }

    /**
     * FastIntent 快速执行
     */
    @SuppressWarnings("unchecked")
    private CompletableFuture<Map<String, Object>> handleFastIntentExecution(
            Experience experience,
            ActionDefinition action,
            ActionMatch match) {

        ExperienceArtifact artifact = experience.getArtifact();
        ExperienceArtifact.ReactArtifact react = artifact != null ? artifact.getReact() : null;
        List<ExperienceArtifact.ToolCallSpec> toolCalls = react != null && react.getPlan() != null ?
                react.getPlan().getToolCalls() : List.of();

        if (CollectionUtils.isEmpty(toolCalls)) {
            logger.warn("UnifiedIntentRecognitionHook#handleFastIntentExecution - reason=experience has no toolCalls, expId={}",
                    experience.getId());
            // 降级到 Planning 执行
            return CompletableFuture.completedFuture(Map.of());
        }

        // 构造 AssistantMessage.ToolCall
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
            return CompletableFuture.completedFuture(Map.of());
        }

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(react != null ? react.getAssistantText() : null)
                .toolCalls(assistantToolCalls)
                .build();

        Map<String, Object> intentState = Map.of(
                "hit", true,
                "mode", "fast_intent",
                "action_id", action.getActionId(),
                "action_name", action.getActionName(),
                "experience_id", experience.getId(),
                "confidence", match.getConfidence()
        );

        logger.info("UnifiedIntentRecognitionHook#handleFastIntentExecution - reason=fast intent executed, actionId={}, expId={}",
                action.getActionId(), experience.getId());

        return CompletableFuture.completedFuture(Map.of(
                "messages", List.of(assistantMessage),
                "jump_to", JumpTo.tool,
                "unified_intent", intentState
        ));
    }

    /**
     * Planning 直接执行
     */
    private CompletableFuture<Map<String, Object>> handlePlanningDirectExecution(
            ActionMatch match,
            String userInput,
            Map<String, Object> context) {

        ActionDefinition action = match.getAction();
        logger.info("UnifiedIntentRecognitionHook#handlePlanningDirectExecution - reason=direct execution, actionId={}",
                action.getActionId());

        try {
            // 1. 生成执行计划
            PlanGenerator.PlanGenerationContext genContext = createGenerationContext(userInput, context);
            Map<String, Object> params = match.getExtractedParameters() != null ?
                    match.getExtractedParameters() : Collections.emptyMap();
            ExecutionPlan plan = planGenerator.generate(action, params, genContext);

            // 2. 执行计划
            PlanExecutionResult result = planExecutor.execute(plan, context);

            // 3. 构造响应消息
            String responseText = buildResponseText(action, result);
            AssistantMessage assistantMessage = new AssistantMessage(responseText);

            // 4. 构造状态
            Map<String, Object> intentState = Map.of(
                    "hit", true,
                    "mode", "planning_direct",
                    "action_id", action.getActionId(),
                    "action_name", action.getActionName(),
                    "plan_id", plan.getPlanId(),
                    "success", result.isSuccess(),
                    "confidence", match.getConfidence()
            );

            logger.info("UnifiedIntentRecognitionHook#handlePlanningDirectExecution - reason=execution completed, planId={}, success={}",
                    plan.getPlanId(), result.isSuccess());

            return CompletableFuture.completedFuture(Map.of(
                    "messages", List.of(assistantMessage),
                    "jump_to", JumpTo.end,
                    "unified_intent", intentState
            ));

        } catch (Exception e) {
            logger.error("UnifiedIntentRecognitionHook#handlePlanningDirectExecution - reason=execution failed, actionId={}",
                    action.getActionId(), e);
            // 执行失败，降级到提示注入
            return handleHintInjection(match, userInput);
        }
    }

    /**
     * 中等置信度：注入提示
     */
    private CompletableFuture<Map<String, Object>> handleHintInjection(
            ActionMatch match,
            String userInput) {

        ActionDefinition action = match.getAction();
        logger.info("UnifiedIntentRecognitionHook#handleHintInjection - reason=hint injection, actionId={}, confidence={}",
                action.getActionId(), match.getConfidence());

        // 构造提示信息
        StringBuilder hint = new StringBuilder();
        hint.append("\n\n【系统提示 - 动作匹配】\n");
        hint.append("检测到用户输入可能匹配预定义动作：\n");
        hint.append("- 动作ID: ").append(action.getActionId()).append("\n");
        hint.append("- 动作名称: ").append(action.getActionName()).append("\n");
        hint.append("- 置信度: ").append(String.format("%.2f", match.getConfidence())).append("\n");
        hint.append("- 描述: ").append(action.getDescription()).append("\n");

        if (match.getExtractedParameters() != null && !match.getExtractedParameters().isEmpty()) {
            hint.append("- 提取的参数: ").append(JSON.toJSONString(match.getExtractedParameters())).append("\n");
        }

        if (match.hasMissingParameters()) {
            hint.append("- 缺失参数: ");
            match.getMissingParameters().forEach((name, param) ->
                    hint.append(name).append("(").append(param.getDescription()).append("), "));
            hint.append("\n");
        }

        hint.append("\n建议：请使用相关工具来执行此动作。\n");

        Map<String, Object> intentState = Map.of(
                "hit", true,
                "mode", "hint_injection",
                "action_id", action.getActionId(),
                "action_name", action.getActionName(),
                "confidence", match.getConfidence(),
                "hint", hint.toString()
        );

        return CompletableFuture.completedFuture(Map.of(
                "system_hint", hint.toString(),
                "jump_to", JumpTo.model,
                "unified_intent", intentState
        ));
    }

    /**
     * 构建响应文本
     */
    private String buildResponseText(ActionDefinition action, PlanExecutionResult result) {
        StringBuilder sb = new StringBuilder();

        if (result.isSuccess()) {
            sb.append("已为您执行操作「").append(action.getActionName()).append("」。\n\n");

            if (result.getOutput() != null) {
                sb.append("执行结果：\n");
                if (result.getOutput() instanceof Map) {
                    Map<?, ?> output = (Map<?, ?>) result.getOutput();
                    output.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
                } else {
                    sb.append(result.getOutput().toString());
                }
            } else {
                sb.append("操作已成功完成。");
            }
        } else {
            sb.append("执行操作「").append(action.getActionName()).append("」时遇到问题：\n");
            sb.append(result.getErrorMessage() != null ? result.getErrorMessage() : "未知错误");
        }

        return sb.toString();
    }

    /**
     * 从状态中提取用户输入
     */
    @SuppressWarnings("unchecked")
    private String extractUserInput(OverAllState state) {
        if (state == null) {
            return null;
        }

        // 首先尝试从 input 字段获取
        Optional<String> input = state.value("input", String.class);
        if (input.isPresent() && StringUtils.hasText(input.get())) {
            return input.get();
        }

        // 回退：从最后一条 UserMessage 获取
        Optional<Object> messagesOpt = state.value("messages");
        if (messagesOpt.isPresent() && messagesOpt.get() instanceof List) {
            List<Message> messages = (List<Message>) messagesOpt.get();
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message msg = messages.get(i);
                if (msg instanceof UserMessage userMsg) {
                    return userMsg.getText();
                }
            }
        }

        return null;
    }

    /**
     * 构建匹配上下文
     */
    private Map<String, Object> buildMatchContext(OverAllState state, RunnableConfig config) {
        Map<String, Object> context = new HashMap<>();

        if (state != null) {
            state.value("user_id", String.class).ifPresent(v -> context.put("userId", v));
            state.value("session_id", String.class).ifPresent(v -> context.put("sessionId", v));
            state.value("project_id", String.class).ifPresent(v -> context.put("projectId", v));
        }

        if (config != null && config.metadata().isPresent()) {
            config.metadata().get().forEach(context::put);
        }

        return context;
    }

    /**
     * 构建 Experience 查询上下文
     */
    private ExperienceQueryContext buildExperienceQueryContext(OverAllState state, RunnableConfig config) {
        ExperienceQueryContext context = new ExperienceQueryContext();

        if (state != null) {
            state.value("user_id", String.class).ifPresent(context::setUserId);
            state.value("project_id", String.class).ifPresent(context::setProjectId);
            state.value("task_type", String.class).ifPresent(context::setTaskType);
        }

        if (config != null && config.metadata().isPresent()) {
            config.metadata("agent_name").ifPresent(name -> context.setAgentName(name.toString()));
            config.metadata("task_type").ifPresent(type -> context.setTaskType(type.toString()));
        }

        return context;
    }

    /**
     * 创建计划生成上下文
     */
    private PlanGenerator.PlanGenerationContext createGenerationContext(
            String userInput, Map<String, Object> context) {

        return new PlanGenerator.PlanGenerationContext() {
            @Override
            public String getSessionId() {
                return context.get("sessionId") != null ? context.get("sessionId").toString() : null;
            }

            @Override
            public String getUserId() {
                return context.get("userId") != null ? context.get("userId").toString() : null;
            }

            @Override
            public String getUserInput() {
                return userInput;
            }

            @Override
            public Map<String, Object> getContextVariables() {
                return context;
            }

            @Override
            public Integer getTimeoutMinutes() {
                return context.get("timeoutMinutes") != null ?
                        (Integer) context.get("timeoutMinutes") : null;
            }
        };
    }

    /**
     * 检查 Hook 是否启用
     */
    private boolean isEnabled() {
        if (!properties.isEnabled()) {
            return false;
        }
        PlanningExtensionProperties.IntentConfig intentConfig = properties.getIntent();
        return intentConfig != null && intentConfig.isEnabled();
    }

    /**
     * 刷新关键词索引
     */
    public void refreshKeywordIndex() {
        keywordMatcher.clear();
        initKeywordMatcher();
        logger.info("UnifiedIntentRecognitionHook#refreshKeywordIndex - reason=keyword index refreshed");
    }

    /**
     * 注册新动作
     */
    public void registerAction(ActionDefinition action) {
        if (action != null && Boolean.TRUE.equals(action.getEnabled())) {
            keywordMatcher.registerAction(action);
        }
    }

    /**
     * 移除动作
     */
    public void removeAction(String actionId) {
        keywordMatcher.removeAction(actionId);
    }
}
