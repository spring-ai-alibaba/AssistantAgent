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

import com.alibaba.assistant.agent.data.cache.DatasourceCache;
import com.alibaba.assistant.agent.data.model.DatasourceDefinition;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Persistent implementation of DatasourceProvider that queries datasource metadata
 * from a database table with caching support.
 * <p>
 * This provider retrieves datasource configurations from the 'datasource' and
 * 'agent_datasource' tables in the data agent database. It uses a two-level
 * caching strategy to minimize database queries:
 * <ul>
 *     <li>Cache by ID for direct lookups</li>
 *     <li>Cache by systemId for agent-scoped lookups</li>
 * </ul>
 * <p>
 * Enabled via configuration:
 * <pre>
 * spring:
 *   assistant-agent:
 *     data:
 *       persistent-datasource:
 *         enabled: true
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(
        prefix = "spring.assistant-agent.data.persistent-datasource",
        name = "enabled",
        havingValue = "true"
)
public class PersistentDatasourceProvider implements DatasourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(PersistentDatasourceProvider.class);

    private static final String QUERY_BY_ID =
            "SELECT id, name, type, host, port, database_name, username, password, " +
                    "connection_url, status " +
                    "FROM datasource " +
                    "WHERE id = ? AND status = 'active'";

    private static final String QUERY_BY_SYSTEM_ID =
            "SELECT d.id, d.name, d.type, d.host, d.port, d.database_name, d.username, " +
                    "d.password, d.connection_url, d.status " +
                    "FROM datasource d " +
                    "INNER JOIN agent_datasource ad ON d.id = ad.datasource_id " +
                    "WHERE ad.agent_id = ? AND ad.is_active = 1 AND d.status = 'active' " +
                    "LIMIT 1";

    private final JdbcTemplate jdbcTemplate;
    private final DatasourceCache cache;

    /**
     * Constructor with dependency injection.
     *
     * @param jdbcTemplate the JdbcTemplate configured for the data agent database
     * @param cache the datasource cache for performance optimization
     */
    public PersistentDatasourceProvider(
            @Qualifier("dataAgentJdbcTemplate") JdbcTemplate jdbcTemplate,
            DatasourceCache cache) {
        this.jdbcTemplate = jdbcTemplate;
        this.cache = cache;
    }

    @Override
    public Optional<DatasourceDefinition> getById(Long id) {
        logger.debug("PersistentDatasourceProvider#getById - Querying datasource: id={}", id);

        // Check cache first
        DatasourceDefinition cached = cache.getById(id);
        if (cached != null) {
            logger.debug("PersistentDatasourceProvider#getById - Cache hit: id={}", id);
            return Optional.of(cached);
        }

        // Query database
        try {
            DatasourceDefinition datasource = jdbcTemplate.queryForObject(
                    QUERY_BY_ID,
                    new DatasourceRowMapper(),
                    id
            );

            // Cache the result
            if (datasource != null) {
                cache.putById(id, datasource);
                logger.debug("PersistentDatasourceProvider#getById - Found and cached: id={}", id);
            }

            return Optional.ofNullable(datasource);
        } catch (EmptyResultDataAccessException e) {
            logger.debug("PersistentDatasourceProvider#getById - Not found: id={}", id);
            return Optional.empty();
        }
    }

    @Override
    public Optional<DatasourceDefinition> getBySystemId(String systemId) {
        logger.debug("PersistentDatasourceProvider#getBySystemId - Querying datasource: systemId={}", systemId);

        // Parse systemId as Long (agent ID)
        Long agentId;
        try {
            agentId = Long.parseLong(systemId);
        } catch (NumberFormatException e) {
            logger.error("PersistentDatasourceProvider#getBySystemId - Invalid systemId format: systemId={}", systemId);
            throw new IllegalArgumentException("Invalid systemId: must be a numeric agent ID", e);
        }

        // Check cache first
        DatasourceDefinition cached = cache.getBySystemId(systemId);
        if (cached != null) {
            logger.debug("PersistentDatasourceProvider#getBySystemId - Cache hit: systemId={}", systemId);
            return Optional.of(cached);
        }

        // Query database with JOIN
        try {
            DatasourceDefinition datasource = jdbcTemplate.queryForObject(
                    QUERY_BY_SYSTEM_ID,
                    new DatasourceRowMapper(),
                    agentId
            );

            // Cache the result by both systemId and id
            if (datasource != null) {
                cache.putBySystemId(systemId, datasource);
                cache.putById(datasource.getId(), datasource);
                logger.debug("PersistentDatasourceProvider#getBySystemId - Found and cached: systemId={}, datasourceId={}",
                        systemId, datasource.getId());
            }

            return Optional.ofNullable(datasource);
        } catch (EmptyResultDataAccessException e) {
            logger.debug("PersistentDatasourceProvider#getBySystemId - Not found: systemId={}", systemId);
            return Optional.empty();
        }
    }

    @Override
    public List<DatasourceDefinition> getAll() {
        // Persistent provider is read-only and doesn't support listing all datasources
        logger.debug("PersistentDatasourceProvider#getAll - Not supported, returning empty list");
        return List.of();
    }

    @Override
    public boolean testConnection(DatasourceDefinition datasource) {
        logger.debug("PersistentDatasourceProvider#testConnection - Testing connection: datasourceId={}", datasource.getId());

        try (Connection conn = DriverManager.getConnection(
                datasource.getEffectiveUrl(),
                datasource.getUsername(),
                datasource.getPassword())) {
            boolean valid = conn.isValid(5);
            logger.debug("PersistentDatasourceProvider#testConnection - Connection test result: datasourceId={}, valid={}",
                    datasource.getId(), valid);
            return valid;
        } catch (SQLException e) {
            logger.warn("PersistentDatasourceProvider#testConnection - Connection test failed: datasourceId={}, error={}",
                    datasource.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * RowMapper implementation for mapping database rows to DatasourceDefinition objects.
     */
    private static class DatasourceRowMapper implements RowMapper<DatasourceDefinition> {

        @Override
        public DatasourceDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
            DatasourceDefinition ds = new DatasourceDefinition();
            ds.setId(rs.getLong("id"));
            ds.setName(rs.getString("name"));
            ds.setType(rs.getString("type"));
            ds.setHost(rs.getString("host"));
            ds.setPort(rs.getInt("port"));
            ds.setDatabaseName(rs.getString("database_name"));
            ds.setUsername(rs.getString("username"));
            ds.setPassword(rs.getString("password"));
            ds.setConnectionUrl(rs.getString("connection_url"));
            ds.setStatus(rs.getString("status"));
            return ds;
        }
    }
}
