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

package com.alibaba.assistant.agent.start.persistence.learning.repository;

import com.alibaba.assistant.agent.extension.learning.model.LearningSearchRequest;
import com.alibaba.assistant.agent.start.persistence.learning.entity.LearningRecordEntity;
import com.alibaba.assistant.agent.start.persistence.learning.mapper.LearningRecordMapper;
import com.alibaba.assistant.agent.extension.learning.spi.LearningRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MySQL实现的学习记录存储
 * 使用JSON序列化存储泛型学习记录
 *
 * @param <T> 学习记录类型
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class MysqlLearningRepository<T> implements LearningRepository<T> {

    private static final Logger log = LoggerFactory.getLogger(MysqlLearningRepository.class);

    private final LearningRecordMapper mapper;

    private final ObjectMapper objectMapper;

    private final Class<T> recordType;

    public MysqlLearningRepository(LearningRecordMapper mapper, ObjectMapper objectMapper, Class<T> recordType) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.recordType = recordType;
    }

    @Override
    public void save(String namespace, String key, T record) {
        if (namespace == null || key == null || record == null) {
            log.warn("MysqlLearningRepository#save - reason=invalid parameters, namespace={}, key={}, record={}",
                    namespace, key, record);
            return;
        }

        try {
            LearningRecordEntity existing = mapper.selectByNamespaceAndKey(namespace, key);
            LearningRecordEntity entity = toEntity(namespace, key, record);

            if (existing == null) {
                // Insert new record
                entity.setCreatedAt(Instant.now());
                entity.setUpdatedAt(Instant.now());
                mapper.insert(entity);
                log.debug("MysqlLearningRepository#save - reason=inserted new record, namespace={}, key={}",
                        namespace, key);
            } else {
                // Update existing record
                entity.setId(existing.getId());
                entity.setCreatedAt(existing.getCreatedAt());
                entity.setUpdatedAt(Instant.now());
                mapper.updateById(entity);
                log.debug("MysqlLearningRepository#save - reason=updated existing record, namespace={}, key={}",
                        namespace, key);
            }
        } catch (JsonProcessingException e) {
            log.error("MysqlLearningRepository#save - reason=failed to serialize record, namespace={}, key={}",
                    namespace, key, e);
            throw new RuntimeException("Failed to serialize learning record", e);
        }
    }

    @Override
    public void saveBatch(String namespace, List<T> records) {
        if (namespace == null || records == null || records.isEmpty()) {
            log.warn("MysqlLearningRepository#saveBatch - reason=invalid parameters, namespace={}, recordCount={}",
                    namespace, records != null ? records.size() : 0);
            return;
        }

        try {
            for (T record : records) {
                // Generate unique key
                String key = UUID.randomUUID().toString();
                LearningRecordEntity entity = toEntity(namespace, key, record);
                entity.setCreatedAt(Instant.now());
                entity.setUpdatedAt(Instant.now());
                mapper.insert(entity);
            }

            log.info("MysqlLearningRepository#saveBatch - reason=batch saved successfully, namespace={}, recordCount={}",
                    namespace, records.size());
        } catch (JsonProcessingException e) {
            log.error("MysqlLearningRepository#saveBatch - reason=failed to serialize records, namespace={}",
                    namespace, e);
            throw new RuntimeException("Failed to serialize learning records", e);
        }
    }

    @Override
    public T get(String namespace, String key) {
        if (namespace == null || key == null) {
            log.warn("MysqlLearningRepository#get - reason=invalid parameters, namespace={}, key={}",
                    namespace, key);
            return null;
        }

        LearningRecordEntity entity = mapper.selectByNamespaceAndKey(namespace, key);
        if (entity == null) {
            log.debug("MysqlLearningRepository#get - reason=record not found, namespace={}, key={}",
                    namespace, key);
            return null;
        }

        try {
            T record = fromEntity(entity);
            log.debug("MysqlLearningRepository#get - reason=record retrieved, namespace={}, key={}",
                    namespace, key);
            return record;
        } catch (JsonProcessingException e) {
            log.error("MysqlLearningRepository#get - reason=failed to deserialize record, namespace={}, key={}",
                    namespace, key, e);
            return null;
        }
    }

    @Override
    public List<T> search(LearningSearchRequest request) {
        if (request == null) {
            log.warn("MysqlLearningRepository#search - reason=request is null");
            return new ArrayList<>();
        }

        String namespace = request.getNamespace();
        if (namespace == null) {
            log.warn("MysqlLearningRepository#search - reason=namespace is null in request");
            return new ArrayList<>();
        }

        List<LearningRecordEntity> entities;
        String learningType = request.getLearningType();

        if (learningType != null && !learningType.isEmpty()) {
            // Search by namespace and learning type
            entities = mapper.selectByNamespaceAndTypeWithPaging(
                    namespace, learningType, request.getOffset(), request.getLimit());
        } else {
            // Search by namespace only
            entities = mapper.selectByNamespaceWithPaging(
                    namespace, request.getOffset(), request.getLimit());
        }

        List<T> results = entities.stream()
                .map(entity -> {
                    try {
                        return fromEntity(entity);
                    } catch (JsonProcessingException e) {
                        log.error("MysqlLearningRepository#search - reason=failed to deserialize record, id={}",
                                entity.getId(), e);
                        return null;
                    }
                })
                .filter(record -> record != null)
                .collect(Collectors.toList());

        log.debug("MysqlLearningRepository#search - reason=search completed, namespace={}, returnedRecords={}",
                namespace, results.size());

        return results;
    }

    @Override
    public void delete(String namespace, String key) {
        if (namespace == null || key == null) {
            log.warn("MysqlLearningRepository#delete - reason=invalid parameters, namespace={}, key={}",
                    namespace, key);
            return;
        }

        int rows = mapper.deleteByNamespaceAndKey(namespace, key);
        log.debug("MysqlLearningRepository#delete - reason=record deleted, namespace={}, key={}, deleted={}",
                namespace, key, rows > 0);
    }

    @Override
    public Class<T> getSupportedRecordType() {
        return recordType;
    }

    /**
     * 获取命名空间中的记录数量
     * @param namespace 命名空间
     * @return 记录数量
     */
    public int getRecordCount(String namespace) {
        return mapper.countByNamespace(namespace);
    }

    /**
     * 清空命名空间
     * @param namespace 命名空间
     */
    public void clearNamespace(String namespace) {
        int rows = mapper.deleteByNamespace(namespace);
        log.info("MysqlLearningRepository#clearNamespace - reason=namespace cleared, namespace={}, deletedRows={}",
                namespace, rows);
    }

    /**
     * 将领域模型转换为数据库实体
     */
    private LearningRecordEntity toEntity(String namespace, String key, T record) throws JsonProcessingException {
        LearningRecordEntity entity = new LearningRecordEntity();
        entity.setNamespace(namespace);
        entity.setRecordKey(key);
        entity.setRecordType(recordType.getName());

        // Serialize record to JSON
        String recordData = objectMapper.writeValueAsString(record);
        entity.setRecordData(recordData);

        // Extract learning type if possible (optional field)
        // You can implement custom logic here if needed
        entity.setLearningType(null);

        return entity;
    }

    /**
     * 将数据库实体转换为领域模型
     */
    private T fromEntity(LearningRecordEntity entity) throws JsonProcessingException {
        String recordData = entity.getRecordData();
        return objectMapper.readValue(recordData, recordType);
    }
}
