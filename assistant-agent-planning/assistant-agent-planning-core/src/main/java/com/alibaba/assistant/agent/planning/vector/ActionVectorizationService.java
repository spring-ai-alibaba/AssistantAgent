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
package com.alibaba.assistant.agent.planning.vector;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 动作向量化服务
 *
 * <p>负责动作的向量化和基于向量的相似度搜索。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ActionVectorizationService {

    private static final Logger logger = LoggerFactory.getLogger(ActionVectorizationService.class);

    private static final String INDEX_NAME = "action_vectors";
    private static final int VECTOR_DIMENSION = 1024; // DashScope text-embedding-v2 dimension

    private final ElasticsearchClient esClient;
    private final EmbeddingModel embeddingModel;

    public ActionVectorizationService(ElasticsearchClient esClient, EmbeddingModel embeddingModel) {
        this.esClient = esClient;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 启动时自动初始化索引
     */
    @PostConstruct
    public void init() {
        try {
            initIndex();
            logger.info("ActionVectorizationService#init - reason=ES index initialized on startup");
        } catch (Exception e) {
            logger.error("ActionVectorizationService#init - reason=failed to init ES index on startup, error={}",
                    e.getMessage(), e);
        }
    }

    /**
     * 初始化 ES 索引（如果不存在则创建）
     */
    public void initIndex() throws IOException {
        boolean exists = esClient.indices().exists(e -> e.index(INDEX_NAME)).value();

        if (!exists) {
            logger.info("ActionVectorizationService#initIndex - reason=creating index, indexName={}", INDEX_NAME);

            esClient.indices().create(c -> c
                    .index(INDEX_NAME)
                    .mappings(m -> m
                            .properties("actionId", p -> p.keyword(k -> k))
                            .properties("actionName", p -> p.text(t -> t.analyzer("icu_analyzer")))
                            .properties("description", p -> p.text(t -> t.analyzer("icu_analyzer")))
                            .properties("category", p -> p.keyword(k -> k))
                            .properties("tags", p -> p.keyword(k -> k))
                            .properties("triggerKeywords", p -> p.keyword(k -> k))
                            .properties("synonyms", p -> p.keyword(k -> k))
                            .properties("exampleInputs", p -> p.text(t -> t.analyzer("icu_analyzer")))
                            .properties("embeddingText", p -> p.text(t -> t.analyzer("icu_analyzer")))
                            .properties("embedding", p -> p.denseVector(d -> d
                                    .dims(VECTOR_DIMENSION)
                                    .index(true)
                                    .similarity("cosine")))
                            .properties("priority", p -> p.integer(i -> i))
                            .properties("enabled", p -> p.boolean_(b -> b))
                    )
            );

            logger.info("ActionVectorizationService#initIndex - reason=index created successfully");
        }
    }

    /**
     * 索引动作
     */
    public void indexAction(ActionDefinition action) {
        try {
            ActionVectorDocument doc = buildDocument(action);

            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(action.getActionId())
                    .document(doc)
            );

            logger.debug("ActionVectorizationService#indexAction - reason=indexed action, actionId={}",
                    action.getActionId());

        } catch (Exception e) {
            logger.error("ActionVectorizationService#indexAction - reason=failed to index action, actionId={}, error={}",
                    action.getActionId(), e.getMessage(), e);
        }
    }

    /**
     * 批量索引动作
     */
    public void indexActions(List<ActionDefinition> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (ActionDefinition action : actions) {
                ActionVectorDocument doc = buildDocument(action);
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .id(action.getActionId())
                                .document(doc)
                        )
                );
            }

            BulkResponse response = esClient.bulk(bulkBuilder.build());

            if (response.errors()) {
                logger.warn("ActionVectorizationService#indexActions - reason=bulk index had errors");
            } else {
                logger.info("ActionVectorizationService#indexActions - reason=bulk indexed {} actions",
                        actions.size());
            }

        } catch (Exception e) {
            logger.error("ActionVectorizationService#indexActions - reason=failed to bulk index, error={}",
                    e.getMessage(), e);
        }
    }

    /**
     * 删除动作索引
     */
    public void deleteAction(String actionId) {
        try {
            esClient.delete(d -> d
                    .index(INDEX_NAME)
                    .id(actionId)
            );
            logger.debug("ActionVectorizationService#deleteAction - reason=deleted action, actionId={}", actionId);
        } catch (Exception e) {
            logger.error("ActionVectorizationService#deleteAction - reason=failed to delete, actionId={}, error={}",
                    actionId, e.getMessage());
        }
    }

    /**
     * 向量相似度搜索
     */
    public List<VectorSearchResult> searchSimilar(String queryText, int topK) {
        try {
            // 获取查询文本的向量
            float[] queryVector = getEmbedding(queryText);

            // 执行向量搜索
            SearchResponse<ActionVectorDocument> response = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .query(q -> q
                                    .scriptScore(ss -> ss
                                            .query(Query.of(mq -> mq
                                                    .bool(b -> b
                                                            .filter(f -> f.term(t -> t.field("enabled").value(true)))
                                                    )
                                            ))
                                            .script(sc -> sc
                                                    .source("cosineSimilarity(params.query_vector, 'embedding') + 1.0")
                                                    .params("query_vector", co.elastic.clients.json.JsonData.of(queryVector))
                                            )
                                    )
                            )
                            .size(topK),
                    ActionVectorDocument.class
            );

            List<VectorSearchResult> results = new ArrayList<>();
            for (Hit<ActionVectorDocument> hit : response.hits().hits()) {
                ActionVectorDocument doc = hit.source();
                if (doc != null) {
                    // cosine similarity + 1.0 范围是 [0, 2]，转换为 [0, 1]
                    double score = hit.score() != null ? (hit.score() - 1.0) : 0.0;

                    results.add(VectorSearchResult.builder()
                            .actionId(doc.getActionId())
                            .actionName(doc.getActionName())
                            .description(doc.getDescription())
                            .category(doc.getCategory())
                            .score(score)
                            .build());
                }
            }

            logger.debug("ActionVectorizationService#searchSimilar - reason=search completed, query={}, resultCount={}",
                    queryText, results.size());

            return results;

        } catch (Exception e) {
            logger.error("ActionVectorizationService#searchSimilar - reason=search failed, query={}, error={}",
                    queryText, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 混合搜索（向量 + 关键词）
     */
    public List<VectorSearchResult> hybridSearch(String queryText, int topK) {
        try {
            float[] queryVector = getEmbedding(queryText);

            SearchResponse<ActionVectorDocument> response = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .query(q -> q
                                    .bool(b -> b
                                            .filter(f -> f.term(t -> t.field("enabled").value(true)))
                                            .should(sh -> sh
                                                    .scriptScore(ss -> ss
                                                            .query(Query.of(mq -> mq.matchAll(ma -> ma)))
                                                            .script(sc -> sc
                                                                    .source("cosineSimilarity(params.query_vector, 'embedding') + 1.0")
                                                                    .params("query_vector", co.elastic.clients.json.JsonData.of(queryVector))
                                                            )
                                                    )
                                            )
                                            .should(sh -> sh
                                                    .multiMatch(mm -> mm
                                                            .query(queryText)
                                                            .fields("actionName^3", "description^2", "triggerKeywords^4", "synonyms^3", "exampleInputs^2")
                                                    )
                                            )
                                    )
                            )
                            .size(topK),
                    ActionVectorDocument.class
            );

            List<VectorSearchResult> results = new ArrayList<>();
            for (Hit<ActionVectorDocument> hit : response.hits().hits()) {
                ActionVectorDocument doc = hit.source();
                if (doc != null) {
                    results.add(VectorSearchResult.builder()
                            .actionId(doc.getActionId())
                            .actionName(doc.getActionName())
                            .description(doc.getDescription())
                            .category(doc.getCategory())
                            .score(hit.score() != null ? hit.score() / 10.0 : 0.0) // 归一化
                            .build());
                }
            }

            return results;

        } catch (Exception e) {
            logger.error("ActionVectorizationService#hybridSearch - reason=search failed, error={}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private ActionVectorDocument buildDocument(ActionDefinition action) {
        // 构建用于向量化的文本
        String embeddingText = buildEmbeddingText(action);

        // 获取向量
        float[] embedding = getEmbedding(embeddingText);

        return ActionVectorDocument.builder()
                .actionId(action.getActionId())
                .actionName(action.getActionName())
                .description(action.getDescription())
                .category(action.getCategory())
                .tags(action.getTags())
                .triggerKeywords(action.getTriggerKeywords())
                .synonyms(action.getSynonyms())
                .exampleInputs(action.getExampleInputs())
                .embeddingText(embeddingText)
                .embedding(embedding)
                .priority(action.getPriority())
                .enabled(action.getEnabled())
                .build();
    }

    private String buildEmbeddingText(ActionDefinition action) {
        StringBuilder sb = new StringBuilder();

        if (action.getActionName() != null) {
            sb.append(action.getActionName()).append(" ");
        }
        if (action.getDescription() != null) {
            sb.append(action.getDescription()).append(" ");
        }
        if (action.getTriggerKeywords() != null) {
            sb.append(String.join(" ", action.getTriggerKeywords())).append(" ");
        }
        if (action.getSynonyms() != null) {
            sb.append(String.join(" ", action.getSynonyms())).append(" ");
        }
        if (action.getExampleInputs() != null) {
            sb.append(String.join(" ", action.getExampleInputs()));
        }

        return sb.toString().trim();
    }

    private float[] getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[VECTOR_DIMENSION];
        }

        try {
            // 使用 Spring AI EmbeddingModel 获取向量
            // embed(String) 直接返回 float[]
            return embeddingModel.embed(text);

        } catch (Exception e) {
            logger.error("ActionVectorizationService#getEmbedding - reason=failed to get embedding, error={}",
                    e.getMessage());
            return new float[VECTOR_DIMENSION];
        }
    }

    /**
     * 向量搜索结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VectorSearchResult {
        private String actionId;
        private String actionName;
        private String description;
        private String category;
        private double score;
    }
}
