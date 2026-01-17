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
package com.alibaba.assistant.agent.planning.executor;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ExecutionResult;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Action 执行器
 *
 * <p>执行 action 定义的接口调用，支持 HTTP、MCP、INTERNAL 三种类型。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ActionExecutor.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final McpExecutor mcpExecutor;
    private final InternalExecutor internalExecutor;

    public ActionExecutor(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.mcpExecutor = new McpExecutor();
        this.internalExecutor = new InternalExecutor();
    }

    public ActionExecutor(RestTemplate restTemplate,
                          ObjectMapper objectMapper,
                          McpExecutor mcpExecutor,
                          InternalExecutor internalExecutor) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.mcpExecutor = mcpExecutor;
        this.internalExecutor = internalExecutor;
    }

    /**
     * 执行 action
     *
     * @param action          动作定义
     * @param params          参数值
     * @param timeoutSeconds  超时时间（秒）
     * @return 执行结果
     */
    public ExecutionResult execute(ActionDefinition action, Map<String, Object> params, Integer timeoutSeconds) {
        logger.info("ActionExecutor#execute - actionId={}, params={}", action.getActionId(), params.keySet());

        try {
            // 检查接口绑定配置
            StepDefinition.InterfaceBinding binding = action.getInterfaceBinding();
            if (binding == null) {
                return ExecutionResult.builder()
                        .success(false)
                        .errorMessage("动作未配置接口绑定")
                        .build();
            }

            String type = binding.getType();
            if (type == null) {
                return ExecutionResult.builder()
                        .success(false)
                        .errorMessage("接口类型未配置")
                        .build();
            }

            // 根据类型执行
            return switch (type.toUpperCase()) {
                case "HTTP" -> executeHttp(binding, params, timeoutSeconds);
                case "MCP" -> executeMcp(binding, params, timeoutSeconds);
                case "INTERNAL" -> executeInternal(binding, params, timeoutSeconds);
                default -> ExecutionResult.builder()
                        .success(false)
                        .errorMessage("不支持的接口类型：" + type)
                        .build();
            };

        } catch (Exception e) {
            logger.error("ActionExecutor#execute - execution failed, actionId={}", action.getActionId(), e);
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("执行失败：" + e.getMessage())
                    .build();
        }
    }

    /**
     * 执行 HTTP 接口
     */
    private ExecutionResult executeHttp(StepDefinition.InterfaceBinding binding,
                                         Map<String, Object> params,
                                         Integer timeoutSeconds) {
        StepDefinition.HttpConfig httpConfig = binding.getHttp();
        if (httpConfig == null) {
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("HTTP 配置为空")
                    .build();
        }

        String url = httpConfig.getUrl();
        String method = httpConfig.getMethod();
        if (!StringUtils.hasText(method)) {
            method = "POST";
        }

        try {
            logger.info("ActionExecutor#executeHttp - url={}, method={}", url, method);

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (httpConfig.getHeaders() != null) {
                httpConfig.getHeaders().forEach(headers::add);
            }

            // 构建请求体
            Object body = null;
            if (!params.isEmpty() && !"GET".equalsIgnoreCase(method)) {
                body = params;
            }

            // 创建请求实体
            HttpEntity<Object> requestEntity = new HttpEntity<>(body, headers);

            // 发送请求
            ResponseEntity<String> response;
            if (timeoutSeconds != null && timeoutSeconds > 0) {
                restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory());
                ((org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate.getRequestFactory())
                        .setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
                ((org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate.getRequestFactory())
                        .setReadTimeout(Duration.ofSeconds(timeoutSeconds));
            }

            response = restTemplate.exchange(
                    url,
                    HttpMethod.valueOf(method.toUpperCase()),
                    requestEntity,
                    String.class
            );

            // 解析响应
            String responseBody = response.getBody();
            Object parsedResponse = null;
            if (StringUtils.hasText(responseBody)) {
                try {
                    parsedResponse = objectMapper.readValue(responseBody, Object.class);
                } catch (Exception e) {
                    logger.warn("ActionExecutor#executeHttp - failed to parse response as JSON, returning raw string");
                    parsedResponse = responseBody;
                }
            }

            boolean success = response.getStatusCode().is2xxSuccessful();

            logger.info("ActionExecutor#executeHttp - completed, status={}, success={}",
                    response.getStatusCode(), success);

            return ExecutionResult.builder()
                    .success(success)
                    .httpStatusCode(response.getStatusCode().value())
                    .responseData(parsedResponse)
                    .responseHeaders(response.getHeaders().toSingleValueMap())
                    .metadata(Map.of("rawResponse", responseBody))
                    .build();

        } catch (Exception e) {
            logger.error("ActionExecutor#executeHttp - HTTP request failed", e);
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("HTTP 请求失败：" + e.getMessage())
                    .build();
        }
    }

    /**
     * 执行 MCP 工具
     */
    private ExecutionResult executeMcp(StepDefinition.InterfaceBinding binding,
                                        Map<String, Object> params,
                                        Integer timeoutSeconds) {
        StepDefinition.McpConfig mcpConfig = binding.getMcp();
        if (mcpConfig == null) {
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("MCP 配置为空")
                    .build();
        }

        logger.info("ActionExecutor#executeMcp - server={}, tool={}",
                mcpConfig.getServerName(), mcpConfig.getToolName());

        return mcpExecutor.execute(mcpConfig, params, timeoutSeconds);
    }

    /**
     * 执行内部服务
     */
    private ExecutionResult executeInternal(StepDefinition.InterfaceBinding binding,
                                            Map<String, Object> params,
                                            Integer timeoutSeconds) {
        StepDefinition.InternalConfig internalConfig = binding.getInternal();
        if (internalConfig == null) {
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("Internal 配置为空")
                    .build();
        }

        logger.info("ActionExecutor#executeInternal - bean={}, method={}",
                internalConfig.getBeanName(), internalConfig.getMethodName());

        return internalExecutor.execute(internalConfig, params, timeoutSeconds);
    }

    /**
     * MCP 执行器
     */
    public static class McpExecutor {
        public ExecutionResult execute(StepDefinition.McpConfig config, Map<String, Object> params, Integer timeout) {
            // TODO: 实现 MCP 工具调用
            logger.warn("McpExecutor#execute - not implemented yet");
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("MCP 执行器尚未实现")
                    .build();
        }
    }

    /**
     * 内部服务执行器
     */
    public static class InternalExecutor {
        public ExecutionResult execute(StepDefinition.InternalConfig config, Map<String, Object> params, Integer timeout) {
            // TODO: 实现内部服务调用（Spring Bean 方法调用）
            logger.warn("InternalExecutor#execute - not implemented yet");
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("内部服务执行器尚未实现")
                    .build();
        }
    }
}
