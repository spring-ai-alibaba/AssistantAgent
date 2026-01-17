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
import com.alibaba.assistant.agent.planning.spi.ActionExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP API 执行器
 *
 * <p>执行 HTTP 类型的 Action，支持所有 HTTP 方法（GET, POST, PUT, DELETE, PATCH）。
 *
 * <h3>功能特性</h3>
 * <ul>
 * <li>支持所有标准 HTTP 方法</li>
 * <li>自动序列化请求体为 JSON</li>
 * <li>支持路径参数替换</li>
 * <li>支持请求头配置</li>
 * <li>可配置超时时间</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class HttpExecutor implements ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(HttpExecutor.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HttpExecutor(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        logger.info("HttpExecutor#init - initialized");
    }

    @Override
    public String getExecutorType() {
        return "HTTP";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public ExecutionResult execute(ActionDefinition action,
                                   Map<String, Object> params,
                                   Integer timeoutSeconds) {
        long startTime = System.currentTimeMillis();

        StepDefinition.InterfaceBinding binding = action.getBinding();
        StepDefinition.HttpConfig httpConfig = binding.getHttp();

        String url = httpConfig.getUrl();
        HttpMethod method = HttpMethod.valueOf(httpConfig.getMethod().toUpperCase());

        try {
            // 1. 替换路径参数
            url = replacePathParams(url, params);

            // 2. 构建请求头
            HttpHeaders headers = buildHeaders(httpConfig.getHeaders());

            // 3. 构建请求实体
            HttpEntity<Object> requestEntity = buildRequestEntity(headers, method, params);

            // 4. 发送请求
            logger.debug("HttpExecutor#execute - sending request, actionId={}, url={}, method={}",
                    action.getActionId(), url, method);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    method,
                    requestEntity,
                    String.class
            );

            // 5. 解析响应
            long executionTime = System.currentTimeMillis() - startTime;

            Object responseData = parseResponse(response);
            Map<String, String> responseHeaders = extractResponseHeaders(response.getHeaders());

            logger.info("HttpExecutor#execute - request completed, actionId={}, statusCode={}, time={}ms",
                    action.getActionId(), response.getStatusCodeValue(), executionTime);

            return ExecutionResult.success(
                    response.getStatusCodeValue(),
                    responseData,
                    responseHeaders,
                    executionTime
            );

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.error("HttpExecutor#execute - request failed, actionId={}, time={}ms",
                    action.getActionId(), executionTime, e);
            return ExecutionResult.failure("HTTP 请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 替换路径参数
     * <p>
     * 示例：/api/users/{userId} → /api/users/123
     *
     * @param url    URL 模板
     * @param params 参数值
     * @return 替换后的 URL
     */
    private String replacePathParams(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }

        String result = url;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }

        return result;
    }

    /**
     * 构建请求头
     */
    private HttpHeaders buildHeaders(Map<String, String> configHeaders) {
        HttpHeaders headers = new HttpHeaders();

        // 默认 Content-Type
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 添加配置的请求头
        if (configHeaders != null) {
            configHeaders.forEach(headers::add);
        }

        return headers;
    }

    /**
     * 构建请求实体
     */
    private HttpEntity<Object> buildRequestEntity(HttpHeaders headers,
                                                   HttpMethod method,
                                                   Map<String, Object> params) {
        if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
            // GET 和 DELETE 不使用请求体
            return new HttpEntity<>(headers);
        } else {
            // POST, PUT, PATCH 使用请求体
            return new HttpEntity<>(params, headers);
        }
    }

    /**
     * 解析响应
     */
    private Object parseResponse(ResponseEntity<String> response) {
        try {
            String body = response.getBody();

            if (body == null || body.isEmpty()) {
                return null;
            }

            // 尝试解析为 JSON
            return objectMapper.readValue(body, Object.class);

        } catch (Exception e) {
            logger.warn("HttpExecutor#parseResponse - failed to parse JSON, returning raw string");
            return response.getBody();
        }
    }

    /**
     * 提取响应头
     */
    private Map<String, String> extractResponseHeaders(HttpHeaders headers) {
        return headers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.join(", ", e.getValue())
                ));
    }
}
