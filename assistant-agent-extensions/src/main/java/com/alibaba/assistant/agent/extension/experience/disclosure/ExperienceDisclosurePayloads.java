package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared DTOs for experience progressive disclosure runtime.
 *
 * <p>This file defines the in-memory/request-response models exchanged between the
 * prefetch hook, runtime tools, prompt contributors, and state cache. Keeping them in
 * the extension layer makes {@code search_exp}/{@code read_exp} reusable across
 * different business agents.
 */
public final class ExperienceDisclosurePayloads {

    private ExperienceDisclosurePayloads() {
    }

    public enum ToolInvocationPath {
        REACT_DIRECT,
        CODE_ONLY
    }

    public enum PrefetchStatus {
        NOT_RUN,
        SKIPPED,
        COMPLETED
    }

    public static class ExperienceCandidateCard implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String id;
        private ExperienceType experienceType;
        private String title;
        private String description;
        private String snippet;
        private String disclosureStrategy;
        private Double score;
        private List<String> associatedTools = new ArrayList<>();
        private List<String> relatedExperiences = new ArrayList<>();
        private ToolInvocationPath toolInvocationPath;
        private String callableToolName;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public ExperienceType getExperienceType() {
            return experienceType;
        }

        public void setExperienceType(ExperienceType experienceType) {
            this.experienceType = experienceType;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }

        public String getDisclosureStrategy() {
            return disclosureStrategy;
        }

        public void setDisclosureStrategy(String disclosureStrategy) {
            this.disclosureStrategy = disclosureStrategy;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public List<String> getAssociatedTools() {
            return associatedTools;
        }

        public void setAssociatedTools(List<String> associatedTools) {
            this.associatedTools = associatedTools != null ? associatedTools : new ArrayList<>();
        }

        public List<String> getRelatedExperiences() {
            return relatedExperiences;
        }

        public void setRelatedExperiences(List<String> relatedExperiences) {
            this.relatedExperiences = relatedExperiences != null ? relatedExperiences : new ArrayList<>();
        }

        public ToolInvocationPath getToolInvocationPath() {
            return toolInvocationPath;
        }

        public void setToolInvocationPath(ToolInvocationPath toolInvocationPath) {
            this.toolInvocationPath = toolInvocationPath;
        }

        public String getCallableToolName() {
            return callableToolName;
        }

        public void setCallableToolName(String callableToolName) {
            this.callableToolName = callableToolName;
        }
    }

    public static class DirectExperienceGrounding implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String id;
        private ExperienceType experienceType;
        private String title;
        private String description;
        private String content;
        private String disclosureStrategy;
        private Double score;
        private ToolInvocationPath toolInvocationPath;
        private String callableToolName;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public ExperienceType getExperienceType() {
            return experienceType;
        }

        public void setExperienceType(ExperienceType experienceType) {
            this.experienceType = experienceType;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getDisclosureStrategy() {
            return disclosureStrategy;
        }

        public void setDisclosureStrategy(String disclosureStrategy) {
            this.disclosureStrategy = disclosureStrategy;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public ToolInvocationPath getToolInvocationPath() {
            return toolInvocationPath;
        }

        public void setToolInvocationPath(ToolInvocationPath toolInvocationPath) {
            this.toolInvocationPath = toolInvocationPath;
        }

        public String getCallableToolName() {
            return callableToolName;
        }

        public void setCallableToolName(String callableToolName) {
            this.callableToolName = callableToolName;
        }
    }

    public static class GroupedExperienceCandidates implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private List<ExperienceCandidateCard> commonCandidates = new ArrayList<>();
        private List<ExperienceCandidateCard> reactCandidates = new ArrayList<>();
        private List<ExperienceCandidateCard> toolCandidates = new ArrayList<>();

        public List<ExperienceCandidateCard> getCommonCandidates() {
            return commonCandidates;
        }

        public void setCommonCandidates(List<ExperienceCandidateCard> commonCandidates) {
            this.commonCandidates = commonCandidates != null ? commonCandidates : new ArrayList<>();
        }

        public List<ExperienceCandidateCard> getReactCandidates() {
            return reactCandidates;
        }

        public void setReactCandidates(List<ExperienceCandidateCard> reactCandidates) {
            this.reactCandidates = reactCandidates != null ? reactCandidates : new ArrayList<>();
        }

        public List<ExperienceCandidateCard> getToolCandidates() {
            return toolCandidates;
        }

        public void setToolCandidates(List<ExperienceCandidateCard> toolCandidates) {
            this.toolCandidates = toolCandidates != null ? toolCandidates : new ArrayList<>();
        }
    }

    public static class SearchExpRequest implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String query;
        private Integer commonLimit;
        private Integer reactLimit;
        private Integer toolLimit;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public Integer getCommonLimit() {
            return commonLimit;
        }

        public void setCommonLimit(Integer commonLimit) {
            this.commonLimit = commonLimit;
        }

        public Integer getReactLimit() {
            return reactLimit;
        }

        public void setReactLimit(Integer reactLimit) {
            this.reactLimit = reactLimit;
        }

        public Integer getToolLimit() {
            return toolLimit;
        }

        public void setToolLimit(Integer toolLimit) {
            this.toolLimit = toolLimit;
        }
    }

    public static class SearchExpResponse implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String query;
        private GroupedExperienceCandidates candidates = new GroupedExperienceCandidates();
        private List<DirectExperienceGrounding> directGroundings = new ArrayList<>();

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public GroupedExperienceCandidates getCandidates() {
            return candidates;
        }

        public void setCandidates(GroupedExperienceCandidates candidates) {
            this.candidates = candidates != null ? candidates : new GroupedExperienceCandidates();
        }

        public List<DirectExperienceGrounding> getDirectGroundings() {
            return directGroundings;
        }

        public void setDirectGroundings(List<DirectExperienceGrounding> directGroundings) {
            this.directGroundings = directGroundings != null ? directGroundings : new ArrayList<>();
        }
    }

    public static class ReadExpRequest implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static class ReferenceManifestEntry implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String path;
        private String mediaType;
        private String description;
        private Long size;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getMediaType() { return mediaType; }
        public void setMediaType(String mediaType) { this.mediaType = mediaType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Long getSize() { return size; }
        public void setSize(Long size) { this.size = size; }
    }

    public static class AssetManifestEntry implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String path;
        private String mediaType;
        private String role;
        private String description;
        private Long size;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getMediaType() { return mediaType; }
        public void setMediaType(String mediaType) { this.mediaType = mediaType; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Long getSize() { return size; }
        public void setSize(Long size) { this.size = size; }
    }

    public static class ReadExpDocRequest implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String id;
        private List<String> paths = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public List<String> getPaths() { return paths; }
        public void setPaths(List<String> paths) { this.paths = paths != null ? paths : new ArrayList<>(); }
    }

    public static class ReadExpDocResponse implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String id;
        private List<ReadExpDocument> documents = new ArrayList<>();
        /** Non-fatal per-path errors (e.g., "path is an asset"); empty on success. */
        private List<ReadExpDocError> errors = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public List<ReadExpDocument> getDocuments() { return documents; }
        public void setDocuments(List<ReadExpDocument> documents) {
            this.documents = documents != null ? documents : new ArrayList<>();
        }
        public List<ReadExpDocError> getErrors() { return errors; }
        public void setErrors(List<ReadExpDocError> errors) {
            this.errors = errors != null ? errors : new ArrayList<>();
        }
    }

    public static class ReadExpDocument implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String path;
        private String mediaType;
        private String content;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getMediaType() { return mediaType; }
        public void setMediaType(String mediaType) { this.mediaType = mediaType; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class ReadExpDocError implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String path;
        private String reason;

        public ReadExpDocError() {}
        public ReadExpDocError(String path, String reason) {
            this.path = path;
            this.reason = reason;
        }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ReadExpResponse implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private boolean found;
        private String id;
        private ExperienceType experienceType;
        private String title;
        private String description;
        private String content;
        private String disclosureStrategy;
        private Double score;
        private List<String> associatedTools = new ArrayList<>();
        private List<String> relatedExperiences = new ArrayList<>();
        private ToolInvocationPath toolInvocationPath;
        private String callableToolName;
        private ExperienceArtifact artifact;
        private List<ReferenceManifestEntry> referenceManifest = new ArrayList<>();
        private List<AssetManifestEntry> assetManifest = new ArrayList<>();

        public List<ReferenceManifestEntry> getReferenceManifest() {
            return referenceManifest;
        }

        public void setReferenceManifest(List<ReferenceManifestEntry> referenceManifest) {
            this.referenceManifest = referenceManifest != null ? referenceManifest : new ArrayList<>();
        }

        public List<AssetManifestEntry> getAssetManifest() {
            return assetManifest;
        }

        public void setAssetManifest(List<AssetManifestEntry> assetManifest) {
            this.assetManifest = assetManifest != null ? assetManifest : new ArrayList<>();
        }

        public boolean isFound() {
            return found;
        }

        public void setFound(boolean found) {
            this.found = found;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public ExperienceType getExperienceType() {
            return experienceType;
        }

        public void setExperienceType(ExperienceType experienceType) {
            this.experienceType = experienceType;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getDisclosureStrategy() {
            return disclosureStrategy;
        }

        public void setDisclosureStrategy(String disclosureStrategy) {
            this.disclosureStrategy = disclosureStrategy;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public List<String> getAssociatedTools() {
            return associatedTools;
        }

        public void setAssociatedTools(List<String> associatedTools) {
            this.associatedTools = associatedTools != null ? associatedTools : new ArrayList<>();
        }

        public List<String> getRelatedExperiences() {
            return relatedExperiences;
        }

        public void setRelatedExperiences(List<String> relatedExperiences) {
            this.relatedExperiences = relatedExperiences != null ? relatedExperiences : new ArrayList<>();
        }

        public ToolInvocationPath getToolInvocationPath() {
            return toolInvocationPath;
        }

        public void setToolInvocationPath(ToolInvocationPath toolInvocationPath) {
            this.toolInvocationPath = toolInvocationPath;
        }

        public String getCallableToolName() {
            return callableToolName;
        }

        public void setCallableToolName(String callableToolName) {
            this.callableToolName = callableToolName;
        }

        public ExperienceArtifact getArtifact() {
            return artifact;
        }

        public void setArtifact(ExperienceArtifact artifact) {
            this.artifact = artifact;
        }
    }

    public static class PrefetchedExperienceSnapshot implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String query;
        private PrefetchStatus status = PrefetchStatus.NOT_RUN;
        private GroupedExperienceCandidates candidates = new GroupedExperienceCandidates();
        private List<DirectExperienceGrounding> directGroundings = new ArrayList<>();

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public PrefetchStatus getStatus() {
            return status;
        }

        public void setStatus(PrefetchStatus status) {
            this.status = status != null ? status : PrefetchStatus.NOT_RUN;
        }

        public GroupedExperienceCandidates getCandidates() {
            return candidates;
        }

        public void setCandidates(GroupedExperienceCandidates candidates) {
            this.candidates = candidates != null ? candidates : new GroupedExperienceCandidates();
        }

        public List<DirectExperienceGrounding> getDirectGroundings() {
            return directGroundings;
        }

        public void setDirectGroundings(List<DirectExperienceGrounding> directGroundings) {
            this.directGroundings = directGroundings != null ? directGroundings : new ArrayList<>();
        }
    }
}
