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
package com.alibaba.assistant.agent.core.observation;

import java.util.Map;

/**
 * 观测状态存储接口
 * <p>
 * 允许 Hook 和 Interceptor 在执行过程中注册自定义观测数据，
 * 这些数据会被收集并记录到 Observation 中。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface ObservationState {

    /**
     * 状态Key常量：当前轮次任务ID
     */
    String KEY_CURRENT_ROUND_TASK_ID = "current_round_task_id";

    /**
     * 状态Key常量：用户输入
     */
    String KEY_INPUT = "input";

    /**
     * 状态Key常量：消息历史
     */
    String KEY_MESSAGES = "messages";

    /**
     * 状态Key常量：会话ID
     */
    String KEY_SESSION_ID = "session_id";

    /**
     * 状态Key常量：租户ID
     */
    String KEY_TENANT_ID = "tenant_id";

    /**
     * 状态Key常量：用户ID
     */
    String KEY_USER_ID = "user_id";

    /**
     * 注册观测数据
     *
     * @param key   数据键，建议使用有意义的前缀（如 hook.xxx 或 interceptor.xxx）
     * @param value 数据值
     */
    void put(String key, Object value);

    /**
     * 批量注册观测数据
     *
     * @param data 数据Map
     */
    void putAll(Map<String, Object> data);

    /**
     * 获取观测数据
     *
     * @param key 数据键
     * @param <T> 期望的类型
     * @return 数据值，如果不存在返回null
     */
    <T> T get(String key);

    /**
     * 获取观测数据，如果不存在则返回默认值
     *
     * @param key          数据键
     * @param defaultValue 默认值
     * @param <T>          期望的类型
     * @return 数据值，如果不存在返回默认值
     */
    <T> T getOrDefault(String key, T defaultValue);

    /**
     * 检查是否存在指定键的数据
     *
     * @param key 数据键
     * @return 是否存在
     */
    boolean contains(String key);

    /**
     * 移除指定键的数据
     *
     * @param key 数据键
     * @return 被移除的值，如果不存在返回null
     */
    Object remove(String key);

    /**
     * 获取所有观测数据
     *
     * @return 所有观测数据的不可变视图
     */
    Map<String, Object> getAll();

    /**
     * 清空所有观测数据
     */
    void clear();

    /**
     * 获取数据数量
     *
     * @return 数据数量
     */
    int size();
}

