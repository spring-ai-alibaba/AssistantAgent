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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;

import java.util.List;

/**
 * Internal handler interface for specific option source types.
 * Each handler implements fetching logic for one source type.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
interface OptionsSourceHandler {

    /**
     * Handle option fetching for specific source type.
     *
     * @param systemId Datasource identifier (nullable)
     * @param specificConfig Type-specific configuration object
     * @return List of option items
     */
    List<OptionItem> handle(String systemId, Object specificConfig);

    /**
     * Source type this handler supports.
     *
     * @return Source type enum
     */
    OptionsSourceConfig.SourceType supportedType();
}
