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

package com.alibaba.assistant.agent.start.persistence.trigger.entity;

import com.alibaba.assistant.agent.extension.trigger.model.ScheduleMode;
import com.alibaba.assistant.agent.extension.trigger.model.SourceType;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.Instant;
import java.util.Map;

/**
 * 触发器定义数据库实体
 * 对应 trigger_definitions 表
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@TableName(value = "trigger_definitions", autoResultMap = true)
public class TriggerDefinitionEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("trigger_id")
    private String triggerId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("source_type")
    private SourceType sourceType;

    @TableField("source_id")
    private String sourceId;

    @TableField("created_by")
    private String createdBy;

    @TableField("event_protocol")
    private String eventProtocol;

    @TableField("event_key")
    private String eventKey;

    @TableField("schedule_mode")
    private ScheduleMode scheduleMode;

    @TableField("schedule_value")
    private String scheduleValue;

    @TableField("condition_function")
    private String conditionFunction;

    @TableField("execute_function")
    private String executeFunction;

    @TableField(value = "parameters", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> parameters;

    @TableField("session_snapshot_id")
    private String sessionSnapshotId;

    @TableField("graph_name")
    private String graphName;

    @TableField("agent_name")
    private String agentName;

    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    @TableField("status")
    private TriggerStatus status;

    @TableField("expire_at")
    private Instant expireAt;

    @TableField("max_retries")
    private Integer maxRetries;

    @TableField("retry_delay")
    private Long retryDelay;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getEventProtocol() {
        return eventProtocol;
    }

    public void setEventProtocol(String eventProtocol) {
        this.eventProtocol = eventProtocol;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public ScheduleMode getScheduleMode() {
        return scheduleMode;
    }

    public void setScheduleMode(ScheduleMode scheduleMode) {
        this.scheduleMode = scheduleMode;
    }

    public String getScheduleValue() {
        return scheduleValue;
    }

    public void setScheduleValue(String scheduleValue) {
        this.scheduleValue = scheduleValue;
    }

    public String getConditionFunction() {
        return conditionFunction;
    }

    public void setConditionFunction(String conditionFunction) {
        this.conditionFunction = conditionFunction;
    }

    public String getExecuteFunction() {
        return executeFunction;
    }

    public void setExecuteFunction(String executeFunction) {
        this.executeFunction = executeFunction;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public String getSessionSnapshotId() {
        return sessionSnapshotId;
    }

    public void setSessionSnapshotId(String sessionSnapshotId) {
        this.sessionSnapshotId = sessionSnapshotId;
    }

    public String getGraphName() {
        return graphName;
    }

    public void setGraphName(String graphName) {
        this.graphName = graphName;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public TriggerStatus getStatus() {
        return status;
    }

    public void setStatus(TriggerStatus status) {
        this.status = status;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Instant expireAt) {
        this.expireAt = expireAt;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Long getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Long retryDelay) {
        this.retryDelay = retryDelay;
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
}
