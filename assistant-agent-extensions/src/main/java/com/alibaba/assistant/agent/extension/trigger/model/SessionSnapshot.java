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
 * 会话快照
 * 用于在触发器执行时恢复上下文，实现脱离Agent体系的独立执行
 *
 * @author canfeng
 * @since 1.0.0
 */
public class SessionSnapshot {

	/**
	 * 快照唯一标识
	 */
	private String snapshotId;

	/**
	 * 原会话ID
	 */
	private String sessionId;

	/**
	 * 用户ID
	 */
	private String userId;

	/**
	 * 租户ID
	 */
	private String tenantId;

	/**
	 * 渠道类型（如USER、DINGTALK_GROUP等）
	 */
	private String channelType;

	/**
	 * 渠道ID
	 */
	private String channelId;

	/**
	 * 函数代码快照
	 * key: 函数名, value: 函数代码
	 */
	private Map<String, String> functionCode;

	/**
	 * 上下文变量快照
	 * 用于恢复执行时的变量环境
	 */
	private Map<String, Object> contextVariables;

	/**
	 * 创建时间
	 */
	private Instant createdAt;

	/**
	 * 过期时间
	 */
	private Instant expireAt;

	/**
	 * 扩展元数据
	 */
	private Map<String, Object> metadata;

	public SessionSnapshot() {
		this.functionCode = new HashMap<>();
		this.contextVariables = new HashMap<>();
		this.metadata = new HashMap<>();
		this.createdAt = Instant.now();
	}

	// Static factory methods

	/**
	 * 创建会话快照
	 */
	public static SessionSnapshot create(String sessionId, String userId, String tenantId) {
		SessionSnapshot snapshot = new SessionSnapshot();
		snapshot.setSnapshotId(generateSnapshotId());
		snapshot.setSessionId(sessionId);
		snapshot.setUserId(userId);
		snapshot.setTenantId(tenantId);
		return snapshot;
	}

	private static String generateSnapshotId() {
		return "snapshot_" + java.util.UUID.randomUUID().toString().replace("-", "");
	}

	// Getters and Setters

	public String getSnapshotId() {
		return snapshotId;
	}

	public void setSnapshotId(String snapshotId) {
		this.snapshotId = snapshotId;
	}

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

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public String getChannelType() {
		return channelType;
	}

	public void setChannelType(String channelType) {
		this.channelType = channelType;
	}

	public String getChannelId() {
		return channelId;
	}

	public void setChannelId(String channelId) {
		this.channelId = channelId;
	}

	public Map<String, String> getFunctionCode() {
		return functionCode;
	}

	public void setFunctionCode(Map<String, String> functionCode) {
		this.functionCode = functionCode;
	}

	/**
	 * 添加函数代码
	 */
	public void addFunctionCode(String functionName, String code) {
		this.functionCode.put(functionName, code);
	}

	public Map<String, Object> getContextVariables() {
		return contextVariables;
	}

	public void setContextVariables(Map<String, Object> contextVariables) {
		this.contextVariables = contextVariables;
	}

	/**
	 * 添加上下文变量
	 */
	public void addContextVariable(String key, Object value) {
		this.contextVariables.put(key, value);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getExpireAt() {
		return expireAt;
	}

	public void setExpireAt(Instant expireAt) {
		this.expireAt = expireAt;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	/**
	 * 检查快照是否过期
	 */
	public boolean isExpired() {
		if (expireAt == null) {
			return false;
		}
		return Instant.now().isAfter(expireAt);
	}

	@Override
	public String toString() {
		return "SessionSnapshot{" +
				"snapshotId='" + snapshotId + '\'' +
				", sessionId='" + sessionId + '\'' +
				", userId='" + userId + '\'' +
				", tenantId='" + tenantId + '\'' +
				", channelType='" + channelType + '\'' +
				", functionCodeCount=" + (functionCode != null ? functionCode.size() : 0) +
				", createdAt=" + createdAt +
				", expireAt=" + expireAt +
				'}';
	}

}

