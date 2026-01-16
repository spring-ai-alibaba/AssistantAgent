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

import com.alibaba.assistant.agent.extension.trigger.model.ExecutionStatus;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerExecutionRecord;
import com.alibaba.assistant.agent.start.persistence.trigger.entity.TriggerExecutionRecordEntity;
import com.alibaba.assistant.agent.start.persistence.trigger.mapper.TriggerExecutionRecordMapper;
import com.alibaba.assistant.agent.extension.trigger.repository.TriggerExecutionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MySQL实现的触发器执行记录存储
 * 使用MyBatis Plus进行数据持久化
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class MysqlTriggerExecutionLogRepository implements TriggerExecutionLogRepository {

    private static final Logger log = LoggerFactory.getLogger(MysqlTriggerExecutionLogRepository.class);

    private final TriggerExecutionRecordMapper mapper;

    public MysqlTriggerExecutionLogRepository(TriggerExecutionRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(TriggerExecutionRecord record) {
        log.debug("MysqlTriggerExecutionLogRepository#save - reason=saving execution record, executionId={}, triggerId={}",
                record.getExecutionId(), record.getTriggerId());

        TriggerExecutionRecordEntity existing = mapper.selectByExecutionId(record.getExecutionId());
        TriggerExecutionRecordEntity entity = toEntity(record);

        if (existing == null) {
            // Insert new record
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            mapper.insert(entity);
            log.debug("MysqlTriggerExecutionLogRepository#save - reason=inserted new record, executionId={}",
                    record.getExecutionId());
        } else {
            // Update existing record
            entity.setId(existing.getId());
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setUpdatedAt(Instant.now());
            mapper.updateById(entity);
            log.debug("MysqlTriggerExecutionLogRepository#save - reason=updated existing record, executionId={}",
                    record.getExecutionId());
        }
    }

    @Override
    public Optional<TriggerExecutionRecord> findById(String executionId) {
        log.debug("MysqlTriggerExecutionLogRepository#findById - reason=finding execution record, executionId={}",
                executionId);
        TriggerExecutionRecordEntity entity = mapper.selectByExecutionId(executionId);
        return Optional.ofNullable(entity).map(this::toModel);
    }

    @Override
    public void updateStatus(String executionId, ExecutionStatus status, String errorMessage,
                             Map<String, Object> outputSummary) {
        log.debug("MysqlTriggerExecutionLogRepository#updateStatus - reason=updating execution status, executionId={}, status={}",
                executionId, status);

        TriggerExecutionRecordEntity entity = mapper.selectByExecutionId(executionId);
        if (entity == null) {
            log.warn("MysqlTriggerExecutionLogRepository#updateStatus - reason=execution record not found, executionId={}",
                    executionId);
            return;
        }

        // Update status
        entity.setStatus(status);

        // Update error message if provided
        if (errorMessage != null) {
            entity.setErrorMessage(errorMessage);
        }

        // Update output summary if provided
        if (outputSummary != null) {
            entity.setOutputSummary(outputSummary);
        }

        // Set end time if terminal status
        if (status == ExecutionStatus.SUCCESS || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.TIMEOUT) {
            entity.setEndTime(Instant.now());
        }

        entity.setUpdatedAt(Instant.now());
        mapper.updateById(entity);

        log.debug("MysqlTriggerExecutionLogRepository#updateStatus - reason=status updated, executionId={}", executionId);
    }

    @Override
    public List<TriggerExecutionRecord> listByTrigger(String triggerId, int limit) {
        log.debug("MysqlTriggerExecutionLogRepository#listByTrigger - reason=listing execution records, triggerId={}, limit={}",
                triggerId, limit);
        List<TriggerExecutionRecordEntity> entities = mapper.selectByTriggerIdWithLimit(triggerId, limit);
        return entities.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<TriggerExecutionRecord> findByTriggerId(String triggerId) {
        log.debug("MysqlTriggerExecutionLogRepository#findByTriggerId - reason=finding all execution records, triggerId={}",
                triggerId);
        List<TriggerExecutionRecordEntity> entities = mapper.selectByTriggerId(triggerId);
        return entities.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public void delete(String executionId) {
        log.debug("MysqlTriggerExecutionLogRepository#delete - reason=deleting execution record, executionId={}",
                executionId);
        int rows = mapper.deleteByExecutionId(executionId);
        if (rows == 0) {
            log.warn("MysqlTriggerExecutionLogRepository#delete - reason=execution record not found, executionId={}",
                    executionId);
        }
    }

    /**
     * 将领域模型转换为数据库实体
     */
    private TriggerExecutionRecordEntity toEntity(TriggerExecutionRecord model) {
        TriggerExecutionRecordEntity entity = new TriggerExecutionRecordEntity();
        entity.setExecutionId(model.getExecutionId());
        entity.setTriggerId(model.getTriggerId());
        entity.setScheduledTime(model.getScheduledTime());
        entity.setStartTime(model.getStartTime());
        entity.setEndTime(model.getEndTime());
        entity.setStatus(model.getStatus());
        entity.setErrorMessage(model.getErrorMessage());
        entity.setErrorStack(model.getErrorStack());
        entity.setOutputSummary(model.getOutputSummary());
        entity.setBackendTaskId(model.getBackendTaskId());
        entity.setThreadId(model.getThreadId());
        entity.setSandboxId(model.getSandboxId());
        entity.setRetryCount(model.getRetryCount());
        return entity;
    }

    /**
     * 将数据库实体转换为领域模型
     */
    private TriggerExecutionRecord toModel(TriggerExecutionRecordEntity entity) {
        TriggerExecutionRecord model = new TriggerExecutionRecord();
        model.setExecutionId(entity.getExecutionId());
        model.setTriggerId(entity.getTriggerId());
        model.setScheduledTime(entity.getScheduledTime());
        model.setStartTime(entity.getStartTime());
        model.setEndTime(entity.getEndTime());
        model.setStatus(entity.getStatus());
        model.setErrorMessage(entity.getErrorMessage());
        model.setErrorStack(entity.getErrorStack());
        model.setOutputSummary(entity.getOutputSummary());
        model.setBackendTaskId(entity.getBackendTaskId());
        model.setThreadId(entity.getThreadId());
        model.setSandboxId(entity.getSandboxId());
        model.setRetryCount(entity.getRetryCount());
        return model;
    }
}
