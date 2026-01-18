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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PersistentDatasourceConfiguration.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
class PersistentDatasourceConfigurationTest {

    private PersistentDatasourceConfiguration configuration;
    private PersistentDatasourceProperties properties;

    @BeforeEach
    void setUp() {
        configuration = new PersistentDatasourceConfiguration();
        properties = new PersistentDatasourceProperties();
        properties.getConnection().setUrl("jdbc:h2:mem:testdb");
        properties.getConnection().setUsername("sa");
        properties.getConnection().setPassword("");
        properties.getConnection().getPool().setMaximumPoolSize(5);
        properties.getConnection().getPool().setMinimumIdle(1);
        properties.getConnection().getPool().setConnectionTimeout(20000);
    }

    @Test
    void shouldCreateHikariConfig() {
        HikariConfig config = configuration.dataAgentHikariConfig(properties);

        assertNotNull(config);
        assertEquals("jdbc:h2:mem:testdb", config.getJdbcUrl());
        assertEquals("sa", config.getUsername());
        assertEquals("", config.getPassword());
        assertEquals(5, config.getMaximumPoolSize());
        assertEquals(1, config.getMinimumIdle());
        assertEquals(20000, config.getConnectionTimeout());
    }

    @Test
    void shouldCreateDataSource() {
        HikariConfig config = configuration.dataAgentHikariConfig(properties);
        DataSource dataSource = configuration.dataAgentDataSource(config);

        assertNotNull(dataSource);
        assertInstanceOf(HikariDataSource.class, dataSource);
    }

    @Test
    void shouldCreateJdbcTemplate() {
        HikariConfig config = configuration.dataAgentHikariConfig(properties);
        DataSource dataSource = configuration.dataAgentDataSource(config);
        JdbcTemplate jdbcTemplate = configuration.dataAgentJdbcTemplate(dataSource);

        assertNotNull(jdbcTemplate);
        assertSame(dataSource, jdbcTemplate.getDataSource());
    }
}
