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
package com.alibaba.assistant.agent.planning.web.dto;

import com.alibaba.assistant.agent.planning.model.ActionMatch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 动作匹配响应
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionMatchResponse {

    /**
     * 匹配结果列表
     */
    private List<MatchResult> matches;

    /**
     * 用户输入
     */
    private String userInput;

    /**
     * 匹配数量
     */
    private int matchCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchResult {
        private String actionId;
        private String actionName;
        private String description;
        private String category;
        private double confidence;
        private String matchType;

        public static MatchResult from(ActionMatch match) {
            return MatchResult.builder()
                    .actionId(match.getAction().getActionId())
                    .actionName(match.getAction().getActionName())
                    .description(match.getAction().getDescription())
                    .category(match.getAction().getCategory())
                    .confidence(match.getConfidence())
                    .matchType(match.getMatchType() != null ? match.getMatchType().name() : null)
                    .build();
        }
    }

    public static ActionMatchResponse from(List<ActionMatch> matches, String userInput) {
        List<MatchResult> results = matches.stream()
                .map(MatchResult::from)
                .collect(Collectors.toList());

        return ActionMatchResponse.builder()
                .matches(results)
                .userInput(userInput)
                .matchCount(results.size())
                .build();
    }
}
