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
package com.alibaba.assistant.agent.start;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

/**
 * Assistant Agent Application
 *
 * <p>This example demonstrates all Assistant Agent features with:
 * <ul>
 * <li>Demo experience data initialization</li>
 * <li>FastIntent quick response patterns</li>
 * <li>Custom evaluation suites</li>
 * <li>Custom reply tools</li>
 * <li>Hook registration diagnostics</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * export DASHSCOPE_API_KEY=your-api-key
 * mvn -pl assistant-agent-start spring-boot:run
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.alibaba.assistant.agent.start",
        "com.alibaba.assistant.agent.autoconfigure",
    "com.alibaba.assistant.agent.extension",
    "com.alibaba.assistant.agent.planning"
})
@MapperScan({
    "com.alibaba.assistant.agent.start.auth.mapper",
    "com.alibaba.assistant.agent.start.persistence.*.mapper",
    "com.alibaba.assistant.agent.planning.persistence.mapper"
})
public class AssistantAgentApplication {

    private static final Logger logger = LoggerFactory.getLogger(AssistantAgentApplication.class);

    public static void main(String[] args) {
        logger.info("AssistantAgentApplication#main - reason=Starting Assistant Agent Full Example");
        SpringApplication.run(AssistantAgentApplication.class, args);
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> applicationReadyEventListener(Environment environment) {
        return event -> {
            String port = environment.getProperty("server.port", "8080");
            String contextPath = environment.getProperty("server.servlet.context-path", "");
            String accessUrl = "http://localhost:" + port + contextPath + "/chatui/index.html";
            System.out.println("\n🎉========================================🎉");
            System.out.println("✅ Assistant Agent (Full Example) is ready!");
            System.out.println("🚀 Chat with your agent: " + accessUrl);
            System.out.println("📚 Demo experiences loaded");
            System.out.println("🎉========================================🎉\n");
        };
    }
}
