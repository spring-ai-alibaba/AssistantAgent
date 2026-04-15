package com.alibaba.assistant.agent.extension.experience.model;

import org.springframework.util.StringUtils;

/**
 * 经验查询上下文
 * Hook基于OverAllState/RunnableConfig构造的上下文信息
 *
 * @author Assistant Agent Team
 */
public class ExperienceQueryContext {

    /**
     * 用户查询内容（用于经验检索）
     */
    private String userQuery;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 用户ID
     */
    private String userId;

    public ExperienceQueryContext() {
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = StringUtils.hasText(sessionId) ? sessionId.trim() : null;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = StringUtils.hasText(tenantId) ? tenantId.trim() : null;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = StringUtils.hasText(userId) ? userId.trim() : null;
    }
}
