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
package com.alibaba.assistant.agent.planning.service;

import com.alibaba.assistant.agent.planning.context.TenantContext;
import com.alibaba.assistant.agent.planning.executor.ActionExecutorFactory;
import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionParameter;
import com.alibaba.assistant.agent.planning.model.ExecutionResult;
import com.alibaba.assistant.agent.planning.model.ParamCollectionSession;
import com.alibaba.assistant.agent.planning.param.*;
import com.alibaba.assistant.agent.planning.spi.SessionProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 参数收集服务
 *
 * <p>管理 action 参数收集的完整流程，包括：
 * <ul>
 * <li>创建和管理参数收集会话</li>
 * <li>使用 LLM 提取参数</li>
 * <li>验证参数完整性</li>
 * <li>生成追问提示</li>
 * <li>生成确认卡片</li>
 * <li>执行 action</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Service
public class ParamCollectionService {

    private static final Logger logger = LoggerFactory.getLogger(ParamCollectionService.class);

    private final StructuredParamExtractor paramExtractor;
    private final ParameterValidator parameterValidator;
    private final ActionExecutorFactory actionExecutorFactory;
    private final ObjectMapper objectMapper;
    private final SessionProvider sessionProvider;

    public ParamCollectionService(StructuredParamExtractor paramExtractor,
                                   ParameterValidator parameterValidator,
                                   ActionExecutorFactory actionExecutorFactory,
                                   ObjectMapper objectMapper,
                                   SessionProvider sessionProvider) {
        this.paramExtractor = paramExtractor;
        this.parameterValidator = parameterValidator;
        this.actionExecutorFactory = actionExecutorFactory;
        this.objectMapper = objectMapper;
        this.sessionProvider = sessionProvider;
        logger.info("ParamCollectionService#init - sessionProvider={}", sessionProvider.getProviderType());
    }

    /**
     * 创建新的参数收集会话
     *
     * @param action             动作定义
     * @param assistantSessionId Assistant Agent 会话 ID
     * @param userId             用户 ID
     * @return 会话对象
     */
    public ParamCollectionSession createSession(ActionDefinition action,
                                                  String assistantSessionId,
                                                  String userId) {
        String sessionId = UUID.randomUUID().toString();

        // 获取租户上下文
        Long tenantId = null;
        Long systemId = null;
        if (TenantContext.isPresent()) {
            tenantId = TenantContext.getTenantId();
            systemId = TenantContext.getSystemId();
        }

        ParamCollectionSession session = ParamCollectionSession.builder()
                .sessionId(sessionId)
                .actionId(action.getActionId())
                .assistantSessionId(assistantSessionId)
                .userId(userId)
                .tenantId(tenantId)
                .systemId(systemId)
                .state(ParamCollectionSession.CollectionState.INIT)
                .collectedParams(new HashMap<>())
                .missingParams(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .metadata(new HashMap<>())
                .build();

        // 使用 SessionProvider 保存会话
        sessionProvider.saveSession(session);

        logger.info("ParamCollectionService#createSession - sessionId={}, actionId={}, userId={}, tenantId={}",
                sessionId, action.getActionId(), userId, tenantId);

        return session;
    }

    /**
     * 获取会话
     */
    public ParamCollectionSession getSession(String sessionId) {
        return sessionProvider.getSession(sessionId);
    }

    /**
     * 根据 Assistant Session ID 获取活跃的参数收集会话
     */
    public ParamCollectionSession getActiveSessionByAssistantSessionId(String assistantSessionId) {
        return sessionProvider.getActiveSessionByAssistantSessionId(assistantSessionId);
    }

    /**
     * 处理用户输入并更新会话
     *
     * @param session    参数收集会话
     * @param action     动作定义
     * @param userInput  用户输入
     * @param chatHistory 对话历史（可选）
     * @return 处理结果
     */
    public ProcessResult processUserInput(ParamCollectionSession session,
                                          ActionDefinition action,
                                          String userInput,
                                          List<String> chatHistory) {
        logger.info("ParamCollectionService#processUserInput - sessionId={}, state={}, userInput={}",
                session.getSessionId(), session.getState(), userInput);

        // 检查会话状态
        if (!session.canCollect()) {
            return ProcessResult.builder()
                    .requiresInput(false)
                    .completed(false)
                    .message("当前会话无法继续收集参数")
                    .build();
        }

        // 更新状态为收集中
        session.updateToCollecting();

        // 1. 使用 LLM 提取参数
        StructuredParamExtractor.ExtractionResult extractionResult =
                paramExtractor.extract(action, userInput, chatHistory);

        if (!extractionResult.isSuccess()) {
            logger.warn("ParamCollectionService#processUserInput - extraction failed, error={}",
                    extractionResult.getErrorMessage());
            return ProcessResult.builder()
                    .requiresInput(true)
                    .completed(false)
                    .message("参数提取失败，请重新表述")
                    .build();
        }

        // 2. 合并已收集的参数
        Map<String, Object> allParams = new HashMap<>(session.getCollectedParams().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getValue()
                )));
        allParams.putAll(extractionResult.getParamValueMap());

        // 3. 验证参数
        ParameterValidator.ValidationResult validationResult =
                parameterValidator.validate(action, allParams);

        // 4. 更新会话参数
        updateSessionParams(session, extractionResult, validationResult);

        // 5. 保存会话到 SessionProvider
        sessionProvider.saveSession(session);

        // 6. 根据验证结果返回响应
        if (validationResult.isValid()) {
            // 所有参数已收集完成，生成确认卡片
            session.updateToPendingConfirm();
            sessionProvider.saveSession(session);
            ProcessResult result = generateConfirmation(session, action);
            return result.toBuilder()
                    .completed(true)
                    .requiresInput(false)
                    .requiresConfirmation(true)
                    .build();
        } else {
            // 仍有缺失参数，生成追问
            ProcessResult result = generateFollowUp(session, validationResult);
            sessionProvider.saveSession(session);
            return result.toBuilder()
                    .completed(false)
                    .requiresInput(true)
                    .requiresConfirmation(false)
                    .build();
        }
    }

    /**
     * 更新会话参数
     */
    private void updateSessionParams(ParamCollectionSession session,
                                      StructuredParamExtractor.ExtractionResult extractionResult,
                                      ParameterValidator.ValidationResult validationResult) {
        // 添加新提取的参数
        extractionResult.getExtractedParams().forEach((name, paramValue) -> {
            session.setParamValue(
                    name,
                    paramValue.getValue(),
                    paramValue.getType(),
                    paramValue.getConfidence(),
                    paramValue.getSource()
            );
        });

        // 添加默认值
        validationResult.getDefaultValues().forEach((name, value) -> {
            if (!session.getCollectedParams().containsKey(name)) {
                session.setParamValue(name, value, "DEFAULT", 1.0, "DEFAULT");
            }
        });

        // 更新缺失参数列表
        session.setMissingParams(validationResult.getMissingParams());
    }

    /**
     * 生成追问
     */
    private ProcessResult generateFollowUp(ParamCollectionSession session,
                                            ParameterValidator.ValidationResult validationResult) {
        List<ParamCollectionSession.MissingParamInfo> missingParams = validationResult.getMissingParams();

        if (missingParams.isEmpty()) {
            return ProcessResult.builder()
                    .message("参数已完整，请确认")
                    .build();
        }

        // 如果只有一个缺失参数，直接询问
        if (missingParams.size() == 1) {
            ParamCollectionSession.MissingParamInfo param = missingParams.get(0);
            return ProcessResult.builder()
                    .message(generateSingleParamFollowUp(param))
                    .missingParams(missingParams)
                    .build();
        }

        // 如果有多个缺失参数，生成综合追问
        return ProcessResult.builder()
                .message(generateMultiParamFollowUp(missingParams))
                .missingParams(missingParams)
                .build();
    }

    /**
     * 生成单个参数的追问
     */
    private String generateSingleParamFollowUp(ParamCollectionSession.MissingParamInfo param) {
        StringBuilder sb = new StringBuilder();
        sb.append("请输入").append(param.getLabel()).append(" ");

        if (StringUtils.hasText(param.getPromptHint())) {
            sb.append("（").append(param.getPromptHint()).append("）");
        } else if (StringUtils.hasText(param.getDescription())) {
            sb.append("（").append(param.getDescription()).append("）");
        }

        // 如果是枚举类型，显示选项
        if ("enum".equals(param.getType()) && !param.getEnumOptions().isEmpty()) {
            sb.append("\n\n可选值：");
            for (int i = 0; i < param.getEnumOptions().size(); i++) {
                sb.append("\n").append(i + 1).append(". ").append(param.getEnumOptions().get(i));
            }
        }

        return sb.toString();
    }

    /**
     * 生成多个参数的追问
     */
    private String generateMultiParamFollowUp(List<ParamCollectionSession.MissingParamInfo> missingParams) {
        StringBuilder sb = new StringBuilder();
        sb.append("还需要以下信息：\n\n");

        for (ParamCollectionSession.MissingParamInfo param : missingParams) {
            sb.append("- ").append(param.getLabel());
            if (StringUtils.hasText(param.getDescription())) {
                sb.append("（").append(param.getDescription()).append("）");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 生成确认卡片
     */
    private ProcessResult generateConfirmation(ParamCollectionSession session, ActionDefinition action) {
        List<ConfirmationParam> confirmParams = new ArrayList<>();

        for (ActionParameter param : action.getParameters()) {
            if (Boolean.TRUE.equals(param.getRequired()) || param.getDefaultValue() != null) {
                Object value = session.getParamValue(param.getName());
                if (value != null) {
                    confirmParams.add(ConfirmationParam.builder()
                            .name(param.getName())
                            .label(param.getLabel() != null ? param.getLabel() : param.getName())
                            .value(value)
                            .type(param.getType())
                            .required(param.getRequired())
                            .build());
                }
            }
        }

        return ProcessResult.builder()
                .message("请确认以下信息：")
                .confirmationParams(confirmParams)
                .build();
    }

    /**
     * 确认并执行 action
     *
     * @param session 参数收集会话
     * @param action  动作定义
     * @return 执行结果
     */
    public ProcessResult confirmAndExecute(ParamCollectionSession session, ActionDefinition action) {
        logger.info("ParamCollectionService#confirmAndExecute - sessionId={}, actionId={}",
                session.getSessionId(), action.getActionId());

        // 检查会话状态
        if (session.getState() != ParamCollectionSession.CollectionState.PENDING_CONFIRM) {
            return ProcessResult.builder()
                    .success(false)
                    .message("当前状态无法执行")
                    .build();
        }

        // 确认会话
        session.confirm();

        // 标记为执行中
        session.setState(ParamCollectionSession.CollectionState.EXECUTING);
        sessionProvider.saveSession(session);

        try {
            // 收集参数值
            Map<String, Object> params = new HashMap<>();
            session.getCollectedParams().forEach((name, param) -> {
                params.put(name, param.getValue());
            });

            // 执行 action
            long startTime = System.currentTimeMillis();
            ExecutionResult executionResult =
                    actionExecutorFactory.execute(action, params, action.getTimeoutMinutes());
            long duration = System.currentTimeMillis() - startTime;

            if (executionResult.isSuccess()) {
                // 执行成功
                session.markCompleted();
                sessionProvider.saveSession(session);
                return ProcessResult.builder()
                        .success(true)
                        .message("操作成功完成")
                        .executionResult(executionResult)
                        .build();
            } else {
                // 执行失败
                session.markFailed(executionResult.getErrorMessage());
                sessionProvider.saveSession(session);
                return ProcessResult.builder()
                        .success(false)
                        .message("操作失败：" + executionResult.getErrorMessage())
                        .executionResult(executionResult)
                        .build();
            }

        } catch (Exception e) {
            logger.error("ParamCollectionService#confirmAndExecute - execution failed", e);
            session.markFailed(e.getMessage());
            sessionProvider.saveSession(session);
            return ProcessResult.builder()
                    .success(false)
                    .message("执行失败：" + e.getMessage())
                    .build();
        }
    }

    /**
     * 取消会话
     */
    public void cancelSession(String sessionId) {
        ParamCollectionSession session = sessionProvider.getSession(sessionId);
        if (session != null) {
            session.markCancelled();
            sessionProvider.saveSession(session);
            logger.info("ParamCollectionService#cancelSession - sessionId={}", sessionId);
        }
    }

    /**
     * 清理过期会话
     */
    public void cleanupExpiredSessions() {
        int cleaned = sessionProvider.cleanupExpiredSessions();
        if (cleaned > 0) {
            logger.info("ParamCollectionService#cleanupExpiredSessions - cleaned up {} sessions", cleaned);
        }
    }

    /**
     * 处理结果
     */
    @lombok.Data
    @lombok.Builder(toBuilder = true)
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProcessResult {
        /**
         * 是否成功
         */
        @lombok.Builder.Default
        private boolean success = true;

        /**
         * 是否完成（所有参数已收集）
         */
        @lombok.Builder.Default
        private boolean completed = false;

        /**
         * 是否需要用户输入
         */
        @lombok.Builder.Default
        private boolean requiresInput = false;

        /**
         * 是否需要确认
         */
        @lombok.Builder.Default
        private boolean requiresConfirmation = false;

        /**
         * 返回给用户的消息
         */
        private String message;

        /**
         * 缺失的参数列表
         */
        @lombok.Builder.Default
        private List<ParamCollectionSession.MissingParamInfo> missingParams = new ArrayList<>();

        /**
         * 确认参数列表
         */
        @lombok.Builder.Default
        private List<ConfirmationParam> confirmationParams = new ArrayList<>();

        /**
         * 执行结果
         */
        private ExecutionResult executionResult;

        /**
         * 会话元数据（用于返回给前端）
         */
        @lombok.Builder.Default
        private Map<String, Object> metadata = new HashMap<>();
    }

    /**
     * 确认参数
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConfirmationParam {
        /**
         * 参数名
         */
        private String name;

        /**
         * 参数标签
         */
        private String label;

        /**
         * 参数值
         */
        private Object value;

        /**
         * 参数类型
         */
        private String type;

        /**
         * 是否必填
         */
        private boolean required;
    }
}
