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

import java.util.List;
import java.util.Optional;

/**
 * 动作存储库 SPI
 *
 * <p>负责动作定义的持久化存储和检索。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface ActionRepository {

    /**
     * 保存动作定义
     *
     * @param action 动作定义
     * @return 保存后的动作定义
     */
    ActionDefinition save(ActionDefinition action);

    /**
     * 批量保存动作定义
     *
     * @param actions 动作定义列表
     * @return 保存后的动作定义列表
     */
    List<ActionDefinition> saveAll(List<ActionDefinition> actions);

    /**
     * 根据 ID 查找动作
     *
     * @param actionId 动作 ID
     * @return 动作定义（如果存在）
     */
    Optional<ActionDefinition> findById(String actionId);

    /**
     * 根据名称查找动作
     *
     * @param actionName 动作名称
     * @return 动作定义（如果存在）
     */
    Optional<ActionDefinition> findByName(String actionName);

    /**
     * 获取所有动作
     *
     * @return 所有动作定义列表
     */
    List<ActionDefinition> findAll();

    /**
     * 根据分类查找动作
     *
     * @param category 分类名称
     * @return 该分类下的动作列表
     */
    List<ActionDefinition> findByCategory(String category);

    /**
     * 根据标签查找动作
     *
     * @param tag 标签
     * @return 包含该标签的动作列表
     */
    List<ActionDefinition> findByTag(String tag);

    /**
     * 根据启用状态查找动作
     *
     * @param enabled 是否启用
     * @return 符合条件的动作列表
     */
    List<ActionDefinition> findByEnabled(boolean enabled);

    /**
     * 删除动作
     *
     * @param actionId 动作 ID
     * @return 是否删除成功
     */
    boolean deleteById(String actionId);

    /**
     * 删除所有动作
     */
    void deleteAll();

    /**
     * 判断动作是否存在
     *
     * @param actionId 动作 ID
     * @return 是否存在
     */
    boolean existsById(String actionId);

    /**
     * 统计动作数量
     *
     * @return 动作总数
     */
    long count();

    /**
     * 获取所有分类
     *
     * @return 分类列表
     */
    List<String> findAllCategories();

    /**
     * 获取所有标签
     *
     * @return 标签列表
     */
    List<String> findAllTags();
}
