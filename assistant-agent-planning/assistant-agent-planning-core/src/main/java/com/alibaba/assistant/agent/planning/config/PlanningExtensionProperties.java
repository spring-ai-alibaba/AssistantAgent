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
package com.alibaba.assistant.agent.planning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Planning 扩展配置属性
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "spring.ai.alibaba.codeact.extension.planning")
public class PlanningExtensionProperties {

    /**
     * 是否启用 Planning 扩展
     */
    private boolean enabled = true;

    /**
     * 是否启用 plan_action 工具
     */
    private boolean planActionEnabled = true;

    /**
     * 是否启用 execute_action 工具
     */
    private boolean executeActionEnabled = true;

    /**
     * 是否启用 list_actions 工具
     */
    private boolean listActionsEnabled = true;

    /**
     * 是否启用 get_action_details 工具
     */
    private boolean getActionDetailsEnabled = true;

    /**
     * 是否启用 Web API 接口
     */
    private boolean webEnabled = true;

    /**
     * 默认执行超时时间（毫秒）
     */
    private long defaultTimeoutMs = 30000;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 内存存储配置
     */
    private InMemoryConfig inMemory = new InMemoryConfig();

    /**
     * MySQL 持久化配置
     */
    private MysqlConfig mysql = new MysqlConfig();

    /**
     * Elasticsearch 配置
     */
    private ElasticsearchConfig elasticsearch = new ElasticsearchConfig();

    /**
     * 匹配配置
     */
    private MatchingConfig matching = new MatchingConfig();

    /**
     * 意图识别配置
     */
    private IntentConfig intent = new IntentConfig();

    /**
     * 评估集成配置
     */
    private EvaluationConfig evaluation = new EvaluationConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPlanActionEnabled() {
        return planActionEnabled;
    }

    public void setPlanActionEnabled(boolean planActionEnabled) {
        this.planActionEnabled = planActionEnabled;
    }

    public boolean isExecuteActionEnabled() {
        return executeActionEnabled;
    }

    public void setExecuteActionEnabled(boolean executeActionEnabled) {
        this.executeActionEnabled = executeActionEnabled;
    }

    public boolean isListActionsEnabled() {
        return listActionsEnabled;
    }

    public void setListActionsEnabled(boolean listActionsEnabled) {
        this.listActionsEnabled = listActionsEnabled;
    }

    public boolean isGetActionDetailsEnabled() {
        return getActionDetailsEnabled;
    }

    public void setGetActionDetailsEnabled(boolean getActionDetailsEnabled) {
        this.getActionDetailsEnabled = getActionDetailsEnabled;
    }

    public boolean isWebEnabled() {
        return webEnabled;
    }

    public void setWebEnabled(boolean webEnabled) {
        this.webEnabled = webEnabled;
    }

    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public void setDefaultTimeoutMs(long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public InMemoryConfig getInMemory() {
        return inMemory;
    }

    public void setInMemory(InMemoryConfig inMemory) {
        this.inMemory = inMemory;
    }

    public MatchingConfig getMatching() {
        return matching;
    }

    public void setMatching(MatchingConfig matching) {
        this.matching = matching;
    }

    public MysqlConfig getMysql() {
        return mysql;
    }

    public void setMysql(MysqlConfig mysql) {
        this.mysql = mysql;
    }

    public ElasticsearchConfig getElasticsearch() {
        return elasticsearch;
    }

    public void setElasticsearch(ElasticsearchConfig elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    public IntentConfig getIntent() {
        return intent;
    }

    public void setIntent(IntentConfig intent) {
        this.intent = intent;
    }

    public EvaluationConfig getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(EvaluationConfig evaluation) {
        this.evaluation = evaluation;
    }

    /**
     * 内存存储配置
     */
    public static class InMemoryConfig {
        /**
         * 是否启用内存存储（当 MySQL 不可用时作为后备）
         */
        private boolean enabled = false;

        /**
         * 最大动作数量
         */
        private int maxActions = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxActions() {
            return maxActions;
        }

        public void setMaxActions(int maxActions) {
            this.maxActions = maxActions;
        }
    }

    /**
     * 匹配配置
     */
    public static class MatchingConfig {
        /**
         * 匹配阈值（0-1）
         */
        private double threshold = 0.7;

        /**
         * 是否启用语义匹配
         */
        private boolean semanticMatchingEnabled = false;

        /**
         * 最大匹配结果数量
         */
        private int maxMatches = 5;

        /**
         * 是否启用参数收集流程
         */
        private boolean paramCollectionEnabled = false;

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }

        public boolean isSemanticMatchingEnabled() {
            return semanticMatchingEnabled;
        }

        public void setSemanticMatchingEnabled(boolean semanticMatchingEnabled) {
            this.semanticMatchingEnabled = semanticMatchingEnabled;
        }

        public int getMaxMatches() {
            return maxMatches;
        }

        public void setMaxMatches(int maxMatches) {
            this.maxMatches = maxMatches;
        }

        public boolean isParamCollectionEnabled() {
            return paramCollectionEnabled;
        }

        public void setParamCollectionEnabled(boolean paramCollectionEnabled) {
            this.paramCollectionEnabled = paramCollectionEnabled;
        }
    }

    /**
     * MySQL 持久化配置
     */
    public static class MysqlConfig {
        /**
         * 是否启用 MySQL 存储（默认启用）
         */
        private boolean enabled = true;

        /**
         * 表名前缀
         */
        private String tablePrefix = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTablePrefix() {
            return tablePrefix;
        }

        public void setTablePrefix(String tablePrefix) {
            this.tablePrefix = tablePrefix;
        }
    }

    /**
     * Elasticsearch 配置
     */
    public static class ElasticsearchConfig {
        /**
         * 是否启用 ES 向量搜索
         */
        private boolean enabled = false;

        /**
         * ES 索引名称
         */
        private String indexName = "action_vectors";

        /**
         * 向量维度
         */
        private int vectorDimension = 1024;

        /**
         * 语义权重（0-1）
         */
        private double semanticWeight = 0.6;

        /**
         * 关键词权重（0-1）
         */
        private double keywordWeight = 0.4;

        /**
         * 匹配阈值
         */
        private double matchThreshold = 0.5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public int getVectorDimension() {
            return vectorDimension;
        }

        public void setVectorDimension(int vectorDimension) {
            this.vectorDimension = vectorDimension;
        }

        public double getSemanticWeight() {
            return semanticWeight;
        }

        public void setSemanticWeight(double semanticWeight) {
            this.semanticWeight = semanticWeight;
        }

        public double getKeywordWeight() {
            return keywordWeight;
        }

        public void setKeywordWeight(double keywordWeight) {
            this.keywordWeight = keywordWeight;
        }

        public double getMatchThreshold() {
            return matchThreshold;
        }

        public void setMatchThreshold(double matchThreshold) {
            this.matchThreshold = matchThreshold;
        }
    }

    /**
     * 意图识别配置
     */
    public static class IntentConfig {
        /**
         * 是否启用意图识别 Hook
         */
        private boolean enabled = true;

        /**
         * 直接执行阈值（置信度 >= 此值时直接执行，跳过 LLM）
         */
        private double directExecuteThreshold = 0.95;

        /**
         * 提示注入阈值（置信度 >= 此值时注入提示让 LLM 决策）
         */
        private double hintThreshold = 0.7;

        /**
         * 最大匹配候选数
         */
        private int maxCandidates = 5;

        /**
         * 是否启用关键词预过滤
         */
        private boolean keywordFilterEnabled = true;

        /**
         * 响应模板（直接执行成功时）
         */
        private String successTemplate = "已为您执行操作「{actionName}」。";

        /**
         * 响应模板（直接执行失败时）
         */
        private String failureTemplate = "执行操作「{actionName}」时遇到问题：{errorMessage}";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getDirectExecuteThreshold() {
            return directExecuteThreshold;
        }

        public void setDirectExecuteThreshold(double directExecuteThreshold) {
            this.directExecuteThreshold = directExecuteThreshold;
        }

        public double getHintThreshold() {
            return hintThreshold;
        }

        public void setHintThreshold(double hintThreshold) {
            this.hintThreshold = hintThreshold;
        }

        public int getMaxCandidates() {
            return maxCandidates;
        }

        public void setMaxCandidates(int maxCandidates) {
            this.maxCandidates = maxCandidates;
        }

        public boolean isKeywordFilterEnabled() {
            return keywordFilterEnabled;
        }

        public void setKeywordFilterEnabled(boolean keywordFilterEnabled) {
            this.keywordFilterEnabled = keywordFilterEnabled;
        }

        public String getSuccessTemplate() {
            return successTemplate;
        }

        public void setSuccessTemplate(String successTemplate) {
            this.successTemplate = successTemplate;
        }

        public String getFailureTemplate() {
            return failureTemplate;
        }

        public void setFailureTemplate(String failureTemplate) {
            this.failureTemplate = failureTemplate;
        }
    }

    /**
     * 评估集成配置
     */
    public static class EvaluationConfig {
        /**
         * 是否启用评估集成
         */
        private boolean enabled = true;

        /**
         * 是否启用参数收集流程
         */
        private boolean paramCollectionEnabled = false;

        /**
         * 是否启用 LLM 二次验证
         * 启用后，会在关键词+向量匹配后，使用 LLM 对候选结果进行精确判断
         */
        private boolean llmVerificationEnabled = false;

        /**
         * LLM 验证的最大候选数量
         */
        private int llmVerificationMaxCandidates = 5;

        /**
         * 参数收集会话超时时间（分钟）
         */
        private int sessionTimeoutMinutes = 60;

        /**
         * 最小匹配置信度（低于此值不触发参数收集）
         */
        private double minMatchConfidence = 0.5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isParamCollectionEnabled() {
            return paramCollectionEnabled;
        }

        public void setParamCollectionEnabled(boolean paramCollectionEnabled) {
            this.paramCollectionEnabled = paramCollectionEnabled;
        }

        public boolean isLlmVerificationEnabled() {
            return llmVerificationEnabled;
        }

        public void setLlmVerificationEnabled(boolean llmVerificationEnabled) {
            this.llmVerificationEnabled = llmVerificationEnabled;
        }

        public int getLlmVerificationMaxCandidates() {
            return llmVerificationMaxCandidates;
        }

        public void setLlmVerificationMaxCandidates(int llmVerificationMaxCandidates) {
            this.llmVerificationMaxCandidates = llmVerificationMaxCandidates;
        }

        public int getSessionTimeoutMinutes() {
            return sessionTimeoutMinutes;
        }

        public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) {
            this.sessionTimeoutMinutes = sessionTimeoutMinutes;
        }

        public double getMinMatchConfidence() {
            return minMatchConfidence;
        }

        public void setMinMatchConfidence(double minMatchConfidence) {
            this.minMatchConfidence = minMatchConfidence;
        }
    }
}
