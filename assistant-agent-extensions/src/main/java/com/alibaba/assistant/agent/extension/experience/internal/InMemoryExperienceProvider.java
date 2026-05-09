package com.alibaba.assistant.agent.extension.experience.internal;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于内存的经验提供者实现
 * 从InMemoryExperienceRepository读取并过滤经验数据
 *
 * @author Assistant Agent Team
 */
public class InMemoryExperienceProvider implements ExperienceProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemoryExperienceProvider.class);

    private final ExperienceRepository experienceRepository;

    public InMemoryExperienceProvider(ExperienceRepository experienceRepository) {
        this.experienceRepository = experienceRepository;
    }

    @Override
    public List<Experience> query(ExperienceQuery query, ExperienceQueryContext context) {
        log.debug("InMemoryExperienceProvider#query - reason=start querying experiences type={}, limit={}",
                query != null ? query.getType() : null, query != null ? query.getLimit() : 0);

        if (query == null) {
            log.warn("InMemoryExperienceProvider#query - reason=query is null, return empty list");
            return new ArrayList<>();
        }

        List<Experience> candidates = experienceRepository.findByTypeAndTenantId(
                query.getType(),
                context != null ? context.getTenantId() : null);

        // 应用过滤条件
        List<Experience> filtered = applyFilters(candidates, query, context);

        // 排序和限制数量
        List<Experience> results = applySortingAndLimit(filtered, query);

        log.info("InMemoryExperienceProvider#query - reason=query completed, found {} experiences after filtering",
                results.size());

        return results;
    }

    /**
     * 应用过滤条件
     */
    private List<Experience> applyFilters(List<Experience> experiences, ExperienceQuery query, ExperienceQueryContext context) {
        return experiences.stream()
                .filter(experience -> matchesTags(experience, query))
                .filter(experience -> matchesText(experience, query))
                .filter(experience -> matchesDisclosureStrategy(experience, query))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 标签匹配检查
     */
    private boolean matchesTags(Experience experience, ExperienceQuery query) {
        Set<String> queryTags = query.getTags();
        if (queryTags == null || queryTags.isEmpty()) {
            return true; // 没有标签限制
        }

        Set<String> experienceTags = experience.getTags();
        if (experienceTags == null || experienceTags.isEmpty()) {
            return false; // 经验没有标签，但查询有标签要求
        }

        // 检查是否有任何交集
        return queryTags.stream().anyMatch(experienceTags::contains);
    }

    /**
     * 文本匹配检查 (基于子串匹配数量)
     * 要求匹配分数达到最低阈值，避免仅靠 "结果"/"是多" 等常见 2 字词就命中。
     */
    private boolean matchesText(Experience experience, ExperienceQuery query) {
        String queryText = query.getText();
        if (!StringUtils.hasText(queryText)) {
            return true; // 没有文本限制
        }

        int minScore = Math.max(3, queryText.length() / 3);

        String content = experience.getContent();
        String name = experience.getName();
        String description = experience.getDescription();

        // 匹配 content、name 或 description 任一
        boolean hasMatch = false;
        if (StringUtils.hasText(content) && calculateMatchScore(content, queryText) >= minScore) {
            hasMatch = true;
        }
        if (!hasMatch && StringUtils.hasText(name) && calculateMatchScore(name, queryText) >= minScore) {
            hasMatch = true;
        }
        if (!hasMatch && StringUtils.hasText(description) && calculateMatchScore(description, queryText) >= minScore) {
            hasMatch = true;
        }

        return hasMatch;
    }

    /**
     * 披露策略匹配检查
     */
    private boolean matchesDisclosureStrategy(Experience experience, ExperienceQuery query) {
        if (query.getDisclosureStrategy() == null) {
            return true;
        }
        return query.getDisclosureStrategy().equals(experience.getDisclosureStrategy());
    }

    /**
     * 计算匹配分数：queryText的所有子串在content中出现的次数总和
     */
    private int calculateMatchScore(String content, String queryText) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(queryText)) {
            return 0;
        }

        String lowerContent = content.toLowerCase();
        String lowerQuery = queryText.toLowerCase();
        int n = lowerQuery.length();

        // 如果查询词过短，直接全匹配
        if (n < 2) {
            return lowerContent.contains(lowerQuery) ? 1 : 0;
        }

        int score = 0;
        // 生成所有长度>=2的子串
        for (int len = 2; len <= n; len++) {
            for (int i = 0; i <= n - len; i++) {
                String sub = lowerQuery.substring(i, i + len);
                if (lowerContent.contains(sub)) {
                    score++;
                }
            }
        }
        return score;
    }

    /**
     * 应用排序和数量限制
     */
    private List<Experience> applySortingAndLimit(List<Experience> experiences, ExperienceQuery query) {
        Comparator<Experience> comparator = getComparator(query);

        return experiences.stream()
                .sorted(comparator)
                .limit(query.getLimit())
                .collect(Collectors.toList());
    }

    /**
     * 获取排序比较器
     */
    private Comparator<Experience> getComparator(ExperienceQuery query) {
        // 如果有文本查询，优先按匹配数量排序
        if (StringUtils.hasText(query.getText())) {
            String queryText = query.getText();
            return (e1, e2) -> {
                int score1 = calculateMatchScore(e1.getContent(), queryText);
                int score2 = calculateMatchScore(e2.getContent(), queryText);

                // 降序排列
                return Integer.compare(score2, score1);
            };
        }

        return switch (query.getOrderBy()) {
            case CREATED_AT -> Comparator.comparing(Experience::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case UPDATED_AT -> Comparator.comparing(Experience::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case SCORE ->
                // TODO: 实现基于置信度的评分排序
                    (e1, e2) -> {
                        Double score1 = e1.getMetadata().getConfidence();
                        Double score2 = e2.getMetadata().getConfidence();
                        if (score1 == null) score1 = 0.5;
                        if (score2 == null) score2 = 0.5;
                        return score2.compareTo(score1);
                    };
        };
    }
}
