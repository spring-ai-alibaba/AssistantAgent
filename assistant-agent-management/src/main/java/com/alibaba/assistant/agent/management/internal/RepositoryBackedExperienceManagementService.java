package com.alibaba.assistant.agent.management.internal;

import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceToolInvocationClassifier;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceMetadata;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import com.alibaba.assistant.agent.management.model.ExperienceCreateRequest;
import com.alibaba.assistant.agent.management.model.ExperienceListQuery;
import com.alibaba.assistant.agent.management.model.ExperienceUpdateRequest;
import com.alibaba.assistant.agent.management.model.ExperienceVO;
import com.alibaba.assistant.agent.management.model.PageResult;
import com.alibaba.assistant.agent.management.spi.ExperienceManagementService;
import com.alibaba.assistant.agent.management.spi.ReferenceSummarizer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RepositoryBackedExperienceManagementService implements ExperienceManagementService {

    private final ExperienceRepository repository;
    private final ExperienceToolInvocationClassifier toolInvocationClassifier;
    private final ReferenceSummarizer referenceSummarizer;

    public RepositoryBackedExperienceManagementService(ExperienceRepository repository) {
        this(repository, null, null);
    }

    public RepositoryBackedExperienceManagementService(ExperienceRepository repository,
                                                       ExperienceToolInvocationClassifier toolInvocationClassifier) {
        this(repository, toolInvocationClassifier, null);
    }

    public RepositoryBackedExperienceManagementService(ExperienceRepository repository,
                                                       ExperienceToolInvocationClassifier toolInvocationClassifier,
                                                       ReferenceSummarizer referenceSummarizer) {
        this.repository = repository;
        this.toolInvocationClassifier = toolInvocationClassifier;
        this.referenceSummarizer = referenceSummarizer != null ? referenceSummarizer : new NoopReferenceSummarizer();
    }

    @Override
    public PageResult<ExperienceVO> list(ExperienceListQuery query) {
        List<Experience> all = collectExperiences(query.getType());
        List<Experience> filtered = applyKeywordFilter(all, query.getKeyword());
        filtered = applyTenantFilter(filtered, query.getTenantId(), query.isIncludeGlobal());

        long total = filtered.size();
        int page = Math.max(query.getPage(), 1);
        int size = Math.max(query.getSize(), 1);
        int fromIndex = (page - 1) * size;

        List<ExperienceVO> pageData;
        if (fromIndex >= filtered.size()) {
            pageData = List.of();
        } else {
            int toIndex = Math.min(fromIndex + size, filtered.size());
            pageData = filtered.subList(fromIndex, toIndex).stream()
                    .map(this::toViewObject)
                    .collect(Collectors.toList());
        }
        return PageResult.of(pageData, total, page, size);
    }

    @Override
    public List<ExperienceVO> search(String keyword, ExperienceType type, int topK) {
        List<Experience> all = collectExperiences(type);
        List<Experience> filtered = applyKeywordFilter(all, keyword);
        return filtered.stream()
                .limit(topK)
                .map(this::toViewObject)
                .collect(Collectors.toList());
    }

    @Override
    public ExperienceVO getById(String id) {
        return repository.findById(id)
                .map(this::toViewObject)
                .orElse(null);
    }

    @Override
    public String create(ExperienceCreateRequest request) {
        Experience exp = new Experience(request.getType(), request.getName(), request.getContent());
        exp.setDescription(request.getDescription());
        exp.setDisclosureStrategy(request.getDisclosureStrategy());
        exp.setTags(request.getTags() != null ? new HashSet<>(request.getTags()) : new HashSet<>());
        exp.setAssociatedTools(request.getAssociatedTools() != null ? new ArrayList<>(request.getAssociatedTools()) : new ArrayList<>());
        exp.setRelatedExperiences(request.getRelatedExperiences() != null ? new ArrayList<>(request.getRelatedExperiences()) : new ArrayList<>());
        exp.setArtifact(request.getArtifact());
        exp.setFastIntentConfig(request.getFastIntentConfig());

        if (request.getProperties() != null && !request.getProperties().isEmpty()) {
            ExperienceMetadata metadata = exp.getMetadata();
            for (Map.Entry<String, Object> entry : request.getProperties().entrySet()) {
                metadata.putProperty(entry.getKey(), entry.getValue());
            }
        }
        exp.getMetadata().setTenantIdList(request.getTenantIdList());

        Experience saved = repository.save(exp);
        return saved.getId();
    }

    @Override
    public void update(String id, ExperienceUpdateRequest request) {
        Experience exp = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Experience not found: " + id));

        exp.setName(request.getName());
        exp.setDescription(request.getDescription());
        exp.setContent(request.getContent());
        exp.setDisclosureStrategy(request.getDisclosureStrategy());
        exp.setTags(request.getTags() != null ? new HashSet<>(request.getTags()) : new HashSet<>());
        exp.setAssociatedTools(request.getAssociatedTools() != null ? new ArrayList<>(request.getAssociatedTools()) : new ArrayList<>());
        exp.setRelatedExperiences(request.getRelatedExperiences() != null ? new ArrayList<>(request.getRelatedExperiences()) : new ArrayList<>());
        exp.setArtifact(request.getArtifact());
        exp.setFastIntentConfig(request.getFastIntentConfig());
        ExperienceMetadata metadata = exp.getMetadata();
        metadata.setProperties(new java.util.HashMap<>());
        if (request.getProperties() != null && !request.getProperties().isEmpty()) {
            for (Map.Entry<String, Object> entry : request.getProperties().entrySet()) {
                metadata.putProperty(entry.getKey(), entry.getValue());
            }
        }
        metadata.setTenantIdList(request.getTenantIdList());
        exp.touch();
        repository.save(exp);
    }

    @Override
    public void delete(String id) {
        repository.deleteById(id);
    }

    @Override
    public Map<ExperienceType, Long> countByType() {
        Map<ExperienceType, Long> counts = new EnumMap<>(ExperienceType.class);
        for (ExperienceType type : ExperienceType.values()) {
            counts.put(type, (long) repository.findAllByType(type).size());
        }
        return counts;
    }

    @Override
    public com.alibaba.assistant.agent.extension.experience.model.AssetEntry loadAsset(String id, String path) {
        if (id == null || path == null) {
            return null;
        }
        Experience exp = repository.findById(id).orElse(null);
        if (exp == null || exp.getAssets() == null) {
            return null;
        }
        for (var a : exp.getAssets()) {
            if (path.equals(a.getPath())) {
                return a;
            }
        }
        return null;
    }

    @Override
    public int resummarizeReferences(String id) {
        if (id == null) {
            return 0;
        }
        Experience exp = repository.findById(id).orElse(null);
        if (exp == null || exp.getReferences() == null || exp.getReferences().isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (var ref : exp.getReferences()) {
            String content = ref.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            String oldDesc = ref.getDescription();
            String newDesc = DescriptionResolver.resolve(ref.getPath(), content, referenceSummarizer);
            if (newDesc != null && !newDesc.equals(oldDesc)) {
                ref.setDescription(newDesc);
                updated++;
            }
        }
        if (updated > 0) {
            exp.touch();
            repository.save(exp);
        }
        return updated;
    }

    private List<Experience> collectExperiences(ExperienceType type) {
        if (type != null) {
            return repository.findAllByType(type);
        }
        List<Experience> all = new ArrayList<>();
        for (ExperienceType t : ExperienceType.values()) {
            all.addAll(repository.findAllByType(t));
        }
        return all;
    }

    private List<Experience> applyKeywordFilter(List<Experience> experiences, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return experiences;
        }
        String lowerKeyword = keyword.toLowerCase();
        return experiences.stream()
                .filter(exp -> containsKeyword(exp, lowerKeyword))
                .collect(Collectors.toList());
    }

    private List<Experience> applyTenantFilter(List<Experience> experiences, String tenantId, boolean includeGlobal) {
        if (tenantId == null || tenantId.isBlank()) {
            return experiences;
        }
        return experiences.stream()
                .filter(exp -> exp.getMetadata() != null)
                .filter(exp -> exp.getMetadata().matchesTenantId(tenantId, includeGlobal))
                .collect(Collectors.toList());
    }

    private boolean containsKeyword(Experience exp, String lowerKeyword) {
        return (exp.getName() != null && exp.getName().toLowerCase().contains(lowerKeyword))
                || (exp.getDescription() != null && exp.getDescription().toLowerCase().contains(lowerKeyword))
                || (exp.getContent() != null && exp.getContent().toLowerCase().contains(lowerKeyword));
    }

    private ExperienceVO toViewObject(Experience experience) {
        ExperienceVO vo = ExperienceVO.fromExperience(experience);
        if (experience.getType() != ExperienceType.TOOL) {
            return vo;
        }
        String toolInvocationPath = resolveToolInvocationPath(experience);
        if (toolInvocationPath != null && !toolInvocationPath.isBlank()) {
            vo.setToolInvocationPath(toolInvocationPath);
        }
        return vo;
    }

    private String resolveToolInvocationPath(Experience experience) {
        if (toolInvocationClassifier != null) {
            var invocationPath = toolInvocationClassifier.classify(experience);
            return invocationPath != null ? invocationPath.name() : null;
        }
        ExperienceMetadata metadata = experience.getMetadata();
        if (metadata == null) {
            return null;
        }
        Object configuredValue = metadata.getProperty(ExperienceToolInvocationClassifier.TOOL_INVOCATION_PATH_PROPERTY);
        return configuredValue != null ? String.valueOf(configuredValue) : null;
    }
}
