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
import com.alibaba.assistant.agent.planning.model.StaticOptionsConfig;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Handler for static option lists.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
class StaticOptionsHandler implements OptionsSourceHandler {

    /**
     * Handle option fetching for static source type.
     * Returns the predefined list of options from the configuration.
     *
     * @param systemId Datasource identifier (not used for static options)
     * @param specificConfig StaticOptionsConfig containing the option list
     * @return List of option items, or empty list if options are null
     * @throws IllegalArgumentException if specificConfig is not StaticOptionsConfig
     */
    @Override
    public List<OptionItem> handle(String systemId, Object specificConfig) {
        if (!(specificConfig instanceof StaticOptionsConfig)) {
            throw new IllegalArgumentException(
                "Expected StaticOptionsConfig but got: " +
                (specificConfig != null ? specificConfig.getClass().getName() : "null"));
        }
        StaticOptionsConfig config = (StaticOptionsConfig) specificConfig;
        List<OptionItem> options = config.getOptions();
        return options != null ? options : Collections.emptyList();
    }

    /**
     * Returns the source type this handler supports.
     *
     * @return STATIC source type
     */
    @Override
    public OptionsSourceConfig.SourceType supportedType() {
        return OptionsSourceConfig.SourceType.STATIC;
    }
}
