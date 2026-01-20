/*
 * Copyright 2025 Alibaba Group Holding Limited.
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

package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.cache.OptionsCache;
import com.alibaba.assistant.agent.planning.exception.OptionsSourceException;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import com.alibaba.assistant.agent.planning.spi.ParameterOptionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of ParameterOptionsService.
 * <p>
 * Routes option fetch requests to appropriate handlers based on source type,
 * manages caching, and handles errors gracefully.
 *
 * @author Claude
 * @since 2026-01-20
 */
@Service
public class DefaultParameterOptionsService implements ParameterOptionsService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultParameterOptionsService.class);

    private final Map<OptionsSourceConfig.SourceType, OptionsSourceHandler> handlers;

    private final OptionsCache cache;

    /**
     * Constructor with dependency injection.
     *
     * @param handlerList list of all available option source handlers
     * @param cache cache instance for storing option results
     */
    public DefaultParameterOptionsService(List<OptionsSourceHandler> handlerList, OptionsCache cache) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(OptionsSourceHandler::supportedType, handler -> handler));
        this.cache = cache;
        logger.info("DefaultParameterOptionsService initialized with {} handlers", handlers.size());
    }

    @Override
    public List<OptionItem> fetchOptions(OptionsSourceConfig config) {
        // 1. Default type to NL2SQL if null
        OptionsSourceConfig.SourceType type = config.getType();
        if (type == null) {
            type = OptionsSourceConfig.SourceType.NL2SQL;
            logger.debug("DefaultParameterOptionsService#fetchOptions - No type specified, using default: NL2SQL");
        }

        // 2. Build cache key
        String cacheKey = buildCacheKey(config, type);

        // 3. Check cache
        List<OptionItem> cached = cache.get(cacheKey);
        if (cached != null) {
            logger.debug("DefaultParameterOptionsService#fetchOptions - Cache hit: key={}", cacheKey);
            return cached;
        }

        // 4. Find handler
        OptionsSourceHandler handler = handlers.get(type);
        if (handler == null) {
            throw new OptionsSourceException("No handler found for type: " + type);
        }

        // 5. Execute handler with try-catch for graceful degradation
        try {
            logger.debug("DefaultParameterOptionsService#fetchOptions - Fetching options: type={}, systemId={}",
                    type, config.getSystemId());

            List<OptionItem> options = handler.handle(config.getSystemId(), config.getConfig());

            // 6. Cache result before returning
            cache.put(cacheKey, options);

            logger.info("DefaultParameterOptionsService#fetchOptions - Success: type={}, systemId={}, count={}",
                    type, config.getSystemId(), options.size());

            return options;
        } catch (Exception e) {
            logger.error("DefaultParameterOptionsService#fetchOptions - Failed: type={}, systemId={}, error={}",
                    type, config.getSystemId(), e.getMessage(), e);
            return Collections.emptyList(); // Graceful degradation
        }
    }

    /**
     * Builds cache key from configuration.
     * Format: "{type}:{systemId}:{configHashCode}"
     *
     * @param config original configuration
     * @param type resolved source type (after defaulting)
     * @return cache key string
     */
    private String buildCacheKey(OptionsSourceConfig config, OptionsSourceConfig.SourceType type) {
        return String.format("%s:%s:%s",
                type,
                config.getSystemId(),
                config.getConfig() != null ? config.getConfig().hashCode() : "null");
    }

    @Override
    public boolean supports(OptionsSourceConfig.SourceType sourceType) {
        return handlers.containsKey(sourceType);
    }

    @Override
    public String getName() {
        return "DefaultParameterOptionsService";
    }
}
