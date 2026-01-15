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

package com.alibaba.assistant.agent.extension.trigger.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 触发器定义
 * 表示一个触发器实例的完整元数据定义
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class TriggerDefinition {

	/**
	 * 触发器唯一标识
	 */
	private String triggerId;

	/**
	 * 触发器名称
	 */
	private String name;

	/**
	 * 触发器描述
	 */
	private String description;

	/**
	 * 来源类型
	 */
	private SourceType sourceType;

	/**
	 * 来源标识（如用户ID、群组ID等）
	 */
	private String sourceId;

	/**
	 * 创建者标识
	 */
	private String createdBy;

	/**
	 * 创建时间
	 */
	private Instant createdAt;

	/**
	 * 更新时间
	 */
	private Instant updatedAt;

	/**
	 * 事件协议（time / callback / mq / http_poll等）
	 */
	private String eventProtocol;

	/**
	 * 事件标识
	 */
	private String eventKey;

	/**
	 * 调度模式
	 */
	private ScheduleMode scheduleMode;

	/**
	 * 调度值（如Cron表达式、延迟时间等）
	 */
	private String scheduleValue;

	/**
	 * 条件函数名（可选，为空表示无额外条件）
	 */
	private String conditionFunction;

	/**
	 * 执行函数名
	 */
	private String executeFunction;

	/**
	 * 放弃条件函数名（可选，用于判断是否停止监听）
	 */
	private String abandonFunction;

	/**
	 * 函数代码快照（执行时恢复）
	 * key: 函数名, value: 函数代码
	 */
	private Map<String, String> functionCodeSnapshot;

	/**
	 * 是否需要用户确认才能激活
	 */
	private boolean requireConfirmation = true;

	/**
	 * 确认卡片类型（用于前端渲染）
	 */
	private String confirmCardType;

	/**
	 * 执行参数
	 */
	private Map<String, Object> parameters;

	/**
	 * 会话快照ID
	 */
	private String sessionSnapshotId;

	/**
	 * 绑定的图名称
	 */
	private String graphName;

	/**
	 * 绑定的Agent名称
	 */
	private String agentName;

	/**
	 * 扩展元数据
	 */
	private Map<String, Object> metadata;

	/**
	 * 触发器状态
	 */
	private TriggerStatus status;

	/**
	 * 过期时间（可选）
	 */
	private Instant expireAt;

	/**
	 * 最大重试次数
	 */
	private Integer maxRetries;

	/**
	 * 重试延迟（毫秒）
	 */
	private Long retryDelay;

	public TriggerDefinition() {
		this.parameters = new HashMap<>();
		this.metadata = new HashMap<>();
		this.functionCodeSnapshot = new HashMap<>();
		this.createdAt = Instant.now();
		this.updatedAt = Instant.now();
		this.status = TriggerStatus.PENDING_ACTIVATE;
		this.requireConfirmation = true;
	}

	// Getters and Setters

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

	public String getAbandonFunction() {
		return abandonFunction;
	}

	public void setAbandonFunction(String abandonFunction) {
		this.abandonFunction = abandonFunction;
	}

	public Map<String, String> getFunctionCodeSnapshot() {
		return functionCodeSnapshot;
	}

	public void setFunctionCodeSnapshot(Map<String, String> functionCodeSnapshot) {
		this.functionCodeSnapshot = functionCodeSnapshot;
	}

	public boolean isRequireConfirmation() {
		return requireConfirmation;
	}

	public void setRequireConfirmation(boolean requireConfirmation) {
		this.requireConfirmation = requireConfirmation;
	}

	public String getConfirmCardType() {
		return confirmCardType;
	}

	public void setConfirmCardType(String confirmCardType) {
		this.confirmCardType = confirmCardType;
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
		this.updatedAt = Instant.now();
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

	@Override
	public String toString() {
		return "TriggerDefinition{" + "triggerId='" + triggerId + '\'' + ", name='" + name + '\'' + ", sourceType="
				+ sourceType + ", sourceId='" + sourceId + '\'' + ", scheduleMode=" + scheduleMode + ", scheduleValue='"
				+ scheduleValue + '\'' + ", executeFunction='" + executeFunction + '\'' + ", status=" + status + '}';
	}

}

