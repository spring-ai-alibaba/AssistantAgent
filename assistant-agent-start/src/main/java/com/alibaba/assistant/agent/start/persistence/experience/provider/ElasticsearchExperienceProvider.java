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

package com.alibaba.assistant.agent.start.persistence.experience.provider;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.alibaba.assistant.agent.extension.experience.model.*;
import com.alibaba.assistant.agent.start.persistence.experience.document.ExperienceDocument;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch implementation of ExperienceProvider
 * Provides advanced search capabilities with full-text and filter-based queries
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ElasticsearchExperienceProvider implements ExperienceProvider {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchExperienceProvider.class);

    private final ElasticsearchClient client;
    private final ObjectMapper objectMapper;
    private final String indexName;

    public ElasticsearchExperienceProvider(ElasticsearchClient client, ObjectMapper objectMapper, String indexName) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.indexName = indexName;
    }

    @Override
    public List<Experience> query(ExperienceQuery query, ExperienceQueryContext context) {
        if (query == null) {
            log.warn("ElasticsearchExperienceProvider#query - reason=query is null");
            return Collections.emptyList();
        }

        try {
            SearchRequest request = buildSearchRequest(query, context);
            SearchResponse<ExperienceDocument> response = client.search(request, ExperienceDocument.class);

            List<Experience> experiences = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::fromDocument)
                    .collect(Collectors.toList());

            log.debug("ElasticsearchExperienceProvider#query - reason=query completed, " +
                    "type={}, text={}, count={}", query.getType(), query.getText(), experiences.size());

            return experiences;

        } catch (IOException e) {
            log.error("ElasticsearchExperienceProvider#query - reason=query failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * Build Elasticsearch SearchRequest from ExperienceQuery
     */
    private SearchRequest buildSearchRequest(ExperienceQuery query, ExperienceQueryContext context) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // 1. Type filter
        if (query.getType() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("type").value(query.getType().name())));
        }

        // 2. Scope filter
        if (!CollectionUtils.isEmpty(query.getScopes())) {
            List<String> scopeValues = query.getScopes().stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
            boolQuery.filter(f -> f.terms(t -> t.field("scope").terms(tv -> tv.value(
                    scopeValues.stream()
                            .map(v -> co.elastic.clients.elasticsearch._types.FieldValue.of(v))
                            .collect(Collectors.toList())
            ))));
        }

        // 3. OwnerId filter
        if (query.getOwnerId() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("ownerId").value(query.getOwnerId())));
        }

        // 4. ProjectId filter
        if (query.getProjectId() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("projectId").value(query.getProjectId())));
        }

        // 5. Language filter
        if (query.getLanguage() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("language").value(query.getLanguage())));
        }

        // 6. Tags filter (all tags must match)
        if (!CollectionUtils.isEmpty(query.getTags())) {
            for (String tag : query.getTags()) {
                boolQuery.filter(f -> f.term(t -> t.field("tags").value(tag)));
            }
        }

        // 7. Text search (multi-field match)
        if (query.getText() != null && !query.getText().isBlank()) {
            boolQuery.must(m -> m.multiMatch(mm -> mm
                    .query(query.getText())
                    .fields("title^2", "content")  // Boost title matches
                    .fuzziness("AUTO")
            ));
        }

        // Build final search request
        return SearchRequest.of(s -> {
            s.index(indexName)
                    .query(q -> q.bool(boolQuery.build()))
                    .size(query.getLimit());

            // Sort order
            if (query.getOrderBy() != null) {
                switch (query.getOrderBy()) {
                    case CREATED_AT:
                        s.sort(sort -> sort.field(f -> f.field("createdAt").order(SortOrder.Desc)));
                        break;
                    case UPDATED_AT:
                        s.sort(sort -> sort.field(f -> f.field("updatedAt").order(SortOrder.Desc)));
                        break;
                    case SCORE:
                        // Default sort by relevance score
                        break;
                }
            }

            return s;
        });
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
        exp.setCreatedAt(doc.getCreatedAt() != null ? java.time.Instant.ofEpochMilli(doc.getCreatedAt()) : null);
        exp.setUpdatedAt(doc.getUpdatedAt() != null ? java.time.Instant.ofEpochMilli(doc.getUpdatedAt()) : null);

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
            log.warn("ElasticsearchExperienceProvider#fromDocument - reason=failed to deserialize complex objects, id={}",
                    doc.getId(), e);
        }

        return exp;
    }
}
