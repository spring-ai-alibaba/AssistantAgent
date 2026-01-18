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

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 实现的参数收集会话存储
 *
 * <p>使用 Redis 存储参数收集会话，支持分布式环境。
 *
 * <p>Key 格式：
 * <ul>
 *     <li>会话：param_collection:session:{sessionId}</li>
 *     <li>用户索引：param_collection:user:{userId}</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class RedisParamCollectionSessionStore implements ParamCollectionSessionStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisParamCollectionSessionStore.class);

    private static final String KEY_PREFIX = "param_collection:session:";
    private static final String USER_INDEX_PREFIX = "param_collection:user:";

    private final StringRedisTemplate redisTemplate;
    private final Duration defaultTtl;

    public RedisParamCollectionSessionStore(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_TTL);
    }

    public RedisParamCollectionSessionStore(StringRedisTemplate redisTemplate, Duration defaultTtl) {
        this.redisTemplate = redisTemplate;
        this.defaultTtl = defaultTtl;
        logger.info("RedisParamCollectionSessionStore#<init> - reason=initialized, defaultTtl={}",
                defaultTtl);
    }

    @Override
    public void save(ParamCollectionSession session) {
        save(session, defaultTtl);
    }

    @Override
    public void save(ParamCollectionSession session, Duration ttl) {
        if (session == null || session.getSessionId() == null) {
            logger.warn("RedisParamCollectionSessionStore#save - reason=invalid session, session is null or sessionId is null");
            return;
        }

        try {
            String sessionKey = KEY_PREFIX + session.getSessionId();
            session.setUpdatedAt(Instant.now());
            session.setExpireAt(Instant.now().plus(ttl));

            String json = JSON.toJSONString(session);
            redisTemplate.opsForValue().set(sessionKey, json, ttl.toMillis(), TimeUnit.MILLISECONDS);

            // 如果有 userId，维护用户索引
            if (session.getUserId() != null && session.isActive()) {
                String userKey = USER_INDEX_PREFIX + session.getUserId();
                redisTemplate.opsForValue().set(userKey, session.getSessionId(), ttl.toMillis(), TimeUnit.MILLISECONDS);
            }

            logger.debug("RedisParamCollectionSessionStore#save - reason=session saved, sessionId={}, ttl={}",
                    session.getSessionId(), ttl);

        } catch (Exception e) {
            logger.error("RedisParamCollectionSessionStore#save - reason=failed to save session, sessionId={}",
                    session.getSessionId(), e);
            throw new RuntimeException("Failed to save param collection session", e);
        }
    }

    @Override
    public Optional<ParamCollectionSession> get(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }

        try {
            String sessionKey = KEY_PREFIX + sessionId;
            String json = redisTemplate.opsForValue().get(sessionKey);

            if (json == null) {
                logger.debug("RedisParamCollectionSessionStore#get - reason=session not found, sessionId={}", sessionId);
                return Optional.empty();
            }

            ParamCollectionSession session = JSON.parseObject(json, ParamCollectionSession.class);

            // 检查是否过期
            if (session.isExpired()) {
                logger.debug("RedisParamCollectionSessionStore#get - reason=session expired, sessionId={}", sessionId);
                delete(sessionId);
                return Optional.empty();
            }

            logger.debug("RedisParamCollectionSessionStore#get - reason=session found, sessionId={}, active={}",
                    sessionId, session.isActive());
            return Optional.of(session);

        } catch (Exception e) {
            logger.error("RedisParamCollectionSessionStore#get - reason=failed to get session, sessionId={}",
                    sessionId, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<ParamCollectionSession> getActiveByUserId(String userId) {
        if (userId == null) {
            return Optional.empty();
        }

        try {
            String userKey = USER_INDEX_PREFIX + userId;
            String sessionId = redisTemplate.opsForValue().get(userKey);

            if (sessionId == null) {
                logger.debug("RedisParamCollectionSessionStore#getActiveByUserId - reason=no active session for user, userId={}", userId);
                return Optional.empty();
            }

            Optional<ParamCollectionSession> session = get(sessionId);

            // 验证会话是否活跃
            if (session.isPresent() && session.get().isActive()) {
                return session;
            }

            // 会话不活跃或不存在，清理用户索引
            redisTemplate.delete(userKey);
            return Optional.empty();

        } catch (Exception e) {
            logger.error("RedisParamCollectionSessionStore#getActiveByUserId - reason=failed to get session by userId, userId={}",
                    userId, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String sessionId) {
        if (sessionId == null) {
            return;
        }

        try {
            // 先获取会话以清理用户索引
            Optional<ParamCollectionSession> sessionOpt = get(sessionId);
            if (sessionOpt.isPresent() && sessionOpt.get().getUserId() != null) {
                String userKey = USER_INDEX_PREFIX + sessionOpt.get().getUserId();
                redisTemplate.delete(userKey);
            }

            String sessionKey = KEY_PREFIX + sessionId;
            redisTemplate.delete(sessionKey);

            logger.debug("RedisParamCollectionSessionStore#delete - reason=session deleted, sessionId={}", sessionId);

        } catch (Exception e) {
            logger.error("RedisParamCollectionSessionStore#delete - reason=failed to delete session, sessionId={}",
                    sessionId, e);
        }
    }

    @Override
    public boolean exists(String sessionId) {
        if (sessionId == null) {
            return false;
        }

        try {
            String sessionKey = KEY_PREFIX + sessionId;
            Boolean exists = redisTemplate.hasKey(sessionKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.error("RedisParamCollectionSessionStore#exists - reason=failed to check session existence, sessionId={}",
                    sessionId, e);
            return false;
        }
    }

    @Override
    public boolean refresh(String sessionId, Duration ttl) {
        if (sessionId == null) {
            return false;
        }

        try {
            String sessionKey = KEY_PREFIX + sessionId;
            Boolean result = redisTemplate.expire(sessionKey, ttl.toMillis(), TimeUnit.MILLISECONDS);

            if (Boolean.TRUE.equals(result)) {
                // 更新会话中的过期时间
                Optional<ParamCollectionSession> sessionOpt = get(sessionId);
                if (sessionOpt.isPresent()) {
                    ParamCollectionSession session = sessionOpt.get();
                    session.setExpireAt(Instant.now().plus(ttl));
                    session.setUpdatedAt(Instant.now());
                    save(session, ttl);
                }

                logger.debug("RedisParamCollectionSessionStore#refresh - reason=session refreshed, sessionId={}, ttl={}",
                        sessionId, ttl);
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("RedisParamCollectionSessionStore#refresh - reason=failed to refresh session, sessionId={}",
                    sessionId, e);
            return false;
        }
    }

    @Override
    public void close(String sessionId) {
        if (sessionId == null) {
            return;
        }

        try {
            Optional<ParamCollectionSession> sessionOpt = get(sessionId);
            if (sessionOpt.isPresent()) {
                ParamCollectionSession session = sessionOpt.get();
                session.close();
                save(session, Duration.ofMinutes(5)); // 关闭后保留5分钟用于审计

                // 清理用户索引
                if (session.getUserId() != null) {
                    String userKey = USER_INDEX_PREFIX + session.getUserId();
                    redisTemplate.delete(userKey);
                }

                logger.debug("RedisParamCollectionSessionStore#close - reason=session closed, sessionId={}", sessionId);
            }

        } catch (Exception e) {
            logger.error("RedisParamCollectionSessionStore#close - reason=failed to close session, sessionId={}",
                    sessionId, e);
        }
    }

    @Override
    public String getStoreType() {
        return "redis";
    }
}
