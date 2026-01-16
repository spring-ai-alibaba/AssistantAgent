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
package com.alibaba.assistant.agent.planning.persistence;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.persistence.converter.ActionEntityConverter;
import com.alibaba.assistant.agent.planning.persistence.entity.ActionRegistryEntity;
import com.alibaba.assistant.agent.planning.persistence.mapper.ActionRegistryMapper;
import com.alibaba.assistant.agent.planning.spi.ActionRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 MyBatis Plus 的动作存储库实现
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class MybatisPlusActionRepository implements ActionRepository {

    private static final Logger logger = LoggerFactory.getLogger(MybatisPlusActionRepository.class);

    private final ActionRegistryMapper mapper;
    private final ActionEntityConverter converter;

    public MybatisPlusActionRepository(ActionRegistryMapper mapper) {
        this.mapper = mapper;
        this.converter = new ActionEntityConverter();
    }

    public MybatisPlusActionRepository(ActionRegistryMapper mapper, ActionEntityConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public ActionDefinition save(ActionDefinition action) {
        if (action == null || action.getActionId() == null) {
            throw new IllegalArgumentException("Action and actionId must not be null");
        }

        ActionRegistryEntity entity = converter.toEntity(action);

        // 检查是否存在
        ActionRegistryEntity existing = mapper.selectByActionId(action.getActionId());
        if (existing != null) {
            // 更新
            entity.setId(existing.getId());
            mapper.updateById(entity);
            logger.debug("MybatisPlusActionRepository#save - reason=updated action, actionId={}", action.getActionId());
        } else {
            // 插入
            mapper.insert(entity);
            logger.debug("MybatisPlusActionRepository#save - reason=inserted action, actionId={}", action.getActionId());
        }

        return action;
    }

    @Override
    public List<ActionDefinition> saveAll(List<ActionDefinition> actions) {
        if (actions == null) {
            return Collections.emptyList();
        }
        actions.forEach(this::save);
        return actions;
    }

    @Override
    public Optional<ActionDefinition> findById(String actionId) {
        ActionRegistryEntity entity = mapper.selectByActionId(actionId);
        return Optional.ofNullable(converter.toDefinition(entity));
    }

    @Override
    public Optional<ActionDefinition> findByName(String actionName) {
        ActionRegistryEntity entity = mapper.selectByActionName(actionName);
        return Optional.ofNullable(converter.toDefinition(entity));
    }

    @Override
    public List<ActionDefinition> findAll() {
        List<ActionRegistryEntity> entities = mapper.selectList(null);
        return entities.stream()
                .map(converter::toDefinition)
                .collect(Collectors.toList());
    }

    @Override
    public List<ActionDefinition> findByCategory(String category) {
        List<ActionRegistryEntity> entities = mapper.selectByCategory(category);
        return entities.stream()
                .map(converter::toDefinition)
                .collect(Collectors.toList());
    }

    @Override
    public List<ActionDefinition> findByTag(String tag) {
        // 使用 LIKE 查询 JSON 数组中的标签
        LambdaQueryWrapper<ActionRegistryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(ActionRegistryEntity::getTags, "\"" + tag + "\"")
                .eq(ActionRegistryEntity::getEnabled, true);

        List<ActionRegistryEntity> entities = mapper.selectList(wrapper);
        return entities.stream()
                .map(converter::toDefinition)
                .collect(Collectors.toList());
    }

    @Override
    public List<ActionDefinition> findByEnabled(boolean enabled) {
        LambdaQueryWrapper<ActionRegistryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActionRegistryEntity::getEnabled, enabled)
                .orderByDesc(ActionRegistryEntity::getPriority);

        List<ActionRegistryEntity> entities = mapper.selectList(wrapper);
        return entities.stream()
                .map(converter::toDefinition)
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteById(String actionId) {
        ActionRegistryEntity entity = mapper.selectByActionId(actionId);
        if (entity != null) {
            int rows = mapper.deleteById(entity.getId());
            logger.debug("MybatisPlusActionRepository#deleteById - reason=deleted action, actionId={}", actionId);
            return rows > 0;
        }
        return false;
    }

    @Override
    public void deleteAll() {
        mapper.delete(null);
        logger.debug("MybatisPlusActionRepository#deleteAll - reason=deleted all actions");
    }

    @Override
    public boolean existsById(String actionId) {
        return mapper.selectByActionId(actionId) != null;
    }

    @Override
    public long count() {
        return mapper.selectCount(null);
    }

    @Override
    public List<String> findAllCategories() {
        return mapper.selectAllCategories();
    }

    @Override
    public List<String> findAllTags() {
        // 从所有动作中提取标签
        List<ActionRegistryEntity> entities = mapper.selectAllEnabled();
        Set<String> tags = new HashSet<>();

        for (ActionRegistryEntity entity : entities) {
            ActionDefinition def = converter.toDefinition(entity);
            if (def.getTags() != null) {
                tags.addAll(def.getTags());
            }
        }

        return new ArrayList<>(tags);
    }

    /**
     * 关键词搜索
     */
    public List<ActionDefinition> searchByKeyword(String keyword, int limit) {
        List<ActionRegistryEntity> entities = mapper.searchByKeyword(keyword, limit);
        return entities.stream()
                .map(converter::toDefinition)
                .collect(Collectors.toList());
    }

    /**
     * 增加使用次数
     */
    public void incrementUsageCount(String actionId) {
        mapper.incrementUsageCount(actionId);
    }
}
