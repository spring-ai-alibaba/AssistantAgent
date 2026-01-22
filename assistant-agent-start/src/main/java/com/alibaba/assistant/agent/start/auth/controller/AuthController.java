/*
 * Copyright 2025 the original author or authors.
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

package com.alibaba.assistant.agent.start.auth.controller;

import com.alibaba.assistant.agent.start.auth.dto.ApiResponse;
import com.alibaba.assistant.agent.start.auth.dto.LoginRequest;
import com.alibaba.assistant.agent.start.auth.dto.LoginResponse;
import com.alibaba.assistant.agent.start.auth.service.AuthenticationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthenticationService authenticationService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        logger.info("AuthController#login - 登录请求: username={}", request.getUsername());

        try {
            LoginResponse response = authenticationService.login(request);
            return ApiResponse.success("登录成功", response);
        } catch (Exception e) {
            logger.error("AuthController#login - 登录失败: username={}, error={}", request.getUsername(), e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 验证 Token
     */
    @GetMapping("/validate")
    public ApiResponse<Boolean> validateToken(@RequestHeader("Authorization") String authorization) {
        logger.debug("AuthController#validateToken - 验证 Token");

        try {
            String token = authorization.replace("Bearer ", "");
            boolean valid = authenticationService.validateToken(token);
            return ApiResponse.success(valid);
        } catch (Exception e) {
            logger.error("AuthController#validateToken - 验证失败: error={}", e.getMessage());
            return ApiResponse.success(false);
        }
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        logger.info("AuthController#logout - 用户登出");
        // TODO: 实现登出逻辑（如将 token 加入黑名单）
        return ApiResponse.success("登出成功", null);
    }
}
