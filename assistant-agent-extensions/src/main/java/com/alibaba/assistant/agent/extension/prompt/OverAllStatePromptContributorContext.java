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
package com.alibaba.assistant.agent.extension.prompt;

import com.alibaba.assistant.agent.extension.evaluation.store.OverAllStateEvaluationResultStore;
import com.alibaba.assistant.agent.prompt.PromptContributorContext;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 OverAllState 的 PromptContributorContext 实现
 * 将 OverAllState 中的数据暴露给 PromptContributor
 *
 * @author Assistant Agent Team
 */
public class OverAllStatePromptContributorContext implements PromptContributorContext {

    /**
     * 上下文属性 key：评估结果存储
     */
    public static final String ATTR_EVALUATION_STORE = "evaluationResultStore";

    private final OverAllState state;
    private final SystemMessage systemMessage;
    private final String phase;
    private final Map<String, Object> extraAttributes;

    public OverAllStatePromptContributorContext(OverAllState state,
                                                 SystemMessage systemMessage,
                                                 String phase) {
        this(state, systemMessage, phase, Map.of());
    }

    public OverAllStatePromptContributorContext(OverAllState state,
                                                 SystemMessage systemMessage,
                                                 String phase,
                                                 Map<String, Object> extraAttributes) {
        this.state = state;
        this.systemMessage = systemMessage;
        this.phase = phase;
        this.extraAttributes = extraAttributes != null ? extraAttributes : Map.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Message> getMessages() {
        return state.value("messages", List.class).orElse(Collections.emptyList());
    }

    @Override
    public Optional<SystemMessage> getSystemMessage() {
        return Optional.ofNullable(systemMessage);
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attrs = new HashMap<>(state.data());
        attrs.putAll(extraAttributes);
        // 将 EvaluationResultStore 放入属性，供 PromptContributor 使用
        attrs.put(ATTR_EVALUATION_STORE, new OverAllStateEvaluationResultStore(state));
        return Collections.unmodifiableMap(attrs);
    }

    @Override
    public Optional<String> getPhase() {
        return Optional.ofNullable(phase);
    }

    /**
     * 获取关联的 OverAllState
     *
     * @return OverAllState
     */
    public OverAllState getState() {
        return state;
    }
}

