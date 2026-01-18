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
package com.alibaba.assistant.agent.data.provider;

import com.alibaba.assistant.agent.data.executor.SqlExecutor;
import com.alibaba.assistant.agent.data.model.DatasourceDefinition;
import com.alibaba.assistant.agent.data.model.QueryResult;
import com.alibaba.assistant.agent.data.security.SqlSecurityValidator;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import com.alibaba.assistant.agent.data.spi.SqlExecutionProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of SQL execution provider.
 * Uses HikariCP for connection pooling.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class DefaultSqlExecutionProvider implements SqlExecutionProvider {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSqlExecutionProvider.class);

    private final DatasourceProvider datasourceProvider;
    private final SqlSecurityValidator securityValidator;
    private final Map<Long, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    public DefaultSqlExecutionProvider(DatasourceProvider datasourceProvider) {
        this.datasourceProvider = Objects.requireNonNull(datasourceProvider, "DatasourceProvider cannot be null");
        this.securityValidator = new SqlSecurityValidator();
    }

    @Override
    public QueryResult execute(String systemId, String sql) {
        return execute(systemId, sql, 1000);
    }

    @Override
    public QueryResult execute(String systemId, String sql, int maxRows) {
        logger.info("DefaultSqlExecutionProvider#execute - systemId={}, maxRows={}", systemId, maxRows);

        if (maxRows <= 0) {
            throw new IllegalArgumentException("maxRows must be positive, got: " + maxRows);
        }

        // Validate read-only
        securityValidator.validateReadOnly(sql);

        // Get datasource
        DatasourceDefinition datasource = datasourceProvider.getBySystemId(systemId)
            .orElseThrow(() -> new IllegalArgumentException("Datasource not found for systemId: " + systemId));

        // Execute query
        HikariDataSource dataSource = getOrCreateDataSource(datasource);
        try (Connection connection = dataSource.getConnection()) {
            return SqlExecutor.executeQuery(connection, sql, maxRows);
        } catch (SQLException e) {
            logger.error("DefaultSqlExecutionProvider#execute - SQL execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to execute SQL query: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateReadOnly(String sql) {
        securityValidator.validateReadOnly(sql);
    }

    private HikariDataSource getOrCreateDataSource(DatasourceDefinition def) {
        return dataSourceCache.computeIfAbsent(def.getId(), id -> {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(def.getEffectiveUrl());
            config.setUsername(def.getUsername());
            config.setPassword(def.getPassword());
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10000);

            logger.info("DefaultSqlExecutionProvider - created datasource pool for id={}", id);
            return new HikariDataSource(config);
        });
    }

    @PreDestroy
    public void destroy() {
        logger.info("DefaultSqlExecutionProvider#destroy - closing {} connection pool(s)", dataSourceCache.size());
        dataSourceCache.values().forEach(HikariDataSource::close);
        dataSourceCache.clear();
    }
}
