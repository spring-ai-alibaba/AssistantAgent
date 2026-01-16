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
 * Planning 意图识别 Hook
 *
 * <p>实现三层匹配策略：
 * <ol>
 *     <li>第一层：关键词快速匹配（<1ms）</li>
 *     <li>第二层：语义匹配（~50ms，使用 ES 向量搜索）</li>
 *     <li>第三层：置信度分流</li>
 * </ol>
 *
 * <p>根据置信度分流：
 * <ul>
 *     <li>>= directExecuteThreshold (默认 0.95)：直接执行，跳过 LLM</li>
 *     <li>>= hintThreshold (默认 0.7)：注入提示，让 LLM 决策</li>
 *     <li>< hintThreshold：不干预，正常流程</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@HookPositions(HookPosition.BEFORE_AGENT)
public class PlanningIntentHook extends AgentHook {

    private static final Logger logger = LoggerFactory.getLogger(PlanningIntentHook.class);

    private final ActionProvider actionProvider;
    private final PlanGenerator planGenerator;
    private final PlanExecutor planExecutor;
    private final KeywordMatcher keywordMatcher;
    private final PlanningExtensionProperties properties;

    /**
     * 直接执行阈值
     */
    private final double directExecuteThreshold;

    /**
     * 提示注入阈值
     */
    private final double hintThreshold;

    public PlanningIntentHook(ActionProvider actionProvider,
                              PlanGenerator planGenerator,
                              PlanExecutor planExecutor,
                              KeywordMatcher keywordMatcher,
                              PlanningExtensionProperties properties) {
        this.actionProvider = actionProvider;
        this.planGenerator = planGenerator;
        this.planExecutor = planExecutor;
        this.keywordMatcher = keywordMatcher;
        this.properties = properties;

        // 从配置读取阈值
        PlanningExtensionProperties.IntentConfig intentConfig = properties.getIntent();
        this.directExecuteThreshold = intentConfig != null ? intentConfig.getDirectExecuteThreshold() : 0.95;
        this.hintThreshold = intentConfig != null ? intentConfig.getHintThreshold() : 0.7;

        // 初始化关键词匹配器
        initKeywordMatcher();
    }

    /**
     * 初始化关键词匹配器，注册所有动作
     */
    private void initKeywordMatcher() {
        try {
            List<ActionDefinition> allActions = actionProvider.getAllActions();
            for (ActionDefinition action : allActions) {
                if (Boolean.TRUE.equals(action.getEnabled())) {
                    keywordMatcher.registerAction(action);
                }
            }
            logger.info("PlanningIntentHook#initKeywordMatcher - reason=initialized, actionCount={}, keywordIndexSize={}",
                    keywordMatcher.getRegisteredActionCount(), keywordMatcher.getKeywordIndexSize());
        } catch (Exception e) {
            logger.error("PlanningIntentHook#initKeywordMatcher - reason=failed to init keyword matcher", e);
        }
    }

    @Override
    public String getName() {
        return "PlanningIntentHook";
    }

    @Override
    public List<JumpTo> canJumpTo() {
        // 直接执行时可以跳到 end，注入提示时继续到 model
        return List.of(JumpTo.end, JumpTo.model);
    }

    @Override
    public Map<String, KeyStrategy> getKeyStrategys() {
        return Map.of(
                "jump_to", new ReplaceStrategy(),
                "planning_intent", new ReplaceStrategy()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        logger.debug("PlanningIntentHook#beforeAgent - reason=checking planning intent");

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
                logger.debug("PlanningIntentHook#beforeAgent - reason=keyword filter: no match");
                return CompletableFuture.completedFuture(Map.of());
            }

            // 第二层：语义匹配
            Map<String, Object> context = buildMatchContext(state, config);
            List<ActionMatch> matches = actionProvider.matchActions(userInput, context);

            if (CollectionUtils.isEmpty(matches)) {
                logger.debug("PlanningIntentHook#beforeAgent - reason=semantic match: no match");
                return CompletableFuture.completedFuture(Map.of());
            }

            // 获取最佳匹配
            ActionMatch bestMatch = matches.get(0);
            double confidence = bestMatch.getConfidence() != null ? bestMatch.getConfidence() : 0.0;

            logger.info("PlanningIntentHook#beforeAgent - reason=match found, actionId={}, confidence={}, matchType={}",
                    bestMatch.getAction().getActionId(), confidence, bestMatch.getMatchType());

            // 第三层：置信度分流
            if (confidence >= directExecuteThreshold) {
                // 高置信度：直接执行
                return handleDirectExecution(bestMatch, userInput, context, state);
            } else if (confidence >= hintThreshold) {
                // 中等置信度：注入提示
                return handleHintInjection(bestMatch, userInput, state);
            } else {
                // 低置信度：不干预
                logger.debug("PlanningIntentHook#beforeAgent - reason=low confidence, skipping");
                return CompletableFuture.completedFuture(Map.of());
            }

        } catch (Exception e) {
            logger.error("PlanningIntentHook#beforeAgent - reason=error occurred", e);
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    /**
     * 高置信度：直接执行动作
     */
    private CompletableFuture<Map<String, Object>> handleDirectExecution(
            ActionMatch match,
            String userInput,
            Map<String, Object> context,
            OverAllState state) {

        ActionDefinition action = match.getAction();
        logger.info("PlanningIntentHook#handleDirectExecution - reason=direct execution, actionId={}, actionName={}",
                action.getActionId(), action.getActionName());

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

            // 4. 构造 planning intent 状态
            Map<String, Object> intentState = Map.of(
                    "hit", true,
                    "mode", "direct_execute",
                    "action_id", action.getActionId(),
                    "action_name", action.getActionName(),
                    "plan_id", plan.getPlanId(),
                    "success", result.isSuccess(),
                    "confidence", match.getConfidence()
            );

            logger.info("PlanningIntentHook#handleDirectExecution - reason=execution completed, planId={}, success={}",
                    plan.getPlanId(), result.isSuccess());

            // 返回结果，跳过 LLM 调用
            return CompletableFuture.completedFuture(Map.of(
                    "messages", List.of(assistantMessage),
                    "jump_to", JumpTo.end,
                    "planning_intent", intentState
            ));

        } catch (Exception e) {
            logger.error("PlanningIntentHook#handleDirectExecution - reason=execution failed", e);
            // 执行失败，降级到 LLM 处理
            return handleHintInjection(match, userInput, state);
        }
    }

    /**
     * 中等置信度：注入提示让 LLM 决策
     */
    private CompletableFuture<Map<String, Object>> handleHintInjection(
            ActionMatch match,
            String userInput,
            OverAllState state) {

        ActionDefinition action = match.getAction();
        logger.info("PlanningIntentHook#handleHintInjection - reason=hint injection, actionId={}, confidence={}",
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

        hint.append("\n建议：请使用 plan_action 和 execute_action 工具来执行此动作。\n");

        // 构造 planning intent 状态
        Map<String, Object> intentState = Map.of(
                "hit", true,
                "mode", "hint_injection",
                "action_id", action.getActionId(),
                "action_name", action.getActionName(),
                "confidence", match.getConfidence(),
                "hint", hint.toString()
        );

        // 注入提示到上下文（通过 system_hint 字段）
        return CompletableFuture.completedFuture(Map.of(
                "system_hint", hint.toString(),
                "jump_to", JumpTo.model,
                "planning_intent", intentState
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
            config.metadata().get().forEach((k, v) -> context.put(k, v));
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
     * 刷新关键词索引（当动作列表变化时调用）
     */
    public void refreshKeywordIndex() {
        keywordMatcher.clear();
        initKeywordMatcher();
        logger.info("PlanningIntentHook#refreshKeywordIndex - reason=keyword index refreshed");
    }

    /**
     * 注册新动作到关键词匹配器
     */
    public void registerAction(ActionDefinition action) {
        if (action != null && Boolean.TRUE.equals(action.getEnabled())) {
            keywordMatcher.registerAction(action);
        }
    }

    /**
     * 从关键词匹配器移除动作
     */
    public void removeAction(String actionId) {
        keywordMatcher.removeAction(actionId);
    }
}
