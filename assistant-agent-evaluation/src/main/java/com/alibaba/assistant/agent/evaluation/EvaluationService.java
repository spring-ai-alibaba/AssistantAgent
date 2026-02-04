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

import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.assistant.agent.evaluation.model.EvaluationResult;
import com.alibaba.assistant.agent.evaluation.model.EvaluationSuite;
import io.opentelemetry.api.trace.Span;

import java.util.concurrent.CompletableFuture;

/**
 * Service interface for evaluation execution
 * Provides unified API for executing evaluations
 *
 * @author Assistant Agent Team
 */
public interface EvaluationService {

	/**
	 * Execute evaluation synchronously
	 *
	 * @param suite Evaluation suite to execute
	 * @param context Evaluation context
	 * @return Evaluation result
	 */
	EvaluationResult evaluate(EvaluationSuite suite, EvaluationContext context);

	/**
	 * Execute evaluation synchronously with parent Span for tracing
	 *
	 * @param suite Evaluation suite to execute
	 * @param context Evaluation context
	 * @param parentSpan Parent span for establishing span hierarchy
	 * @return Evaluation result
	 */
	default EvaluationResult evaluate(EvaluationSuite suite, EvaluationContext context,
									  Span parentSpan) {
		// Default implementation ignores parentSpan for backward compatibility
		return evaluate(suite, context);
	}

	/**
	 * Execute evaluation asynchronously
	 *
	 * @param suite Evaluation suite to execute
	 * @param context Evaluation context
	 * @return CompletableFuture of evaluation result
	 */
	CompletableFuture<EvaluationResult> evaluateAsync(EvaluationSuite suite, EvaluationContext context);

	/**
	 * Execute evaluation asynchronously with parent Span for tracing
	 *
	 * @param suite Evaluation suite to execute
	 * @param context Evaluation context
	 * @param parentSpan Parent span for establishing span hierarchy
	 * @return CompletableFuture of evaluation result
	 */
	default CompletableFuture<EvaluationResult> evaluateAsync(EvaluationSuite suite, EvaluationContext context,
															  Span parentSpan) {
		// Default implementation ignores parentSpan for backward compatibility
		return evaluateAsync(suite, context);
	}

	/**
	 * Load a pre-configured evaluation suite by ID
	 *
	 * @param suiteId Suite identifier
	 * @return Evaluation suite
	 */
	EvaluationSuite loadSuite(String suiteId);

	/**
	 * Register a pre-configured evaluation suite
	 *
	 * @param suite Evaluation suite to register
	 */
	void registerSuite(EvaluationSuite suite);
}

