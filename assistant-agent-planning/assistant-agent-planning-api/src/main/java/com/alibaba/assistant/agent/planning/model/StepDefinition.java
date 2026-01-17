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

import java.util.List;
import java.util.Map;

/**
 * 步骤定义
 *
 * <p>定义多步骤动作中的单个步骤配置。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepDefinition {

    /**
     * 步骤 ID
     */
    private String stepId;

    /**
     * 步骤名称
     */
    private String name;

    /**
     * 步骤描述
     */
    private String description;

    /**
     * 步骤类型
     */
    private StepType type;

    /**
     * 步骤顺序
     */
    @Builder.Default
    private Integer order = 0;

    /**
     * 输入参数定义
     */
    private List<ActionParameter> inputParams;

    /**
     * 输出规格定义
     */
    private OutputSpec outputSpec;

    /**
     * 接口绑定配置
     */
    private InterfaceBinding interfaceBinding;

    /**
     * 执行策略配置
     */
    private ExecutionStrategy executionStrategy;

    /**
     * 条件表达式（用于条件步骤）
     */
    private String condition;

    /**
     * 下一步骤 ID 列表
     */
    private List<String> nextSteps;

    /**
     * 是否可跳过
     */
    @Builder.Default
    private Boolean skippable = false;

    /**
     * 是否需要中断等待用户输入
     */
    @Builder.Default
    private Boolean interruptForInput = false;

    /**
     * 用户提示消息（等待输入时显示）
     */
    private String userPrompt;

    /**
     * 超时时间（秒）
     */
    private Integer timeoutSeconds;

    /**
     * 输出规格定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputSpec {
        /**
         * 输出字段定义
         */
        private List<OutputField> fields;

        /**
         * 输出是否为数组
         */
        @Builder.Default
        private Boolean isArray = false;
    }

    /**
     * 输出字段定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputField {
        private String name;
        private String type;
        private String description;
    }

    /**
     * 接口绑定配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterfaceBinding {
        /**
         * 接口类型（HTTP, MCP, INTERNAL, DATA_AGENT）
         */
        private String type;

        /**
         * HTTP 配置
         */
        private HttpConfig http;

        /**
         * MCP 工具配置
         */
        private McpConfig mcp;

        /**
         * 内部服务配置
         */
        private InternalConfig internal;

        /**
         * DataAgent 配置
         */
        private DataAgentConfig dataAgent;
    }

    /**
     * HTTP 接口配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HttpConfig {
        private String url;
        private String method;
        private Map<String, String> headers;
        private String bodyTemplate;
        private String responseMapping;
    }

    /**
     * MCP 工具配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpConfig {
        private String toolName;
        private String serverName;
    }

    /**
     * 内部服务配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InternalConfig {
        private String beanName;
        private String methodName;

        /**
         * 方法参数定义（用于支持方法重载）
         */
        private List<MethodParam> methodParams;
    }

    /**
     * 方法参数定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodParam {
        /**
         * 参数名称
         */
        private String name;

        /**
         * 参数类型（完整类名，如 java.lang.Long）
         */
        private String type;
    }

    /**
     * DataAgent 配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataAgentConfig {
        /**
         * 数据源 ID
         */
        private String dataSourceId;

        /**
         * 查询类型（NATURAL_LANGUAGE, SQL_TEMPLATE）
         */
        @Builder.Default
        private String queryType = "NATURAL_LANGUAGE";

        /**
         * SQL 模板（当 queryType 为 SQL_TEMPLATE 时使用）
         */
        private String sqlTemplate;
    }

    /**
     * 执行策略配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionStrategy {
        /**
         * 最大重试次数
         */
        @Builder.Default
        private Integer maxRetries = 0;

        /**
         * 重试间隔（毫秒）
         */
        @Builder.Default
        private Long retryDelayMs = 1000L;

        /**
         * 失败时是否继续
         */
        @Builder.Default
        private Boolean continueOnFailure = false;

        /**
         * 补偿配置（SAGA 事务）
         */
        private CompensationConfig compensation;
    }

    /**
     * 补偿配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompensationConfig {
        /**
         * 补偿接口绑定
         */
        private InterfaceBinding binding;

        /**
         * 补偿参数映射
         */
        private Map<String, String> paramMapping;
    }
}
