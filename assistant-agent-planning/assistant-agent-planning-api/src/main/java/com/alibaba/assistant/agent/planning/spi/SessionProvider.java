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
package com.alibaba.assistant.agent.planning.spi;

import com.alibaba.assistant.agent.planning.model.ParamCollectionSession;

/**
 * 参数收集会话存储提供者 SPI
 *
 * <p>此接口定义了参数收集会话的持久化操作。实现类可以提供不同的存储策略：
 * <ul>
 * <li>InMemorySessionProvider - 内存存储（默认，仅用于测试）</li>
 * <li>RedisSessionProvider - Redis 存储（推荐，支持分布式）</li>
 * <li>DatabaseSessionProvider - 数据库存储（支持持久化和查询）</li>
 * </ul>
 *
 * <h3>线程安全性</h3>
 * 实现类必须是线程安全的。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Component
 * public class MyRedisSessionProvider implements SessionProvider {
 *     &#64;Autowired
 *     private RedisTemplate<String, ParamCollectionSession> redisTemplate;
 *
 *     &#64;Override
 *     public void saveSession(ParamCollectionSession session) {
 *         redisTemplate.opsForValue().set(
 *             "session:" + session.getSessionId(),
 *             session,
 *             Duration.ofMinutes(30)
 *         );
 *     }
 *
 *     // ... 其他方法实现
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface SessionProvider {

    /**
     * 保存参数收集会话
     *
     * <p>如果会话已存在，则更新；否则创建新会话。
     *
     * @param session 参数收集会话
     * @throws IllegalArgumentException 如果 session 为 null
     */
    void saveSession(ParamCollectionSession session);

    /**
     * 根据会话 ID 获取参数收集会话
     *
     * @param sessionId 会话 ID
     * @return 参数收集会话，如果不存在则返回 null
     * @throws IllegalArgumentException 如果 sessionId 为 null 或空
     */
    ParamCollectionSession getSession(String sessionId);

    /**
     * 根据 Assistant 会话 ID 获取活跃的参数收集会话
     *
     * <p>一个 Assistant 会话在同一时间只能有一个活跃的参数收集会话。
     *
     * @param assistantSessionId Assistant 会话 ID
     * @return 活跃的参数收集会话，如果不存在则返回 null
     * @throws IllegalArgumentException 如果 assistantSessionId 为 null 或空
     */
    ParamCollectionSession getActiveSessionByAssistantSessionId(String assistantSessionId);

    /**
     * 根据 Assistant 会话 ID 和用户 ID 获取活跃的参数收集会话
     *
     * <p>此方法用于增强的会话隔离，确保不同用户的会话不会混淆。
     *
     * @param assistantSessionId Assistant 会话 ID
     * @param userId 用户 ID
     * @return 活跃的参数收集会话，如果不存在则返回 null
     * @throws IllegalArgumentException 如果任一参数为 null 或空
     */
    default ParamCollectionSession getActiveSessionByAssistantSessionIdAndUserId(
            String assistantSessionId, String userId) {
        return getActiveSessionByAssistantSessionId(assistantSessionId);
    }

    /**
     * 删除参数收集会话
     *
     * @param sessionId 会话 ID
     * @throws IllegalArgumentException 如果 sessionId 为 null 或空
     */
    void deleteSession(String sessionId);

    /**
     * 清理过期的会话
     *
     * <p>此方法应该删除所有过期时间早于当前时间的会话。
     *
     * @return 删除的会话数量
     */
    int cleanupExpiredSessions();

    /**
     * 获取存储提供者的类型名称
     *
     * <p>用于日志记录和监控。
     *
     * @return 类型名称，例如 "InMemory", "Redis", "Database"
     */
    default String getProviderType() {
        return "Unknown";
    }
}
