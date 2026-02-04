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

package com.alibaba.assistant.agent.extension.learning.config;

import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import com.alibaba.assistant.agent.extension.learning.extractor.ExperienceLearningExtractor;
import com.alibaba.assistant.agent.extension.learning.hook.AfterAgentLearningHook;
import com.alibaba.assistant.agent.extension.learning.hook.AfterModelLearningHook;
import com.alibaba.assistant.agent.extension.learning.interceptor.LearningToolInterceptor;
import com.alibaba.assistant.agent.extension.learning.internal.AsyncLearningHandler;
import com.alibaba.assistant.agent.extension.learning.internal.DefaultLearningExecutor;
import com.alibaba.assistant.agent.extension.learning.internal.DefaultLearningStrategy;
import com.alibaba.assistant.agent.extension.learning.internal.InMemoryLearningRepository;
import com.alibaba.assistant.agent.extension.learning.internal.StoreLearningRepository;
import com.alibaba.assistant.agent.extension.learning.internal.*;
import com.alibaba.assistant.agent.extension.learning.offline.ExperienceLearningGraph;
import com.alibaba.assistant.agent.extension.learning.offline.LearningScheduleConfig;
import com.alibaba.assistant.agent.extension.learning.offline.LearningScheduledTask;
import com.alibaba.assistant.agent.extension.learning.offline.OfflineLearningGraph;
import com.alibaba.assistant.agent.extension.learning.repository.ExperienceLearningRepository;
import com.alibaba.assistant.agent.extension.learning.spi.LearningExecutor;
import com.alibaba.assistant.agent.extension.learning.spi.LearningExtractor;
import com.alibaba.assistant.agent.extension.learning.spi.LearningRepository;
import com.alibaba.assistant.agent.extension.learning.spi.LearningStrategy;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.store.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 学习模块自动配置类
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.learning", name = "enabled",
		havingValue = "true")
@EnableConfigurationProperties(LearningExtensionProperties.class)
public class LearningExtensionAutoConfiguration {

	private static final Logger log = LoggerFactory.getLogger(LearningExtensionAutoConfiguration.class);

	public LearningExtensionAutoConfiguration() {
		log.info("LearningExtensionAutoConfiguration#constructor - reason=learning extension auto-configuration initialized");
	}

	/**
	 * 配置异步学习处理器
	 * <p>
	 * 如果提供了名为 "learningAsyncExecutor" 的 ExecutorService Bean，
	 * 则使用该自定义 ExecutorService（支持 trace 上下文传递等特性）。
	 * 否则使用默认的线程池配置。
	 *
	 * @param properties 学习扩展配置
	 * @param customExecutor 可选的自定义 ExecutorService（名为 "learningAsyncExecutor"）
	 * @return AsyncLearningHandler
	 */
	@Bean
	@ConditionalOnMissingBean
	public AsyncLearningHandler asyncLearningHandler(
			LearningExtensionProperties properties,
			@Autowired(required = false) @Qualifier("learningAsyncExecutor") ExecutorService customExecutor) {
		if (customExecutor != null) {
			log.info(
					"LearningExtensionAutoConfiguration#asyncLearningHandler - reason=creating async learning handler with custom executor, executorType={}",
					customExecutor.getClass().getSimpleName());
			return new AsyncLearningHandler(customExecutor);
		}

		LearningExtensionProperties.AsyncConfig asyncConfig = properties.getAsync();
		log.info(
				"LearningExtensionAutoConfiguration#asyncLearningHandler - reason=creating async learning handler with default executor, threadPoolSize={}, queueCapacity={}",
				asyncConfig.getThreadPoolSize(), asyncConfig.getQueueCapacity());
		return new AsyncLearningHandler(asyncConfig.getThreadPoolSize(), asyncConfig.getQueueCapacity());
	}

	/**
	 * 配置学习策略
	 */
	@Bean
	@ConditionalOnMissingBean
	public LearningStrategy learningStrategy(List<LearningExtractor<?>> extractors,
			List<LearningRepository<?>> repositories, LearningExtensionProperties properties) {
		boolean asyncEnabled = properties.getAsync().isEnabled();
		log.info(
				"LearningExtensionAutoConfiguration#learningStrategy - reason=creating learning strategy, asyncEnabled={}",
				asyncEnabled);
		return new DefaultLearningStrategy(extractors, repositories, asyncEnabled);
	}

	/**
	 * 配置学习执行器
	 */
	@Bean
	@ConditionalOnMissingBean
	public LearningExecutor learningExecutor(List<LearningExtractor<?>> extractors,
			List<LearningRepository<?>> repositories, LearningStrategy learningStrategy,
			AsyncLearningHandler asyncHandler) {
		log.info(
				"LearningExtensionAutoConfiguration#learningExecutor - reason=creating learning executor, extractorCount={}, repositoryCount={}",
				extractors.size(), repositories.size());
		return new DefaultLearningExecutor(extractors, repositories, learningStrategy, asyncHandler);
	}

	/**
	 * 配置内存学习仓库（通用类型）
	 */
	@Bean
	@ConditionalOnMissingBean(name = "inMemoryLearningRepository")
	@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.learning.storage", name = "type",
			havingValue = "in-memory", matchIfMissing = true)
	public LearningRepository<?> inMemoryLearningRepository() {
		log.info(
				"LearningExtensionAutoConfiguration#inMemoryLearningRepository - reason=creating in-memory learning repository");
		return new InMemoryLearningRepository<>(Object.class);
	}

	/**
	 * 配置Store持久化学习仓库
	 */
	@Bean
	@ConditionalOnMissingBean(name = "storeLearningRepository")
	@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.learning.storage", name = "type",
			havingValue = "store")
	public LearningRepository<?> storeLearningRepository(Store store) {
		log.info(
				"LearningExtensionAutoConfiguration#storeLearningRepository - reason=creating store-based learning repository");
		return new StoreLearningRepository<>(store, Object.class);
	}

	/**
	 * 配置经验学习提取器（LLM智能版）
	 */
	@Bean
	@ConditionalOnMissingBean
	public ExperienceLearningExtractor experienceLearningExtractor(ChatModel chatModel) {
		if (chatModel == null) {
			throw new IllegalStateException("ChatModel is required for ExperienceLearningExtractor. Please configure Spring AI ChatModel bean.");
		}
		log.info("LearningExtensionAutoConfiguration#experienceLearningExtractor - reason=creating LLM-based experience learning extractor");
		return new ExperienceLearningExtractor(chatModel);
	}

	/**
	 * 配置经验学习仓库
	 */
	@Bean
	@ConditionalOnMissingBean(name = "experienceLearningRepository")
	public ExperienceLearningRepository experienceLearningRepository(ExperienceRepository experienceRepository) {
		log.info("LearningExtensionAutoConfiguration#experienceLearningRepository - reason=creating experience learning repository");
		return new ExperienceLearningRepository(experienceRepository);
	}

	/**
	 * 配置Agent执行后学习Hook
	 */
	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.learning.online.after-agent", name = "enabled", havingValue = "true", matchIfMissing = true)
	public AfterAgentLearningHook afterAgentLearningHook(LearningExecutor learningExecutor,
			LearningStrategy learningStrategy, LearningExtensionProperties properties) {
		String learningType = properties.getOnline().getAfterAgent().getLearningTypes().get(0);
		log.info(
				"LearningExtensionAutoConfiguration#afterAgentLearningHook - reason=creating after agent learning hook, learningType={}",
				learningType);
		return new AfterAgentLearningHook(learningExecutor, learningStrategy, learningType);
	}

	/**
	 * 配置模型调用后学习Hook
	 */
	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.learning.online.after-model", name = "enabled",
			havingValue = "true")
	public AfterModelLearningHook afterModelLearningHook(LearningExecutor learningExecutor,
			LearningStrategy learningStrategy) {
		log.info(
				"LearningExtensionAutoConfiguration#afterModelLearningHook - reason=creating after model learning hook");
		return new AfterModelLearningHook(learningExecutor, learningStrategy, "model_call");
	}

	/**
	 * 配置工具调用学习拦截器
	 */
	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.learning.online.tool-interceptor",
			name = "enabled", havingValue = "true")
	public LearningToolInterceptor learningToolInterceptor(LearningExecutor learningExecutor,
			LearningStrategy learningStrategy, LearningExtensionProperties properties) {
		java.util.Set<String> includedTools = new java.util.HashSet<>(
				properties.getOnline().getToolInterceptor().getIncludedTools());
		log.info(
				"LearningExtensionAutoConfiguration#learningToolInterceptor - reason=creating learning tool interceptor, includedToolsCount={}",
				includedTools.size());
		return new LearningToolInterceptor(learningExecutor, learningStrategy, "tool_usage", includedTools);
	}

	/**
	 * 配置离线学习图
	 */
	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.learning.offline", name = "enabled",
			havingValue = "true")
	public OfflineLearningGraph offlineLearningGraph(LearningExecutor learningExecutor,
			List<LearningExtractor<?>> extractors, List<LearningRepository<?>> repositories) {
		log.info(
				"LearningExtensionAutoConfiguration#offlineLearningGraph - reason=creating offline learning graph");
		return new OfflineLearningGraph(learningExecutor, extractors, repositories, "offline_learning_graph");
	}

	/**
	 * 配置经验学习图
	 */
	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.learning.offline", name = "enabled",
			havingValue = "true")
	public ExperienceLearningGraph experienceLearningGraph(LearningExecutor learningExecutor,
			List<LearningExtractor<?>> extractors, List<LearningRepository<?>> repositories,
			ExperienceLearningExtractor experienceExtractor, ExperienceRepository experienceRepository,
			LearningExtensionProperties properties) {
		long lookbackHours = 24; // 默认24小时
		if (!properties.getOffline().getTasks().isEmpty()) {
			Long lookbackPeriod = properties.getOffline().getTasks().get(0).getLookbackPeriod();
			if (lookbackPeriod != null) {
				lookbackHours = lookbackPeriod;
			}
		}
		log.info(
				"LearningExtensionAutoConfiguration#experienceLearningGraph - reason=creating experience learning graph, lookbackHours={}",
				lookbackHours);
		return new ExperienceLearningGraph(learningExecutor, extractors, repositories, experienceExtractor,
				experienceRepository, lookbackHours);
	}

	/**
	 * 配置学习调度任务
	 */
	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.learning.offline", name = "enabled",
			havingValue = "true")
	public LearningScheduledTask learningScheduledTask(ExperienceLearningGraph experienceLearningGraph,
			LearningExtensionProperties properties) {
		// Build the graph
		CompiledGraph compiledGraph = experienceLearningGraph.buildLearningGraph();

		// Create schedule config from properties
		LearningScheduleConfig scheduleConfig = LearningScheduleConfig.builder()
			.taskName("experience_learning_task")
			.graphName("experience_learning_graph")
			.scheduleMode("cron")
			.cronExpression("0 0 2 * * ?") // 默认每天凌晨2点
			.lookbackPeriod(24L) // 默认24小时
			.build();

		// Override with configured values if available
		if (!properties.getOffline().getTasks().isEmpty()) {
			LearningExtensionProperties.ScheduledTaskConfig taskConfig = properties.getOffline().getTasks().get(0);
			if (taskConfig.getTaskName() != null) {
				scheduleConfig.setTaskName(taskConfig.getTaskName());
			}
			if (taskConfig.getGraphName() != null) {
				scheduleConfig.setGraphName(taskConfig.getGraphName());
			}
			if (taskConfig.getScheduleMode() != null) {
				scheduleConfig.setScheduleMode(taskConfig.getScheduleMode());
			}
			if (taskConfig.getCronExpression() != null) {
				scheduleConfig.setCronExpression(taskConfig.getCronExpression());
			}
			if (taskConfig.getIntervalMs() != null) {
				scheduleConfig.setIntervalMs(taskConfig.getIntervalMs());
			}
			if (taskConfig.getLookbackPeriod() != null) {
				scheduleConfig.setLookbackPeriod(taskConfig.getLookbackPeriod());
			}
		}

		log.info(
				"LearningExtensionAutoConfiguration#learningScheduledTask - reason=creating learning scheduled task, taskName={}, scheduleMode={}",
				scheduleConfig.getTaskName(), scheduleConfig.getScheduleMode());

		return new LearningScheduledTask(compiledGraph, scheduleConfig);
	}

}

