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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Trigger模块配置属性
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "spring.ai.alibaba.codeact.extension.trigger")
public class TriggerProperties {

	/**
	 * 是否启用Trigger模块
	 */
	private boolean enabled = true;

	/**
	 * 调度器配置
	 */
	private SchedulerConfig scheduler = new SchedulerConfig();

	/**
	 * 执行配置
	 */
	private ExecutionConfig execution = new ExecutionConfig();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public SchedulerConfig getScheduler() {
		return scheduler;
	}

	public void setScheduler(SchedulerConfig scheduler) {
		this.scheduler = scheduler;
	}

	public ExecutionConfig getExecution() {
		return execution;
	}

	public void setExecution(ExecutionConfig execution) {
		this.execution = execution;
	}

	public static class SchedulerConfig {

		/**
		 * 调度器线程池大小
		 */
		private int poolSize = 10;

		/**
		 * 关闭时等待任务完成的超时时间（秒）
		 */
		private int awaitTerminationSeconds = 60;

		public int getPoolSize() {
			return poolSize;
		}

		public void setPoolSize(int poolSize) {
			this.poolSize = poolSize;
		}

		public int getAwaitTerminationSeconds() {
			return awaitTerminationSeconds;
		}

		public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
			this.awaitTerminationSeconds = awaitTerminationSeconds;
		}

	}

	public static class ExecutionConfig {

		/**
		 * 默认最大重试次数
		 */
		private int defaultMaxRetries = 3;

		/**
		 * 默认重试延迟（毫秒）
		 */
		private long defaultRetryDelay = 1000;

		/**
		 * 执行超时时间（毫秒），0表示不限制
		 */
		private long executionTimeout = 30000;

		/**
		 * 是否允许IO操作
		 */
		private boolean allowIO = false;

		/**
		 * 是否允许本地访问
		 */
		private boolean allowNativeAccess = false;

		public int getDefaultMaxRetries() {
			return defaultMaxRetries;
		}

		public void setDefaultMaxRetries(int defaultMaxRetries) {
			this.defaultMaxRetries = defaultMaxRetries;
		}

		public long getDefaultRetryDelay() {
			return defaultRetryDelay;
		}

		public void setDefaultRetryDelay(long defaultRetryDelay) {
			this.defaultRetryDelay = defaultRetryDelay;
		}

		public long getExecutionTimeout() {
			return executionTimeout;
		}

		public void setExecutionTimeout(long executionTimeout) {
			this.executionTimeout = executionTimeout;
		}

		public boolean isAllowIO() {
			return allowIO;
		}

		public void setAllowIO(boolean allowIO) {
			this.allowIO = allowIO;
		}

		public boolean isAllowNativeAccess() {
			return allowNativeAccess;
		}

		public void setAllowNativeAccess(boolean allowNativeAccess) {
			this.allowNativeAccess = allowNativeAccess;
		}

	}

}

