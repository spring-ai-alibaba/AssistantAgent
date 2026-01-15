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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存会话快照存储实现
 * 用于开发测试，生产环境应使用数据库实现
 *
 * @author canfeng
 * @since 1.0.0
 */
public class InMemorySessionSnapshotRepository implements SessionSnapshotRepository {

	private static final Logger log = LoggerFactory.getLogger(InMemorySessionSnapshotRepository.class);

	private final Map<String, SessionSnapshot> storage = new ConcurrentHashMap<>();

	@Override
	public void save(SessionSnapshot snapshot) {
		if (snapshot == null || snapshot.getSnapshotId() == null) {
			throw new IllegalArgumentException("Snapshot and snapshotId cannot be null");
		}
		storage.put(snapshot.getSnapshotId(), snapshot);
		log.debug("InMemorySessionSnapshotRepository save 保存快照成功, snapshotId={}", snapshot.getSnapshotId());
	}

	@Override
	public Optional<SessionSnapshot> findById(String snapshotId) {
		SessionSnapshot snapshot = storage.get(snapshotId);
		log.debug("InMemorySessionSnapshotRepository findById 查找快照, snapshotId={}, found={}",
				snapshotId, snapshot != null);
		return Optional.ofNullable(snapshot);
	}

	@Override
	public List<SessionSnapshot> findBySessionId(String sessionId) {
		List<SessionSnapshot> snapshots = storage.values().stream()
				.filter(s -> sessionId.equals(s.getSessionId()))
				.collect(Collectors.toList());
		log.debug("InMemorySessionSnapshotRepository findBySessionId 查找会话快照, sessionId={}, count={}",
				sessionId, snapshots.size());
		return snapshots;
	}

	@Override
	public void deleteById(String snapshotId) {
		SessionSnapshot removed = storage.remove(snapshotId);
		log.debug("InMemorySessionSnapshotRepository deleteById 删除快照, snapshotId={}, success={}",
				snapshotId, removed != null);
	}

	@Override
	public int deleteExpired(Instant expireTime) {
		List<String> expiredIds = storage.values().stream()
				.filter(s -> s.getExpireAt() != null && s.getExpireAt().isBefore(expireTime))
				.map(SessionSnapshot::getSnapshotId)
				.collect(Collectors.toList());

		for (String id : expiredIds) {
			storage.remove(id);
		}

		log.debug("InMemorySessionSnapshotRepository deleteExpired 删除过期快照, expireTime={}, count={}",
				expireTime, expiredIds.size());
		return expiredIds.size();
	}

	/**
	 * 获取当前存储的快照数量（用于测试）
	 */
	public int size() {
		return storage.size();
	}

	/**
	 * 清空所有快照（用于测试）
	 */
	public void clear() {
		storage.clear();
		log.debug("InMemorySessionSnapshotRepository clear 清空所有快照");
	}

}

