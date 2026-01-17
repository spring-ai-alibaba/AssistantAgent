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
package com.alibaba.assistant.agent.planning.param;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionParameter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 结构化参数提取器
 *
 * <p>使用 LLM 从用户输入中提取结构化的 action 参数。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class StructuredParamExtractor {

    private static final Logger logger = LoggerFactory.getLogger(StructuredParamExtractor.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_EXTRACT_PROMPT = """
            你是一个专业的参数提取助手。请从用户输入中提取指定动作的参数。

            ## 动作信息
            动作名称：{actionName}
            动作描述：{actionDescription}

            ## 参数定义
            {parameterDefinitions}

            ## 用户输入
            用户说：{userInput}

            ## 提取要求
            1. 仅从用户输入中提取明确提到的参数值
            2. 如果参数值未明确提及，不要提取（即使有默认值也不要使用）
            3. 保持原始值的类型（字符串、数字、布尔值等）
            4. 对于枚举类型，确保提取的值在有效值范围内
            5. 返回 JSON 格式，包含已提取的参数和置信度

            ## 返回格式
            ```json
            {
              "extractedParams": {
                "参数名": "参数值",
                ...
              },
              "confidence": {
                "参数名": 置信度(0-1),
                ...
              },
              "reasoning": "提取理由说明"
            }
            ```

            请严格按照 JSON 格式返回，不要包含其他内容。
            """;

    public StructuredParamExtractor(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
    }

    /**
     * 从用户输入中提取参数
     *
     * @param action       动作定义
     * @param userInput    用户输入
     * @param chatHistory  对话历史（可选，用于上下文理解）
     * @return 提取结果
     */
    public ExtractionResult extract(ActionDefinition action, String userInput, List<String> chatHistory) {
        logger.debug("StructuredParamExtractor#extract - actionId={}, userInput={}",
                action.getActionId(), userInput);

        try {
            // 构建参数定义描述
            String paramDefinitions = buildParameterDefinitions(action.getParameters());

            // 构建提示词
            String prompt = buildExtractionPrompt(action, userInput, paramDefinitions);

            // 调用 LLM
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            logger.debug("StructuredParamExtractor#extract - LLM response: {}", response);

            // 解析响应
            return parseExtractionResponse(response, action);

        } catch (Exception e) {
            logger.error("StructuredParamExtractor#extract - extraction failed, actionId={}",
                    action.getActionId(), e);
            return ExtractionResult.builder()
                    .success(false)
                    .errorMessage("参数提取失败：" + e.getMessage())
                    .build();
        }
    }

    /**
     * 构建参数定义描述
     */
    private String buildParameterDefinitions(List<ActionParameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "无参数";
        }

        return parameters.stream()
                .map(param -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("- **").append(param.getName()).append("**");
                    if (StringUtils.hasText(param.getLabel())) {
                        sb.append(" (").append(param.getLabel()).append(")");
                    }
                    sb.append("\n");
                    if (StringUtils.hasText(param.getDescription())) {
                        sb.append("  描述：").append(param.getDescription()).append("\n");
                    }
                    sb.append("  类型：").append(param.getType()).append("\n");
                    sb.append("  必填：").append(param.getRequired() ? "是" : "否").append("\n");
                    if (param.getEnumValues() != null && !param.getEnumValues().isEmpty()) {
                        sb.append("  可选值：").append(param.getEnumValues()).append("\n");
                    }
                    if (StringUtils.hasText(param.getPlaceholder())) {
                        sb.append("  示例：").append(param.getPlaceholder()).append("\n");
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * 构建提取提示词
     */
    private String buildExtractionPrompt(ActionDefinition action, String userInput, String paramDefinitions) {
        return DEFAULT_EXTRACT_PROMPT
                .replace("{actionName}", action.getActionName() != null ? action.getActionName() : "")
                .replace("{actionDescription}",
                        action.getDescription() != null ? action.getDescription() : "无描述")
                .replace("{parameterDefinitions}", paramDefinitions)
                .replace("{userInput}", userInput);
    }

    /**
     * 解析提取响应
     */
    private ExtractionResult parseExtractionResponse(String response, ActionDefinition action) {
        try {
            // 提取 JSON 部分（可能包含 markdown 代码块）
            String jsonContent = extractJsonContent(response);

            // 解析 JSON
            Map<String, Object> result = objectMapper.readValue(jsonContent, new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            Map<String, Object> extractedParams = (Map<String, Object>) result.getOrDefault("extractedParams",
                    new HashMap<>());

            @SuppressWarnings("unchecked")
            Map<String, Object> confidenceMap = (Map<String, Object>) result.getOrDefault("confidence",
                    new HashMap<>());

            String reasoning = (String) result.getOrDefault("reasoning", "");

            // 转换为 ParamValue 列表
            Map<String, ParamValue> params = new HashMap<>();
            for (Map.Entry<String, Object> entry : extractedParams.entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();

                // 获取置信度
                double confidence = 0.8; // 默认置信度
                if (confidenceMap.containsKey(paramName)) {
                    Object confValue = confidenceMap.get(paramName);
                    if (confValue instanceof Number) {
                        confidence = ((Number) confValue).doubleValue();
                    }
                }

                // 查找参数定义
                ActionParameter paramDef = findParameter(action, paramName);
                String paramType = paramDef != null ? paramDef.getType() : "string";

                params.put(paramName, ParamValue.builder()
                        .name(paramName)
                        .value(paramValue)
                        .type(paramType)
                        .confidence(confidence)
                        .source("LLM_EXTRACTED")
                        .build());
            }

            return ExtractionResult.builder()
                    .success(true)
                    .extractedParams(params)
                    .reasoning(reasoning)
                    .rawResponse(response)
                    .build();

        } catch (Exception e) {
            logger.error("StructuredParamExtractor#parseExtractionResponse - parsing failed", e);
            return ExtractionResult.builder()
                    .success(false)
                    .errorMessage("解析 LLM 响应失败：" + e.getMessage())
                    .rawResponse(response)
                    .build();
        }
    }

    /**
     * 从响应中提取 JSON 内容
     */
    private String extractJsonContent(String response) {
        // 移除 markdown 代码块标记
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    /**
     * 查找参数定义
     */
    private ActionParameter findParameter(ActionDefinition action, String paramName) {
        if (action.getParameters() == null) {
            return null;
        }
        return action.getParameters().stream()
                .filter(p -> p.getName().equals(paramName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 参数提取结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ExtractionResult {
        /**
         * 是否提取成功
         */
        private boolean success;

        /**
         * 提取的参数（参数名 -> 参数值）
         */
        @lombok.Builder.Default
        private Map<String, ParamValue> extractedParams = new HashMap<>();

        /**
         * 提取理由
         */
        private String reasoning;

        /**
         * 原始响应
         */
        private String rawResponse;

        /**
         * 错误信息
         */
        private String errorMessage;

        /**
         * 获取提取的参数值映射
         */
        public Map<String, Object> getParamValueMap() {
            Map<String, Object> result = new HashMap<>();
            extractedParams.forEach((name, paramValue) -> result.put(name, paramValue.getValue()));
            return result;
        }

        /**
         * 是否有提取到参数
         */
        public boolean hasExtractedParams() {
            return !extractedParams.isEmpty();
        }
    }

    /**
     * 参数值
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ParamValue {
        /**
         * 参数名
         */
        private String name;

        /**
         * 参数值
         */
        private Object value;

        /**
         * 参数类型
         */
        private String type;

        /**
         * 置信度（0-1）
         */
        @lombok.Builder.Default
        private double confidence = 1.0;

        /**
         * 来源（LLM_EXTRACTED, USER_INPUT, DEFAULT）
         */
        @lombok.Builder.Default
        private String source = "LLM_EXTRACTED";
    }
}
