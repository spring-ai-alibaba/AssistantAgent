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
package com.alibaba.assistant.agent.evaluation;

import com.alibaba.assistant.agent.evaluation.executor.GraphBasedEvaluationExecutor;
import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.assistant.agent.evaluation.model.EvaluationResult;
import com.alibaba.assistant.agent.evaluation.model.EvaluationSuite;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of EvaluationService
 * Uses graph-based executor for evaluation execution
 *
 * @author Assistant Agent Team
 */
public class DefaultEvaluationService implements EvaluationService {

	private static final Logger logger = LoggerFactory.getLogger(DefaultEvaluationService.class);

	private final GraphBasedEvaluationExecutor executor;
	private final Map<String, EvaluationSuite> suiteRegistry = new ConcurrentHashMap<>();
	private final ExecutorService asyncExecutor;

	public DefaultEvaluationService() {
		this.executor = new GraphBasedEvaluationExecutor();
		this.asyncExecutor = Executors.newCachedThreadPool();
	}

	public DefaultEvaluationService(ExecutorService asyncExecutor) {
		this.executor = new GraphBasedEvaluationExecutor();
		this.asyncExecutor = asyncExecutor;
	}

	public DefaultEvaluationService(GraphBasedEvaluationExecutor executor, ExecutorService asyncExecutor) {
		this.executor = executor;
		this.asyncExecutor = asyncExecutor;
	}

	@Override
	public EvaluationResult evaluate(EvaluationSuite suite, EvaluationContext context) {
		return evaluate(suite, context, null);
	}

	@Override
	public EvaluationResult evaluate(EvaluationSuite suite, EvaluationContext context,
									 Span parentSpan) {
		logger.info("DefaultEvaluationService#evaluate - reason=开始评估, suite={}, hasParent={}",
				suite.getName(), parentSpan != null);

		// 设置 parent Span 到 ThreadLocal
		if (parentSpan != null) {
			com.alibaba.assistant.agent.evaluation.observation.EvaluationObservationLifecycleListener
					.setParentSpan(parentSpan);
		}

		try {
			// Execute evaluation with parent span
			EvaluationResult result = executor.execute(suite, context, parentSpan);

			logger.info("DefaultEvaluationService#evaluate - reason=评估完成, suite={}", suite.getName());

			return result;
		} catch (Exception e) {
			logger.error("DefaultEvaluationService#evaluate - reason=评估失败, suite={}", suite.getName(), e);
			throw new RuntimeException("Evaluation execution failed", e);
		} finally {
			// 清理 ThreadLocal
			if (parentSpan != null) {
				com.alibaba.assistant.agent.evaluation.observation.EvaluationObservationLifecycleListener
						.clearParentSpan();
			}
		}
	}

	@Override
	public CompletableFuture<EvaluationResult> evaluateAsync(EvaluationSuite suite, EvaluationContext context) {
		return evaluateAsync(suite, context, null);
	}

	@Override
	public CompletableFuture<EvaluationResult> evaluateAsync(EvaluationSuite suite, EvaluationContext context,
															 Span parentSpan) {
		// 捕获当前的 parent Span 和 Context，在异步执行时传递
		final Span capturedParent = parentSpan;
		final Context capturedContext = parentSpan != null ? Context.current().with(parentSpan) : Context.current();

		return CompletableFuture.supplyAsync(() -> {
			// 在异步线程中恢复 trace context
			try (Scope ignored = capturedContext.makeCurrent()) {
				// 设置 parent Span 到 ThreadLocal，供 EvaluationObservationLifecycleListener 使用
				if (capturedParent != null) {
					com.alibaba.assistant.agent.evaluation.observation.EvaluationObservationLifecycleListener
							.setParentSpan(capturedParent);
				}
				try {
					return executor.execute(suite, context, capturedParent);
				} finally {
					// 清理 ThreadLocal
					com.alibaba.assistant.agent.evaluation.observation.EvaluationObservationLifecycleListener
							.clearParentSpan();
				}
			}
		}, asyncExecutor);
	}

	@Override
	public EvaluationSuite loadSuite(String suiteId) {
		EvaluationSuite suite = suiteRegistry.get(suiteId);
		if (suite == null) {
			throw new IllegalArgumentException("Evaluation suite not found: " + suiteId);
		}
		return suite;
	}

	@Override
	public void registerSuite(EvaluationSuite suite) {
		if (suite.getId() == null || suite.getId().isEmpty()) {
			throw new IllegalArgumentException("Suite ID cannot be null or empty");
		}
		suiteRegistry.put(suite.getId(), suite);
		logger.info("DefaultEvaluationService#registerSuite - reason=注册评估套件, suiteId={}", suite.getId());
	}

	/**
	 * Get the executor service used for batch processing
	 */
	public ExecutorService getExecutorService() {
		return executor.getExecutorService();
	}

	/**
	 * Shutdown both async executor and batch processing executor
	 */
	public void shutdown() {
		asyncExecutor.shutdown();
		executor.shutdown();
	}
}
