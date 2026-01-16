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
package com.alibaba.assistant.agent.planning.internal.executor;

import com.alibaba.assistant.agent.planning.model.ExecutionStep;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.alibaba.assistant.agent.planning.model.StepExecutionResult;
import com.alibaba.assistant.agent.planning.model.StepType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * API 调用步骤执行器
 *
 * <p>执行 HTTP API 调用。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ApiCallStepExecutor extends AbstractStepExecutor {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ApiCallStepExecutor() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public ApiCallStepExecutor(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public StepType getSupportedType() {
        return StepType.API_CALL;
    }

    @Override
    protected StepExecutionResult doExecute(ExecutionStep step, StepExecutionContext context) {
        logger.debug("ApiCallStepExecutor#doExecute - reason=executing API call step, stepId={}", step.getStepId());

        StepDefinition definition = step.getDefinition();
        if (definition == null || definition.getInterfaceBinding() == null) {
            return StepExecutionResult.failure("API call step requires interface binding");
        }

        StepDefinition.InterfaceBinding binding = definition.getInterfaceBinding();
        if (binding.getHttp() == null) {
            return StepExecutionResult.failure("API call step requires HTTP configuration");
        }

        StepDefinition.HttpConfig http = binding.getHttp();

        try {
            // 构建请求 URL
            String url = resolveTemplate(http.getUrl(), step.getInputValues(), context);

            // 构建请求体
            String body = null;
            if (http.getBodyTemplate() != null) {
                body = resolveTemplate(http.getBodyTemplate(), step.getInputValues(), context);
            }

            logger.info("ApiCallStepExecutor#doExecute - reason=calling API, url={}, method={}",
                    url, http.getMethod());

            // 执行请求
            Object response = executeRequest(http.getMethod(), url, body, http.getHeaders());

            // 解析响应
            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("response", response);

            // 如果有响应映射，进行映射
            if (http.getResponseMapping() != null && response instanceof Map) {
                Object mappedResult = extractValue((Map<?, ?>) response, http.getResponseMapping());
                output.put("mappedResult", mappedResult);
            }

            return StepExecutionResult.success(output);

        } catch (Exception e) {
            logger.error("ApiCallStepExecutor#doExecute - reason=API call failed, stepId={}, error={}",
                    step.getStepId(), e.getMessage(), e);
            return StepExecutionResult.failure("API call failed: " + e.getMessage());
        }
    }

    private String resolveTemplate(String template, Map<String, Object> inputs, StepExecutionContext context) {
        if (template == null) {
            return null;
        }

        String result = template;

        // 替换输入参数
        if (inputs != null) {
            for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                result = result.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
                result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
            }
        }

        // 替换上下文变量
        if (context != null && context.getVariables() != null) {
            for (Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
                result = result.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
                result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
            }
        }

        return result;
    }

    private Object executeRequest(String method, String url, String body, Map<String, String> headers) {
        // 简化实现 - 实际应该使用完整的 HTTP 客户端
        if ("GET".equalsIgnoreCase(method)) {
            return restTemplate.getForObject(url, Map.class);
        } else if ("POST".equalsIgnoreCase(method)) {
            return restTemplate.postForObject(url, body, Map.class);
        } else if ("PUT".equalsIgnoreCase(method)) {
            restTemplate.put(url, body);
            return Map.of("success", true);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            restTemplate.delete(url);
            return Map.of("success", true);
        }

        throw new UnsupportedOperationException("Unsupported HTTP method: " + method);
    }

    private Object extractValue(Map<?, ?> data, String expression) {
        if (expression == null || expression.isBlank()) {
            return data;
        }

        // 简单的 JSONPath 支持
        if (expression.startsWith("$.")) {
            String[] parts = expression.substring(2).split("\\.");
            Object current = data;
            for (String part : parts) {
                if (current instanceof Map) {
                    current = ((Map<?, ?>) current).get(part);
                } else {
                    return null;
                }
            }
            return current;
        }

        return data.get(expression);
    }
}
