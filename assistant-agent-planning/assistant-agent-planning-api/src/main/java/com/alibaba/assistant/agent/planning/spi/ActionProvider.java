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

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionMatch;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 动作提供者 SPI
 *
 * <p>负责提供和匹配动作定义。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface ActionProvider {

    /**
     * 获取所有可用动作
     *
     * @return 动作定义列表
     */
    List<ActionDefinition> getAllActions();

    /**
     * 根据动作 ID 获取动作定义
     *
     * @param actionId 动作 ID
     * @return 动作定义（如果存在）
     */
    Optional<ActionDefinition> getAction(String actionId);

    /**
     * 根据动作名称获取动作定义
     *
     * @param actionName 动作名称
     * @return 动作定义（如果存在）
     */
    Optional<ActionDefinition> getActionByName(String actionName);

    /**
     * 根据用户输入匹配动作
     *
     * @param userInput 用户输入
     * @param context   上下文信息
     * @return 匹配结果列表，按置信度降序排列
     */
    List<ActionMatch> matchActions(String userInput, Map<String, Object> context);

    /**
     * 根据分类获取动作
     *
     * @param category 分类名称
     * @return 该分类下的动作列表
     */
    List<ActionDefinition> getActionsByCategory(String category);

    /**
     * 根据标签获取动作
     *
     * @param tags 标签列表
     * @return 包含指定标签的动作列表
     */
    List<ActionDefinition> getActionsByTags(List<String> tags);

    /**
     * 搜索动作
     *
     * @param keyword 搜索关键词
     * @param limit   最大返回数量
     * @return 匹配的动作列表
     */
    List<ActionDefinition> searchActions(String keyword, int limit);

    /**
     * 获取所有分类
     *
     * @return 分类列表
     */
    List<String> getAllCategories();

    /**
     * 获取所有标签
     *
     * @return 标签列表
     */
    List<String> getAllTags();

    /**
     * 获取提供者名称
     *
     * @return 提供者名称
     */
    default String getProviderName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 获取提供者优先级（数值越大优先级越高）
     *
     * @return 优先级
     */
    default int getPriority() {
        return 0;
    }
}
