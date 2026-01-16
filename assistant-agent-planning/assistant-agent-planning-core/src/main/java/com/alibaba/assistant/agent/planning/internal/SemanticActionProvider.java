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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionMatch;
import com.alibaba.assistant.agent.planning.spi.ActionProvider;
import com.alibaba.assistant.agent.planning.spi.ActionRepository;
import com.alibaba.assistant.agent.planning.vector.ActionVectorizationService;
import com.alibaba.assistant.agent.planning.vector.ActionVectorizationService.VectorSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 语义动作提供者
 *
 * <p>结合关键词匹配和向量语义匹配的动作提供者。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SemanticActionProvider implements ActionProvider {

    private static final Logger logger = LoggerFactory.getLogger(SemanticActionProvider.class);

    private final ActionRepository actionRepository;
    private final ActionVectorizationService vectorService;
    private final double semanticWeight;
    private final double keywordWeight;
    private final double matchThreshold;

    public SemanticActionProvider(ActionRepository actionRepository,
                                  ActionVectorizationService vectorService) {
        this(actionRepository, vectorService, 0.6, 0.4, 0.5);
    }

    public SemanticActionProvider(ActionRepository actionRepository,
                                  ActionVectorizationService vectorService,
                                  double semanticWeight,
                                  double keywordWeight,
                                  double matchThreshold) {
        this.actionRepository = actionRepository;
        this.vectorService = vectorService;
        this.semanticWeight = semanticWeight;
        this.keywordWeight = keywordWeight;
        this.matchThreshold = matchThreshold;
    }

    @Override
    public List<ActionDefinition> getAllActions() {
        return actionRepository.findByEnabled(true);
    }

    @Override
    public Optional<ActionDefinition> getAction(String actionId) {
        return actionRepository.findById(actionId);
    }

    @Override
    public Optional<ActionDefinition> getActionByName(String actionName) {
        return actionRepository.findByName(actionName);
    }

    @Override
    public List<ActionMatch> matchActions(String userInput, Map<String, Object> context) {
        if (userInput == null || userInput.isBlank()) {
            return Collections.emptyList();
        }

        logger.debug("SemanticActionProvider#matchActions - reason=matching actions, userInput={}", userInput);

        // 1. 向量语义搜索
        List<VectorSearchResult> semanticResults = vectorService.hybridSearch(userInput, 10);

        // 2. 关键词匹配（作为补充）
        Map<String, Double> keywordScores = computeKeywordScores(userInput);

        // 3. 融合结果
        Map<String, Double> combinedScores = new HashMap<>();
        Set<String> allActionIds = new HashSet<>();

        // 添加语义搜索结果
        for (VectorSearchResult result : semanticResults) {
            allActionIds.add(result.getActionId());
            combinedScores.put(result.getActionId(),
                    result.getScore() * semanticWeight);
        }

        // 融合关键词得分
        for (Map.Entry<String, Double> entry : keywordScores.entrySet()) {
            allActionIds.add(entry.getKey());
            combinedScores.merge(entry.getKey(),
                    entry.getValue() * keywordWeight,
                    Double::sum);
        }

        // 4. 构建匹配结果
        List<ActionMatch> matches = new ArrayList<>();
        for (String actionId : allActionIds) {
            double score = combinedScores.getOrDefault(actionId, 0.0);

            if (score >= matchThreshold) {
                Optional<ActionDefinition> actionOpt = actionRepository.findById(actionId);
                if (actionOpt.isPresent()) {
                    ActionDefinition action = actionOpt.get();

                    // 确定匹配类型
                    ActionMatch.MatchType matchType = determineMatchType(actionId, semanticResults, keywordScores);

                    matches.add(ActionMatch.builder()
                            .action(action)
                            .confidence(Math.min(score, 1.0))
                            .matchType(matchType)
                            .build());
                }
            }
        }

        // 按置信度降序排序
        matches.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));

        logger.info("SemanticActionProvider#matchActions - reason=matching completed, userInput={}, matchCount={}",
                userInput, matches.size());

        return matches;
    }

    private Map<String, Double> computeKeywordScores(String userInput) {
        Map<String, Double> scores = new HashMap<>();
        String normalizedInput = userInput.toLowerCase().trim();

        for (ActionDefinition action : getAllActions()) {
            double score = computeKeywordScore(normalizedInput, action);
            if (score > 0) {
                scores.put(action.getActionId(), score);
            }
        }

        return scores;
    }

    private double computeKeywordScore(String normalizedInput, ActionDefinition action) {
        double score = 0.0;

        // 精确关键词匹配
        if (action.getTriggerKeywords() != null) {
            for (String keyword : action.getTriggerKeywords()) {
                if (normalizedInput.contains(keyword.toLowerCase())) {
                    score = Math.max(score, 0.95);
                }
            }
        }

        // 同义词匹配
        if (action.getSynonyms() != null && score < 0.95) {
            for (String synonym : action.getSynonyms()) {
                if (normalizedInput.contains(synonym.toLowerCase())) {
                    score = Math.max(score, 0.85);
                }
            }
        }

        // 示例匹配
        if (action.getExampleInputs() != null && score < 0.85) {
            for (String example : action.getExampleInputs()) {
                double similarity = calculateSimilarity(normalizedInput, example.toLowerCase());
                if (similarity > 0.6) {
                    score = Math.max(score, similarity * 0.8);
                }
            }
        }

        // 名称匹配
        if (action.getActionName() != null && score < 0.7) {
            if (normalizedInput.contains(action.getActionName().toLowerCase())) {
                score = Math.max(score, 0.7);
            }
        }

        return score;
    }

    private double calculateSimilarity(String s1, String s2) {
        Set<String> words1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }

    private ActionMatch.MatchType determineMatchType(String actionId,
                                                      List<VectorSearchResult> semanticResults,
                                                      Map<String, Double> keywordScores) {
        boolean inSemantic = semanticResults.stream()
                .anyMatch(r -> r.getActionId().equals(actionId));
        boolean hasKeyword = keywordScores.containsKey(actionId);

        if (hasKeyword && keywordScores.get(actionId) > 0.9) {
            return ActionMatch.MatchType.KEYWORD_EXACT;
        } else if (inSemantic && (!hasKeyword || keywordScores.get(actionId) < 0.7)) {
            return ActionMatch.MatchType.SEMANTIC;
        } else if (hasKeyword) {
            return ActionMatch.MatchType.KEYWORD_FUZZY;
        } else {
            return ActionMatch.MatchType.SEMANTIC;
        }
    }

    @Override
    public List<ActionDefinition> getActionsByCategory(String category) {
        return actionRepository.findByCategory(category);
    }

    @Override
    public List<ActionDefinition> getActionsByTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }

        Set<ActionDefinition> result = new HashSet<>();
        for (String tag : tags) {
            result.addAll(actionRepository.findByTag(tag));
        }

        return new ArrayList<>(result);
    }

    @Override
    public List<ActionDefinition> searchActions(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return getAllActions().stream().limit(limit).collect(Collectors.toList());
        }

        // 使用向量搜索
        List<VectorSearchResult> results = vectorService.hybridSearch(keyword, limit);

        List<ActionDefinition> actions = new ArrayList<>();
        for (VectorSearchResult result : results) {
            actionRepository.findById(result.getActionId()).ifPresent(actions::add);
        }

        return actions;
    }

    @Override
    public List<String> getAllCategories() {
        return actionRepository.findAllCategories();
    }

    @Override
    public List<String> getAllTags() {
        return actionRepository.findAllTags();
    }

    @Override
    public String getProviderName() {
        return "SemanticActionProvider";
    }

    @Override
    public int getPriority() {
        return 100; // 高优先级
    }

    /**
     * 同步动作到向量索引
     */
    public void syncToVectorIndex() {
        List<ActionDefinition> actions = getAllActions();
        vectorService.indexActions(actions);
        logger.info("SemanticActionProvider#syncToVectorIndex - reason=synced {} actions to vector index",
                actions.size());
    }

    /**
     * 索引单个动作
     */
    public void indexAction(ActionDefinition action) {
        vectorService.indexAction(action);
    }

    /**
     * 删除动作索引
     */
    public void removeFromIndex(String actionId) {
        vectorService.deleteAction(actionId);
    }
}
