/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.data.config;

import com.alibaba.assistant.agent.data.nl2sql.DefaultNl2SqlService;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import com.alibaba.assistant.agent.data.spi.Nl2SqlService;
import com.alibaba.assistant.agent.data.spi.SchemaProvider;
import com.alibaba.assistant.agent.data.spi.SqlExecutionProvider;
import com.alibaba.assistant.agent.data.tool.Nl2SqlCodeactTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for NL2SQL.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(ChatModel.class)
@ConditionalOnProperty(
        prefix = "spring.assistant-agent.data.nl2sql",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@EnableConfigurationProperties(Nl2SqlProperties.class)
public class Nl2SqlAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public Nl2SqlService nl2SqlService(SchemaProvider schemaProvider,
                                       ChatModel chatModel,
                                       SqlExecutionProvider sqlExecutionProvider,
                                       DatasourceProvider datasourceProvider,
                                       Nl2SqlProperties properties) {
        log.info("Nl2SqlAutoConfiguration - Initializing NL2SQL service with schema filter threshold: {}",
                properties.getSchemaFilterThreshold());
        return new DefaultNl2SqlService(schemaProvider, chatModel, sqlExecutionProvider, datasourceProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public Nl2SqlCodeactTool nl2SqlCodeactTool(Nl2SqlService nl2SqlService) {
        log.info("Nl2SqlAutoConfiguration - Registering nl2sql tool for Agent");
        return new Nl2SqlCodeactTool(nl2SqlService);
    }
}
