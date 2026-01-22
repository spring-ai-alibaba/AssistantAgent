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
import com.alibaba.assistant.agent.planning.session.ParamCollectionSession;
import com.alibaba.assistant.agent.planning.session.ParamCollectionSessionStore;
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
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
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
    private final ChatModel chatModel;  // 用于 LLM 参数验证
    private final ParamCollectionSessionStore sessionStore;  // 参数收集会话存储（支持分布式）
    private final PlanningExtensionProperties properties;

    private final double directExecuteThreshold;
    private final double hintThreshold;

    public UnifiedIntentRecognitionHook(ActionProvider actionProvider,
                                        PlanGenerator planGenerator,
                                        PlanExecutor planExecutor,
                                        KeywordMatcher keywordMatcher,
                                        ExperienceProvider experienceProvider,
                                        ChatModel chatModel,
                                        ParamCollectionSessionStore sessionStore,
                                        PlanningExtensionProperties properties) {
        this.actionProvider = actionProvider;
        this.planGenerator = planGenerator;
        this.planExecutor = planExecutor;
        this.keywordMatcher = keywordMatcher;
        this.experienceProvider = experienceProvider;
        this.chatModel = chatModel;
        this.sessionStore = sessionStore;
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

            // 获取会话ID（用于分布式会话存储）
            String sessionId = extractSessionId(state, config);

            // 🔥 检查是否在参数收集会话中（多轮对话）- 从分布式存储读取
            if (sessionStore != null && sessionId != null) {
                Optional<ParamCollectionSession> sessionOpt = sessionStore.get(sessionId);
                if (sessionOpt.isPresent()) {
                    ParamCollectionSession session = sessionOpt.get();
                    if (session.isActive() && session.isAwaitingInput()) {
                        logger.info("UnifiedIntentRecognitionHook#beforeAgent - reason=continuing param collection session (from store), sessionId={}, userInput={}",
                                sessionId, userInput);
                        return handleParamCollectionContinuation(session, userInput, state, config);
                    }
                }
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
                // 高置信度（>=0.95）：检查是否有 Experience 可以快速执行
                Optional<Experience> experienceOpt = findMatchingExperience(bestMatch.getAction(), userInput, state, config);
                if (experienceOpt.isPresent()) {
                    // 有 Experience：使用 FastIntent 快速执行（不需要参数提取）
                    logger.info("UnifiedIntentRecognitionHook#beforeAgent - reason=found experience, using FastIntent, actionId={}, expId={}",
                            bestMatch.getAction().getActionId(), experienceOpt.get().getId());
                    return handleFastIntentExecution(experienceOpt.get(), bestMatch.getAction(), bestMatch);
                }
                // 无 Experience：直接执行参数收集流程（Planning Direct Execution）
                logger.info("UnifiedIntentRecognitionHook#beforeAgent - reason=high confidence, using Planning direct execution with param collection, actionId={}, confidence={}",
                        bestMatch.getAction().getActionId(), confidence);
                return handlePlanningDirectExecution(bestMatch, userInput, context, state, config);
            } else if (confidence >= hintThreshold) {
                // 中等置信度（>=0.7）：也直接执行参数收集流程
                logger.info("UnifiedIntentRecognitionHook#beforeAgent - reason=medium confidence, using Planning direct execution with param collection, actionId={}, confidence={}",
                        bestMatch.getAction().getActionId(), confidence);
                return handlePlanningDirectExecution(bestMatch, userInput, context, state, config);
            } else {
                // 低置信度（<0.7）：放行到正常 ReAct 流程
                logger.debug("UnifiedIntentRecognitionHook#beforeAgent - reason=confidence < 0.7, defer to normal flow, confidence={}", confidence);
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
            return handlePlanningDirectExecution(match, userInput, context, state, config);
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
        List<Map<String, Object>> simpleToolCalls = new ArrayList<>();
        for (ExperienceArtifact.ToolCallSpec callSpec : toolCalls) {
            if (callSpec == null || !StringUtils.hasText(callSpec.getToolName())) {
                continue;
            }
            String toolCallId = "fast_intent_" + UUID.randomUUID().toString().substring(0, 8);
            String argsJson = callSpec.getArguments() != null ? JSON.toJSONString(callSpec.getArguments()) : "{}";

            // 🔥 将ToolCall转换为简单Map，避免Jackson序列化时的@class重复问题
            Map<String, Object> simpleToolCall = new HashMap<>();
            simpleToolCall.put("id", toolCallId);
            simpleToolCall.put("type", "function");
            simpleToolCall.put("name", callSpec.getToolName());
            simpleToolCall.put("arguments", argsJson);
            simpleToolCalls.add(simpleToolCall);
        }

        if (simpleToolCalls.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        // 使用真正的 AssistantMessage 对象
        List<AssistantMessage.ToolCall> toolCallList = new ArrayList<>();
        for (Map<String, Object> tc : simpleToolCalls) {
            toolCallList.add(new AssistantMessage.ToolCall(
                    (String) tc.get("id"),
                    (String) tc.get("type"),
                    (String) tc.get("name"),
                    (String) tc.get("arguments")
            ));
        }

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(react != null ? react.getAssistantText() : null)
                .toolCalls(toolCallList)
                .build();

        // 构造状态（使用HashMap避免Jackson序列化时的@class重复问题）
        Map<String, Object> intentState = new HashMap<>();
        intentState.put("hit", true);
        intentState.put("mode", "fast_intent");
        intentState.put("action_id", action.getActionId());
        intentState.put("action_name", action.getActionName());
        intentState.put("experience_id", experience.getId());
        intentState.put("confidence", match.getConfidence());

        logger.info("UnifiedIntentRecognitionHook#handleFastIntentExecution - reason=fast intent executed, actionId={}, expId={}",
                action.getActionId(), experience.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("messages", List.of(assistantMessage));
        result.put("jump_to", JumpTo.tool);
        result.put("unified_intent", intentState);

        return CompletableFuture.completedFuture(result);
    }

    /**
     * Planning 直接执行
     */
    private CompletableFuture<Map<String, Object>> handlePlanningDirectExecution(
            ActionMatch match,
            String userInput,
            Map<String, Object> context,
            OverAllState state,
            RunnableConfig config) {

        ActionDefinition action = match.getAction();
        logger.info("UnifiedIntentRecognitionHook#handlePlanningDirectExecution - reason=direct execution, actionId={}",
                action.getActionId());

        try {
            // 0. 检查必填参数是否缺失
            Map<String, Object> extractedParams = match.getExtractedParameters() != null ?
                    match.getExtractedParameters() : Collections.emptyMap();
            List<ActionParameter> missingRequiredParams = findMissingRequiredParameters(action, extractedParams);

            if (!missingRequiredParams.isEmpty()) {
                // 有缺失的必填参数，生成追问问题
                logger.info("UnifiedIntentRecognitionHook#handlePlanningDirectExecution - reason=missing required params, count={}, actionId={}",
                        missingRequiredParams.size(), action.getActionId());
                return handleMissingParameters(action, match, missingRequiredParams, state, config);
            }

            // 1. 生成执行计划
            PlanGenerator.PlanGenerationContext genContext = createGenerationContext(userInput, context);
            ExecutionPlan plan = planGenerator.generate(action, extractedParams, genContext);

            // 2. 执行计划
            PlanExecutionResult result = planExecutor.execute(plan, context);

            // 3. 构造响应消息
            String responseText = buildResponseText(action, result);

            // 4. 构造状态（使用HashMap避免Jackson序列化时的@class重复问题）
            Map<String, Object> intentState = new HashMap<>();
            intentState.put("hit", true);
            intentState.put("mode", "planning_direct");
            intentState.put("action_id", action.getActionId());
            intentState.put("action_name", action.getActionName());
            intentState.put("plan_id", plan.getPlanId());
            intentState.put("success", result.isSuccess());
            intentState.put("confidence", match.getConfidence());

            logger.info("UnifiedIntentRecognitionHook#handlePlanningDirectExecution - reason=execution completed, planId={}, success={}",
                    plan.getPlanId(), result.isSuccess());

            // 使用真正的 AssistantMessage 对象
            AssistantMessage assistantMessage = new AssistantMessage(responseText);

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("messages", List.of(assistantMessage));
            resultMap.put("jump_to", JumpTo.end);
            resultMap.put("unified_intent", intentState);

            return CompletableFuture.completedFuture(resultMap);

        } catch (Exception e) {
            logger.error("UnifiedIntentRecognitionHook#handlePlanningDirectExecution - reason=execution failed, actionId={}",
                    action.getActionId(), e);
            // 执行失败，降级到提示注入
            return handleHintInjection(match, userInput);
        }
    }

    /**
     * 查找缺失的必填参数
     */
    private List<ActionParameter> findMissingRequiredParameters(ActionDefinition action, Map<String, Object> extractedParams) {
        List<ActionParameter> missing = new ArrayList<>();

        if (action.getParameters() == null || action.getParameters().isEmpty()) {
            return missing;
        }

        for (ActionParameter param : action.getParameters()) {
            // 检查是否为必填参数
            if (Boolean.TRUE.equals(param.getRequired())) {
                String paramName = param.getName();
                // 检查参数是否已提供
                if (!extractedParams.containsKey(paramName) ||
                        extractedParams.get(paramName) == null ||
                        (extractedParams.get(paramName) instanceof String && ((String) extractedParams.get(paramName)).isBlank())) {
                    missing.add(param);
                }
            }
        }

        return missing;
    }

    /**
     * 处理缺失参数：生成追问问题返回给用户
     */
    private CompletableFuture<Map<String, Object>> handleMissingParameters(
            ActionDefinition action,
            ActionMatch match,
            List<ActionParameter> missingParams,
            OverAllState state,
            RunnableConfig config) {

        // 生成追问问题（询问第一个缺失的必填参数）
        ActionParameter firstMissing = missingParams.get(0);
        String question = generateParameterQuestion(firstMissing, action);

        logger.info("UnifiedIntentRecognitionHook#handleMissingParameters - reason=asking for param, paramName={}, actionId={}",
                firstMissing.getName(), action.getActionId());

        // 🔥 创建并保存参数收集会话到分布式存储
        String sessionId = extractSessionId(state, config);
        if (sessionStore != null && sessionId != null) {
            ParamCollectionSession session = new ParamCollectionSession(sessionId);
            session.activate(action.getActionId(), action.getActionName(),
                    match.getConfidence() != null ? match.getConfidence() : 0.0);
            session.setNextQuestionAndAwait(question,
                    missingParams.stream().map(ActionParameter::getName).toList());
            if (match.getExtractedParameters() != null) {
                session.setCollectedParams(new HashMap<>(match.getExtractedParameters()));
            }
            // 从 state 获取 userId
            if (state != null) {
                state.value("user_id", String.class).ifPresent(session::setUserId);
            }
            saveSession(session);
            logger.info("UnifiedIntentRecognitionHook#handleMissingParameters - reason=session saved to store, sessionId={}, actionId={}",
                    sessionId, action.getActionId());
        } else {
            logger.warn("UnifiedIntentRecognitionHook#handleMissingParameters - reason=cannot save session, sessionStore={}, sessionId={}",
                    sessionStore != null ? "available" : "null", sessionId);
        }

        // 构造参数收集状态
        Map<String, Object> paramCollectionState = new HashMap<>();
        paramCollectionState.put("active", true);
        paramCollectionState.put("actionId", action.getActionId());
        paramCollectionState.put("actionName", action.getActionName());
        paramCollectionState.put("awaitingParam", firstMissing.getName());
        paramCollectionState.put("missingParams", missingParams.stream().map(ActionParameter::getName).toList());

        // 🔥 创建防御性副本，确保所有值都是简单类型，避免Jackson序列化时的@class重复问题
        Map<String, Object> simpleCollectedParams = new HashMap<>();
        if (match.getExtractedParameters() != null) {
            for (Map.Entry<String, Object> entry : match.getExtractedParameters().entrySet()) {
                Object value = entry.getValue();
                // 将复杂对象转换为字符串，保留简单类型
                if (value != null && !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                    simpleCollectedParams.put(entry.getKey(), value.toString());
                } else {
                    simpleCollectedParams.put(entry.getKey(), value);
                }
            }
        }
        paramCollectionState.put("collectedParams", simpleCollectedParams);

        // 构造状态（使用HashMap避免Jackson序列化时的@class重复问题）
        Map<String, Object> intentState = new HashMap<>();
        intentState.put("hit", true);
        intentState.put("mode", "param_collection");
        intentState.put("action_id", action.getActionId());
        intentState.put("action_name", action.getActionName());
        intentState.put("confidence", match.getConfidence() != null ? match.getConfidence() : 0.0);

        // 使用真正的 AssistantMessage 对象
        AssistantMessage assistantMessage = new AssistantMessage(question);

        Map<String, Object> result = new HashMap<>();
        result.put("messages", List.of(assistantMessage));
        result.put("jump_to", JumpTo.end);
        result.put("unified_intent", intentState);
        result.put("param_collection", paramCollectionState);

        return CompletableFuture.completedFuture(result);
    }

    /**
     * 生成参数询问问题
     */
    private String generateParameterQuestion(ActionParameter param, ActionDefinition action) {
        StringBuilder question = new StringBuilder();

        // 使用参数的 label 或 name 作为显示名称
        String displayName = StringUtils.hasText(param.getLabel()) ? param.getLabel() : param.getName();

        // 使用参数的 placeholder 或 description 作为提示
        if (StringUtils.hasText(param.getPlaceholder())) {
            question.append("请输入").append(displayName).append("（").append(param.getPlaceholder()).append("）");
        } else if (StringUtils.hasText(param.getDescription())) {
            question.append("请输入").append(displayName).append("：").append(param.getDescription());
        } else {
            question.append("请输入").append(displayName);
        }

        return question.toString();
    }

    /**
     * 处理参数收集会话的后续轮次
     *
     * <p>当用户已经在参数收集会话中时，将用户输入作为参数值处理。
     * <p>使用分布式会话存储保持状态一致性。
     */
    private CompletableFuture<Map<String, Object>> handleParamCollectionContinuation(
            ParamCollectionSession session,
            String userInput,
            OverAllState state,
            RunnableConfig config) {

        String actionId = session.getActionId();
        String actionName = session.getActionName();
        Map<String, Object> collectedParams = session.getCollectedParams() != null ?
                new HashMap<>(session.getCollectedParams()) : new HashMap<>();

        logger.info("UnifiedIntentRecognitionHook#handleParamCollectionContinuation - reason=processing param input, sessionId={}, actionId={}, userInput={}",
                session.getSessionId(), actionId, userInput);

        // 获取动作定义
        ActionDefinition action = null;
        try {
            List<ActionDefinition> allActions = actionProvider.getAllActions();
            for (ActionDefinition a : allActions) {
                if (actionId.equals(a.getActionId())) {
                    action = a;
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("UnifiedIntentRecognitionHook#handleParamCollectionContinuation - reason=failed to get action", e);
        }

        if (action == null) {
            logger.warn("UnifiedIntentRecognitionHook#handleParamCollectionContinuation - reason=action not found, actionId={}", actionId);
            // 关闭会话
            closeSession(session);
            return CompletableFuture.completedFuture(Map.of());
        }

        // 使用 LLM 继续参数提取
        if (chatModel != null) {
            try {
                String prompt = buildContinuationPrompt(action, collectedParams, userInput);
                String llmResponse = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();

                logger.info("UnifiedIntentRecognitionHook#handleParamCollectionContinuation - reason=LLM response, response={}", llmResponse);

                LlmParamResult result = parseLlmParamResult(llmResponse);

                if (result != null) {
                    // 合并新提取的参数
                    if (result.extractedParams != null) {
                        collectedParams.putAll(result.extractedParams);
                        session.mergeCollectedParams(result.extractedParams);
                    }

                    // 检查是否还有 nextQuestion
                    if (StringUtils.hasText(result.nextQuestion)) {
                        logger.info("UnifiedIntentRecognitionHook#handleParamCollectionContinuation - reason=still has missing params, nextQuestion={}",
                                result.nextQuestion);

                        // 更新会话状态并保存到分布式存储
                        session.setNextQuestionAndAwait(result.nextQuestion, result.missingParams);
                        saveSession(session);

                        // 使用真正的 AssistantMessage 对象
                        AssistantMessage assistantMessage = new AssistantMessage(result.nextQuestion);

                        return CompletableFuture.completedFuture(Map.of(
                                "messages", List.of(assistantMessage),
                                "jump_to", JumpTo.end
                        ));
                    }

                    // 参数收集完成，执行动作
                    logger.info("UnifiedIntentRecognitionHook#handleParamCollectionContinuation - reason=params complete, executing action, actionId={}, params={}",
                            actionId, collectedParams);

                    // 关闭会话
                    closeSession(session);

                    // 创建 ActionMatch 并执行
                    ActionMatch match = new ActionMatch();
                    match.setAction(action);
                    match.setConfidence(1.0);
                    match.setExtractedParameters(collectedParams);

                    // 执行动作
                    Map<String, Object> context = buildMatchContext(state, config);
                    return handlePlanningDirectExecution(match, userInput, context, state, config);
                }
            } catch (Exception e) {
                logger.error("UnifiedIntentRecognitionHook#handleParamCollectionContinuation - reason=LLM call failed", e);
            }
        }

        // LLM 不可用或失败，使用简单策略：将用户输入作为第一个缺失参数的值
        List<String> missingParams = session.getMissingParams() != null ?
                session.getMissingParams() : List.of();

        if (!missingParams.isEmpty()) {
            String firstMissing = missingParams.get(0);
            collectedParams.put(firstMissing, userInput);
            session.addCollectedParam(firstMissing, userInput);
            logger.info("UnifiedIntentRecognitionHook#handleParamCollectionContinuation - reason=assigned input to param (fallback), param={}, value={}",
                    firstMissing, userInput);
        }

        // 检查是否还有其他必填参数缺失
        List<ActionParameter> stillMissing = findMissingRequiredParameters(action, collectedParams);

        if (!stillMissing.isEmpty()) {
            // 还有缺失参数，继续询问
            ActionParameter nextParam = stillMissing.get(0);
            String question = generateParameterQuestion(nextParam, action);

            // 更新会话状态并保存
            session.setNextQuestionAndAwait(question, stillMissing.stream().map(ActionParameter::getName).toList());
            saveSession(session);

            // 使用真正的 AssistantMessage 对象
            AssistantMessage assistantMessage = new AssistantMessage(question);

            return CompletableFuture.completedFuture(Map.of(
                    "messages", List.of(assistantMessage),
                    "jump_to", JumpTo.end
            ));
        }

        // 参数收集完成，执行动作
        logger.info("UnifiedIntentRecognitionHook#handleParamCollectionContinuation - reason=params complete (fallback), executing action, actionId={}",
                actionId);

        // 关闭会话
        closeSession(session);

        ActionMatch match = new ActionMatch();
        match.setAction(action);
        match.setConfidence(1.0);
        match.setExtractedParameters(collectedParams);

        Map<String, Object> context = buildMatchContext(state, config);
        return handlePlanningDirectExecution(match, userInput, context, state, config);
    }

    /**
     * 构建参数收集后续轮次的 LLM Prompt
     *
     * <p>委托给 {@link ParamExtractionPromptBuilder} 进行统一管理。
     */
    private String buildContinuationPrompt(ActionDefinition action, Map<String, Object> collectedParams, String userInput) {
        return ParamExtractionPromptBuilder.getInstance().buildContinuationPrompt(action, collectedParams, userInput);
    }

    /**
     * 使用 LLM 进行参数提取和验证
     *
     * <p>调用 LLM 分析用户输入，提取动作参数，检查必填参数是否缺失。
     * 如果有缺失参数，返回 nextQuestion 给用户；如果参数完整，执行动作。
     */
    private CompletableFuture<Map<String, Object>> handleLlmParamExtraction(
            ActionMatch match,
            String userInput,
            Map<String, Object> context,
            OverAllState state,
            RunnableConfig config) {

        ActionDefinition action = match.getAction();

        if (chatModel == null) {
            logger.warn("UnifiedIntentRecognitionHook#handleLlmParamExtraction - reason=chatModel is null, falling back to rule-based check");
            // 降级到规则检查
            Map<String, Object> extractedParams = match.getExtractedParameters() != null ?
                    match.getExtractedParameters() : Collections.emptyMap();
            List<ActionParameter> missingParams = findMissingRequiredParameters(action, extractedParams);
            if (!missingParams.isEmpty()) {
                return handleMissingParameters(action, match, missingParams, state, config);
            }
            return handlePlanningDirectExecution(match, userInput, context, state, config);
        }

        try {
            // 构建 LLM Prompt
            String prompt = buildParamExtractionPrompt(action, userInput);

            logger.debug("UnifiedIntentRecognitionHook#handleLlmParamExtraction - reason=calling LLM, actionId={}", action.getActionId());

            // 调用 LLM
            String llmResponse = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();

            logger.info("UnifiedIntentRecognitionHook#handleLlmParamExtraction - reason=LLM response received, actionId={}, response={}",
                    action.getActionId(), llmResponse);

            // 解析 LLM 返回结果
            LlmParamResult result = parseLlmParamResult(llmResponse);

            if (result == null) {
                logger.warn("UnifiedIntentRecognitionHook#handleLlmParamExtraction - reason=failed to parse LLM response, falling back to rule-based check");
                // 解析失败，降级到规则检查
                Map<String, Object> extractedParams = match.getExtractedParameters() != null ?
                        match.getExtractedParameters() : Collections.emptyMap();
                List<ActionParameter> missingParams = findMissingRequiredParameters(action, extractedParams);
                if (!missingParams.isEmpty()) {
                    return handleMissingParameters(action, match, missingParams, state, config);
                }
                return handlePlanningDirectExecution(match, userInput, context, state, config);
            }

            // 检查是否有 nextQuestion
            if (StringUtils.hasText(result.nextQuestion)) {
                logger.info("UnifiedIntentRecognitionHook#handleLlmParamExtraction - reason=has nextQuestion, returning to user, question={}",
                        result.nextQuestion);
                return handleNextQuestion(action, match, result, state, config);
            }

            // 参数完整，更新 match 中的参数并执行
            if (result.extractedParams != null && !result.extractedParams.isEmpty()) {
                // 合并参数
                Map<String, Object> mergedParams = new HashMap<>();
                if (match.getExtractedParameters() != null) {
                    mergedParams.putAll(match.getExtractedParameters());
                }
                mergedParams.putAll(result.extractedParams);
                match.setExtractedParameters(mergedParams);
            }

            logger.info("UnifiedIntentRecognitionHook#handleLlmParamExtraction - reason=params complete, executing action, actionId={}",
                    action.getActionId());
            return handlePlanningDirectExecution(match, userInput, context, state, config);

        } catch (Exception e) {
            logger.error("UnifiedIntentRecognitionHook#handleLlmParamExtraction - reason=LLM call failed, actionId={}",
                    action.getActionId(), e);
            // LLM 调用失败，降级到规则检查
            Map<String, Object> extractedParams = match.getExtractedParameters() != null ?
                    match.getExtractedParameters() : Collections.emptyMap();
            List<ActionParameter> missingParams = findMissingRequiredParameters(action, extractedParams);
            if (!missingParams.isEmpty()) {
                return handleMissingParameters(action, match, missingParams, state, config);
            }
            return handlePlanningDirectExecution(match, userInput, context, state, config);
        }
    }

    /**
     * 构建参数提取的 LLM Prompt
     *
     * <p>委托给 {@link ParamExtractionPromptBuilder} 进行统一管理。
     */
    private String buildParamExtractionPrompt(ActionDefinition action, String userInput) {
        return ParamExtractionPromptBuilder.getInstance().buildInitialExtractionPrompt(action, userInput);
    }

    /**
     * 解析 LLM 返回的参数提取结果
     */
    private LlmParamResult parseLlmParamResult(String response) {
        try {
            // 尝试提取 JSON
            String json = response;

            // 去除 markdown 代码块
            if (json.contains("```json")) {
                int start = json.indexOf("```json") + 7;
                int end = json.indexOf("```", start);
                if (end > start) {
                    json = json.substring(start, end).trim();
                }
            } else if (json.contains("```")) {
                int start = json.indexOf("```") + 3;
                int end = json.indexOf("```", start);
                if (end > start) {
                    json = json.substring(start, end).trim();
                }
            }

            // 尝试找到 JSON 对象
            int braceStart = json.indexOf("{");
            int braceEnd = json.lastIndexOf("}");
            if (braceStart >= 0 && braceEnd > braceStart) {
                json = json.substring(braceStart, braceEnd + 1);
            }

            JSONObject jsonObj = JSON.parseObject(json);
            LlmParamResult result = new LlmParamResult();

            // 提取 extractedParams
            JSONObject extractedParamsObj = jsonObj.getJSONObject("extractedParams");
            if (extractedParamsObj != null) {
                result.extractedParams = new HashMap<>(extractedParamsObj);
            }

            // 提取 missingParams
            if (jsonObj.containsKey("missingParams")) {
                result.missingParams = jsonObj.getJSONArray("missingParams").toJavaList(String.class);
            }

            // 提取 nextQuestion
            result.nextQuestion = jsonObj.getString("nextQuestion");
            if ("null".equalsIgnoreCase(result.nextQuestion)) {
                result.nextQuestion = null;
            }

            return result;

        } catch (Exception e) {
            logger.warn("UnifiedIntentRecognitionHook#parseLlmParamResult - reason=parse failed, error={}", e.getMessage());
            return null;
        }
    }

    /**
     * LLM 参数提取结果
     */
    private static class LlmParamResult {
        Map<String, Object> extractedParams;
        List<String> missingParams;
        String nextQuestion;
    }

    /**
     * 处理 nextQuestion：返回追问给用户，并保存会话到分布式存储
     */
    private CompletableFuture<Map<String, Object>> handleNextQuestion(
            ActionDefinition action,
            ActionMatch match,
            LlmParamResult result,
            OverAllState state,
            RunnableConfig config) {

        // 获取会话ID并创建/保存会话到分布式存储
        String sessionId = extractSessionId(state, config);
        if (sessionStore != null && sessionId != null) {
            ParamCollectionSession session = new ParamCollectionSession(sessionId);
            session.activate(action.getActionId(), action.getActionName(),
                    match.getConfidence() != null ? match.getConfidence() : 0.0);
            session.setNextQuestionAndAwait(result.nextQuestion, result.missingParams);
            if (result.extractedParams != null) {
                session.setCollectedParams(new HashMap<>(result.extractedParams));
            }
            // 从 state 获取 userId
            if (state != null) {
                state.value("user_id", String.class).ifPresent(session::setUserId);
            }
            saveSession(session);
            logger.info("UnifiedIntentRecognitionHook#handleNextQuestion - reason=session saved to store, sessionId={}, actionId={}",
                    sessionId, action.getActionId());
        }

        // 构造状态（使用HashMap避免Jackson序列化时的@class重复问题）
        Map<String, Object> intentState = new HashMap<>();
        intentState.put("hit", true);
        intentState.put("mode", "param_collection_llm");
        intentState.put("action_id", action.getActionId());
        intentState.put("action_name", action.getActionName());
        intentState.put("confidence", match.getConfidence() != null ? match.getConfidence() : 0.0);

        logger.info("UnifiedIntentRecognitionHook#handleNextQuestion - reason=returning nextQuestion, actionId={}, question={}",
                action.getActionId(), result.nextQuestion);

        // 使用真正的 AssistantMessage 对象
        AssistantMessage assistantMessage = new AssistantMessage(result.nextQuestion);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("messages", List.of(assistantMessage));
        resultMap.put("jump_to", JumpTo.end);
        resultMap.put("unified_intent", intentState);

        return CompletableFuture.completedFuture(resultMap);
    }

    /**
     * 注入提示让 LLM 使用 plan_action 工具（符合 Code-as-Action）
     *
     * <p>此方法用于高置信度意图识别后，引导 LLM 调用 plan_action 工具，
     * 而不是直接执行动作。这样可以确保：
     * <ul>
     *     <li>符合 Code-as-Action 核心流程</li>
     *     <li>通过 BasePlanningCodeactTool 执行</li>
     *     <li>可以被 Experience 模块学习</li>
     *     <li>保持可观测性</li>
     * </ul>
     */
    private CompletableFuture<Map<String, Object>> handleToolBasedHintInjection(
            ActionMatch match,
            String userInput) {

        ActionDefinition action = match.getAction();
        logger.info("UnifiedIntentRecognitionHook#handleToolBasedHintInjection - reason=injecting tool-based hint, actionId={}, confidence={}",
                action.getActionId(), match.getConfidence());

        // 构造明确的工具调用提示
        StringBuilder hint = new StringBuilder();
        hint.append("\n\n【系统指令 - 使用 plan_action 工具】\n");
        hint.append("检测到用户意图明确匹配预定义动作，请使用 plan_action 工具来处理：\n\n");
        hint.append("## 动作信息\n");
        hint.append("- **动作ID**: ").append(action.getActionId()).append("\n");
        hint.append("- **动作名称**: ").append(action.getActionName()).append("\n");
        hint.append("- **置信度**: ").append(String.format("%.2f", match.getConfidence())).append("\n");
        hint.append("- **描述**: ").append(action.getDescription()).append("\n");

        // 如果有提取的参数，提供给 LLM
        if (match.getExtractedParameters() != null && !match.getExtractedParameters().isEmpty()) {
            hint.append("\n## 已识别的参数\n");
            hint.append("```json\n");
            hint.append(JSON.toJSONString(match.getExtractedParameters(), true));
            hint.append("\n```\n");
        }

        // 提供动作的参数定义（帮助 LLM 理解需要哪些参数）
        if (action.getParameters() != null && !action.getParameters().isEmpty()) {
            hint.append("\n## 参数定义\n");
            for (ActionParameter param : action.getParameters()) {
                hint.append("- **").append(param.getName()).append("**");
                if (StringUtils.hasText(param.getLabel())) {
                    hint.append(" (").append(param.getLabel()).append(")");
                }
                hint.append(": ");
                if (StringUtils.hasText(param.getDescription())) {
                    hint.append(param.getDescription());
                }
                if (Boolean.TRUE.equals(param.getRequired())) {
                    hint.append(" **[必填]**");
                }
                if (StringUtils.hasText(param.getPlaceholder())) {
                    hint.append("\n  - 示例: ").append(param.getPlaceholder());
                }
                hint.append("\n");
            }
        }

        hint.append("\n## 执行要求\n");
        hint.append("请使用 **plan_action** 工具，传入以下参数：\n");
        hint.append("```python\n");
        hint.append("result = plan_action(\n");
        hint.append("    action_id=\"").append(action.getActionId()).append("\"\n");
        if (match.getExtractedParameters() != null && !match.getExtractedParameters().isEmpty()) {
            match.getExtractedParameters().forEach((key, value) -> {
                hint.append("    ").append(key).append("=");
                if (value instanceof String) {
                    hint.append("\"").append(value).append("\"");
                } else {
                    hint.append(value);
                }
                hint.append(",\n");
            });
        }
        hint.append("    # 如果有缺失的必填参数，plan_action 会自动引导用户补充\n");
        hint.append(")\n");
        hint.append("```\n");

        // 构造状态（使用HashMap避免Jackson序列化时的@class重复问题）
        Map<String, Object> intentState = new HashMap<>();
        intentState.put("hit", true);
        intentState.put("mode", "tool_based_hint");
        intentState.put("action_id", action.getActionId());
        intentState.put("action_name", action.getActionName());
        intentState.put("confidence", match.getConfidence());

        logger.info("UnifiedIntentRecognitionHook#handleToolBasedHintInjection - reason=tool-based hint injected, actionId={}, hintLength={}",
                action.getActionId(), hint.length());

        Map<String, Object> result = new HashMap<>();
        result.put("system_hint", hint.toString());
        result.put("jump_to", JumpTo.model);
        result.put("unified_intent", intentState);

        return CompletableFuture.completedFuture(result);
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

        // 构造状态（使用HashMap避免Jackson序列化时的@class重复问题）
        Map<String, Object> intentState = new HashMap<>();
        intentState.put("hit", true);
        intentState.put("mode", "hint_injection");
        intentState.put("action_id", action.getActionId());
        intentState.put("action_name", action.getActionName());
        intentState.put("confidence", match.getConfidence());
        intentState.put("hint", hint.toString());

        Map<String, Object> result = new HashMap<>();
        result.put("system_hint", hint.toString());
        result.put("jump_to", JumpTo.model);
        result.put("unified_intent", intentState);

        return CompletableFuture.completedFuture(result);
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
     * 从状态中提取会话ID
     *
     * <p>尝试从以下位置获取会话ID：
     * <ol>
     *     <li>state.session_id</li>
     *     <li>config.metadata.sessionId</li>
     *     <li>config.threadId</li>
     * </ol>
     */
    private String extractSessionId(OverAllState state, RunnableConfig config) {
        // 1. 从 state 获取
        if (state != null) {
            Optional<String> sessionId = state.value("session_id", String.class);
            if (sessionId.isPresent() && StringUtils.hasText(sessionId.get())) {
                return sessionId.get();
            }
        }

        // 2. 从 config.metadata 获取
        if (config != null && config.metadata().isPresent()) {
            Object sessionIdObj = config.metadata().get().get("sessionId");
            if (sessionIdObj instanceof String && StringUtils.hasText((String) sessionIdObj)) {
                return (String) sessionIdObj;
            }
        }

        // 3. 从 config.threadId 获取
        if (config != null && config.threadId().isPresent()) {
            return config.threadId().get();
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

    // ========== 会话存储辅助方法 ==========

    /**
     * 保存会话到分布式存储
     */
    private void saveSession(ParamCollectionSession session) {
        if (sessionStore == null || session == null) {
            return;
        }
        try {
            sessionStore.save(session);
            logger.debug("UnifiedIntentRecognitionHook#saveSession - reason=session saved, sessionId={}",
                    session.getSessionId());
        } catch (Exception e) {
            logger.error("UnifiedIntentRecognitionHook#saveSession - reason=failed to save session, sessionId={}",
                    session.getSessionId(), e);
        }
    }

    /**
     * 关闭会话
     */
    private void closeSession(ParamCollectionSession session) {
        if (sessionStore == null || session == null || session.getSessionId() == null) {
            return;
        }
        try {
            sessionStore.close(session.getSessionId());
            logger.debug("UnifiedIntentRecognitionHook#closeSession - reason=session closed, sessionId={}",
                    session.getSessionId());
        } catch (Exception e) {
            logger.error("UnifiedIntentRecognitionHook#closeSession - reason=failed to close session, sessionId={}",
                    session.getSessionId(), e);
        }
    }
}
