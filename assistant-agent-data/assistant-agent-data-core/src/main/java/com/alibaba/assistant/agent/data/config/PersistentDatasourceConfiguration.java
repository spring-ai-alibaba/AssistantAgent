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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configuration for persistent datasource provider database connection.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(
    prefix = "spring.assistant-agent.data.persistent-datasource",
    name = "enabled",
    havingValue = "true"
)
@EnableConfigurationProperties(PersistentDatasourceProperties.class)
public class PersistentDatasourceConfiguration {

    /**
     * Create HikariCP configuration for DataAgent database.
     *
     * @param properties the persistent datasource properties
     * @return HikariCP configuration
     */
    @Bean
    public HikariConfig dataAgentHikariConfig(PersistentDatasourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getConnection().getUrl());
        config.setUsername(properties.getConnection().getUsername());
        config.setPassword(properties.getConnection().getPassword());
        config.setMaximumPoolSize(properties.getConnection().getPool().getMaximumPoolSize());
        config.setMinimumIdle(properties.getConnection().getPool().getMinimumIdle());
        config.setConnectionTimeout(properties.getConnection().getPool().getConnectionTimeout());
        return config;
    }

    /**
     * Create DataSource for DataAgent database.
     *
     * @param dataAgentHikariConfig the HikariCP configuration
     * @return DataSource instance
     */
    @Bean(name = "dataAgentDataSource")
    public DataSource dataAgentDataSource(HikariConfig dataAgentHikariConfig) {
        return new HikariDataSource(dataAgentHikariConfig);
    }

    /**
     * Create JdbcTemplate for DataAgent database.
     *
     * @param dataSource the DataAgent data source
     * @return JdbcTemplate instance
     */
    @Bean(name = "dataAgentJdbcTemplate")
    public JdbcTemplate dataAgentJdbcTemplate(@Qualifier("dataAgentDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
