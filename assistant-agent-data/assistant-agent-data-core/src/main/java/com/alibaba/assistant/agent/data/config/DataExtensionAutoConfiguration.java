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
package com.alibaba.assistant.agent.data.config;

import com.alibaba.assistant.agent.data.provider.DefaultSchemaProvider;
import com.alibaba.assistant.agent.data.provider.DefaultSqlExecutionProvider;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import com.alibaba.assistant.agent.data.spi.SchemaProvider;
import com.alibaba.assistant.agent.data.spi.SqlExecutionProvider;
import com.alibaba.assistant.agent.data.tool.ExecuteSqlCodeactTool;
import com.alibaba.assistant.agent.data.tool.QuerySchemaCodeactTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for Data Extension.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.data", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DataExtensionProperties.class)
@ComponentScan(basePackages = "com.alibaba.assistant.agent.data")
public class DataExtensionAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DataExtensionAutoConfiguration.class);

    public DataExtensionAutoConfiguration() {
        logger.info("DataExtensionAutoConfiguration - initializing data extension");
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlExecutionProvider sqlExecutionProvider(DatasourceProvider datasourceProvider) {
        logger.info("DataExtensionAutoConfiguration - creating DefaultSqlExecutionProvider");
        return new DefaultSqlExecutionProvider(datasourceProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.data.schema", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SchemaProvider schemaProvider(DatasourceProvider datasourceProvider) {
        logger.info("DataExtensionAutoConfiguration - creating DefaultSchemaProvider");
        return new DefaultSchemaProvider(datasourceProvider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.data.tools", name = "execute-sql-enabled", havingValue = "true", matchIfMissing = true)
    public ExecuteSqlCodeactTool executeSqlCodeactTool(SqlExecutionProvider sqlExecutionProvider) {
        logger.info("DataExtensionAutoConfiguration - creating ExecuteSqlCodeactTool");
        return new ExecuteSqlCodeactTool(sqlExecutionProvider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.data.tools", name = "query-schema-enabled", havingValue = "true", matchIfMissing = true)
    public QuerySchemaCodeactTool querySchemaCodeactTool(SchemaProvider schemaProvider) {
        logger.info("DataExtensionAutoConfiguration - creating QuerySchemaCodeactTool");
        return new QuerySchemaCodeactTool(schemaProvider);
    }
}
