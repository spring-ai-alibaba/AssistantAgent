package com.alibaba.assistant.agent.extension.experience.model;

import java.util.Set;

/**
 * 经验查询条件
 *
 * @author Assistant Agent Team
 */
public class ExperienceQuery {

    /**
     * 经验类型
     */
    private ExperienceType type;

    /**
     * 标签过滤
     */
    private Set<String> tags;

    /**
     * 模糊搜索文本
     */
    private String text;

    /**
     * 最多返回多少条，默认5条
     */
    private int limit = 5;

    /**
     * 经验候选召回策略。
     */
    private RetrievalMode retrievalMode = RetrievalMode.DEFAULT;

    /**
     * 排序方式
     */
    private OrderBy orderBy = OrderBy.UPDATED_AT;

    /**
     * 披露策略过滤
     */
    private DisclosureStrategy disclosureStrategy;

    /**
     * 排序枚举
     */
    public enum OrderBy {
        CREATED_AT,
        UPDATED_AT,
        SCORE
    }

    /**
     * 候选召回策略。
     */
    public enum RetrievalMode {
        /**
         * 默认策略，由 Provider 自行决定是否走向量召回或普通查询。
         */
        DEFAULT,
        /**
         * 全量扫描候选集，适用于 FastIntent 等需要穷举匹配的场景。
         */
        FULL_SCAN
    }

    public ExperienceQuery() {
    }

    public ExperienceQuery(ExperienceType type) {
        this.type = type;
    }

    public ExperienceType getType() {
        return type;
    }

    public void setType(ExperienceType type) {
        this.type = type;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public RetrievalMode getRetrievalMode() {
        return retrievalMode;
    }

    public void setRetrievalMode(RetrievalMode retrievalMode) {
        this.retrievalMode = retrievalMode;
    }

    public OrderBy getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(OrderBy orderBy) {
        this.orderBy = orderBy;
    }

    public DisclosureStrategy getDisclosureStrategy() {
        return disclosureStrategy;
    }

    public void setDisclosureStrategy(DisclosureStrategy disclosureStrategy) {
        this.disclosureStrategy = disclosureStrategy;
    }
}
