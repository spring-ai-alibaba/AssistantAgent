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
package com.alibaba.assistant.agent.planning.integration;

import com.alibaba.assistant.agent.evaluation.evaluator.Evaluator;
import com.alibaba.assistant.agent.evaluation.model.CriterionExecutionContext;
import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.assistant.agent.evaluation.model.CriterionStatus;
import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionMatch;
import com.alibaba.assistant.agent.planning.model.ParamCollectionSession;
import com.alibaba.assistant.agent.planning.service.ParamCollectionService;
import com.alibaba.assistant.agent.planning.spi.ActionProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动作意图评估器
 *
 * <p>负责评估用户输入是否匹配预定义动作，并返回匹配结果。
 *
 * <p>集成参数收集流程：
 * <ul>
 * <li>匹配 action</li>
 * <li>检查是否需要参数收集</li>
 * <li>创建或恢复参数收集会话</li>
 * <li>处理用户输入（提取参数、验证、生成追问或确认）</li>
 * <li>返回参数收集状态</li>
 * </ul>
 *
 * <p>评估结果格式：
 * <pre>
 * NO_MATCH - 未匹配到动作
 * MATCHED|actionId|actionName|confidence|matchType - 匹配到动作
 * PARAM_COLLECTION|sessionId|state|message - 参数收集状态
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ActionIntentEvaluator implements Evaluator {

    private static final Logger logger = LoggerFactory.getLogger(ActionIntentEvaluator.class);

    private static final String EVALUATOR_ID = "action_intent_evaluator";

    private static final int DEFAULT_MAX_CANDIDATES = 5;

    private final ActionProvider actionProvider;

    private final ParamCollectionService paramCollectionService;

    private final ObjectMapper objectMapper;

    /**
     * 是否启用参数收集流程
     */
    private final boolean enableParamCollection;

    /**
     * 是否启用 LLM 二次验证（返回候选列表供 LLM 判断）
     */
    private final boolean enableLLMVerification;

    /**
     * 最大候选数量
     */
    private final int maxCandidates;

    public ActionIntentEvaluator(ActionProvider actionProvider) {
        this(actionProvider, null, false, false, DEFAULT_MAX_CANDIDATES);
    }

    public ActionIntentEvaluator(ActionProvider actionProvider,
                                  ParamCollectionService paramCollectionService,
                                  boolean enableParamCollection) {
        this(actionProvider, paramCollectionService, enableParamCollection, false, DEFAULT_MAX_CANDIDATES);
    }

    public ActionIntentEvaluator(ActionProvider actionProvider,
                                  ParamCollectionService paramCollectionService,
                                  boolean enableParamCollection,
                                  boolean enableLLMVerification,
                                  int maxCandidates) {
        this.actionProvider = actionProvider;
        this.paramCollectionService = paramCollectionService;
        this.enableParamCollection = enableParamCollection;
        this.enableLLMVerification = enableLLMVerification;
        this.maxCandidates = maxCandidates > 0 ? maxCandidates : DEFAULT_MAX_CANDIDATES;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getEvaluatorId() {
        return EVALUATOR_ID;
    }

    @Override
    public CriterionResult evaluate(CriterionExecutionContext executionContext) {
        CriterionResult result = new CriterionResult();
        result.setCriterionName(executionContext.getCriterion().getName());
        result.setStartTimeMillis(System.currentTimeMillis());

        try {
            // 获取用户输入和会话信息
            EvaluationContext inputContext = executionContext.getInputContext();
            String userInput = (String) inputContext.getInputValue("userInput");
            String sessionId = (String) inputContext.getInputValue("sessionId");
            String userId = (String) inputContext.getInputValue("userId");

            if (userInput == null || userInput.isBlank()) {
                result.setStatus(CriterionStatus.SUCCESS);
                result.setValue("NO_MATCH");
                return result;
            }

            // 构建匹配上下文
            Map<String, Object> context = buildMatchContext(executionContext);

            // 执行动作匹配
            List<ActionMatch> matches = actionProvider.matchActions(userInput, context);

            // 未匹配到动作
            if (CollectionUtils.isEmpty(matches)) {
                logger.debug("ActionIntentEvaluator#evaluate - reason=no action matched, userInput={}", userInput);
                result.setStatus(CriterionStatus.SUCCESS);
                result.setValue("NO_MATCH");
                return result;
            }

            // 如果启用了 LLM 二次验证，返回候选列表供 LLM 判断
            if (enableLLMVerification) {
                return buildCandidatesResult(matches, userInput, result);
            }

            // 匹配到最佳动作
            ActionMatch bestMatch = matches.get(0);
            ActionDefinition action = bestMatch.getAction();
            logger.info("ActionIntentEvaluator#evaluate - reason=action matched, actionId={}, confidence={}",
                    action.getActionId(), bestMatch.getConfidence());

            // 如果启用了参数收集且动作需要参数
            if (enableParamCollection && paramCollectionService != null && needsParamCollection(action)) {
                return handleParamCollection(action, userInput, sessionId, userId, result);
            }

            // 直接返回匹配结果（不进行参数收集）
            result.setStatus(CriterionStatus.SUCCESS);
            String matchResult = String.format("MATCHED|%s|%s|%.2f|%s",
                    action.getActionId() != null ? action.getActionId() : "",
                    action.getActionName() != null ? action.getActionName() : "",
                    bestMatch.getConfidence() != null ? bestMatch.getConfidence() : 0.0,
                    bestMatch.getMatchType() != null ? bestMatch.getMatchType().name() : "UNKNOWN");
            result.setValue(matchResult);
            return result;

        } catch (Exception e) {
            logger.error("ActionIntentEvaluator#evaluate - reason=evaluation failed", e);
            result.setStatus(CriterionStatus.ERROR);
            result.setErrorMessage(e.getMessage());
            result.setValue("NO_MATCH");
        } finally {
            result.setEndTimeMillis(System.currentTimeMillis());
        }

        return result;
    }

    /**
     * 构建候选列表结果（供 LLM 二次验证使用）
     *
     * <p>包含参数定义，让 LLM 在验证动作的同时提取参数
     */
    private CriterionResult buildCandidatesResult(List<ActionMatch> matches, String userInput, CriterionResult result) {
        try {
            // 限制候选数量
            List<ActionMatch> topMatches = matches.size() > maxCandidates
                    ? matches.subList(0, maxCandidates)
                    : matches;

            // 构建候选列表 JSON（包含参数定义）
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (ActionMatch match : topMatches) {
                ActionDefinition action = match.getAction();
                Map<String, Object> candidate = new HashMap<>();
                candidate.put("actionId", action.getActionId());
                candidate.put("actionName", action.getActionName());
                candidate.put("description", action.getDescription());
                candidate.put("confidence", match.getConfidence());
                candidate.put("matchType", match.getMatchType() != null ? match.getMatchType().name() : "UNKNOWN");

                // 添加触发关键词供 LLM 参考
                if (action.getTriggerKeywords() != null && !action.getTriggerKeywords().isEmpty()) {
                    candidate.put("triggerKeywords", action.getTriggerKeywords());
                }

                // 添加参数定义供 LLM 提取参数
                if (action.getParameters() != null && !action.getParameters().isEmpty()) {
                    List<Map<String, Object>> params = new ArrayList<>();
                    for (var param : action.getParameters()) {
                        Map<String, Object> paramInfo = new HashMap<>();
                        paramInfo.put("name", param.getName());
                        paramInfo.put("label", param.getLabel());  // 显示标签
                        paramInfo.put("description", param.getDescription());
                        paramInfo.put("type", param.getType());
                        paramInfo.put("required", param.getRequired());
                        if (param.getDefaultValue() != null) {
                            paramInfo.put("defaultValue", param.getDefaultValue());
                        }
                        if (param.getPlaceholder() != null) {
                            paramInfo.put("placeholder", param.getPlaceholder());
                        }
                        params.add(paramInfo);
                    }
                    candidate.put("parameters", params);
                }
                candidates.add(candidate);
            }

            // 构建结果 JSON
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("status", "CANDIDATES");
            resultData.put("userInput", userInput);
            resultData.put("candidateCount", candidates.size());
            resultData.put("candidates", candidates);

            String jsonResult = objectMapper.writeValueAsString(resultData);

            result.setStatus(CriterionStatus.SUCCESS);
            result.setValue(jsonResult);

            // 添加元数据供后续处理使用
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("candidateCount", candidates.size());
            metadata.put("topActionId", topMatches.get(0).getAction().getActionId());
            metadata.put("topConfidence", topMatches.get(0).getConfidence());
            metadata.put("needsLLMVerification", true);
            result.setMetadata(metadata);

            logger.info("ActionIntentEvaluator#buildCandidatesResult - reason=candidates built for LLM verification, " +
                    "userInput={}, candidateCount={}", userInput, candidates.size());

            return result;

        } catch (Exception e) {
            logger.error("ActionIntentEvaluator#buildCandidatesResult - reason=failed to build candidates", e);
            result.setStatus(CriterionStatus.ERROR);
            result.setErrorMessage("构建候选列表失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 处理参数收集流程
     */
    private CriterionResult handleParamCollection(ActionDefinition action,
                                                   String userInput,
                                                   String sessionId,
                                                   String userId,
                                                   CriterionResult result) {
        try {
            // 检查是否已有活跃的参数收集会话
            ParamCollectionSession existingSession = null;
            if (sessionId != null) {
                existingSession = paramCollectionService.getActiveSessionByAssistantSessionId(sessionId);
            }

            ParamCollectionSession session;

            if (existingSession != null) {
                // 继续现有会话
                session = existingSession;
                logger.info("ActionIntentEvaluator#handleParamCollection - reason=existing session, sessionId={}, state={}",
                        session.getSessionId(), session.getState());
            } else {
                // 创建新会话
                session = paramCollectionService.createSession(action, sessionId, userId);
                logger.info("ActionIntentEvaluator#handleParamCollection - reason=new session, sessionId={}",
                        session.getSessionId());
            }

            // 处理用户输入
            ParamCollectionService.ProcessResult processResult =
                    paramCollectionService.processUserInput(session, action, userInput, null);

            // 构建返回结果
            String collectionResult = buildCollectionResult(session, action, processResult);
            result.setValue(collectionResult);
            result.setStatus(CriterionStatus.SUCCESS);

            // 添加上下文元数据（用于前端）
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("paramCollectionSessionId", session.getSessionId());
            metadata.put("actionId", action.getActionId());
            metadata.put("actionName", action.getActionName());
            metadata.put("state", session.getState().name());
            metadata.put("requiresInput", processResult.isRequiresInput());
            metadata.put("requiresConfirmation", processResult.isRequiresConfirmation());
            metadata.put("completed", processResult.isCompleted());
            metadata.put("message", processResult.getMessage());
            result.setMetadata(metadata);

            logger.info("ActionIntentEvaluator#handleParamCollection - completed, sessionId={}, state={}, requiresInput={}",
                    session.getSessionId(), session.getState(), processResult.isRequiresInput());

            return result;

        } catch (Exception e) {
            logger.error("ActionIntentEvaluator#handleParamCollection - failed", e);
            result.setStatus(CriterionStatus.ERROR);
            result.setErrorMessage("参数收集失败：" + e.getMessage());
            result.setValue("ERROR");
            return result;
        }
    }

    /**
     * 构建参数收集结果字符串
     */
    private String buildCollectionResult(ParamCollectionSession session,
                                         ActionDefinition action,
                                         ParamCollectionService.ProcessResult processResult) {
        // 格式：PARAM_COLLECTION|sessionId|state|message
        return String.format("PARAM_COLLECTION|%s|%s|%s|%s|%b|%b",
                session.getSessionId(),
                action.getActionId(),
                session.getState().name(),
                escapeMessage(processResult.getMessage()),
                processResult.isRequiresInput(),
                processResult.isRequiresConfirmation());
    }

    /**
     * 转义消息中的特殊字符
     */
    private String escapeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.replace("|", "\\|").replace("\n", "\\n");
    }

    /**
     * 检查动作是否需要参数收集
     */
    private boolean needsParamCollection(ActionDefinition action) {
        // 如果有参数定义且至少有一个必填参数，则需要参数收集
        if (action.getParameters() == null || action.getParameters().isEmpty()) {
            return false;
        }
        return action.getParameters().stream()
                .anyMatch(p -> Boolean.TRUE.equals(p.getRequired()));
    }

    /**
     * 构建匹配上下文
     */
    private Map<String, Object> buildMatchContext(CriterionExecutionContext executionContext) {
        Map<String, Object> context = new HashMap<>();

        // 从评估上下文获取信息
        EvaluationContext evalContext = executionContext.getInputContext();
        if (evalContext.getInput() != null) {
            context.putAll(evalContext.getInput());
        }
        if (evalContext.getEnvironment() != null) {
            context.putAll(evalContext.getEnvironment());
        }

        return context;
    }
}
