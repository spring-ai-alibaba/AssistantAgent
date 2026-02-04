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

package com.alibaba.assistant.agent.extension.learning.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * 异步学习处理器
 * 管理异步学习任务的执行，包括线程池管理和任务拒绝策略
 * <p>
 * 支持自定义 ExecutorService 注入，允许调用方传入支持 trace 上下文传递的线程池。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class AsyncLearningHandler {

	private static final Logger log = LoggerFactory.getLogger(AsyncLearningHandler.class);

	private final ExecutorService executorService;
	private final boolean externalExecutor;

	/**
	 * 使用默认线程池配置创建 AsyncLearningHandler
	 *
	 * @param threadPoolSize 线程池大小
	 * @param queueCapacity 队列容量
	 */
	public AsyncLearningHandler(int threadPoolSize, int queueCapacity) {
		this.executorService = new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 60L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(queueCapacity), new ThreadFactory() {
					private int count = 0;

					@Override
					public Thread newThread(Runnable r) {
						return new Thread(r, "learning-async-" + (count++));
					}
				}, new ThreadPoolExecutor.CallerRunsPolicy());
		this.externalExecutor = false;

		log.info(
				"AsyncLearningHandler#constructor - reason=async learning handler initialized with default executor, threadPoolSize={}, queueCapacity={}",
				threadPoolSize, queueCapacity);
	}

	/**
	 * 使用自定义 ExecutorService 创建 AsyncLearningHandler
	 * <p>
	 * 允许调用方传入支持 trace 上下文传递的线程池（如 TraceAwareExecutorService），
	 * 确保异步任务的 traceId 与父线程保持一致。
	 *
	 * @param executorService 自定义的 ExecutorService
	 */
	public AsyncLearningHandler(ExecutorService executorService) {
		this.executorService = executorService;
		this.externalExecutor = true;

		log.info(
				"AsyncLearningHandler#constructor - reason=async learning handler initialized with custom executor, executorType={}",
				executorService.getClass().getSimpleName());
	}

	/**
	 * 异步执行任务
	 * @param task 任务
	 * @param <T> 任务返回类型
	 * @return CompletableFuture
	 */
	public <T> CompletableFuture<T> executeAsync(Callable<T> task) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return task.call();
			}
			catch (Exception e) {
				log.error("AsyncLearningHandler#executeAsync - reason=async task execution failed", e);
				throw new CompletionException(e);
			}
		}, executorService);
	}

	/**
	 * 关闭线程池
	 * <p>
	 * 注意：如果使用的是外部传入的 ExecutorService，则不会关闭它，
	 * 由外部调用方负责管理其生命周期。
	 */
	public void shutdown() {
		if (externalExecutor) {
			log.info("AsyncLearningHandler#shutdown - reason=skipping shutdown for external executor");
			return;
		}

		log.info("AsyncLearningHandler#shutdown - reason=shutting down async learning handler");
		executorService.shutdown();
		try {
			if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
				executorService.shutdownNow();
			}
		}
		catch (InterruptedException e) {
			executorService.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

}

