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

import java.time.Duration;
import java.util.Optional;

/**
 * 参数收集会话存储 SPI
 *
 * <p>提供参数收集会话的存储和检索能力，支持多种实现：
 * <ul>
 *     <li>Redis 实现 - 适用于分布式环境</li>
 *     <li>内存实现 - 适用于单机开发/测试</li>
 *     <li>MySQL 实现 - 适用于需要持久化和审计的场景</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface ParamCollectionSessionStore {

    /**
     * 默认会话过期时间（30分钟）
     */
    Duration DEFAULT_TTL = Duration.ofMinutes(30);

    /**
     * 保存会话
     *
     * @param session 会话对象
     */
    void save(ParamCollectionSession session);

    /**
     * 保存会话并设置过期时间
     *
     * @param session 会话对象
     * @param ttl     过期时间
     */
    void save(ParamCollectionSession session, Duration ttl);

    /**
     * 根据会话ID获取会话
     *
     * @param sessionId 会话ID
     * @return 会话对象（可能为空）
     */
    Optional<ParamCollectionSession> get(String sessionId);

    /**
     * 根据用户ID获取活跃会话
     *
     * @param userId 用户ID
     * @return 活跃的会话对象（可能为空）
     */
    Optional<ParamCollectionSession> getActiveByUserId(String userId);

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     */
    void delete(String sessionId);

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    boolean exists(String sessionId);

    /**
     * 刷新会话过期时间
     *
     * @param sessionId 会话ID
     * @param ttl       新的过期时间
     * @return 是否成功
     */
    boolean refresh(String sessionId, Duration ttl);

    /**
     * 关闭会话（标记为非活跃并可选删除）
     *
     * @param sessionId 会话ID
     */
    void close(String sessionId);

    /**
     * 获取存储类型名称
     *
     * @return 存储类型名称（如 "redis", "memory", "mysql"）
     */
    String getStoreType();
}
