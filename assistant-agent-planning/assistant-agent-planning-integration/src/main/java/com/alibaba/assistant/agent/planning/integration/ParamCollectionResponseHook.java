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
package com.alibaba.assistant.agent.planning.integration;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 参数收集响应 Hook
 *
 * <p>在评估阶段后检查是否有 nextQuestion 需要返回给用户。
 * 如果 LLM 验证结果中包含 nextQuestion（表示有缺失的必填参数），
 * 则自动将问题作为 AssistantMessage 返回给用户，并结束当前 Agent 轮次。
 *
 * <p>工作流程：
 * <ol>
 * <li>在 BEFORE_MODEL 阶段读取评估结果中的 action_intent_llm_verify 结果</li>
 * <li>解析 JSON 检查是否有 nextQuestion 字段</li>
 * <li>如果有，创建 AssistantMessage 并设置 jump_to=end</li>
 * <li>如果没有，不做任何处理，继续正常流程</li>
 * </ol>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@HookPositions(HookPosition.BEFORE_MODEL)
public class ParamCollectionResponseHook extends AgentHook {

    private static final Logger log = LoggerFactory.getLogger(ParamCollectionResponseHook.class);

    private static final String CRITERION_NAME = "action_intent_llm_verify";
    private static final String EVALUATION_ROOT = "evaluation";

    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public ParamCollectionResponseHook(boolean enabled) {
        this.enabled = enabled;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "ParamCollectionResponseHook";
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return List.of(JumpTo.end);
    }

    @Override
    public Map<String, KeyStrategy> getKeyStrategys() {
        return Map.of("jump_to", new ReplaceStrategy());
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        if (!enabled) {
            return CompletableFuture.completedFuture(Map.of());
        }

        log.debug("ParamCollectionResponseHook#beforeModel - reason=checking for nextQuestion");

        try {
            // 从 state 中获取评估结果
            Optional<Map<String, Object>> evalRootOpt = state.value(EVALUATION_ROOT);
            if (evalRootOpt.isEmpty()) {
                log.debug("ParamCollectionResponseHook#beforeModel - reason=no evaluation results found");
                return CompletableFuture.completedFuture(Map.of());
            }

            Map<String, Object> evalRoot = evalRootOpt.get();

            // 查找 input_routing 结果（React 阶段的评估结果存储在这里）
            Object inputRoutingObj = evalRoot.get("input_routing");
            if (inputRoutingObj == null) {
                // 也尝试查找 inputRouting（驼峰命名）
                inputRoutingObj = evalRoot.get("inputRouting");
            }
            if (inputRoutingObj == null) {
                log.debug("ParamCollectionResponseHook#beforeModel - reason=no input_routing results");
                return CompletableFuture.completedFuture(Map.of());
            }

            Map<String, Object> inputRouting = (Map<String, Object>) inputRoutingObj;
            Map<String, Object> criteriaResults = (Map<String, Object>) inputRouting.get("criteriaResults");

            if (criteriaResults == null) {
                return CompletableFuture.completedFuture(Map.of());
            }

            // 查找 action_intent_llm_verify 结果
            Object llmVerifyObj = criteriaResults.get(CRITERION_NAME);
            if (llmVerifyObj == null) {
                log.debug("ParamCollectionResponseHook#beforeModel - reason=no {} result", CRITERION_NAME);
                return CompletableFuture.completedFuture(Map.of());
            }

            Map<String, Object> llmVerifyResult = (Map<String, Object>) llmVerifyObj;
            Object valueObj = llmVerifyResult.get("value");

            if (valueObj == null) {
                return CompletableFuture.completedFuture(Map.of());
            }

            // 解析 JSON 结果
            String valueStr = valueObj.toString();
            String nextQuestion = extractNextQuestion(valueStr);

            if (nextQuestion == null || nextQuestion.isEmpty() || "null".equals(nextQuestion)) {
                log.debug("ParamCollectionResponseHook#beforeModel - reason=no nextQuestion in result");
                return CompletableFuture.completedFuture(Map.of());
            }

            // 有 nextQuestion，创建 AssistantMessage 返回给用户
            log.info("ParamCollectionResponseHook#beforeModel - reason=found nextQuestion, returning to user, question={}",
                    nextQuestion);

            // 创建带有问题的 AssistantMessage
            AssistantMessage assistantMessage = new AssistantMessage(nextQuestion);

            // 获取额外信息用于状态保存
            String actionId = extractField(valueStr, "actionId");
            String actionName = extractField(valueStr, "actionName");

            Map<String, Object> paramCollectionState = Map.of(
                    "active", true,
                    "actionId", actionId != null ? actionId : "",
                    "actionName", actionName != null ? actionName : "",
                    "nextQuestion", nextQuestion,
                    "awaitingInput", true
            );

            return CompletableFuture.completedFuture(Map.of(
                    "messages", List.of(assistantMessage),
                    "jump_to", JumpTo.end,
                    "param_collection", paramCollectionState
            ));

        } catch (Exception e) {
            log.error("ParamCollectionResponseHook#beforeModel - reason=failed to check nextQuestion", e);
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    /**
     * 从 LLM 返回的 JSON 中提取 nextQuestion
     */
    private String extractNextQuestion(String jsonStr) {
        try {
            // 尝试从 RESULT: {...} 格式中提取 JSON
            String json = jsonStr;
            if (json.contains("RESULT:")) {
                int startIdx = json.indexOf("{");
                int endIdx = json.lastIndexOf("}");
                if (startIdx >= 0 && endIdx > startIdx) {
                    json = json.substring(startIdx, endIdx + 1);
                }
            }

            JsonNode node = objectMapper.readTree(json);
            JsonNode nextQuestionNode = node.get("nextQuestion");

            if (nextQuestionNode != null && !nextQuestionNode.isNull()) {
                return nextQuestionNode.asText();
            }
        } catch (Exception e) {
            log.debug("ParamCollectionResponseHook#extractNextQuestion - reason=failed to parse JSON: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从 JSON 中提取指定字段
     */
    private String extractField(String jsonStr, String fieldName) {
        try {
            String json = jsonStr;
            if (json.contains("RESULT:")) {
                int startIdx = json.indexOf("{");
                int endIdx = json.lastIndexOf("}");
                if (startIdx >= 0 && endIdx > startIdx) {
                    json = json.substring(startIdx, endIdx + 1);
                }
            }

            JsonNode node = objectMapper.readTree(json);
            JsonNode fieldNode = node.get(fieldName);

            if (fieldNode != null && !fieldNode.isNull()) {
                return fieldNode.asText();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
