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
package com.alibaba.assistant.agent.planning.intent;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 关键词快速匹配器
 *
 * <p>第一层匹配：基于 triggerKeywords、synonyms、exampleInputs 进行快速关键词匹配。
 * 这是最快的匹配方式（<1ms），用于快速过滤明显不匹配的请求。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class KeywordMatcher {

    private static final Logger logger = LoggerFactory.getLogger(KeywordMatcher.class);

    /**
     * 动作ID -> 关键词集合的映射（用于快速查找）
     */
    private final Map<String, Set<String>> actionKeywordsMap = new ConcurrentHashMap<>();

    /**
     * 关键词 -> 动作ID列表的倒排索引
     */
    private final Map<String, Set<String>> keywordToActionsIndex = new ConcurrentHashMap<>();

    /**
     * 动作ID -> ActionDefinition 的映射
     */
    private final Map<String, ActionDefinition> actionDefinitions = new ConcurrentHashMap<>();

    /**
     * 预编译的正则表达式缓存
     */
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    /**
     * 注册动作的关键词
     *
     * @param action 动作定义
     */
    public void registerAction(ActionDefinition action) {
        if (action == null || !StringUtils.hasText(action.getActionId())) {
            return;
        }

        String actionId = action.getActionId();
        Set<String> keywords = new HashSet<>();

        // 收集所有关键词
        // 1. triggerKeywords
        if (!CollectionUtils.isEmpty(action.getTriggerKeywords())) {
            keywords.addAll(normalizeKeywords(action.getTriggerKeywords()));
        }

        // 2. synonyms
        if (!CollectionUtils.isEmpty(action.getSynonyms())) {
            keywords.addAll(normalizeKeywords(action.getSynonyms()));
        }

        // 3. 从 exampleInputs 提取关键词
        if (!CollectionUtils.isEmpty(action.getExampleInputs())) {
            for (String example : action.getExampleInputs()) {
                keywords.addAll(extractKeywords(example));
            }
        }

        // 4. actionName 分词
        if (StringUtils.hasText(action.getActionName())) {
            keywords.addAll(extractKeywords(action.getActionName()));
        }

        // 存储映射
        actionKeywordsMap.put(actionId, keywords);
        actionDefinitions.put(actionId, action);

        // 构建倒排索引
        for (String keyword : keywords) {
            keywordToActionsIndex.computeIfAbsent(keyword, k -> ConcurrentHashMap.newKeySet())
                    .add(actionId);
        }

        logger.debug("KeywordMatcher#registerAction - actionId={}, keywords={}", actionId, keywords.size());
    }

    /**
     * 移除动作
     *
     * @param actionId 动作ID
     */
    public void removeAction(String actionId) {
        Set<String> keywords = actionKeywordsMap.remove(actionId);
        actionDefinitions.remove(actionId);

        if (keywords != null) {
            for (String keyword : keywords) {
                Set<String> actions = keywordToActionsIndex.get(keyword);
                if (actions != null) {
                    actions.remove(actionId);
                    if (actions.isEmpty()) {
                        keywordToActionsIndex.remove(keyword);
                    }
                }
            }
        }
    }

    /**
     * 清空所有注册的动作
     */
    public void clear() {
        actionKeywordsMap.clear();
        keywordToActionsIndex.clear();
        actionDefinitions.clear();
    }

    /**
     * 快速检查用户输入是否可能匹配某个动作
     *
     * @param userInput 用户输入
     * @return 是否可能匹配
     */
    public boolean mayMatch(String userInput) {
        if (!StringUtils.hasText(userInput)) {
            return false;
        }

        Set<String> inputKeywords = extractKeywords(userInput);
        for (String keyword : inputKeywords) {
            if (keywordToActionsIndex.containsKey(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 匹配用户输入，返回候选动作列表
     *
     * @param userInput 用户输入
     * @param limit     最大返回数量
     * @return 匹配结果列表，按匹配分数降序排列
     */
    public List<ActionMatch> match(String userInput, int limit) {
        if (!StringUtils.hasText(userInput)) {
            return Collections.emptyList();
        }

        Set<String> inputKeywords = extractKeywords(userInput);
        String normalizedInput = userInput.toLowerCase().trim();

        // 计算每个动作的匹配分数
        Map<String, Double> actionScores = new HashMap<>();
        Map<String, ActionMatch.MatchType> actionMatchTypes = new HashMap<>();

        for (String keyword : inputKeywords) {
            Set<String> matchedActions = keywordToActionsIndex.get(keyword);
            if (matchedActions != null) {
                for (String actionId : matchedActions) {
                    // 累加分数
                    double score = actionScores.getOrDefault(actionId, 0.0);
                    actionScores.put(actionId, score + 1.0);
                    actionMatchTypes.putIfAbsent(actionId, ActionMatch.MatchType.KEYWORD_FUZZY);
                }
            }
        }

        // 检查精确匹配（输入包含完整的触发关键词）
        for (Map.Entry<String, Set<String>> entry : actionKeywordsMap.entrySet()) {
            String actionId = entry.getKey();
            for (String keyword : entry.getValue()) {
                if (normalizedInput.contains(keyword) && keyword.length() > 1) {
                    double bonus = keyword.length() * 0.5; // 长关键词加分
                    double score = actionScores.getOrDefault(actionId, 0.0);
                    actionScores.put(actionId, score + bonus);

                    // 如果完整匹配关键词，更新匹配类型
                    if (keyword.length() >= 2) {
                        actionMatchTypes.put(actionId, ActionMatch.MatchType.KEYWORD_EXACT);
                    }
                }
            }
        }

        // 转换为 ActionMatch 列表
        List<ActionMatch> results = new ArrayList<>();
        for (Map.Entry<String, Double> entry : actionScores.entrySet()) {
            String actionId = entry.getKey();
            double rawScore = entry.getValue();
            ActionDefinition action = actionDefinitions.get(actionId);

            if (action == null) {
                continue;
            }

            // 归一化分数到 0-1 范围
            // 使用动作关键词数量作为基准
            Set<String> actionKeywords = actionKeywordsMap.get(actionId);
            int totalKeywords = actionKeywords != null ? actionKeywords.size() : 1;
            double confidence = Math.min(1.0, rawScore / Math.max(totalKeywords * 0.5, 3));

            // 优先级加成
            if (action.getPriority() != null && action.getPriority() > 0) {
                confidence = Math.min(1.0, confidence + action.getPriority() * 0.05);
            }

            results.add(ActionMatch.builder()
                    .action(action)
                    .confidence(confidence)
                    .matchType(actionMatchTypes.getOrDefault(actionId, ActionMatch.MatchType.KEYWORD_FUZZY))
                    .explanation("关键词匹配: 得分=" + String.format("%.2f", rawScore))
                    .build());
        }

        // 按置信度降序排序
        results.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));

        // 限制返回数量
        if (results.size() > limit) {
            results = results.subList(0, limit);
        }

        logger.debug("KeywordMatcher#match - input={}, candidates={}", userInput, results.size());
        return results;
    }

    /**
     * 从文本中提取关键词
     *
     * @param text 文本
     * @return 关键词集合
     */
    private Set<String> extractKeywords(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptySet();
        }

        Set<String> keywords = new HashSet<>();
        String normalized = text.toLowerCase().trim();

        // 1. 按空格、标点分词（使用 Unicode 编码避免编码问题）
        // 匹配：空白字符、英文标点、中文标点
        String[] tokens = normalized.split("[\\s,\\u3002\\uff0c\\uff01\\uff1f\\u3001\\uff1b\\uff1a\\u201c\\u201d\\u2018\\u2019\\uff08\\uff09()\\[\\]{}]+");
        for (String token : tokens) {
            if (token.length() >= 2) {
                keywords.add(token);
            }
        }

        // 2. 提取中文词语（简单的 N-gram）
        StringBuilder chineseChars = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (isChineseChar(c)) {
                chineseChars.append(c);
            } else {
                if (chineseChars.length() >= 2) {
                    // 添加连续中文字符
                    String chinese = chineseChars.toString();
                    keywords.add(chinese);
                    // 添加2-gram
                    for (int i = 0; i < chinese.length() - 1; i++) {
                        keywords.add(chinese.substring(i, i + 2));
                    }
                    // 添加3-gram
                    for (int i = 0; i < chinese.length() - 2; i++) {
                        keywords.add(chinese.substring(i, i + 3));
                    }
                }
                chineseChars = new StringBuilder();
            }
        }
        // 处理末尾的中文
        if (chineseChars.length() >= 2) {
            String chinese = chineseChars.toString();
            keywords.add(chinese);
            for (int i = 0; i < chinese.length() - 1; i++) {
                keywords.add(chinese.substring(i, i + 2));
            }
            for (int i = 0; i < chinese.length() - 2; i++) {
                keywords.add(chinese.substring(i, i + 3));
            }
        }

        return keywords;
    }

    /**
     * 规范化关键词列表
     */
    private Set<String> normalizeKeywords(List<String> keywords) {
        return keywords.stream()
                .filter(StringUtils::hasText)
                .map(k -> k.toLowerCase().trim())
                .collect(Collectors.toSet());
    }

    /**
     * 判断是否为中文字符
     */
    private boolean isChineseChar(char c) {
        return c >= 0x4E00 && c <= 0x9FA5;
    }

    /**
     * 获取已注册的动作数量
     */
    public int getRegisteredActionCount() {
        return actionDefinitions.size();
    }

    /**
     * 获取关键词索引大小
     */
    public int getKeywordIndexSize() {
        return keywordToActionsIndex.size();
    }
}
