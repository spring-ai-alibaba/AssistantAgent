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

import com.alibaba.assistant.agent.data.model.Nl2SqlSourceConfig;
import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.data.spi.Nl2SqlService;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Handler for NL2SQL-based option sources.
 * Delegates to Nl2SqlService from data module.
 *
 * <p>This handler is only registered when Nl2SqlService bean is available.
 * This allows the parameter options service to function with other handlers
 * when NL2SQL module is disabled.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
@ConditionalOnBean(Nl2SqlService.class)
class Nl2SqlOptionsHandler implements OptionsSourceHandler {

    private static final Logger logger = LoggerFactory.getLogger(Nl2SqlOptionsHandler.class);

    private final Nl2SqlService nl2SqlService;

    public Nl2SqlOptionsHandler(Nl2SqlService nl2SqlService) {
        this.nl2SqlService = nl2SqlService;
    }

    @Override
    public List<OptionItem> handle(String systemId, Object specificConfig) {
        Nl2SqlSourceConfig config = (Nl2SqlSourceConfig) specificConfig;

        try {
            return nl2SqlService.generateAndExecute(
                    systemId,
                    config.getDescription(),
                    config.getLabelColumn(),
                    config.getValueColumn()
            );
        } catch (Exception e) {
            logger.error("Nl2SqlOptionsHandler#handle - NL2SQL query failed: systemId={}, description={}, error={}",
                    systemId, config.getDescription(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public OptionsSourceConfig.SourceType supportedType() {
        return OptionsSourceConfig.SourceType.NL2SQL;
    }
}
