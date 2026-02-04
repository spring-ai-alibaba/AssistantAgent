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
package com.alibaba.assistant.agent.evaluation.aggregation;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for batch aggregation strategies.
 * Manages available aggregation strategies and provides lookup by strategy ID.
 *
 * @author Assistant Agent Team
 */
public class BatchAggregationStrategyRegistry {

	private static final Logger logger = LoggerFactory.getLogger(BatchAggregationStrategyRegistry.class);

	private final Map<String, BatchAggregationStrategy> strategies = new ConcurrentHashMap<>();

	public BatchAggregationStrategyRegistry() {
		// Register built-in strategies
		registerStrategy(new AnyTrueAggregationStrategy());
		registerStrategy(new AllTrueAggregationStrategy());
		registerStrategy(new MergeListsAggregationStrategy());
	}

	/**
	 * Register a batch aggregation strategy.
	 * @param strategy The strategy to register
	 */
	public void registerStrategy(BatchAggregationStrategy strategy) {
		if (strategy == null) {
			throw new IllegalArgumentException("Strategy cannot be null");
		}
		String strategyId = strategy.getStrategyId();
		if (strategyId == null || strategyId.trim().isEmpty()) {
			throw new IllegalArgumentException("Strategy ID cannot be null or empty");
		}
		strategies.put(strategyId, strategy);
		logger.info("Registered batch aggregation strategy: {}", strategyId);
	}

	/**
	 * Get a strategy by ID.
	 * @param strategyId The strategy identifier
	 * @return The strategy, or null if not found
	 */
	public BatchAggregationStrategy getStrategy(String strategyId) {
		if (strategyId == null || strategyId.trim().isEmpty()) {
			return null;
		}
		return strategies.get(strategyId);
	}

	/**
	 * Check if a strategy is registered.
	 * @param strategyId The strategy identifier
	 * @return true if registered, false otherwise
	 */
	public boolean hasStrategy(String strategyId) {
		return strategyId != null && strategies.containsKey(strategyId);
	}

	/**
	 * Get all registered strategy IDs.
	 * @return Set of strategy IDs
	 */
	public java.util.Set<String> getRegisteredStrategyIds() {
		return strategies.keySet();
	}
}

