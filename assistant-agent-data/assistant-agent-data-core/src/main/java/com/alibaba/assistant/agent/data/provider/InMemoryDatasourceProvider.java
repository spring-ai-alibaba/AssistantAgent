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

import com.alibaba.assistant.agent.data.model.DatasourceDefinition;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of DatasourceProvider for demo/testing.
 * Stores datasources in memory without persistence.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
@ConditionalOnMissingBean(DatasourceProvider.class)
public class InMemoryDatasourceProvider implements DatasourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryDatasourceProvider.class);

    private final Map<Long, DatasourceDefinition> datasources = new ConcurrentHashMap<>();
    private final Map<String, Long> systemIdIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Optional<DatasourceDefinition> getById(Long id) {
        return Optional.ofNullable(datasources.get(id));
    }

    @Override
    public Optional<DatasourceDefinition> getBySystemId(String systemId) {
        Long id = systemIdIndex.get(systemId);
        return id != null ? Optional.ofNullable(datasources.get(id)) : Optional.empty();
    }

    @Override
    public List<DatasourceDefinition> getAll() {
        return new ArrayList<>(datasources.values());
    }

    @Override
    public boolean testConnection(DatasourceDefinition datasource) {
        try (Connection conn = DriverManager.getConnection(
                datasource.getEffectiveUrl(),
                datasource.getUsername(),
                datasource.getPassword())) {
            return conn.isValid(5);
        } catch (SQLException e) {
            logger.debug("InMemoryDatasourceProvider#testConnection - failed for datasource id={}: {}",
                datasource.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Register a datasource for testing/demo purposes.
     */
    public Long register(String systemId, DatasourceDefinition datasource) {
        Objects.requireNonNull(systemId, "systemId cannot be null");
        Objects.requireNonNull(datasource, "datasource cannot be null");

        if (datasource.getId() == null) {
            datasource.setId(idGenerator.getAndIncrement());
        }
        datasources.put(datasource.getId(), datasource);
        systemIdIndex.put(systemId, datasource.getId());
        return datasource.getId();
    }
}
