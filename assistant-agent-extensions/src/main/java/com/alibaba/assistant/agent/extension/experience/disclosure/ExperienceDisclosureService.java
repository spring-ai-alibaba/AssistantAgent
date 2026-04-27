package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.extension.experience.config.ExperienceExtensionProperties;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.AssetManifestEntry;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.DirectExperienceGrounding;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ExperienceCandidateCard;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.GroupedExperienceCandidates;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.PrefetchedExperienceSnapshot;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ReadExpDocError;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ReadExpDocResponse;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ReadExpDocument;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ReadExpResponse;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ReferenceManifestEntry;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.SearchExpResponse;
import com.alibaba.assistant.agent.extension.experience.model.AssetEntry;
import com.alibaba.assistant.agent.extension.experience.model.DisclosureStrategy;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.model.ReferenceEntry;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.PrefetchStatus;

/**
 * Core retrieval service behind experience progressive disclosure.
 *
 * <p>It turns repository/provider data into grouped lightweight candidate cards for
 * prefetch/search and full-detail payloads for {@code read_exp}, while preserving
 * tenant filtering, disclosure strategy, and TOOL invocation metadata.
 */
public class ExperienceDisclosureService {

    private static final int DEFAULT_COMMON_LIMIT = 3;
    private static final int SNIPPET_LENGTH = 280;
    private static final int DIRECT_CONTENT_MAX_LENGTH = 500;
    private static final double DIRECT_CONFIDENCE_THRESHOLD = 0.8D;

    private final ExperienceProvider experienceProvider;
    private final ExperienceRepository experienceRepository;
    private final ExperienceExtensionProperties properties;
    private final ExperienceToolInvocationClassifier toolInvocationClassifier;

    public ExperienceDisclosureService(ExperienceProvider experienceProvider,
                                       ExperienceRepository experienceRepository,
                                       ExperienceExtensionProperties properties,
                                       ExperienceToolInvocationClassifier toolInvocationClassifier) {
        this.experienceProvider = experienceProvider;
        this.experienceRepository = experienceRepository;
        this.properties = properties;
        this.toolInvocationClassifier = toolInvocationClassifier;
    }

    public PrefetchedExperienceSnapshot prefetch(String query, ExperienceQueryContext context) {
        PrefetchedExperienceSnapshot snapshot = new PrefetchedExperienceSnapshot();
        snapshot.setQuery(query);
        if (!StringUtils.hasText(query)) {
            snapshot.setStatus(PrefetchStatus.SKIPPED);
            return snapshot;
        }
        List<Experience> commonExperiences = queryByType(ExperienceType.COMMON, query, DEFAULT_COMMON_LIMIT, context);
        List<Experience> reactExperiences = queryByType(ExperienceType.REACT, query, resolveActionLimit(), context);
        List<Experience> toolExperiences = queryByType(ExperienceType.TOOL, query, resolveActionLimit(), context);
        snapshot.setCandidates(searchGrouped(commonExperiences, reactExperiences, toolExperiences));
        snapshot.setDirectGroundings(extractDirectGroundings(commonExperiences, reactExperiences, toolExperiences));
        snapshot.setStatus(PrefetchStatus.COMPLETED);
        return snapshot;
    }

    public SearchExpResponse search(String query, Integer commonLimit, Integer reactLimit, Integer toolLimit,
                                    ExperienceQueryContext context) {
        SearchExpResponse response = new SearchExpResponse();
        response.setQuery(query);
        List<Experience> commonExperiences = queryByType(
                ExperienceType.COMMON, query, normalizeLimit(commonLimit, DEFAULT_COMMON_LIMIT), context);
        List<Experience> reactExperiences = queryByType(
                ExperienceType.REACT, query, normalizeLimit(reactLimit, resolveActionLimit()), context);
        List<Experience> toolExperiences = queryByType(
                ExperienceType.TOOL, query, normalizeLimit(toolLimit, resolveActionLimit()), context);
        response.setCandidates(searchGrouped(commonExperiences, reactExperiences, toolExperiences));
        response.setDirectGroundings(extractDirectGroundings(commonExperiences, reactExperiences, toolExperiences));
        return response;
    }

    public ReadExpResponse read(String id) {
        ReadExpResponse response = new ReadExpResponse();
        response.setId(id);
        if (!StringUtils.hasText(id)) {
            response.setFound(false);
            return response;
        }
        Optional<Experience> experienceOptional = experienceRepository.findById(id);
        if (experienceOptional.isEmpty()) {
            response.setFound(false);
            return response;
        }

        Experience experience = experienceOptional.get();
        response.setFound(true);
        response.setExperienceType(experience.getType());
        response.setTitle(experience.getName());
        response.setDescription(experience.getDescription());
        response.setContent(experience.getContent());
        response.setDisclosureStrategy(experience.getDisclosureStrategy() != null
                ? experience.getDisclosureStrategy().name() : null);
        response.setScore(resolveScore(experience));
        response.setAssociatedTools(new ArrayList<>(experience.getAssociatedTools()));
        response.setRelatedExperiences(new ArrayList<>(experience.getRelatedExperiences()));
        response.setArtifact(experience.getArtifact());
        response.setReferenceManifest(buildReferenceManifest(experience));
        response.setAssetManifest(buildAssetManifest(experience));

        if (experience.getType() == ExperienceType.TOOL) {
            response.setToolInvocationPath(toolInvocationClassifier.classify(experience));
            response.setCallableToolName(toolInvocationClassifier.resolveCallableToolName(experience));
        }
        return response;
    }

    /**
     * L3 渐进披露：为 {@code read_exp_doc} 工具提供 reference 内容读取。
     *
     * <p>仅接受 {@link Experience#getReferences()} 中声明的路径；asset 路径会被拒绝，
     * 提示调用方在沙箱内以 {@code /workspace/<path>} 读取。
     *
     * @param id     经验 ID
     * @param paths  本次希望读取的路径列表（最多 {@code maxPaths} 个）
     * @return 包含已命中文档及错误列表的响应
     */
    public ReadExpDocResponse readDocs(String id, List<String> paths, int maxPaths) {
        ReadExpDocResponse response = new ReadExpDocResponse();
        response.setId(id);
        if (!StringUtils.hasText(id) || paths == null || paths.isEmpty()) {
            response.getErrors().add(new ReadExpDocError(null, "id 和 paths 均为必填"));
            return response;
        }
        if (maxPaths > 0 && paths.size() > maxPaths) {
            response.getErrors().add(new ReadExpDocError(null,
                    "paths 数量超过上限 " + maxPaths + "；请分多次调用"));
            return response;
        }
        Optional<Experience> opt = experienceRepository.findById(id);
        if (opt.isEmpty()) {
            response.getErrors().add(new ReadExpDocError(null, "Experience not found: " + id));
            return response;
        }
        Experience experience = opt.get();
        Set<String> refPaths = new LinkedHashSet<>();
        if (experience.getReferences() != null) {
            for (ReferenceEntry r : experience.getReferences()) {
                if (r.getPath() != null) {
                    refPaths.add(r.getPath());
                }
            }
        }
        Set<String> assetPaths = new LinkedHashSet<>();
        if (experience.getAssets() != null) {
            for (AssetEntry a : experience.getAssets()) {
                if (a.getPath() != null) {
                    assetPaths.add(a.getPath());
                }
            }
        }
        for (String requested : paths) {
            if (requested == null || requested.isBlank()) {
                response.getErrors().add(new ReadExpDocError(requested, "path 为空"));
                continue;
            }
            if (assetPaths.contains(requested)) {
                response.getErrors().add(new ReadExpDocError(requested,
                        "path is an asset; access it inside the sandbox workspace instead"));
                continue;
            }
            if (!refPaths.contains(requested)) {
                response.getErrors().add(new ReadExpDocError(requested,
                        "unknown path; available references: " + String.join(", ", refPaths)));
                continue;
            }
            ReferenceEntry ref = experience.getReferences().stream()
                    .filter(r -> requested.equals(r.getPath()))
                    .findFirst()
                    .orElse(null);
            if (ref == null) {
                continue;
            }
            ReadExpDocument doc = new ReadExpDocument();
            doc.setPath(ref.getPath());
            doc.setMediaType(ref.getMediaType());
            doc.setContent(ref.getContent());
            response.getDocuments().add(doc);
        }
        return response;
    }

    private List<ReferenceManifestEntry> buildReferenceManifest(Experience experience) {
        List<ReferenceManifestEntry> out = new ArrayList<>();
        if (experience.getReferences() == null) {
            return out;
        }
        for (ReferenceEntry r : experience.getReferences()) {
            ReferenceManifestEntry m = new ReferenceManifestEntry();
            m.setPath(r.getPath());
            m.setMediaType(r.getMediaType());
            m.setDescription(r.getDescription());
            m.setSize(r.getSize());
            out.add(m);
        }
        return out;
    }

    private List<AssetManifestEntry> buildAssetManifest(Experience experience) {
        List<AssetManifestEntry> out = new ArrayList<>();
        if (experience.getAssets() == null) {
            return out;
        }
        for (AssetEntry a : experience.getAssets()) {
            AssetManifestEntry m = new AssetManifestEntry();
            m.setPath(a.getPath());
            m.setMediaType(a.getMediaType());
            m.setRole(a.getRole());
            m.setDescription(a.getDescription());
            m.setSize(a.getSize());
            out.add(m);
        }
        return out;
    }

    private GroupedExperienceCandidates searchGrouped(List<Experience> commonExperiences,
                                                      List<Experience> reactExperiences,
                                                      List<Experience> toolExperiences) {
        GroupedExperienceCandidates candidates = new GroupedExperienceCandidates();
        candidates.setCommonCandidates(toCards(commonExperiences));
        candidates.setReactCandidates(toCards(reactExperiences));
        candidates.setToolCandidates(toCards(toolExperiences));
        return candidates;
    }

    private List<Experience> queryByType(ExperienceType type, String query, int limit, ExperienceQueryContext context) {
        ExperienceQuery experienceQuery = new ExperienceQuery(type);
        experienceQuery.setText(query);
        experienceQuery.setLimit(limit);
        if (type == ExperienceType.COMMON) {
            experienceQuery.setDisclosureStrategy(DisclosureStrategy.DIRECT);
        }
        return experienceProvider.query(experienceQuery, context);
    }

    private List<ExperienceCandidateCard> toCards(List<Experience> experiences) {
        List<ExperienceCandidateCard> cards = new ArrayList<>();
        for (Experience experience : experiences) {
            ExperienceCandidateCard card = new ExperienceCandidateCard();
            card.setId(experience.getId());
            card.setExperienceType(experience.getType());
            card.setTitle(experience.getName());
            card.setDescription(experience.getDescription());
            card.setSnippet(snippet(experience.getContent()));
            card.setDisclosureStrategy(experience.getDisclosureStrategy() != null
                    ? experience.getDisclosureStrategy().name() : null);
            card.setScore(resolveScore(experience));
            card.setAssociatedTools(new ArrayList<>(experience.getAssociatedTools()));
            card.setRelatedExperiences(new ArrayList<>(experience.getRelatedExperiences()));
            if (experience.getType() == ExperienceType.TOOL) {
                card.setToolInvocationPath(toolInvocationClassifier.classify(experience));
                card.setCallableToolName(toolInvocationClassifier.resolveCallableToolName(experience));
            }
            cards.add(card);
        }
        return cards;
    }

    private List<DirectExperienceGrounding> extractDirectGroundings(List<Experience> commonExperiences,
                                                                    List<Experience> reactExperiences,
                                                                    List<Experience> toolExperiences) {
        List<DirectExperienceGrounding> groundings = new ArrayList<>();
        appendDirectGroundings(groundings, commonExperiences);
        appendDirectGroundings(groundings, reactExperiences);
        appendDirectGroundings(groundings, toolExperiences);
        return groundings;
    }

    private void appendDirectGroundings(List<DirectExperienceGrounding> groundings, List<Experience> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return;
        }
        for (Experience experience : experiences) {
            Double score = resolveScore(experience);
            String disclosureStrategy = experience.getDisclosureStrategy() != null
                    ? experience.getDisclosureStrategy().name() : null;
            if (!isDirectGroundingEligible(disclosureStrategy, experience.getContent(), score)) {
                continue;
            }
            DirectExperienceGrounding grounding = new DirectExperienceGrounding();
            grounding.setId(experience.getId());
            grounding.setExperienceType(experience.getType());
            grounding.setTitle(experience.getName());
            grounding.setDescription(experience.getDescription());
            grounding.setContent(experience.getContent());
            grounding.setDisclosureStrategy(disclosureStrategy);
            grounding.setScore(score);
            if (experience.getType() == ExperienceType.TOOL) {
                grounding.setToolInvocationPath(toolInvocationClassifier.classify(experience));
                grounding.setCallableToolName(toolInvocationClassifier.resolveCallableToolName(experience));
            }
            groundings.add(grounding);
        }
    }

    public boolean isDirectGroundingEligible(ReadExpResponse response) {
        if (response == null || !response.isFound()) {
            return false;
        }
        return isDirectGroundingEligible(response.getDisclosureStrategy(), response.getContent(), response.getScore());
    }

    public DirectExperienceGrounding toDirectGrounding(ReadExpResponse response) {
        if (!isDirectGroundingEligible(response)) {
            return null;
        }
        DirectExperienceGrounding grounding = new DirectExperienceGrounding();
        grounding.setId(response.getId());
        grounding.setExperienceType(response.getExperienceType());
        grounding.setTitle(response.getTitle());
        grounding.setDescription(response.getDescription());
        grounding.setContent(response.getContent());
        grounding.setDisclosureStrategy(response.getDisclosureStrategy());
        grounding.setScore(response.getScore());
        grounding.setToolInvocationPath(response.getToolInvocationPath());
        grounding.setCallableToolName(response.getCallableToolName());
        return grounding;
    }

    public boolean isDirectGroundingEligible(DirectExperienceGrounding grounding) {
        if (grounding == null) {
            return false;
        }
        return isDirectGroundingEligible(grounding.getDisclosureStrategy(), grounding.getContent(), grounding.getScore());
    }

    private boolean isDirectGroundingEligible(String disclosureStrategy, String content, Double score) {
        if (!DisclosureStrategy.DIRECT.name().equals(disclosureStrategy)) {
            return false;
        }
        if (!StringUtils.hasText(content) || content.length() > DIRECT_CONTENT_MAX_LENGTH) {
            return false;
        }
        return score == null || score >= DIRECT_CONFIDENCE_THRESHOLD;
    }

    private Double resolveScore(Experience experience) {
        if (experience == null || experience.getMetadata() == null) {
            return null;
        }
        return experience.getMetadata().getConfidence();
    }

    private int resolveActionLimit() {
        return Math.max(1, properties.getMaxItemsPerQuery());
    }

    private int normalizeLimit(Integer requestedLimit, int fallback) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return fallback;
        }
        return requestedLimit;
    }

    private String snippet(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        if (content.length() <= SNIPPET_LENGTH) {
            return content;
        }
        return content.substring(0, SNIPPET_LENGTH) + "...";
    }
}
