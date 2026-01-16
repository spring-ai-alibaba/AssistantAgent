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

package com.alibaba.assistant.agent.start.persistence.experience.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.alibaba.assistant.agent.extension.experience.model.*;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import com.alibaba.assistant.agent.start.persistence.experience.document.ExperienceDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch implementation of ExperienceRepository
 * Provides CRUD operations for Experience storage in Elasticsearch
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ElasticsearchExperienceRepository implements ExperienceRepository {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchExperienceRepository.class);

    private final ElasticsearchClient client;
    private final ObjectMapper objectMapper;
    private final String indexName;

    public ElasticsearchExperienceRepository(ElasticsearchClient client, ObjectMapper objectMapper, String indexName) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.indexName = indexName;
    }

    @Override
    public Experience save(Experience experience) {
        if (experience == null) {
            log.warn("ElasticsearchExperienceRepository#save - reason=experience is null");
            return null;
        }

        try {
            // Generate ID if not present
            if (experience.getId() == null || experience.getId().isBlank()) {
                experience.setId(UUID.randomUUID().toString());
            }

            // Set timestamps
            Instant now = Instant.now();
            if (experience.getCreatedAt() == null) {
                experience.setCreatedAt(now);
            }
            experience.setUpdatedAt(now);

            ExperienceDocument doc = toDocument(experience);

            IndexResponse response = client.index(i -> i
                    .index(indexName)
                    .id(experience.getId())
                    .document(doc)
            );

            log.info("ElasticsearchExperienceRepository#save - reason=experience saved, id={}, result={}",
                    experience.getId(), response.result().jsonValue());

            return experience;

        } catch (IOException e) {
            log.error("ElasticsearchExperienceRepository#save - reason=save failed, id={}", experience.getId(), e);
            throw new RuntimeException("Failed to save experience", e);
        }
    }

    @Override
    public List<Experience> batchSave(Collection<Experience> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            log.warn("ElasticsearchExperienceRepository#batchSave - reason=experiences is null or empty");
            return Collections.emptyList();
        }

        try {
            Instant now = Instant.now();
            List<Experience> processedExperiences = new ArrayList<>();

            List<BulkOperation> operations = new ArrayList<>();
            for (Experience exp : experiences) {
                // Generate ID if not present
                if (exp.getId() == null || exp.getId().isBlank()) {
                    exp.setId(UUID.randomUUID().toString());
                }

                // Set timestamps
                if (exp.getCreatedAt() == null) {
                    exp.setCreatedAt(now);
                }
                exp.setUpdatedAt(now);

                ExperienceDocument doc = toDocument(exp);
                operations.add(BulkOperation.of(op -> op
                        .index(idx -> idx
                                .index(indexName)
                                .id(exp.getId())
                                .document(doc)
                        )
                ));
                processedExperiences.add(exp);
            }

            BulkResponse response = client.bulk(b -> b.operations(operations));

            if (response.errors()) {
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        log.error("ElasticsearchExperienceRepository#batchSave - reason=bulk item failed, id={}, error={}",
                                item.id(), item.error().reason());
                    }
                }
            }

            log.info("ElasticsearchExperienceRepository#batchSave - reason=batch save completed, count={}, errors={}",
                    processedExperiences.size(), response.errors());

            return processedExperiences;

        } catch (IOException e) {
            log.error("ElasticsearchExperienceRepository#batchSave - reason=batch save failed", e);
            throw new RuntimeException("Failed to batch save experiences", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        if (id == null || id.isBlank()) {
            log.warn("ElasticsearchExperienceRepository#deleteById - reason=id is null or blank");
            return false;
        }

        try {
            DeleteResponse response = client.delete(d -> d
                    .index(indexName)
                    .id(id)
            );

            boolean deleted = "deleted".equals(response.result().jsonValue());
            log.info("ElasticsearchExperienceRepository#deleteById - reason=delete completed, id={}, result={}",
                    id, response.result().jsonValue());

            return deleted;

        } catch (IOException e) {
            log.error("ElasticsearchExperienceRepository#deleteById - reason=delete failed, id={}", id, e);
            return false;
        }
    }

    @Override
    public Optional<Experience> findById(String id) {
        if (id == null || id.isBlank()) {
            log.warn("ElasticsearchExperienceRepository#findById - reason=id is null or blank");
            return Optional.empty();
        }

        try {
            GetResponse<ExperienceDocument> response = client.get(g -> g
                            .index(indexName)
                            .id(id),
                    ExperienceDocument.class
            );

            if (response.found() && response.source() != null) {
                Experience exp = fromDocument(response.source());
                log.debug("ElasticsearchExperienceRepository#findById - reason=found, id={}", id);
                return Optional.of(exp);
            }

            log.debug("ElasticsearchExperienceRepository#findById - reason=not found, id={}", id);
            return Optional.empty();

        } catch (IOException e) {
            log.error("ElasticsearchExperienceRepository#findById - reason=find failed, id={}", id, e);
            return Optional.empty();
        }
    }

    @Override
    public List<Experience> findByTypeAndScope(ExperienceType type, ExperienceScope scope, String ownerId, String projectId) {
        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            if (type != null) {
                boolQuery.filter(f -> f.term(t -> t.field("type").value(type.name())));
            }

            if (scope != null) {
                boolQuery.filter(f -> f.term(t -> t.field("scope").value(scope.name())));
            }

            if (ownerId != null) {
                boolQuery.filter(f -> f.term(t -> t.field("ownerId").value(ownerId)));
            }

            if (projectId != null) {
                boolQuery.filter(f -> f.term(t -> t.field("projectId").value(projectId)));
            }

            SearchResponse<ExperienceDocument> response = client.search(s -> s
                            .index(indexName)
                            .query(q -> q.bool(boolQuery.build()))
                            .size(1000),
                    ExperienceDocument.class
            );

            List<Experience> results = response.hits().hits().stream()
                    .map(hit -> hit.source())
                    .filter(Objects::nonNull)
                    .map(this::fromDocument)
                    .collect(Collectors.toList());

            log.debug("ElasticsearchExperienceRepository#findByTypeAndScope - reason=search completed, type={}, scope={}, count={}",
                    type, scope, results.size());

            return results;

        } catch (IOException e) {
            log.error("ElasticsearchExperienceRepository#findByTypeAndScope - reason=search failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public long count() {
        try {
            CountResponse response = client.count(c -> c.index(indexName));
            log.debug("ElasticsearchExperienceRepository#count - reason=count completed, count={}", response.count());
            return response.count();

        } catch (IOException e) {
            log.error("ElasticsearchExperienceRepository#count - reason=count failed", e);
            return 0;
        }
    }

    @Override
    public long countByTypeAndScope(ExperienceType type, ExperienceScope scope) {
        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            if (type != null) {
                boolQuery.filter(f -> f.term(t -> t.field("type").value(type.name())));
            }

            if (scope != null) {
                boolQuery.filter(f -> f.term(t -> t.field("scope").value(scope.name())));
            }

            CountResponse response = client.count(c -> c
                    .index(indexName)
                    .query(q -> q.bool(boolQuery.build()))
            );

            log.debug("ElasticsearchExperienceRepository#countByTypeAndScope - reason=count completed, type={}, scope={}, count={}",
                    type, scope, response.count());

            return response.count();

        } catch (IOException e) {
            log.error("ElasticsearchExperienceRepository#countByTypeAndScope - reason=count failed", e);
            return 0;
        }
    }

    /**
     * Convert Experience to Elasticsearch Document
     */
    private ExperienceDocument toDocument(Experience exp) {
        ExperienceDocument doc = new ExperienceDocument();
        doc.setId(exp.getId());
        doc.setType(exp.getType() != null ? exp.getType().name() : null);
        doc.setTitle(exp.getTitle());
        doc.setContent(exp.getContent());
        doc.setScope(exp.getScope() != null ? exp.getScope().name() : null);
        doc.setOwnerId(exp.getOwnerId());
        doc.setProjectId(exp.getProjectId());
        doc.setRepoId(exp.getRepoId());
        doc.setLanguage(exp.getLanguage());
        doc.setTags(exp.getTags());
        // Convert Instant to epoch millis
        doc.setCreatedAt(exp.getCreatedAt() != null ? exp.getCreatedAt().toEpochMilli() : null);
        doc.setUpdatedAt(exp.getUpdatedAt() != null ? exp.getUpdatedAt().toEpochMilli() : null);

        // Serialize complex objects to JSON
        try {
            if (exp.getArtifact() != null) {
                doc.setArtifactJson(objectMapper.writeValueAsString(exp.getArtifact()));
            }
            if (exp.getFastIntentConfig() != null) {
                doc.setFastIntentConfigJson(objectMapper.writeValueAsString(exp.getFastIntentConfig()));
            }
            if (exp.getMetadata() != null) {
                doc.setMetadataJson(objectMapper.writeValueAsString(exp.getMetadata()));
            }
        } catch (JsonProcessingException e) {
            log.warn("ElasticsearchExperienceRepository#toDocument - reason=failed to serialize complex objects, id={}",
                    exp.getId(), e);
        }

        return doc;
    }

    /**
     * Convert Elasticsearch Document to Experience
     */
    private Experience fromDocument(ExperienceDocument doc) {
        Experience exp = new Experience();
        exp.setId(doc.getId());
        exp.setType(doc.getType() != null ? ExperienceType.valueOf(doc.getType()) : null);
        exp.setTitle(doc.getTitle());
        exp.setContent(doc.getContent());
        exp.setScope(doc.getScope() != null ? ExperienceScope.valueOf(doc.getScope()) : null);
        exp.setOwnerId(doc.getOwnerId());
        exp.setProjectId(doc.getProjectId());
        exp.setRepoId(doc.getRepoId());
        exp.setLanguage(doc.getLanguage());
        exp.setTags(doc.getTags());
        // Convert epoch millis to Instant
        exp.setCreatedAt(doc.getCreatedAt() != null ? Instant.ofEpochMilli(doc.getCreatedAt()) : null);
        exp.setUpdatedAt(doc.getUpdatedAt() != null ? Instant.ofEpochMilli(doc.getUpdatedAt()) : null);

        // Deserialize complex objects from JSON
        try {
            if (doc.getArtifactJson() != null) {
                exp.setArtifact(objectMapper.readValue(doc.getArtifactJson(), ExperienceArtifact.class));
            }
            if (doc.getFastIntentConfigJson() != null) {
                exp.setFastIntentConfig(objectMapper.readValue(doc.getFastIntentConfigJson(), FastIntentConfig.class));
            }
            if (doc.getMetadataJson() != null) {
                exp.setMetadata(objectMapper.readValue(doc.getMetadataJson(), ExperienceMetadata.class));
            }
        } catch (JsonProcessingException e) {
            log.warn("ElasticsearchExperienceRepository#fromDocument - reason=failed to deserialize complex objects, id={}",
                    doc.getId(), e);
        }

        return exp;
    }
}
