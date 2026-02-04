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

import com.alibaba.assistant.agent.evaluation.model.CriterionExecutionContext;
import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.assistant.agent.evaluation.model.CriterionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Merge lists aggregation strategy - combines all list results from batches into a single merged list.
 * Suitable for scenarios like "collect all matching tools from different batches".
 * 
 * Features:
 * - Merges all list results into a single list
 * - Removes duplicates while preserving order (first occurrence wins)
 * - Handles null and empty lists gracefully
 *
 * @author Assistant Agent Team
 */
public class MergeListsAggregationStrategy implements BatchAggregationStrategy {

	private static final Logger logger = LoggerFactory.getLogger(MergeListsAggregationStrategy.class);

	@Override
	public CriterionResult aggregate(CriterionExecutionContext baseContext, List<CriterionResult> batchResults) {
		CriterionResult aggregated = new CriterionResult();
		aggregated.setCriterionName(baseContext.getCriterion().getName());
		aggregated.setStartTimeMillis(System.currentTimeMillis());

		if (batchResults == null || batchResults.isEmpty()) {
			logger.debug("Empty batch results for criterion '{}', returning empty list", 
				baseContext.getCriterion().getName());
			aggregated.setStatus(CriterionStatus.SUCCESS);
			aggregated.setValue(new ArrayList<>());
			aggregated.setReason("No batch results to merge (empty collection)");
			aggregated.setEndTimeMillis(System.currentTimeMillis());
			return aggregated;
		}

		// Check for errors first
		boolean hasError = batchResults.stream().anyMatch(r -> r.getStatus() == CriterionStatus.ERROR);
		if (hasError) {
			logger.warn("At least one batch result has ERROR status for criterion '{}'",
				baseContext.getCriterion().getName());
			aggregated.setStatus(CriterionStatus.ERROR);
			aggregated.setErrorMessage("One or more batch evaluations failed with ERROR");
			aggregated.setValue(new ArrayList<>());
			aggregated.setEndTimeMillis(System.currentTimeMillis());
			return aggregated;
		}

		// Check for timeouts
		boolean hasTimeout = batchResults.stream().anyMatch(r -> r.getStatus() == CriterionStatus.TIMEOUT);
		if (hasTimeout) {
			logger.warn("At least one batch result has TIMEOUT status for criterion '{}'",
				baseContext.getCriterion().getName());
			aggregated.setStatus(CriterionStatus.TIMEOUT);
			aggregated.setValue(new ArrayList<>());
			aggregated.setEndTimeMillis(System.currentTimeMillis());
			return aggregated;
		}

		// Merge all list results, using LinkedHashSet to preserve order and remove duplicates
		Set<Object> mergedSet = new LinkedHashSet<>();
		int totalItemsBeforeDedupe = 0;

		for (CriterionResult batchResult : batchResults) {
			Object value = batchResult.getValue();
			if (value instanceof Collection) {
				Collection<?> listValue = (Collection<?>) value;
				totalItemsBeforeDedupe += listValue.size();
				mergedSet.addAll(listValue);
			} else if (value instanceof Object[]) {
				Object[] arrayValue = (Object[]) value;
				totalItemsBeforeDedupe += arrayValue.length;
				for (Object item : arrayValue) {
					mergedSet.add(item);
				}
			} else if (value != null) {
				// Single value, add it
				totalItemsBeforeDedupe++;
				mergedSet.add(value);
			}
		}

		List<Object> mergedList = new ArrayList<>(mergedSet);

		aggregated.setStatus(CriterionStatus.SUCCESS);
		aggregated.setValue(mergedList);
		aggregated.setReason(String.format(
			"Merged %d batch results: %d items before deduplication, %d items after deduplication",
			batchResults.size(), totalItemsBeforeDedupe, mergedList.size()));

		aggregated.setEndTimeMillis(System.currentTimeMillis());

		logger.debug("MERGE_LISTS aggregation for '{}': merged {} batches into {} unique items",
			baseContext.getCriterion().getName(), batchResults.size(), mergedList.size());

		return aggregated;
	}

	@Override
	public String getStrategyId() {
		return "MERGE_LISTS";
	}
}
