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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 内存实现的参数收集会话存储
 *
 * <p>适用于单机开发/测试环境，不支持分布式。
 *
 * <p>特性：
 * <ul>
 *     <li>使用 ConcurrentHashMap 存储</li>
 *     <li>自动清理过期会话</li>
 *     <li>服务重启后数据丢失</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class InMemoryParamCollectionSessionStore implements ParamCollectionSessionStore {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryParamCollectionSessionStore.class);

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> userIndex = new ConcurrentHashMap<>();
    private final Duration defaultTtl;
    private final ScheduledExecutorService cleanupExecutor;

    public InMemoryParamCollectionSessionStore() {
        this(DEFAULT_TTL);
    }

    public InMemoryParamCollectionSessionStore(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;

        // 启动定期清理任务
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "param-collection-session-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredSessions, 1, 1, TimeUnit.MINUTES);

        logger.info("InMemoryParamCollectionSessionStore#<init> - reason=initialized, defaultTtl={}", defaultTtl);
        logger.warn("InMemoryParamCollectionSessionStore#<init> - reason=WARNING: Using in-memory session store, not suitable for distributed environment!");
    }

    @Override
    public void save(ParamCollectionSession session) {
        save(session, defaultTtl);
    }

    @Override
    public void save(ParamCollectionSession session, Duration ttl) {
        if (session == null || session.getSessionId() == null) {
            logger.warn("InMemoryParamCollectionSessionStore#save - reason=invalid session");
            return;
        }

        session.setUpdatedAt(Instant.now());
        Instant expireAt = Instant.now().plus(ttl);
        session.setExpireAt(expireAt);

        sessions.put(session.getSessionId(), new SessionEntry(session, expireAt));

        // 维护用户索引
        if (session.getUserId() != null && session.isActive()) {
            userIndex.put(session.getUserId(), session.getSessionId());
        }

        logger.debug("InMemoryParamCollectionSessionStore#save - reason=session saved, sessionId={}, ttl={}",
                session.getSessionId(), ttl);
    }

    @Override
    public Optional<ParamCollectionSession> get(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }

        SessionEntry entry = sessions.get(sessionId);
        if (entry == null) {
            logger.debug("InMemoryParamCollectionSessionStore#get - reason=session not found, sessionId={}", sessionId);
            return Optional.empty();
        }

        // 检查是否过期
        if (entry.isExpired()) {
            logger.debug("InMemoryParamCollectionSessionStore#get - reason=session expired, sessionId={}", sessionId);
            delete(sessionId);
            return Optional.empty();
        }

        logger.debug("InMemoryParamCollectionSessionStore#get - reason=session found, sessionId={}, active={}",
                sessionId, entry.session.isActive());
        return Optional.of(entry.session);
    }

    @Override
    public Optional<ParamCollectionSession> getActiveByUserId(String userId) {
        if (userId == null) {
            return Optional.empty();
        }

        String sessionId = userIndex.get(userId);
        if (sessionId == null) {
            logger.debug("InMemoryParamCollectionSessionStore#getActiveByUserId - reason=no active session for user, userId={}", userId);
            return Optional.empty();
        }

        Optional<ParamCollectionSession> sessionOpt = get(sessionId);

        // 验证会话是否活跃
        if (sessionOpt.isPresent() && sessionOpt.get().isActive()) {
            return sessionOpt;
        }

        // 清理用户索引
        userIndex.remove(userId);
        return Optional.empty();
    }

    @Override
    public void delete(String sessionId) {
        if (sessionId == null) {
            return;
        }

        SessionEntry entry = sessions.remove(sessionId);
        if (entry != null && entry.session.getUserId() != null) {
            userIndex.remove(entry.session.getUserId());
        }

        logger.debug("InMemoryParamCollectionSessionStore#delete - reason=session deleted, sessionId={}", sessionId);
    }

    @Override
    public boolean exists(String sessionId) {
        if (sessionId == null) {
            return false;
        }

        SessionEntry entry = sessions.get(sessionId);
        if (entry == null) {
            return false;
        }

        // 检查是否过期
        if (entry.isExpired()) {
            delete(sessionId);
            return false;
        }

        return true;
    }

    @Override
    public boolean refresh(String sessionId, Duration ttl) {
        if (sessionId == null) {
            return false;
        }

        SessionEntry entry = sessions.get(sessionId);
        if (entry == null || entry.isExpired()) {
            return false;
        }

        Instant newExpireAt = Instant.now().plus(ttl);
        entry.expireAt = newExpireAt;
        entry.session.setExpireAt(newExpireAt);
        entry.session.setUpdatedAt(Instant.now());

        logger.debug("InMemoryParamCollectionSessionStore#refresh - reason=session refreshed, sessionId={}, ttl={}",
                sessionId, ttl);
        return true;
    }

    @Override
    public void close(String sessionId) {
        if (sessionId == null) {
            return;
        }

        SessionEntry entry = sessions.get(sessionId);
        if (entry != null) {
            entry.session.close();
            entry.expireAt = Instant.now().plus(Duration.ofMinutes(5)); // 关闭后保留5分钟

            // 清理用户索引
            if (entry.session.getUserId() != null) {
                userIndex.remove(entry.session.getUserId());
            }

            logger.debug("InMemoryParamCollectionSessionStore#close - reason=session closed, sessionId={}", sessionId);
        }
    }

    @Override
    public String getStoreType() {
        return "memory";
    }

    /**
     * 清理过期会话
     */
    private void cleanupExpiredSessions() {
        int cleanedCount = 0;

        for (Map.Entry<String, SessionEntry> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                delete(entry.getKey());
                cleanedCount++;
            }
        }

        if (cleanedCount > 0) {
            logger.info("InMemoryParamCollectionSessionStore#cleanupExpiredSessions - reason=cleaned expired sessions, count={}",
                    cleanedCount);
        }
    }

    /**
     * 关闭清理线程
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("InMemoryParamCollectionSessionStore#shutdown - reason=shutdown completed");
    }

    /**
     * 获取当前会话数量（用于监控）
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * 会话条目（包含过期时间）
     */
    private static class SessionEntry {
        final ParamCollectionSession session;
        Instant expireAt;

        SessionEntry(ParamCollectionSession session, Instant expireAt) {
            this.session = session;
            this.expireAt = expireAt;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expireAt);
        }
    }
}
