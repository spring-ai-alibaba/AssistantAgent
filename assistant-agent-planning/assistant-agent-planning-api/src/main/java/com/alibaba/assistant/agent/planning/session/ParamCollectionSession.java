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
package com.alibaba.assistant.agent.planning.session;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数收集会话
 *
 * <p>用于在分布式环境下存储多轮参数收集的会话状态。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ParamCollectionSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID（通常与用户会话ID关联）
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 是否活跃
     */
    private boolean active;

    /**
     * 是否等待用户输入
     */
    private boolean awaitingInput;

    /**
     * 动作ID
     */
    private String actionId;

    /**
     * 动作名称
     */
    private String actionName;

    /**
     * 下一个问题
     */
    private String nextQuestion;

    /**
     * 当前等待的参数名
     */
    private String awaitingParam;

    /**
     * 缺失的参数列表
     */
    private List<String> missingParams;

    /**
     * 已收集的参数
     */
    private Map<String, Object> collectedParams;

    /**
     * 匹配置信度
     */
    private Double confidence;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;

    /**
     * 过期时间
     */
    private Instant expireAt;

    public ParamCollectionSession() {
        this.collectedParams = new HashMap<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public ParamCollectionSession(String sessionId) {
        this();
        this.sessionId = sessionId;
    }

    /**
     * 从 Map 创建会话对象
     */
    @SuppressWarnings("unchecked")
    public static ParamCollectionSession fromMap(String sessionId, Map<String, Object> map) {
        ParamCollectionSession session = new ParamCollectionSession(sessionId);

        if (map == null) {
            return session;
        }

        session.setActive(Boolean.TRUE.equals(map.get("active")));
        session.setAwaitingInput(Boolean.TRUE.equals(map.get("awaitingInput")));
        session.setActionId((String) map.get("actionId"));
        session.setActionName((String) map.get("actionName"));
        session.setNextQuestion((String) map.get("nextQuestion"));
        session.setAwaitingParam((String) map.get("awaitingParam"));

        if (map.get("missingParams") instanceof List) {
            session.setMissingParams((List<String>) map.get("missingParams"));
        }

        if (map.get("collectedParams") instanceof Map) {
            session.setCollectedParams(new HashMap<>((Map<String, Object>) map.get("collectedParams")));
        }

        if (map.get("confidence") instanceof Number) {
            session.setConfidence(((Number) map.get("confidence")).doubleValue());
        }

        return session;
    }

    /**
     * 转换为 Map（用于存储到状态中）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("active", active);
        map.put("awaitingInput", awaitingInput);
        map.put("actionId", actionId);
        map.put("actionName", actionName);
        map.put("nextQuestion", nextQuestion);
        map.put("awaitingParam", awaitingParam);
        map.put("missingParams", missingParams);
        map.put("collectedParams", collectedParams);
        map.put("confidence", confidence);
        return map;
    }

    /**
     * 标记会话为活跃状态
     */
    public void activate(String actionId, String actionName, Double confidence) {
        this.active = true;
        this.actionId = actionId;
        this.actionName = actionName;
        this.confidence = confidence;
        this.updatedAt = Instant.now();
    }

    /**
     * 设置下一个问题
     */
    public void setNextQuestionAndAwait(String question, List<String> missingParams) {
        this.nextQuestion = question;
        this.missingParams = missingParams;
        this.awaitingInput = true;
        this.updatedAt = Instant.now();
    }

    /**
     * 添加已收集的参数
     */
    public void addCollectedParam(String name, Object value) {
        if (this.collectedParams == null) {
            this.collectedParams = new HashMap<>();
        }
        this.collectedParams.put(name, value);
        this.updatedAt = Instant.now();
    }

    /**
     * 合并已收集的参数
     */
    public void mergeCollectedParams(Map<String, Object> params) {
        if (params == null) {
            return;
        }
        if (this.collectedParams == null) {
            this.collectedParams = new HashMap<>();
        }
        this.collectedParams.putAll(params);
        this.updatedAt = Instant.now();
    }

    /**
     * 关闭会话
     */
    public void close() {
        this.active = false;
        this.awaitingInput = false;
        this.updatedAt = Instant.now();
    }

    /**
     * 检查会话是否过期
     */
    public boolean isExpired() {
        if (expireAt == null) {
            return false;
        }
        return Instant.now().isAfter(expireAt);
    }

    // ========== Getters and Setters ==========

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAwaitingInput() {
        return awaitingInput;
    }

    public void setAwaitingInput(boolean awaitingInput) {
        this.awaitingInput = awaitingInput;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public String getNextQuestion() {
        return nextQuestion;
    }

    public void setNextQuestion(String nextQuestion) {
        this.nextQuestion = nextQuestion;
    }

    public String getAwaitingParam() {
        return awaitingParam;
    }

    public void setAwaitingParam(String awaitingParam) {
        this.awaitingParam = awaitingParam;
    }

    public List<String> getMissingParams() {
        return missingParams;
    }

    public void setMissingParams(List<String> missingParams) {
        this.missingParams = missingParams;
    }

    public Map<String, Object> getCollectedParams() {
        return collectedParams;
    }

    public void setCollectedParams(Map<String, Object> collectedParams) {
        this.collectedParams = collectedParams;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Instant expireAt) {
        this.expireAt = expireAt;
    }

    @Override
    public String toString() {
        return "ParamCollectionSession{" +
                "sessionId='" + sessionId + '\'' +
                ", active=" + active +
                ", awaitingInput=" + awaitingInput +
                ", actionId='" + actionId + '\'' +
                ", actionName='" + actionName + '\'' +
                ", missingParams=" + missingParams +
                ", collectedParams=" + collectedParams +
                '}';
    }
}
