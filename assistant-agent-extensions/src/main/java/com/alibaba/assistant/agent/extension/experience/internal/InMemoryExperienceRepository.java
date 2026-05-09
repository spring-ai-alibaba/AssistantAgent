package com.alibaba.assistant.agent.extension.experience.internal;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceMetadata;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于内存的经验仓库实现
 * 使用线程安全集合存储经验，适合本地开发与小规模POC
 *
 * @author Assistant Agent Team
 */
public class InMemoryExperienceRepository implements ExperienceRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryExperienceRepository.class);

    /**
     * 经验存储容器，使用ConcurrentHashMap保证线程安全
     */
    private final Map<String, Experience> experienceStore = new ConcurrentHashMap<>();

    @Override
    public Experience save(Experience experience) {
        log.debug("InMemoryExperienceRepository#save - reason=start saving experience id={}", experience.getId());

        if (experience == null) {
            log.warn("InMemoryExperienceRepository#save - reason=experience is null, skip saving");
            throw new IllegalArgumentException("Experience cannot be null");
        }

        // 更新时间戳
        experience.touch();

        // 保存到内存
        experienceStore.put(experience.getId(), experience);

        log.info("InMemoryExperienceRepository#save - reason=experience saved successfully, id={}, type={}",
                experience.getId(), experience.getType());

        return experience;
    }

    @Override
    public List<Experience> batchSave(Collection<Experience> experiences) {
        log.debug("InMemoryExperienceRepository#batchSave - reason=start batch saving {} experiences",
                experiences != null ? experiences.size() : 0);

        if (experiences == null || experiences.isEmpty()) {
            log.warn("InMemoryExperienceRepository#batchSave - reason=experiences collection is empty");
            return new ArrayList<>();
        }

        List<Experience> savedExperiences = new ArrayList<>();
        for (Experience experience : experiences) {
            try {
                savedExperiences.add(save(experience));
            } catch (Exception e) {
                log.error("InMemoryExperienceRepository#batchSave - reason=failed to save experience id={}",
                        experience != null ? experience.getId() : "null", e);
                // 继续保存其他经验
            }
        }

        log.info("InMemoryExperienceRepository#batchSave - reason=batch save completed, saved={}/{}",
                savedExperiences.size(), experiences.size());

        return savedExperiences;
    }

    @Override
    public boolean deleteById(String id) {
        log.debug("InMemoryExperienceRepository#deleteById - reason=start deleting experience id={}", id);

        if (!StringUtils.hasText(id)) {
            log.warn("InMemoryExperienceRepository#deleteById - reason=id is null or empty");
            return false;
        }

        Experience removed = experienceStore.remove(id);
        boolean success = removed != null;

        log.info("InMemoryExperienceRepository#deleteById - reason=delete operation completed, id={}, success={}",
                id, success);

        return success;
    }

    @Override
    public Optional<Experience> findById(String id) {
        log.debug("InMemoryExperienceRepository#findById - reason=start finding experience by id={}", id);

        if (!StringUtils.hasText(id)) {
            log.warn("InMemoryExperienceRepository#findById - reason=id is null or empty");
            return Optional.empty();
        }

        Experience experience = experienceStore.get(id);
        boolean found = experience != null;

        log.debug("InMemoryExperienceRepository#findById - reason=find operation completed, id={}, found={}",
                id, found);

        return Optional.ofNullable(experience);
    }

    @Override
    public List<Experience> findByTypeAndTenantId(ExperienceType type, String tenantId) {
        log.debug("InMemoryExperienceRepository#findByTypeAndTenantId - reason=start finding experiences type={}, tenantId={}",
                type, tenantId);

        List<Experience> results = experienceStore.values().stream()
                .filter(experience -> type == null || type.equals(experience.getType()))
                .filter(experience -> matchesTenantId(experience, tenantId))
                .sorted((e1, e2) -> e2.getUpdatedAt().compareTo(e1.getUpdatedAt())) // 按更新时间倒序
                .collect(Collectors.toList());

        log.info("InMemoryExperienceRepository#findByTypeAndTenantId - reason=find completed, found {} experiences",
                results.size());

        return results;
    }

    @Override
    public long count() {
        long count = experienceStore.size();
        log.debug("InMemoryExperienceRepository#count - reason=total experiences count={}", count);
        return count;
    }

    @Override
    public long countByType(ExperienceType type) {
        log.debug("InMemoryExperienceRepository#countByType - reason=start counting experiences type={}", type);

        long count = experienceStore.values().stream()
                .filter(experience -> type == null || type.equals(experience.getType()))
                .count();

        log.debug("InMemoryExperienceRepository#countByType - reason=count completed, result={}", count);

        return count;
    }

    @Override
    public List<Experience> findAllByType(ExperienceType type) {
        log.debug("InMemoryExperienceRepository#findAllByType - reason=start finding all experiences type={}", type);

        if (type == null) {
            log.warn("InMemoryExperienceRepository#findAllByType - reason=type is null, return all");
            return new ArrayList<>(experienceStore.values());
        }

        List<Experience> results = experienceStore.values().stream()
                .filter(experience -> type.equals(experience.getType()))
                .sorted((e1, e2) -> e2.getUpdatedAt().compareTo(e1.getUpdatedAt()))
                .collect(Collectors.toList());

        log.info("InMemoryExperienceRepository#findAllByType - reason=find completed, type={}, found {} experiences",
                type, results.size());

        return results;
    }

    private boolean matchesTenantId(Experience experience, String tenantId) {
        ExperienceMetadata metadata = experience.getMetadata() != null ? experience.getMetadata() : new ExperienceMetadata();
        return metadata.matchesTenantId(tenantId);
    }
}
