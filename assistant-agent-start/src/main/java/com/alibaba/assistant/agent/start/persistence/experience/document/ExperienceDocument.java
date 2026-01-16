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

package com.alibaba.assistant.agent.start.persistence.experience.document;

import com.alibaba.assistant.agent.extension.experience.model.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Set;

/**
 * Experience Elasticsearch Document
 * 映射到 Elasticsearch 索引的文档对象
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Document(indexName = "#{@experienceIndexName}")
public class ExperienceDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Text, analyzer = "icu_analyzer")
    private String title;

    @Field(type = FieldType.Text, analyzer = "icu_analyzer")
    private String content;

    @Field(type = FieldType.Keyword)
    private String scope;

    @Field(type = FieldType.Keyword)
    private String ownerId;

    @Field(type = FieldType.Keyword)
    private String projectId;

    @Field(type = FieldType.Keyword)
    private String repoId;

    @Field(type = FieldType.Keyword)
    private String language;

    @Field(type = FieldType.Keyword)
    private Set<String> tags;

    @Field(type = FieldType.Long)
    private Long createdAt;

    @Field(type = FieldType.Long)
    private Long updatedAt;

    // Complex objects stored as JSON (not indexed for search)
    @Field(type = FieldType.Object, enabled = false)
    private String artifactJson;

    @Field(type = FieldType.Object, enabled = false)
    private String fastIntentConfigJson;

    @Field(type = FieldType.Object, enabled = false)
    private String metadataJson;

    // Vector embedding for semantic search (optional)
    @Field(type = FieldType.Dense_Vector, dims = 1024)
    private float[] embedding;

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getRepoId() {
        return repoId;
    }

    public void setRepoId(String repoId) {
        this.repoId = repoId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getArtifactJson() {
        return artifactJson;
    }

    public void setArtifactJson(String artifactJson) {
        this.artifactJson = artifactJson;
    }

    public String getFastIntentConfigJson() {
        return fastIntentConfigJson;
    }

    public void setFastIntentConfigJson(String fastIntentConfigJson) {
        this.fastIntentConfigJson = fastIntentConfigJson;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
}
