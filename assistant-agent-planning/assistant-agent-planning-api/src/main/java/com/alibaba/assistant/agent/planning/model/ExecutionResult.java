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

/**
 * Action 执行结果
 *
 * <p>封装 Action 执行的结果信息，包括执行状态、响应数据和错误信息。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {

    /**
     * 是否执行成功
     */
    @Builder.Default
    private boolean success = false;

    /**
     * HTTP 响应状态码（仅 HTTP 类型的 Action）
     */
    private Integer httpStatusCode;

    /**
     * 响应数据
     */
    private Object responseData;

    /**
     * 响应头（仅 HTTP 类型的 Action）
     */
    private java.util.Map<String, String> responseHeaders;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 异常对象（用于调试）
     */
    private Throwable exception;

    /**
     * 执行耗时（毫秒）
     */
    private Long executionTimeMs;

    /**
     * 元数据（用于存储额外信息）
     */
    @Builder.Default
    private java.util.Map<String, Object> metadata = new java.util.HashMap<>();

    /**
     * 获取错误消息
     *
     * <p>如果 errorMessage 不为空则返回它，否则返回异常的消息。
     *
     * @return 错误消息，如果没有错误返回 null
     */
    public String getErrorMessage() {
        if (errorMessage != null && !errorMessage.isEmpty()) {
            return errorMessage;
        }
        if (exception != null) {
            return exception.getMessage();
        }
        return null;
    }

    /**
     * 创建失败结果
     *
     * @param errorMessage 错误消息
     * @return 失败结果
     */
    public static ExecutionResult failure(String errorMessage) {
        return ExecutionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建失败结果（带异常）
     *
     * @param errorMessage 错误消息
     * @param exception    异常对象
     * @return 失败结果
     */
    public static ExecutionResult failure(String errorMessage, Throwable exception) {
        return ExecutionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .exception(exception)
                .build();
    }

    /**
     * 创建成功结果
     *
     * @param responseData 响应数据
     * @return 成功结果
     */
    public static ExecutionResult success(Object responseData) {
        return ExecutionResult.builder()
                .success(true)
                .responseData(responseData)
                .build();
    }

    /**
     * 创建成功结果（带 HTTP 状态码）
     *
     * @param httpStatusCode HTTP 状态码
     * @param responseData    响应数据
     * @return 成功结果
     */
    public static ExecutionResult success(int httpStatusCode, Object responseData) {
        return ExecutionResult.builder()
                .success(true)
                .httpStatusCode(httpStatusCode)
                .responseData(responseData)
                .build();
    }

    /**
     * 创建成功结果（完整）
     *
     * @param httpStatusCode  HTTP 状态码
     * @param responseData     响应数据
     * @param responseHeaders  响应头
     * @param executionTimeMs  执行耗时
     * @return 成功结果
     */
    public static ExecutionResult success(int httpStatusCode,
                                         Object responseData,
                                         java.util.Map<String, String> responseHeaders,
                                         long executionTimeMs) {
        return ExecutionResult.builder()
                .success(true)
                .httpStatusCode(httpStatusCode)
                .responseData(responseData)
                .responseHeaders(responseHeaders)
                .executionTimeMs(executionTimeMs)
                .build();
    }
}
