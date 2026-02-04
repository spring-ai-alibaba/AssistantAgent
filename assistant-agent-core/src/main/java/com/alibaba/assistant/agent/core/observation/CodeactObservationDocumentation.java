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
package com.alibaba.assistant.agent.core.observation;

import io.opentelemetry.api.common.AttributeKey;

/**
 * CodeactAgent 可观测性文档定义
 * <p>
 * 定义通用的、标准化的观测指标，供所有使用 CodeactAgent 的项目复用。
 * 使用 OpenTelemetry AttributeKey 定义标准属性。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public final class CodeactObservationDocumentation {

    private CodeactObservationDocumentation() {
        // Utility class
    }

    // ==================== Span Names ====================

    public static final String SPAN_HOOK = "codeact.hook";
    public static final String SPAN_INTERCEPTOR = "codeact.interceptor";
    public static final String SPAN_REACT = "codeact.react";
    public static final String SPAN_EXECUTION = "codeact.execution";
    public static final String SPAN_CODE_GENERATION = "codeact.codegen";
    public static final String SPAN_TOOL_CALL = "codeact.tool.call";

    // ==================== Hook Attribute Keys ====================

    /**
     * Hook 观测属性键
     */
    public static final class HookAttributes {
        /** Hook名称 */
        public static final AttributeKey<String> NAME = AttributeKey.stringKey("codeact.hook.name");
        /** Hook位置（BEFORE_AGENT, AFTER_AGENT, BEFORE_MODEL, AFTER_MODEL） */
        public static final AttributeKey<String> POSITION = AttributeKey.stringKey("codeact.hook.position");
        /** 是否成功 */
        public static final AttributeKey<Boolean> SUCCESS = AttributeKey.booleanKey("codeact.hook.success");
        /** Agent名称 */
        public static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("codeact.hook.agent_name");
        /** 会话ID */
        public static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("codeact.hook.session_id");
        /** 执行时长（毫秒） */
        public static final AttributeKey<Long> DURATION_MS = AttributeKey.longKey("codeact.hook.duration_ms");
        /** 错误类型 */
        public static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("codeact.hook.error_type");
        /** 错误信息 */
        public static final AttributeKey<String> ERROR_MESSAGE = AttributeKey.stringKey("codeact.hook.error_message");

        private HookAttributes() {}
    }

    // ==================== Interceptor Attribute Keys ====================

    /**
     * Interceptor 观测属性键
     */
    public static final class InterceptorAttributes {
        /** Interceptor名称 */
        public static final AttributeKey<String> NAME = AttributeKey.stringKey("codeact.interceptor.name");
        /** Interceptor类型（MODEL, TOOL） */
        public static final AttributeKey<String> TYPE = AttributeKey.stringKey("codeact.interceptor.type");
        /** 是否成功 */
        public static final AttributeKey<Boolean> SUCCESS = AttributeKey.booleanKey("codeact.interceptor.success");
        /** Agent名称 */
        public static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("codeact.interceptor.agent_name");
        /** 会话ID */
        public static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("codeact.interceptor.session_id");
        /** 执行时长（毫秒） */
        public static final AttributeKey<Long> DURATION_MS = AttributeKey.longKey("codeact.interceptor.duration_ms");
        /** 模型名称（仅ModelInterceptor） */
        public static final AttributeKey<String> MODEL_NAME = AttributeKey.stringKey("codeact.interceptor.model_name");
        /** 工具名称（仅ToolInterceptor） */
        public static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("codeact.interceptor.tool_name");
        /** 工具参数长度（仅ToolInterceptor） */
        public static final AttributeKey<Long> TOOL_ARGUMENTS_LENGTH = AttributeKey.longKey("codeact.interceptor.tool_arguments_length");
        /** 工具结果长度（仅ToolInterceptor） */
        public static final AttributeKey<Long> TOOL_RESULT_LENGTH = AttributeKey.longKey("codeact.interceptor.tool_result_length");
        /** 输入Token数（仅ModelInterceptor） */
        public static final AttributeKey<Long> INPUT_TOKENS = AttributeKey.longKey("codeact.interceptor.input_tokens");
        /** 输出Token数（仅ModelInterceptor） */
        public static final AttributeKey<Long> OUTPUT_TOKENS = AttributeKey.longKey("codeact.interceptor.output_tokens");

        private InterceptorAttributes() {}
    }

    // ==================== React Phase Attribute Keys ====================

    /**
     * React阶段 观测属性键
     */
    public static final class ReactPhaseAttributes {
        /** 节点类型（LLM, TOOL） */
        public static final AttributeKey<String> NODE_TYPE = AttributeKey.stringKey("codeact.react.node_type");
        /** 是否成功 */
        public static final AttributeKey<Boolean> SUCCESS = AttributeKey.booleanKey("codeact.react.success");
        /** 模型名称 */
        public static final AttributeKey<String> MODEL_NAME = AttributeKey.stringKey("codeact.react.model_name");
        /** 会话ID */
        public static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("codeact.react.session_id");
        /** Agent名称 */
        public static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("codeact.react.agent_name");
        /** 节点ID */
        public static final AttributeKey<String> NODE_ID = AttributeKey.stringKey("codeact.react.node_id");
        /** 迭代轮次 */
        public static final AttributeKey<Long> ITERATION = AttributeKey.longKey("codeact.react.iteration");
        /** 执行时长（毫秒） */
        public static final AttributeKey<Long> DURATION_MS = AttributeKey.longKey("codeact.react.duration_ms");
        /** 输入Token数 */
        public static final AttributeKey<Long> INPUT_TOKENS = AttributeKey.longKey("codeact.react.input_tokens");
        /** 输出Token数 */
        public static final AttributeKey<Long> OUTPUT_TOKENS = AttributeKey.longKey("codeact.react.output_tokens");
        /** 提示消息数 */
        public static final AttributeKey<Long> PROMPT_MESSAGE_COUNT = AttributeKey.longKey("codeact.react.prompt_message_count");
        /** 完成原因 */
        public static final AttributeKey<String> FINISH_REASON = AttributeKey.stringKey("codeact.react.finish_reason");
        /** 工具调用数 */
        public static final AttributeKey<Long> TOOL_CALLS_COUNT = AttributeKey.longKey("codeact.react.tool_calls_count");
        /** 工具名称列表（逗号分隔） */
        public static final AttributeKey<String> TOOL_NAMES = AttributeKey.stringKey("codeact.react.tool_names");

        private ReactPhaseAttributes() {}
    }

    // ==================== Codeact Execution Attribute Keys ====================

    /**
     * 代码执行 观测属性键
     */
    public static final class ExecutionAttributes {
        /** 编程语言 */
        public static final AttributeKey<String> LANGUAGE = AttributeKey.stringKey("codeact.language");
        /** 是否成功 */
        public static final AttributeKey<Boolean> SUCCESS = AttributeKey.booleanKey("codeact.success");
        /** 函数名 */
        public static final AttributeKey<String> FUNCTION_NAME = AttributeKey.stringKey("codeact.function.name");
        /** 参数长度 */
        public static final AttributeKey<Long> ARGUMENTS_LENGTH = AttributeKey.longKey("codeact.arguments.length");
        /** 结果长度 */
        public static final AttributeKey<Long> RESULT_LENGTH = AttributeKey.longKey("codeact.result.length");
        /** 执行时长（毫秒） */
        public static final AttributeKey<Long> DURATION_MS = AttributeKey.longKey("codeact.duration.ms");
        /** 错误类型 */
        public static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("codeact.error.type");
        /** 错误信息 */
        public static final AttributeKey<String> ERROR_MESSAGE = AttributeKey.stringKey("codeact.error.message");

        private ExecutionAttributes() {}
    }

    // ==================== Code Generation Attribute Keys ====================

    /**
     * 代码生成 观测属性键
     */
    public static final class CodeGenerationAttributes {
        /** 编程语言 */
        public static final AttributeKey<String> LANGUAGE = AttributeKey.stringKey("codeact.codegen.language");
        /** 模型名称 */
        public static final AttributeKey<String> MODEL_NAME = AttributeKey.stringKey("codeact.codegen.model");
        /** 是否成功 */
        public static final AttributeKey<Boolean> SUCCESS = AttributeKey.booleanKey("codeact.codegen.success");
        /** 函数名 */
        public static final AttributeKey<String> FUNCTION_NAME = AttributeKey.stringKey("codeact.codegen.function.name");
        /** 代码行数 */
        public static final AttributeKey<Long> CODE_LINES = AttributeKey.longKey("codeact.codegen.code.lines");
        /** 输入 Token */
        public static final AttributeKey<Long> INPUT_TOKENS = AttributeKey.longKey("codeact.codegen.input.tokens");
        /** 输出 Token */
        public static final AttributeKey<Long> OUTPUT_TOKENS = AttributeKey.longKey("codeact.codegen.output.tokens");
        /** 执行时长（毫秒） */
        public static final AttributeKey<Long> DURATION_MS = AttributeKey.longKey("codeact.codegen.duration.ms");

        private CodeGenerationAttributes() {}
    }

    // ==================== Codeact Tool Call Attribute Keys ====================

    /**
     * Codeact工具调用 观测属性键
     */
    public static final class ToolCallAttributes {
        /** 工具名称 */
        public static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("codeact.tool.name");
        /** 是否成功 */
        public static final AttributeKey<Boolean> SUCCESS = AttributeKey.booleanKey("codeact.tool.success");
        /** 参数（JSON格式） */
        public static final AttributeKey<String> ARGUMENTS = AttributeKey.stringKey("codeact.tool.arguments");
        /** 参数长度 */
        public static final AttributeKey<Long> ARGUMENTS_LENGTH = AttributeKey.longKey("codeact.tool.arguments.length");
        /** 结果长度 */
        public static final AttributeKey<Long> RESULT_LENGTH = AttributeKey.longKey("codeact.tool.result.length");
        /** 执行时长（毫秒） */
        public static final AttributeKey<Long> DURATION_MS = AttributeKey.longKey("codeact.tool.duration.ms");
        /** 错误类型 */
        public static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("codeact.tool.error.type");

        private ToolCallAttributes() {}
    }

    // ==================== GenAI Semantic Convention Attribute Keys ====================

    /**
     * GenAI 语义约定属性键
     */
    public static final class GenAIAttributes {
        /** 会话ID */
        public static final AttributeKey<String> CONVERSATION_ID = AttributeKey.stringKey("gen_ai.conversation.id");
        /** Span 类型（LLM, TOOL, CHAIN, EVALUATOR） */
        public static final AttributeKey<String> SPAN_KIND_NAME = AttributeKey.stringKey("gen_ai.span_kind_name");
        /** 操作名称（chat, execute_tool, chain, evaluate） */
        public static final AttributeKey<String> OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
        /** Agent 名称 */
        public static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("gen_ai.agent.name");
        /** 响应模型 */
        public static final AttributeKey<String> RESPONSE_MODEL = AttributeKey.stringKey("gen_ai.response.model");
        /** 工具名称 */
        public static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
        /** 输入 Token 使用量 */
        public static final AttributeKey<Long> INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
        /** 输出 Token 使用量 */
        public static final AttributeKey<Long> OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");
        /** 输入消息 */
        public static final AttributeKey<String> INPUT_MESSAGES = AttributeKey.stringKey("gen_ai.input.messages");
        /** 输出消息 */
        public static final AttributeKey<String> OUTPUT_MESSAGES = AttributeKey.stringKey("gen_ai.output.messages");

        private GenAIAttributes() {}
    }
}

