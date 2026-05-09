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
package com.alibaba.assistant.agent.extension.prompt.config;

import com.alibaba.assistant.agent.extension.prompt.CodeactToolSignatureInjectionToolCallback;
import com.alibaba.assistant.agent.extension.prompt.PromptContributionToolCallback;
import com.alibaba.assistant.agent.extension.prompt.ReactPromptContributorModelHook;
import com.alibaba.assistant.agent.prompt.DefaultPromptContributorManager;
import com.alibaba.assistant.agent.prompt.PromptContributor;
import com.alibaba.assistant.agent.prompt.PromptContributorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Prompt Contributor 自动配置类
 * 负责创建 PromptContributorManager、PromptContributorModelHook 和占位 ToolCallback
 *
 * @author Assistant Agent Team
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.prompt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PromptContributorAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PromptContributorAutoConfiguration.class);

    /**
     * 提供默认的 PromptContributorManager Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public PromptContributorManager promptContributorManager(List<PromptContributor> contributors) {
        log.info("PromptContributorAutoConfiguration#promptContributorManager - reason=创建 PromptContributorManager, contributorCount={}",
                contributors != null ? contributors.size() : 0);
        return new DefaultPromptContributorManager(contributors);
    }

    /**
     * 提供 PromptContributorModelHook
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.prompt.react", name = "enabled", havingValue = "true")
    public ReactPromptContributorModelHook promptContributorModelHook(PromptContributorManager manager) {
        log.info("PromptContributorAutoConfiguration#promptContributorModelHook - reason=创建 PromptContributorModelHook");
        return new ReactPromptContributorModelHook(manager);
    }

    /**
     * 占位 ToolCallback：防止 LLM 看到 __prompt_contribution__ tool call 后困惑。
     * <p>需要在 Agent Builder 中通过 {@code .tools(promptContributionToolCallback)} 注册。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.prompt.react", name = "enabled", havingValue = "true")
    public PromptContributionToolCallback promptContributionToolCallback() {
        log.info("PromptContributorAutoConfiguration - reason=创建 PromptContributionToolCallback 占位工具");
        return PromptContributionToolCallback.getInstance();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.prompt.react", name = "enabled", havingValue = "true")
    public CodeactToolSignatureInjectionToolCallback codeactToolSignatureInjectionToolCallback() {
        log.info("PromptContributorAutoConfiguration - reason=创建 CodeactToolSignatureInjectionToolCallback 占位工具");
        return CodeactToolSignatureInjectionToolCallback.getInstance();
    }
}
