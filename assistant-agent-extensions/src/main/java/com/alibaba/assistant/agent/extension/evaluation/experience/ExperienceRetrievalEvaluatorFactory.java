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
package com.alibaba.assistant.agent.extension.evaluation.experience;

import com.alibaba.assistant.agent.evaluation.evaluator.RuleBasedEvaluator;
import com.alibaba.assistant.agent.evaluation.model.CriterionExecutionContext;
import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.assistant.agent.evaluation.model.CriterionStatus;
import com.alibaba.assistant.agent.evaluation.util.EvaluationLogContextHelper;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 经验检索评估器工厂
 *
 * <p>这是 Extension 层提供的核心机制，负责：
 * <ul>
 *   <li>创建经验检索评估器</li>
 *   <li>处理 ref-id 关联（experience ID）</li>
 *   <li>格式化经验内容</li>
 *   <li>字符串交集匹配逻辑</li>
 * </ul>
 *
 * <p>Starter 层通过此工厂创建评估器并自动注册。
 * Example 层只需提供经验数据。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ExperienceRetrievalEvaluatorFactory {

    private static final Logger log = LoggerFactory.getLogger(ExperienceRetrievalEvaluatorFactory.class);

    /**
     * 创建经验检索评估器
     * 检索 COMMON + REACT 类型的经验
     *
     * @param experienceProvider 经验提供者
     * @param maxExperiencesPerType 每种类型最多检索的数量
     * @return 规则评估器
     */
    public static RuleBasedEvaluator createExperienceEvaluator(
            ExperienceProvider experienceProvider,
            int maxExperiencesPerType) {
        return createEvaluator(
                experienceProvider,
                "experience-retrieval",
                List.of(ExperienceType.COMMON, ExperienceType.REACT),
                maxExperiencesPerType,
                "经验参考"
        );
    }

    /**
     * @deprecated 不再区分阶段，使用 {@link #createExperienceEvaluator} 代替
     */
    @Deprecated
    public static RuleBasedEvaluator createReactPhaseEvaluator(
            ExperienceProvider experienceProvider,
            int maxExperiencesPerType) {
        return createExperienceEvaluator(experienceProvider, maxExperiencesPerType);
    }

    /**
     * @deprecated 已弃用
     */
    @Deprecated
    public static RuleBasedEvaluator createCodeActPhaseEvaluator(
            ExperienceProvider experienceProvider,
            int maxExperiencesPerType) {
        return createExperienceEvaluator(experienceProvider, maxExperiencesPerType);
    }

    /**
     * 创建通用经验检索评估器
     */
    private static RuleBasedEvaluator createEvaluator(
            ExperienceProvider experienceProvider,
            String evaluatorId,
            List<ExperienceType> experienceTypes,
            int maxExperiencesPerType,
            String phaseName) {

        return new RuleBasedEvaluator(evaluatorId, ctx -> {
            CriterionResult result = new CriterionResult();
            result.setCriterionName(ctx.getCriterion().getName());

            try {
                // 优先使用 enhanced_user_input，否则使用原始 userInput
                String queryText = extractEnhancedOrOriginalInput(ctx);
                String sessionId = EvaluationLogContextHelper.getSessionId(ctx.getInputContext());
                log.info("ExperienceRetrievalEvaluatorFactory#{} - reason=开始检索经验, sessionId={}, queryText={}, experienceTypes={}",
                        evaluatorId, sessionId, queryText, experienceTypes);

                List<Experience> experiences = queryExperiencesByStringIntersection(
                        experienceProvider,
                        ctx,
                        queryText,
                        experienceTypes,
                        maxExperiencesPerType
                );

                if (experiences.isEmpty()) {
                    log.info("ExperienceRetrievalEvaluatorFactory#{} - reason=未检索到经验, sessionId={}", evaluatorId, sessionId);
                    result.setStatus(CriterionStatus.SUCCESS);
                    result.setValue("");
                    result.getMetadata().put("is_empty", true);
                    return result;
                }

                log.info("ExperienceRetrievalEvaluatorFactory#{} - reason=检索到经验, sessionId={}, count={}, experienceIds={}",
                        evaluatorId, sessionId, experiences.size(),
                        experiences.stream().map(Experience::getId).collect(Collectors.toList()));

                // 构建 ref_entries，每个经验作为一个独立的条目，experience ID 作为 ref-id
                List<Map<String, String>> refEntries = buildRefEntries(experiences);

                result.setStatus(CriterionStatus.SUCCESS);
                result.setValue(formatExperiences(experiences, phaseName));
                result.getMetadata().put("ref_entries", refEntries);
                result.getMetadata().put("experience_ids", experiences.stream().map(Experience::getId).collect(Collectors.toList()));
                result.getMetadata().put("experience_count", experiences.size());

            } catch (Exception e) {
                log.error("ExperienceRetrievalEvaluatorFactory#{} - reason=经验检索失败, sessionId={}",
                        evaluatorId, EvaluationLogContextHelper.getSessionId(ctx.getInputContext()), e);
                result.setStatus(CriterionStatus.ERROR);
                result.setErrorMessage(e.getMessage());
            }

            return result;
        });
    }

    /**
     * 从执行上下文提取增强后的用户输入，如果没有则使用原始输入
     */
    private static String extractEnhancedOrOriginalInput(CriterionExecutionContext ctx) {
        // 优先尝试获取前序 criterion 的 enhanced_user_input 结果
        CriterionResult enhancedResult = ctx.getDependencyResult("enhanced_user_input");
        if (enhancedResult != null && enhancedResult.getValue() != null) {
            String enhanced = enhancedResult.getValue().toString();
            if (StringUtils.hasText(enhanced)) {
                return enhanced;
            }
        }

        // 否则使用原始的 userInput
        Object userInput = ctx.getInputContext().getInput().get("userInput");
        if (userInput != null) {
            return userInput.toString();
        }
        userInput = ctx.getInputContext().getInput().get("user_input");
        if (userInput != null) {
            return userInput.toString();
        }
        return "";
    }

    /**
     * 基于字符串交集查询经验
     */
    private static List<Experience> queryExperiencesByStringIntersection(
            ExperienceProvider experienceProvider,
            CriterionExecutionContext executionContext,
            String queryText,
            List<ExperienceType> types,
            int maxExperiencesPerType) {

        if (!StringUtils.hasText(queryText)) {
            return List.of();
        }

        ExperienceQueryContext queryContext = new ExperienceQueryContext();
        queryContext.setUserQuery(queryText);
        if (executionContext != null && executionContext.getInputContext() != null) {
            Object sessionId = executionContext.getInputContext().getEnvironmentValue("sessionId");
            if (sessionId != null) {
                queryContext.setSessionId(String.valueOf(sessionId));
            }
            Object tenantId = executionContext.getInputContext().getEnvironmentValue("tenantId");
            if (tenantId != null) {
                queryContext.setTenantId(String.valueOf(tenantId));
            }
            Object userId = executionContext.getInputContext().getEnvironmentValue("userId");
            if (userId != null) {
                queryContext.setUserId(String.valueOf(userId));
            }
        }

        List<Experience> allExperiences = new ArrayList<>();

        for (ExperienceType type : types) {
            ExperienceQuery query = new ExperienceQuery(type);
            query.setLimit(maxExperiencesPerType * 2); // 查询更多，后续过滤

            List<Experience> experiences = experienceProvider.query(query, queryContext);
            if (!CollectionUtils.isEmpty(experiences)) {
                // 基于字符串交集过滤
                for (Experience exp : experiences) {
                    if (hasStringIntersection(queryText, exp)) {
                        allExperiences.add(exp);
                        if (allExperiences.size() >= maxExperiencesPerType * types.size()) {
                            break;
                        }
                    }
                }
            }
        }

        return allExperiences;
    }

    /**
     * 判断查询文本和经验是否有字符串交集
     */
    private static boolean hasStringIntersection(String queryText, Experience experience) {
        String queryLower = queryText.toLowerCase();

        // 检查标题
        if (experience.getTitle() != null &&
            hasCommonSubstring(queryLower, experience.getTitle().toLowerCase(), 2)) {
            return true;
        }

        // 检查内容
        if (experience.getContent() != null &&
            hasCommonSubstring(queryLower, experience.getContent().toLowerCase(), 2)) {
            return true;
        }

        // 检查标签
        Set<String> tags = experience.getTags();
        if (tags != null) {
            for (String tag : tags) {
                if (queryLower.contains(tag.toLowerCase()) || tag.toLowerCase().contains(queryLower)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 检查两个字符串是否有长度至少为 minLength 的公共子串
     */
    private static boolean hasCommonSubstring(String s1, String s2, int minLength) {
        if (s1.length() < minLength || s2.length() < minLength) {
            return false;
        }

        for (int i = 0; i <= s1.length() - minLength; i++) {
            String sub = s1.substring(i, i + minLength);
            if (s2.contains(sub)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建 ref_entries，用于在评估结果中关联 experience ID 作为 ref-id
     */
    private static List<Map<String, String>> buildRefEntries(List<Experience> experiences) {
        List<Map<String, String>> refEntries = new ArrayList<>();
        for (Experience exp : experiences) {
            Map<String, String> entry = new HashMap<>();
            entry.put("ref_id", exp.getId());
            entry.put("content", formatSingleExperience(exp));
            refEntries.add(entry);
        }
        return refEntries;
    }

    /**
     * 格式化单个经验为文本
     */
    private static String formatSingleExperience(Experience exp) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【%s】(%s)\n", exp.getTitle(), exp.getType()));

        Set<String> tags = exp.getTags();
        if (tags != null && !tags.isEmpty()) {
            sb.append(String.format("标签: %s\n", String.join(", ", tags)));
        }

        sb.append(exp.getContent());
        return sb.toString();
    }

    /**
     * 格式化经验列表为增强输入的文本
     */
    private static String formatExperiences(List<Experience> experiences, String phase) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n【").append(phase).append(" - 相关经验参考】\n");
        sb.append("以下是与当前任务相关的历史经验，请参考这些经验来处理当前请求：\n\n");

        for (int i = 0; i < experiences.size(); i++) {
            Experience exp = experiences.get(i);
            sb.append(String.format("=== 经验 %d: %s ===\n", i + 1, exp.getTitle()));
            sb.append(String.format("类型: %s\n", exp.getType()));

            Set<String> tags = exp.getTags();
            if (tags != null && !tags.isEmpty()) {
                sb.append(String.format("标签: %s\n", String.join(", ", tags)));
            }

            sb.append(String.format("内容:\n%s\n\n", exp.getContent()));
        }

        sb.append("【经验参考结束】\n");
        return sb.toString();
    }
}
