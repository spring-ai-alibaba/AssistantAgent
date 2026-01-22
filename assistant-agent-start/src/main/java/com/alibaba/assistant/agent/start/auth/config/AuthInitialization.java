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

package com.alibaba.assistant.agent.start.auth.config;

import com.alibaba.assistant.agent.start.auth.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 认证初始化器
 * 应用启动时初始化默认管理员账户
 */
@Component
public class AuthInitialization implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AuthInitialization.class);

    @Autowired
    private AuthenticationService authenticationService;

    @Override
    public void run(ApplicationArguments args) {
        logger.info("AuthInitialization#run - 初始化认证系统");

        try {
            authenticationService.initDefaultAdmin();
            logger.info("AuthInitialization#run - 认证系统初始化完成");
        } catch (Exception e) {
            logger.error("AuthInitialization#run - 认证系统初始化失败: {}", e.getMessage(), e);
        }
    }
}
