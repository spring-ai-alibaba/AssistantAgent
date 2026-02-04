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
package com.alibaba.assistant.agent.extension.evaluation.config;

import com.alibaba.assistant.agent.extension.evaluation.hook.BeforeAgentEvaluationHook;
import com.alibaba.assistant.agent.evaluation.DefaultEvaluationService;
import com.alibaba.assistant.agent.evaluation.EvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Codeact Evaluation 自动配置类
 * 负责创建 EvaluationService、ContextFactory 以及各个 Hook
 *
 * @author Assistant Agent Team
 */
@Configuration
@EnableConfigurationProperties(CodeactEvaluationProperties.class)
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.evaluation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CodeactEvaluationAutoConfiguration {

	private static final Logger log = LoggerFactory.getLogger(CodeactEvaluationAutoConfiguration.class);

	/**
	 * 提供默认的 EvaluationService Bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public EvaluationService evaluationService() {
		log.info("CodeactEvaluationAutoConfiguration#evaluationService - reason=创建默认 EvaluationService");
		return new DefaultEvaluationService();
	}

	/**
	 * 提供 CodeactEvaluationContextFactory Bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public CodeactEvaluationContextFactory codeactEvaluationContextFactory() {
		log.info("CodeactEvaluationAutoConfiguration#codeactEvaluationContextFactory - reason=创建 EvaluationContextFactory");
		return new CodeactEvaluationContextFactory();
	}

	/**
	 * 输入路由评估 Hook
	 */
	@Bean
	@ConditionalOnProperty(
			prefix = "spring.ai.alibaba.codeact.extension.evaluation.input-routing",
			name = "enabled",
			havingValue = "true"
	)
	public BeforeAgentEvaluationHook inputRoutingEvaluationHook(
			EvaluationService evaluationService,
			CodeactEvaluationContextFactory contextFactory,
			CodeactEvaluationProperties properties) {
		log.info("CodeactEvaluationAutoConfiguration#inputRoutingEvaluationHook - reason=创建输入路由评估 Hook");

		return BeforeAgentEvaluationHook.builder(
						evaluationService,
						properties.getInputRouting().getSuiteId())
				.contextBuilder((state, config) -> contextFactory.createInputRoutingContext(state, config))
				.enabled(properties.getInputRouting().isEnabled())
				.async(properties.isAsync())
				.timeoutMs(properties.getTimeoutMs())
				.order(100)
				.build();
	}
}
