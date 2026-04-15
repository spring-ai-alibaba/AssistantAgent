package com.alibaba.assistant.agent.evaluation.util;

import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.cloud.ai.graph.RunnableConfig;

/**
 * 评估链路日志上下文提取工具。
 */
public final class EvaluationLogContextHelper {

    private EvaluationLogContextHelper() {
    }

    public static String getSessionId(EvaluationContext context) {
        if (context == null) {
            return null;
        }
        Object sessionId = context.getEnvironmentValue("sessionId");
        if (sessionId == null) {
            sessionId = context.getEnvironmentValue("threadId");
        }
        return sessionId != null ? String.valueOf(sessionId) : null;
    }

    public static String getSessionId(RunnableConfig config) {
        if (config == null) {
            return null;
        }
        return config.threadId().orElse(null);
    }
}
