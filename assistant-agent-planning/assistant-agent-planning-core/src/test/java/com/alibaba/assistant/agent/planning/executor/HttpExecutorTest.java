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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * HttpExecutor 测试
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class HttpExecutorTest {

    @Mock
    private RestTemplate restTemplate;

    private HttpExecutor httpExecutor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        httpExecutor = new HttpExecutor(restTemplate, objectMapper);
    }

    @Test
    void testGetExecutorType() {
        assertEquals("HTTP", httpExecutor.getExecutorType());
    }

    @Test
    void testGetPriority() {
        assertEquals(100, httpExecutor.getPriority());
    }

    @Test
    void testExecute_GetRequest_Success() throws Exception {
        // 准备测试数据
        ActionDefinition action = createHttpAction("GET", "http://api.example.com/users");
        Map<String, Object> params = new HashMap<>();

        // Mock 响应
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                "{\"users\": []}",
                HttpStatus.OK
        );
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        // 执行测试
        ExecutionResult result = httpExecutor.execute(action, params, null);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(200, result.getHttpStatusCode());
        assertNotNull(result.getResponseData());
        assertNotNull(result.getExecutionTimeMs());
    }

    @Test
    void testExecute_PostRequest_Success() throws Exception {
        // 准备测试数据
        ActionDefinition action = createHttpAction("POST", "http://api.example.com/users");
        Map<String, Object> params = new HashMap<>();
        params.put("name", "John");
        params.put("age", 30);

        // Mock 响应
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                "{\"id\": 123, \"name\": \"John\"}",
                HttpStatus.CREATED
        );
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        // 执行测试
        ExecutionResult result = httpExecutor.execute(action, params, null);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(201, result.getHttpStatusCode());
        assertNotNull(result.getResponseData());
    }

    @Test
    void testExecute_PathParamReplacement() throws Exception {
        // 准备测试数据
        ActionDefinition action = createHttpAction("GET", "http://api.example.com/users/{userId}");
        Map<String, Object> params = new HashMap<>();
        params.put("userId", "123");

        // Mock 响应
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                "{\"id\": 123, \"name\": \"John\"}",
                HttpStatus.OK
        );
        when(restTemplate.exchange(
                eq("http://api.example.com/users/123"),  // 路径参数应该被替换
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        // 执行测试
        ExecutionResult result = httpExecutor.execute(action, params, null);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testExecute_MultiplePathParams() throws Exception {
        // 准备测试数据
        ActionDefinition action = createHttpAction("GET", "http://api.example.com/users/{userId}/posts/{postId}");
        Map<String, Object> params = new HashMap<>();
        params.put("userId", "123");
        params.put("postId", "456");

        // Mock 响应
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                "{\"id\": 456}",
                HttpStatus.OK
        );
        when(restTemplate.exchange(
                eq("http://api.example.com/users/123/posts/456"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        // 执行测试
        ExecutionResult result = httpExecutor.execute(action, params, null);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testExecute_CustomHeaders() throws Exception {
        // 准备测试数据
        ActionDefinition action = createHttpAction("GET", "http://api.example.com/users");
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token123");
        headers.put("X-Custom-Header", "custom-value");
        action.getInterfaceBinding().getHttp().setHeaders(headers);

        Map<String, Object> params = new HashMap<>();

        // Mock 响应
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                "[]",
                HttpStatus.OK
        );
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        // 执行测试
        ExecutionResult result = httpExecutor.execute(action, params, null);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testExecute_HttpError() throws Exception {
        // 准备测试数据
        ActionDefinition action = createHttpAction("GET", "http://api.example.com/users");
        Map<String, Object> params = new HashMap<>();

        // Mock 错误响应
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                "{\"error\": \"Not found\"}",
                HttpStatus.NOT_FOUND
        );
        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        // 执行测试
        ExecutionResult result = httpExecutor.execute(action, params, null);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());  // HTTP 错误仍然被认为是执行成功
        assertEquals(404, result.getHttpStatusCode());
    }

    @Test
    void testExecute_NetworkError() throws Exception {
        // 准备测试数据
        ActionDefinition action = createHttpAction("GET", "http://api.example.com/users");
        Map<String, Object> params = new HashMap<>();

        // Mock 抛出异常
        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RuntimeException("Connection timeout"));

        // 执行测试
        ExecutionResult result = httpExecutor.execute(action, params, null);

        // 验证结果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("HTTP 请求失败"));
    }

    @Test
    void testExecute_PutRequest() throws Exception {
        // 准备测试数据
        ActionDefinition action = createHttpAction("PUT", "http://api.example.com/users/123");
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Updated Name");

        // Mock 响应
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                "{\"id\": 123, \"name\": \"Updated Name\"}",
                HttpStatus.OK
        );
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        // 执行测试
        ExecutionResult result = httpExecutor.execute(action, params, null);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testExecute_DeleteRequest() throws Exception {
        // 准备测试数据
        ActionDefinition action = createHttpAction("DELETE", "http://api.example.com/users/123");
        Map<String, Object> params = new HashMap<>();

        // Mock 响应
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                null,
                HttpStatus.NO_CONTENT
        );
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.DELETE),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        // 执行测试
        ExecutionResult result = httpExecutor.execute(action, params, null);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(204, result.getHttpStatusCode());
    }

    /**
     * 创建 HTTP Action
     */
    private ActionDefinition createHttpAction(String method, String url) {
        ActionDefinition action = new ActionDefinition();
        action.setActionId("test-http-action");
        action.setActionName("Test HTTP Action");

        StepDefinition.InterfaceBinding binding = new StepDefinition.InterfaceBinding();
        binding.setType("HTTP");

        StepDefinition.HttpConfig httpConfig = new StepDefinition.HttpConfig();
        httpConfig.setUrl(url);
        httpConfig.setMethod(method);
        httpConfig.setHeaders(new HashMap<>());

        binding.setHttp(httpConfig);
        action.setInterfaceBinding(binding);

        return action;
    }
}
