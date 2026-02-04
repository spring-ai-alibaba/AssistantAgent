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
package com.alibaba.assistant.agent.extension.evaluation.hook;

import com.alibaba.assistant.agent.common.hook.AgentPhase;
import com.alibaba.assistant.agent.common.hook.HookPhases;
import com.alibaba.assistant.agent.extension.evaluation.config.CodeactEvaluationContextFactory;
import com.alibaba.assistant.agent.evaluation.EvaluationService;
import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;

import java.util.Map;

/**
 * CodeAct 阶段的 BEFORE_MODEL 评估 Hook
 * 
 * <p>在 CodeAct Agent（代码生成子 Agent）的模型调用前进行评估，
 * 关注代码生成任务的增强与评估。
 * 
 * <p>通过 {@code @HookPhases(AgentPhase.CODEACT)} 注解声明阶段，
 * 基类会自动从注解中读取阶段信息。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 * @see BeforeModelEvaluationHook
 */
@HookPhases(AgentPhase.CODEACT)
public class CodeactBeforeModelEvaluationHook extends BeforeModelEvaluationHook {

    public CodeactBeforeModelEvaluationHook(
            EvaluationService evaluationService,
            CodeactEvaluationContextFactory contextFactory,
            String suiteId) {
        super(evaluationService, contextFactory, suiteId);
    }

    public CodeactBeforeModelEvaluationHook(
            EvaluationService evaluationService,
            CodeactEvaluationContextFactory contextFactory,
            String suiteId,
            int order) {
        super(evaluationService, contextFactory, suiteId, order);
    }

    @Override
    protected EvaluationContext createEvaluationContext(OverAllState state, RunnableConfig config) {
        // CodeAct 阶段关注代码生成任务
        String codeTaskDescription = extractCodeTaskDescription(state);
        String targetLanguage = extractTargetLanguage(state);

        return getContextFactory().createCodeGenerationInputContext(
                state, codeTaskDescription, targetLanguage, Map.of());
    }

    private String extractCodeTaskDescription(OverAllState state) {
        // 尝试从 state 中获取代码任务描述
        return state.value("requirement", String.class).orElse("");
    }

    private String extractTargetLanguage(OverAllState state) {
        return state.value("language", String.class).orElse("python");
    }
}
