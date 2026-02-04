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
package com.alibaba.assistant.agent.prompt;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prompt 贡献者上下文
 * 提供 PromptContributor 所需的上下文信息
 * 设计为与具体框架无关的抽象
 *
 * @author Assistant Agent Team
 */
public interface PromptContributorContext {

    /**
     * 获取当前的消息列表
     *
     * @return 消息列表
     */
    List<Message> getMessages();

    /**
     * 获取当前的 System Message
     *
     * @return System Message
     */
    Optional<SystemMessage> getSystemMessage();

    /**
     * 获取扩展属性
     * 这个 Map 可以包含任意自定义数据，如评估结果、会话信息等
     *
     * @return 扩展属性 Map
     */
    Map<String, Object> getAttributes();

    /**
     * 便捷方法：获取指定属性
     *
     * @param key  属性 key
     * @param type 期望的类型
     * @param <T>  类型参数
     * @return 属性值
     */
    default <T> Optional<T> getAttribute(String key, Class<T> type) {
        Object value = getAttributes().get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    /**
     * 获取当前阶段标识（如 "REACT"、"CODEACT" 等）
     * 可用于条件判断
     *
     * @return 阶段标识
     */
    Optional<String> getPhase();
}

