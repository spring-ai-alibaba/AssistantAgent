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
package com.alibaba.assistant.agent.core.observation.context;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * React 阶段观测上下文
 * <p>
 * 存储 React 阶段（LlmNode 和 ToolNode）执行过程中的观测数据。
 * <p>
 * 已从 Micrometer Observation.Context 迁移到 OpenTelemetry 原生 API。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ReactPhaseObservationContext {

    // 标准属性键定义
    public static final AttributeKey<String> SESSION_ID_KEY = AttributeKey.stringKey("gen_ai.conversation.id");
    public static final AttributeKey<String> AGENT_NAME_KEY = AttributeKey.stringKey("gen_ai.agent.name");
    public static final AttributeKey<Long> ITERATION_KEY = AttributeKey.longKey("codeact.react.iteration");
    public static final AttributeKey<String> NODE_TYPE_KEY = AttributeKey.stringKey("codeact.react.node_type");
    public static final AttributeKey<String> NODE_ID_KEY = AttributeKey.stringKey("codeact.react.node_id");
    public static final AttributeKey<Long> DURATION_MS_KEY = AttributeKey.longKey("duration.ms");
    public static final AttributeKey<Boolean> SUCCESS_KEY = AttributeKey.booleanKey("codeact.react.success");
    public static final AttributeKey<String> MODEL_NAME_KEY = AttributeKey.stringKey("gen_ai.response.model");
    public static final AttributeKey<Long> INPUT_TOKENS_KEY = AttributeKey.longKey("gen_ai.usage.input_tokens");
    public static final AttributeKey<Long> OUTPUT_TOKENS_KEY = AttributeKey.longKey("gen_ai.usage.output_tokens");
    public static final AttributeKey<String> FINISH_REASON_KEY = AttributeKey.stringKey("gen_ai.response.finish_reasons");
    public static final AttributeKey<Long> TOOL_CALLS_COUNT_KEY = AttributeKey.longKey("codeact.react.tool_calls_count");

    /**
     * Node 类型枚举
     */
    public enum NodeType {
        LLM,
        TOOL,
        UNKNOWN
    }

    private String sessionId;
    private String agentName;
    private int iteration;
    private NodeType nodeType;
    private String nodeId;
    private long durationMs;
    private boolean success = true;
    private String errorType;
    private String errorMessage;

    // LlmNode specific fields
    private String modelName;
    private int inputTokens;
    private int outputTokens;
    private int promptMessageCount;
    private int responseMessageCount;
    private String finishReason;

    /**
     * LLM 可调用的工具名称列表
     */
    private List<String> availableToolNames = new ArrayList<>();

    /**
     * LLM 输入消息内容（摘要）
     */
    private String inputSummary;

    /**
     * LLM 输出消息内容（摘要）
     */
    private String outputSummary;

    // ToolNode specific fields
    private List<ToolCallInfo> toolCalls = new ArrayList<>();

    /**
     * 自定义数据存储
     */
    private final Map<String, Object> customData = new HashMap<>();

    public ReactPhaseObservationContext() {
    }

    public ReactPhaseObservationContext(String sessionId, NodeType nodeType, String nodeId) {
        this.sessionId = sessionId;
        this.nodeType = nodeType;
        this.nodeId = nodeId;
    }

    // ==================== Common Getters and Setters ====================

    public String getSessionId() {
        return sessionId;
    }

    public ReactPhaseObservationContext setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getAgentName() {
        return agentName;
    }

    public ReactPhaseObservationContext setAgentName(String agentName) {
        this.agentName = agentName;
        return this;
    }

    public int getIteration() {
        return iteration;
    }

    public ReactPhaseObservationContext setIteration(int iteration) {
        this.iteration = iteration;
        return this;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public ReactPhaseObservationContext setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
        return this;
    }

    public String getNodeId() {
        return nodeId;
    }

    public ReactPhaseObservationContext setNodeId(String nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public ReactPhaseObservationContext setDurationMs(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public ReactPhaseObservationContext setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String getErrorType() {
        return errorType;
    }

    public ReactPhaseObservationContext setErrorType(String errorType) {
        this.errorType = errorType;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public ReactPhaseObservationContext setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    // ==================== LlmNode Getters and Setters ====================

    public String getModelName() {
        return modelName;
    }

    public ReactPhaseObservationContext setModelName(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public ReactPhaseObservationContext setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
        return this;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public ReactPhaseObservationContext setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
        return this;
    }

    public int getPromptMessageCount() {
        return promptMessageCount;
    }

    public ReactPhaseObservationContext setPromptMessageCount(int promptMessageCount) {
        this.promptMessageCount = promptMessageCount;
        return this;
    }

    public int getResponseMessageCount() {
        return responseMessageCount;
    }

    public ReactPhaseObservationContext setResponseMessageCount(int responseMessageCount) {
        this.responseMessageCount = responseMessageCount;
        return this;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public ReactPhaseObservationContext setFinishReason(String finishReason) {
        this.finishReason = finishReason;
        return this;
    }

    public List<String> getAvailableToolNames() {
        return availableToolNames;
    }

    public ReactPhaseObservationContext setAvailableToolNames(List<String> availableToolNames) {
        this.availableToolNames = availableToolNames != null ? availableToolNames : new ArrayList<>();
        return this;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public ReactPhaseObservationContext setInputSummary(String inputSummary) {
        this.inputSummary = inputSummary;
        return this;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public ReactPhaseObservationContext setOutputSummary(String outputSummary) {
        this.outputSummary = outputSummary;
        return this;
    }

    // ==================== ToolNode Getters and Setters ====================

    public List<ToolCallInfo> getToolCalls() {
        return toolCalls;
    }

    public ReactPhaseObservationContext setToolCalls(List<ToolCallInfo> toolCalls) {
        this.toolCalls = toolCalls;
        return this;
    }

    public ReactPhaseObservationContext addToolCall(ToolCallInfo toolCall) {
        this.toolCalls.add(toolCall);
        return this;
    }

    // ==================== Custom Data Methods ====================

    public ReactPhaseObservationContext putCustomData(String key, Object value) {
        this.customData.put(key, value);
        return this;
    }

    public ReactPhaseObservationContext putAllCustomData(Map<String, Object> data) {
        if (data != null) {
            this.customData.putAll(data);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCustomData(String key) {
        return (T) this.customData.get(key);
    }

    public Map<String, Object> getAllCustomData() {
        return Map.copyOf(customData);
    }

    // ==================== Tool Call Info ====================

    /**
     * 工具调用信息
     */
    public static class ToolCallInfo {
        private String toolName;
        private String arguments;
        private int argumentsLength;
        private String result;
        private int resultLength;
        private long durationMs;
        private boolean success;
        private String errorType;
        private String errorMessage;

        public ToolCallInfo() {
        }

        public ToolCallInfo(String toolName) {
            this.toolName = toolName;
        }

        // Getters and Setters

        public String getToolName() {
            return toolName;
        }

        public ToolCallInfo setToolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public String getArguments() {
            return arguments;
        }

        public ToolCallInfo setArguments(String arguments) {
            this.arguments = arguments;
            this.argumentsLength = arguments != null ? arguments.length() : 0;
            return this;
        }

        public int getArgumentsLength() {
            return argumentsLength;
        }

        public String getResult() {
            return result;
        }

        public ToolCallInfo setResult(String result) {
            this.result = result;
            this.resultLength = result != null ? result.length() : 0;
            return this;
        }

        public int getResultLength() {
            return resultLength;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public ToolCallInfo setDurationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public boolean isSuccess() {
            return success;
        }

        public ToolCallInfo setSuccess(boolean success) {
            this.success = success;
            return this;
        }

        public String getErrorType() {
            return errorType;
        }

        public ToolCallInfo setErrorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public ToolCallInfo setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        @Override
        public String toString() {
            return "ToolCallInfo{" +
                    "toolName='" + toolName + '\'' +
                    ", argumentsLength=" + argumentsLength +
                    ", resultLength=" + resultLength +
                    ", durationMs=" + durationMs +
                    ", success=" + success +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "ReactPhaseObservationContext{" +
                "sessionId='" + sessionId + '\'' +
                ", agentName='" + agentName + '\'' +
                ", iteration=" + iteration +
                ", nodeType=" + nodeType +
                ", nodeId='" + nodeId + '\'' +
                ", durationMs=" + durationMs +
                ", success=" + success +
                ", modelName='" + modelName + '\'' +
                ", toolCallsCount=" + toolCalls.size() +
                '}';
    }

    /**
     * 将上下文转换为 OpenTelemetry Attributes
     *
     * @return Attributes 对象
     */
    public Attributes toAttributes() {
        AttributesBuilder builder = Attributes.builder();

        if (sessionId != null) {
            builder.put(SESSION_ID_KEY, sessionId);
        }
        if (agentName != null) {
            builder.put(AGENT_NAME_KEY, agentName);
        }
        if (iteration > 0) {
            builder.put(ITERATION_KEY, (long) iteration);
        }
        if (nodeType != null) {
            builder.put(NODE_TYPE_KEY, nodeType.name());
        }
        if (nodeId != null) {
            builder.put(NODE_ID_KEY, nodeId);
        }
        if (durationMs > 0) {
            builder.put(DURATION_MS_KEY, durationMs);
        }
        builder.put(SUCCESS_KEY, success);

        // LLM specific
        if (modelName != null) {
            builder.put(MODEL_NAME_KEY, modelName);
        }
        if (inputTokens > 0) {
            builder.put(INPUT_TOKENS_KEY, (long) inputTokens);
        }
        if (outputTokens > 0) {
            builder.put(OUTPUT_TOKENS_KEY, (long) outputTokens);
        }
        if (finishReason != null) {
            builder.put(FINISH_REASON_KEY, finishReason);
        }

        // Tool calls
        if (!toolCalls.isEmpty()) {
            builder.put(TOOL_CALLS_COUNT_KEY, (long) toolCalls.size());
        }

        // Error info
        if (errorType != null) {
            builder.put(AttributeKey.stringKey("error.type"), errorType);
        }
        if (errorMessage != null) {
            builder.put(AttributeKey.stringKey("error.message"), errorMessage);
        }

        // 添加自定义数据
        for (Map.Entry<String, Object> entry : customData.entrySet()) {
            if (entry.getValue() != null) {
                String key = "codeact.react.custom." + entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    builder.put(AttributeKey.stringKey(key), (String) value);
                } else if (value instanceof Long) {
                    builder.put(AttributeKey.longKey(key), (Long) value);
                } else if (value instanceof Integer) {
                    builder.put(AttributeKey.longKey(key), ((Integer) value).longValue());
                } else if (value instanceof Double) {
                    builder.put(AttributeKey.doubleKey(key), (Double) value);
                } else if (value instanceof Boolean) {
                    builder.put(AttributeKey.booleanKey(key), (Boolean) value);
                } else {
                    builder.put(AttributeKey.stringKey(key), String.valueOf(value));
                }
            }
        }

        return builder.build();
    }
}

