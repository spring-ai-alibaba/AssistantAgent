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

import com.alibaba.assistant.agent.planning.model.ParamCollectionSession;
import com.alibaba.assistant.agent.planning.spi.SessionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话存储提供者
 *
 * <p>用于测试或简单场景的会话存储。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class InMemorySessionProvider implements SessionProvider {

    private static final Logger logger = LoggerFactory.getLogger(InMemorySessionProvider.class);

    private final Map<String, ParamCollectionSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> assistantSessionIndex = new ConcurrentHashMap<>();

    public InMemorySessionProvider() {
        logger.info("InMemorySessionProvider#init - initialized");
    }

    @Override
    public void saveSession(ParamCollectionSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }

        logger.debug("InMemorySessionProvider#saveSession - sessionId={}", session.getSessionId());

        sessions.put(session.getSessionId(), session);

        if (session.getAssistantSessionId() != null) {
            assistantSessionIndex.put(session.getAssistantSessionId(), session.getSessionId());
        }
    }

    @Override
    public ParamCollectionSession getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("SessionId cannot be null or empty");
        }

        logger.debug("InMemorySessionProvider#getSession - sessionId={}", sessionId);

        ParamCollectionSession session = sessions.get(sessionId);
        if (session != null && session.isExpired()) {
            deleteSession(sessionId);
            return null;
        }

        return session;
    }

    @Override
    public ParamCollectionSession getActiveSessionByAssistantSessionId(String assistantSessionId) {
        if (assistantSessionId == null || assistantSessionId.isBlank()) {
            throw new IllegalArgumentException("AssistantSessionId cannot be null or empty");
        }

        logger.debug("InMemorySessionProvider#getActiveSessionByAssistantSessionId - assistantSessionId={}",
                assistantSessionId);

        String sessionId = assistantSessionIndex.get(assistantSessionId);
        if (sessionId == null) {
            return null;
        }

        ParamCollectionSession session = getSession(sessionId);

        if (session != null && !session.canCollect()) {
            return null;
        }

        return session;
    }

    @Override
    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("SessionId cannot be null or empty");
        }

        logger.debug("InMemorySessionProvider#deleteSession - sessionId={}", sessionId);

        ParamCollectionSession session = sessions.get(sessionId);
        if (session != null && session.getAssistantSessionId() != null) {
            assistantSessionIndex.remove(session.getAssistantSessionId());
        }

        sessions.remove(sessionId);
    }

    @Override
    public int cleanupExpiredSessions() {
        int cleaned = 0;

        for (Map.Entry<String, ParamCollectionSession> entry : sessions.entrySet()) {
            ParamCollectionSession session = entry.getValue();
            if (session.isExpired()) {
                deleteSession(session.getSessionId());
                cleaned++;
            }
        }

        if (cleaned > 0) {
            logger.info("InMemorySessionProvider#cleanupExpiredSessions - cleaned up {} sessions", cleaned);
        }

        return cleaned;
    }

    @Override
    public String getProviderType() {
        return "InMemory";
    }
}
