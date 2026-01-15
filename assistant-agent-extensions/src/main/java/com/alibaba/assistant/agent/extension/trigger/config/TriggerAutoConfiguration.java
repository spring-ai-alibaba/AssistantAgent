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

package com.alibaba.assistant.agent.extension.trigger.config;

import com.alibaba.assistant.agent.common.tools.TriggerCodeactTool;
import com.alibaba.assistant.agent.extension.trigger.backend.ExecutionBackend;
import com.alibaba.assistant.agent.extension.trigger.backend.SpringSchedulerExecutionBackend;
import com.alibaba.assistant.agent.extension.trigger.executor.TriggerExecutor;
import com.alibaba.assistant.agent.extension.trigger.manager.TriggerManager;
import com.alibaba.assistant.agent.extension.trigger.repository.InMemorySessionSnapshotRepository;
import com.alibaba.assistant.agent.extension.trigger.repository.InMemoryTriggerExecutionLogRepository;
import com.alibaba.assistant.agent.extension.trigger.repository.InMemoryTriggerRepository;
import com.alibaba.assistant.agent.extension.trigger.repository.SessionSnapshotRepository;
import com.alibaba.assistant.agent.extension.trigger.repository.TriggerExecutionLogRepository;
import com.alibaba.assistant.agent.extension.trigger.repository.TriggerRepository;
import com.alibaba.assistant.agent.extension.trigger.tools.TriggerCodeactToolFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;

/**
 * Trigger模块自动配置
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.trigger", name = "enabled",
		havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TriggerProperties.class)
public class TriggerAutoConfiguration {

	private static final Logger log = LoggerFactory.getLogger(TriggerAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public TriggerRepository triggerRepository() {
		log.info("TriggerAutoConfiguration triggerRepository 创建默认InMemoryTriggerRepository");
		return new InMemoryTriggerRepository();
	}

	@Bean
	@ConditionalOnMissingBean
	public TriggerExecutionLogRepository triggerExecutionLogRepository() {
		log.info("TriggerAutoConfiguration triggerExecutionLogRepository 创建默认InMemoryTriggerExecutionLogRepository");
		return new InMemoryTriggerExecutionLogRepository();
	}

	@Bean
	@ConditionalOnMissingBean
	public SessionSnapshotRepository sessionSnapshotRepository() {
		log.info("TriggerAutoConfiguration sessionSnapshotRepository 创建默认InMemorySessionSnapshotRepository");
		return new InMemorySessionSnapshotRepository();
	}

	@Bean(name = "triggerTaskScheduler")
	@ConditionalOnMissingBean(name = "triggerTaskScheduler")
	public TaskScheduler triggerTaskScheduler(TriggerProperties properties) {
		log.info("TriggerAutoConfiguration triggerTaskScheduler 创建调度器, poolSize={}",
				properties.getScheduler().getPoolSize());
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(properties.getScheduler().getPoolSize());
		scheduler.setThreadNamePrefix("trigger-scheduler-");
		scheduler.setWaitForTasksToCompleteOnShutdown(true);
		scheduler.setAwaitTerminationSeconds(properties.getScheduler().getAwaitTerminationSeconds());
		scheduler.initialize();
		return scheduler;
	}

	@Bean
	@ConditionalOnMissingBean
	public TriggerExecutor triggerExecutor(SessionSnapshotRepository snapshotRepository,
			TriggerProperties properties) {
		TriggerProperties.ExecutionConfig config = properties.getExecution();
		log.info("TriggerAutoConfiguration triggerExecutor 创建触发器执行器, timeout={}ms, allowIO={}, allowNativeAccess={}",
				config.getExecutionTimeout(), config.isAllowIO(), config.isAllowNativeAccess());
		return new TriggerExecutor(
				snapshotRepository,
				config.isAllowIO(),
				config.isAllowNativeAccess(),
				config.getExecutionTimeout()
		);
	}

	@Bean
	@ConditionalOnMissingBean
	public ExecutionBackend executionBackend(TaskScheduler triggerTaskScheduler,
			TriggerExecutionLogRepository executionLogRepository,
			TriggerExecutor triggerExecutor) {
		log.info("TriggerAutoConfiguration executionBackend 创建执行后端并设置回调");
		SpringSchedulerExecutionBackend backend = new SpringSchedulerExecutionBackend(
				triggerTaskScheduler, executionLogRepository);
		// 设置执行回调
		backend.setExecutionCallback(triggerExecutor);
		return backend;
	}

	@Bean
	@ConditionalOnMissingBean
	public TriggerManager triggerManager(TriggerRepository triggerRepository,
			TriggerExecutionLogRepository executionLogRepository, ExecutionBackend executionBackend) {
		log.info("TriggerAutoConfiguration triggerManager 创建触发器管理器");
		return new TriggerManager(triggerRepository, executionLogRepository, executionBackend);
	}

	@Bean
	public TriggerCodeactToolFactory triggerCodeactToolFactory(TriggerManager triggerManager) {
		log.info("TriggerAutoConfiguration triggerCodeactToolFactory 创建TriggerCodeactTool工厂");
		return new TriggerCodeactToolFactory(triggerManager);
	}

	/**
	 * Trigger工具列表Bean - 直接作为List返回，让Spring自动注入
	 */
	@Bean
	public List<TriggerCodeactTool> triggerCodeactTools(TriggerCodeactToolFactory factory) {
		log.info("TriggerAutoConfiguration triggerCodeactTools 开始创建Trigger工具");

		List<TriggerCodeactTool> tools = factory.createTools();

		log.info("TriggerAutoConfiguration triggerCodeactTools Trigger工具创建完成, count={}", tools.size());

		// 打印每个工具的详情
		for (int i = 0; i < tools.size(); i++) {
			TriggerCodeactTool tool = tools.get(i);
			log.info("TriggerAutoConfiguration triggerCodeactTools 创建Trigger工具, index={}, name={}, description={}",
				i + 1, tool.getToolDefinition().name(), tool.getToolDefinition().description());
		}

		return tools;
	}

}

