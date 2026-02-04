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
package com.alibaba.assistant.agent.evaluation.store;

import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.assistant.agent.evaluation.model.EvaluationResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 评估结果存储接口
 * 统一定义评估结果的存取标准，同时提供便捷的访问方法
 *
 * @author Assistant Agent Team
 */
public interface EvaluationResultStore {

    // ===== 存储操作 =====

    /**
     * 存储评估结果
     *
     * @param key    存储的 key（如 suiteId 或自定义标识）
     * @param result 评估结果
     */
    void store(String key, EvaluationResult result);

    /**
     * 获取指定 key 的评估结果
     *
     * @param key 存储的 key
     * @return 评估结果
     */
    Optional<EvaluationResult> get(String key);

    /**
     * 获取所有评估结果
     *
     * @return 所有评估结果列表
     */
    List<EvaluationResult> getAll();

    /**
     * 清除所有评估结果
     */
    void clear();

    // ===== 便捷访问方法（默认实现） =====

    /**
     * 获取指定评估套件中特定指标的值
     *
     * @param suiteId       评估套件 ID
     * @param criterionName 指标名称
     * @return 指标值
     */
    default Optional<Object> getCriterionValue(String suiteId, String criterionName) {
        return get(suiteId)
                .map(r -> r.getCriterionResult(criterionName))
                .map(CriterionResult::getValue);
    }

    /**
     * 获取指定评估套件中特定指标的完整结果
     *
     * @param suiteId       评估套件 ID
     * @param criterionName 指标名称
     * @return 指标结果
     */
    default Optional<CriterionResult> getCriterionResult(String suiteId, String criterionName) {
        return get(suiteId)
                .map(r -> r.getCriterionResult(criterionName));
    }

    /**
     * 检查指定指标是否满足条件
     *
     * @param suiteId       评估套件 ID
     * @param criterionName 指标名称
     * @param expectedValue 期望值
     * @return 是否满足条件
     */
    default boolean checkCriterion(String suiteId, String criterionName, Object expectedValue) {
        return getCriterionValue(suiteId, criterionName)
                .map(v -> Objects.equals(v, expectedValue))
                .orElse(false);
    }
}

