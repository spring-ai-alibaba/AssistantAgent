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
package com.alibaba.assistant.agent.planning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数收集会话
 *
 * <p>跟踪 action 参数收集的进度和状态。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamCollectionSession {

    /**
     * 会话唯一标识
     */
    private String sessionId;

    /**
     * 动作 ID
     */
    private String actionId;

    /**
     * Assistant Agent 会话 ID
     */
    private String assistantSessionId;

    /**
     * 用户 ID
     */
    private String userId;

    // ===== 多租户字段 =====

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 系统 ID
     */
    private Long systemId;

    // ===== 状态字段 =====

    /**
     * 会话状态
     */
    @Builder.Default
    private CollectionState state = CollectionState.INIT;

    /**
     * 已收集的参数（参数名 -> 参数值）
     */
    @Builder.Default
    private Map<String, CollectedParam> collectedParams = new HashMap<>();

    /**
     * 缺失的必填参数列表
     */
    @Builder.Default
    private List<MissingParamInfo> missingParams = new ArrayList<>();

    /**
     * 所有参数是否已确认
     */
    @Builder.Default
    private boolean userConfirmed = false;

    // ===== 时间字段 =====

    /**
     * 会话创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 会话更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 用户确认时间
     */
    private LocalDateTime confirmedAt;

    /**
     * 会话过期时间（1小时后过期）
     */
    private LocalDateTime expiresAt;

    // ===== 错误和元数据 =====

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;

    /**
     * 会话元数据（用于存储额外信息）
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 收集状态枚举
     */
    public enum CollectionState {
        /**
         * 初始化
         */
        INIT,
        /**
         * 收集中
         */
        COLLECTING,
        /**
         * 待确认
         */
        PENDING_CONFIRM,
        /**
         * 已确认
         */
        CONFIRMED,
        /**
         * 执行中
         */
        EXECUTING,
        /**
         * 已完成
         */
        COMPLETED,
        /**
         * 已取消
         */
        CANCELLED,
        /**
         * 已过期
         */
        EXPIRED,
        /**
         * 失败
         */
        FAILED
    }

    /**
     * 已收集参数信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectedParam {
        /**
         * 参数名
         */
        private String name;

        /**
         * 参数值
         */
        private Object value;

        /**
         * 参数类型
         */
        private String type;

        /**
         * 是否用户明确提供（vs 默认值）
         */
        @Builder.Default
        private boolean userProvided = true;

        /**
         * 提取置信度（0-1）
         */
        @Builder.Default
        private Double confidence = 1.0;

        /**
         * 提取来源（LLM, USER_INPUT, DEFAULT）
         */
        private String source;
    }

    /**
     * 缺失参数信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingParamInfo {
        /**
         * 参数名
         */
        private String name;

        /**
         * 参数标签（用于显示）
         */
        private String label;

        /**
         * 参数类型
         */
        private String type;

        /**
         * 是否必填
         */
        private boolean required;

        /**
         * 参数描述
         */
        private String description;

        /**
         * 提示信息
         */
        private String promptHint;

        /**
         * 枚举选项（如果是枚举类型）
         */
        private List<String> enumOptions;

        /**
         * 默认值（如果有）
         */
        private Object defaultValue;
    }

    /**
     * 检查会话是否过期
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 检查是否可以继续收集参数
     */
    public boolean canCollect() {
        return (state == CollectionState.INIT || state == CollectionState.COLLECTING)
                && !isExpired()
                && !state.equals(CollectionState.CANCELLED);
    }

    /**
     * 检查是否所有参数都已收集
     */
    public boolean isAllParamsCollected() {
        return missingParams == null || missingParams.isEmpty();
    }

    /**
     * 设置参数值
     */
    public void setParamValue(String name, Object value, String type, Double confidence, String source) {
        CollectedParam param = CollectedParam.builder()
                .name(name)
                .value(value)
                .type(type)
                .confidence(confidence != null ? confidence : 1.0)
                .source(source != null ? source : "USER_INPUT")
                .userProvided(true)
                .build();
        collectedParams.put(name, param);
        updatedAt = LocalDateTime.now();
    }

    /**
     * 获取参数值
     */
    public Object getParamValue(String name) {
        CollectedParam param = collectedParams.get(name);
        return param != null ? param.getValue() : null;
    }

    /**
     * 标记为已完成
     */
    public void markCompleted() {
        this.state = CollectionState.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记为已取消
     */
    public void markCancelled() {
        this.state = CollectionState.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记为失败
     */
    public void markFailed(String errorMessage) {
        this.state = CollectionState.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 确认参数
     */
    public void confirm() {
        this.state = CollectionState.CONFIRMED;
        this.userConfirmed = true;
        this.confirmedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新状态为收集中
     */
    public void updateToCollecting() {
        this.state = CollectionState.COLLECTING;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新状态为待确认
     */
    public void updateToPendingConfirm() {
        this.state = CollectionState.PENDING_CONFIRM;
        this.updatedAt = LocalDateTime.now();
    }
}
