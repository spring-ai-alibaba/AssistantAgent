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
package com.alibaba.assistant.agent.planning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 动作匹配结果
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionMatch {

    /**
     * 匹配的动作定义
     */
    private ActionDefinition action;

    /**
     * 匹配置信度（0-1）
     */
    private Double confidence;

    /**
     * 匹配方式
     */
    private MatchType matchType;

    /**
     * 从用户输入中提取的参数
     */
    private Map<String, Object> extractedParameters;

    /**
     * 缺失的必填参数
     */
    private Map<String, ActionParameter> missingParameters;

    /**
     * 匹配解释
     */
    private String explanation;

    /**
     * 匹配类型枚举
     */
    public enum MatchType {
        /**
         * 精确匹配关键词
         */
        KEYWORD_EXACT,

        /**
         * 模糊匹配关键词
         */
        KEYWORD_FUZZY,

        /**
         * 语义匹配（向量相似度）
         */
        SEMANTIC,

        /**
         * LLM 推理匹配
         */
        LLM_INFERENCE,

        /**
         * 示例匹配
         */
        EXAMPLE_MATCH
    }

    /**
     * 判断是否为高置信度匹配
     */
    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.8;
    }

    /**
     * 判断是否有缺失参数
     */
    public boolean hasMissingParameters() {
        return missingParameters != null && !missingParameters.isEmpty();
    }
}
