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

package com.alibaba.assistant.agent.start.persistence.trigger.repository;

import com.alibaba.assistant.agent.extension.trigger.model.SourceType;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerDefinition;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerStatus;
import com.alibaba.assistant.agent.start.persistence.trigger.entity.TriggerDefinitionEntity;
import com.alibaba.assistant.agent.start.persistence.trigger.mapper.TriggerDefinitionMapper;
import com.alibaba.assistant.agent.extension.trigger.repository.TriggerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MySQL实现的触发器定义存储
 * 使用MyBatis Plus进行数据持久化
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class MysqlTriggerRepository implements TriggerRepository {

    private static final Logger log = LoggerFactory.getLogger(MysqlTriggerRepository.class);

    private final TriggerDefinitionMapper mapper;

    public MysqlTriggerRepository(TriggerDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(TriggerDefinition definition) {
        log.debug("MysqlTriggerRepository#save - reason=saving trigger definition, triggerId={}",
                definition.getTriggerId());

        TriggerDefinitionEntity existing = mapper.selectByTriggerId(definition.getTriggerId());
        TriggerDefinitionEntity entity = toEntity(definition);

        if (existing == null) {
            // Insert new record
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            mapper.insert(entity);
            log.debug("MysqlTriggerRepository#save - reason=inserted new trigger, triggerId={}",
                    definition.getTriggerId());
        } else {
            // Update existing record
            entity.setId(existing.getId());
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setUpdatedAt(Instant.now());
            mapper.updateById(entity);
            log.debug("MysqlTriggerRepository#save - reason=updated existing trigger, triggerId={}",
                    definition.getTriggerId());
        }
    }

    @Override
    public Optional<TriggerDefinition> findById(String triggerId) {
        log.debug("MysqlTriggerRepository#findById - reason=finding trigger by id, triggerId={}", triggerId);
        TriggerDefinitionEntity entity = mapper.selectByTriggerId(triggerId);
        return Optional.ofNullable(entity).map(this::toModel);
    }

    @Override
    public List<TriggerDefinition> findBySource(SourceType sourceType, String sourceId) {
        log.debug("MysqlTriggerRepository#findBySource - reason=finding triggers by source, sourceType={}, sourceId={}",
                sourceType, sourceId);
        List<TriggerDefinitionEntity> entities = mapper.selectBySource(sourceType, sourceId);
        return entities.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<TriggerDefinition> findByStatus(TriggerStatus status) {
        log.debug("MysqlTriggerRepository#findByStatus - reason=finding triggers by status, status={}", status);
        List<TriggerDefinitionEntity> entities = mapper.selectByStatus(status);
        return entities.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public void updateStatus(String triggerId, TriggerStatus status) {
        log.debug("MysqlTriggerRepository#updateStatus - reason=updating trigger status, triggerId={}, status={}",
                triggerId, status);
        int rows = mapper.updateStatusByTriggerId(triggerId, status);
        if (rows == 0) {
            log.warn("MysqlTriggerRepository#updateStatus - reason=trigger not found, triggerId={}", triggerId);
        }
    }

    @Override
    public void delete(String triggerId) {
        log.debug("MysqlTriggerRepository#delete - reason=deleting trigger, triggerId={}", triggerId);
        int rows = mapper.deleteByTriggerId(triggerId);
        if (rows == 0) {
            log.warn("MysqlTriggerRepository#delete - reason=trigger not found, triggerId={}", triggerId);
        }
    }

    @Override
    public List<TriggerDefinition> findAll() {
        log.debug("MysqlTriggerRepository#findAll - reason=finding all triggers");
        List<TriggerDefinitionEntity> entities = mapper.selectList(null);
        return entities.stream().map(this::toModel).collect(Collectors.toList());
    }

    /**
     * 将领域模型转换为数据库实体
     */
    private TriggerDefinitionEntity toEntity(TriggerDefinition model) {
        TriggerDefinitionEntity entity = new TriggerDefinitionEntity();
        entity.setTriggerId(model.getTriggerId());
        entity.setName(model.getName());
        entity.setDescription(model.getDescription());
        entity.setSourceType(model.getSourceType());
        entity.setSourceId(model.getSourceId());
        entity.setCreatedBy(model.getCreatedBy());
        entity.setEventProtocol(model.getEventProtocol());
        entity.setEventKey(model.getEventKey());
        entity.setScheduleMode(model.getScheduleMode());
        entity.setScheduleValue(model.getScheduleValue());
        entity.setConditionFunction(model.getConditionFunction());
        entity.setExecuteFunction(model.getExecuteFunction());
        entity.setParameters(model.getParameters());
        entity.setSessionSnapshotId(model.getSessionSnapshotId());
        entity.setGraphName(model.getGraphName());
        entity.setAgentName(model.getAgentName());
        entity.setMetadata(model.getMetadata());
        entity.setStatus(model.getStatus());
        entity.setExpireAt(model.getExpireAt());
        entity.setMaxRetries(model.getMaxRetries());
        entity.setRetryDelay(model.getRetryDelay());
        entity.setCreatedAt(model.getCreatedAt());
        entity.setUpdatedAt(model.getUpdatedAt());
        return entity;
    }

    /**
     * 将数据库实体转换为领域模型
     */
    private TriggerDefinition toModel(TriggerDefinitionEntity entity) {
        TriggerDefinition model = new TriggerDefinition();
        model.setTriggerId(entity.getTriggerId());
        model.setName(entity.getName());
        model.setDescription(entity.getDescription());
        model.setSourceType(entity.getSourceType());
        model.setSourceId(entity.getSourceId());
        model.setCreatedBy(entity.getCreatedBy());
        model.setEventProtocol(entity.getEventProtocol());
        model.setEventKey(entity.getEventKey());
        model.setScheduleMode(entity.getScheduleMode());
        model.setScheduleValue(entity.getScheduleValue());
        model.setConditionFunction(entity.getConditionFunction());
        model.setExecuteFunction(entity.getExecuteFunction());
        model.setParameters(entity.getParameters());
        model.setSessionSnapshotId(entity.getSessionSnapshotId());
        model.setGraphName(entity.getGraphName());
        model.setAgentName(entity.getAgentName());
        model.setMetadata(entity.getMetadata());
        model.setStatus(entity.getStatus());
        model.setExpireAt(entity.getExpireAt());
        model.setMaxRetries(entity.getMaxRetries());
        model.setRetryDelay(entity.getRetryDelay());
        model.setCreatedAt(entity.getCreatedAt());
        model.setUpdatedAt(entity.getUpdatedAt());
        return model;
    }
}
