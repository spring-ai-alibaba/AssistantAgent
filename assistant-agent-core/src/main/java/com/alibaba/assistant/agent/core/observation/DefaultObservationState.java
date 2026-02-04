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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的观测状态存储实现
 * <p>
 * 使用 ConcurrentHashMap 存储观测数据，保证线程安全。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class DefaultObservationState implements ObservationState {

    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();

    public DefaultObservationState() {
    }

    /**
     * 从现有Map创建ObservationState
     *
     * @param initialData 初始数据
     */
    public DefaultObservationState(Map<String, Object> initialData) {
        if (initialData != null) {
            data.putAll(initialData);
        }
    }

    @Override
    public void put(String key, Object value) {
        if (key != null && value != null) {
            data.put(key, value);
        }
    }

    @Override
    public void putAll(Map<String, Object> dataMap) {
        if (dataMap != null) {
            dataMap.forEach((key, value) -> {
                if (key != null && value != null) {
                    data.put(key, value);
                }
            });
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        Object value = data.get(key);
        return value != null ? (T) value : defaultValue;
    }

    @Override
    public boolean contains(String key) {
        return data.containsKey(key);
    }

    @Override
    public Object remove(String key) {
        return data.remove(key);
    }

    @Override
    public Map<String, Object> getAll() {
        return Collections.unmodifiableMap(data);
    }

    @Override
    public void clear() {
        data.clear();
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public String toString() {
        return "DefaultObservationState{" +
                "size=" + data.size() +
                ", keys=" + data.keySet() +
                '}';
    }
}

