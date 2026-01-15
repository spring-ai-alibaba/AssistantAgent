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

package com.alibaba.assistant.agent.extension.trigger.repository;

import com.alibaba.assistant.agent.extension.trigger.model.SessionSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 会话快照存储接口（SPI）
 * 应用层需要实现此接口提供持久化能力
 *
 * @author canfeng
 * @since 1.0.0
 */
public interface SessionSnapshotRepository {

	/**
	 * 保存会话快照
	 *
	 * @param snapshot 会话快照
	 */
	void save(SessionSnapshot snapshot);

	/**
	 * 根据ID查找会话快照
	 *
	 * @param snapshotId 快照ID
	 * @return 会话快照（可选）
	 */
	Optional<SessionSnapshot> findById(String snapshotId);

	/**
	 * 根据会话ID查找快照列表
	 *
	 * @param sessionId 会话ID
	 * @return 快照列表
	 */
	List<SessionSnapshot> findBySessionId(String sessionId);

	/**
	 * 删除会话快照
	 *
	 * @param snapshotId 快照ID
	 */
	void deleteById(String snapshotId);

	/**
	 * 删除过期的快照
	 *
	 * @param expireTime 过期时间点
	 * @return 删除的快照数量
	 */
	int deleteExpired(Instant expireTime);

	/**
	 * 检查快照是否存在
	 *
	 * @param snapshotId 快照ID
	 * @return 是否存在
	 */
	default boolean exists(String snapshotId) {
		return findById(snapshotId).isPresent();
	}

}

