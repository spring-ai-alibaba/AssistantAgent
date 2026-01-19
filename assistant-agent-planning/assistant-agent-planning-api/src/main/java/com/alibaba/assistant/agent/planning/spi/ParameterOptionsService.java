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
package com.alibaba.assistant.agent.planning.spi;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.exception.OptionsSourceException;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;

import java.util.List;

/**
 * SPI for fetching parameter options from various sources.
 * Implementations should delegate to specific handlers based on source type.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface ParameterOptionsService {

    /**
     * Fetch parameter options based on configuration.
     *
     * @param config Options source configuration
     * @return List of option items
     * @throws OptionsSourceException if fetching fails
     */
    List<OptionItem> fetchOptions(OptionsSourceConfig config);

    /**
     * Check if this service supports a specific source type.
     *
     * @param sourceType Source type enum
     * @return true if supported
     */
    boolean supports(OptionsSourceConfig.SourceType sourceType);

    /**
     * Get service name for logging and identification.
     *
     * @return Service name
     */
    String getName();
}
