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
package com.alibaba.assistant.agent.extension.evaluation.store;

import com.alibaba.assistant.agent.evaluation.model.EvaluationResult;
import com.alibaba.assistant.agent.evaluation.store.EvaluationResultStore;
import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 OverAllState 的评估结果存储实现
 * 将评估结果存储在 OverAllState 的专用字段中
 *
 * @author Assistant Agent Team
 */
public class OverAllStateEvaluationResultStore implements EvaluationResultStore {

    /**
     * 评估结果在 OverAllState 中的存储 key
     */
    public static final String EVALUATION_RESULTS_KEY = "__evaluation_results__";

    private final OverAllState state;

    public OverAllStateEvaluationResultStore(OverAllState state) {
        this.state = state;
    }

    @Override
    public void store(String key, EvaluationResult result) {
        // 在 Hook 场景下，OverAllState 的修改必须通过 Hook 的返回值来实现
        // 直接调用此方法无法正确更新 state
        // 请使用 createUpdateMap() 方法生成更新 Map，然后通过 Hook 返回值更新 state
        throw new UnsupportedOperationException(
                "Direct store() is not supported in Hook scenario. " +
                "Use createUpdateMap() instead and return the result from Hook.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<EvaluationResult> get(String key) {
        Map<String, EvaluationResult> results = getResultsMap();
        return Optional.ofNullable(results.get(key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<EvaluationResult> getAll() {
        Map<String, EvaluationResult> results = getResultsMap();
        return new ArrayList<>(results.values());
    }

    @Override
    public void clear() {
        // 在 Hook 场景下，OverAllState 的修改必须通过 Hook 的返回值来实现
        // 直接调用此方法无法正确清除 state
        throw new UnsupportedOperationException(
                "Direct clear() is not supported in Hook scenario. " +
                "Manage state clearing through Hook return values.");
    }

    /**
     * 获取评估结果 Map
     *
     * @return 评估结果 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, EvaluationResult> getResultsMap() {
        Optional<Object> opt = state.value(EVALUATION_RESULTS_KEY);
        if (opt.isPresent() && opt.get() instanceof Map) {
            return (Map<String, EvaluationResult>) opt.get();
        }
        return new HashMap<>();
    }

    /**
     * 创建要更新到 state 的 Map
     * 由 Hook 调用此方法，然后返回给框架更新 state
     *
     * @param key    存储的 key
     * @param result 评估结果
     * @return 用于更新 state 的 Map
     */
    public Map<String, Object> createUpdateMap(String key, EvaluationResult result) {
        Map<String, EvaluationResult> results = new HashMap<>(getResultsMap());
        results.put(key, result);
        return Map.of(EVALUATION_RESULTS_KEY, results);
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

