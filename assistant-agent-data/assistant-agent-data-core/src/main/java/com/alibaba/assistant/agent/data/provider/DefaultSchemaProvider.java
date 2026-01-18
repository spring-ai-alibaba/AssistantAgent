/*
 * Copyright 2025 the original author or authors.
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

import com.alibaba.assistant.agent.data.accessor.Accessor;
import com.alibaba.assistant.agent.data.accessor.AccessorFactory;
import com.alibaba.assistant.agent.data.accessor.DbQueryParameter;
import com.alibaba.assistant.agent.data.model.*;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import com.alibaba.assistant.agent.data.spi.SchemaProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of SchemaProvider.
 * Uses Accessor pattern with database-specific optimizations.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class DefaultSchemaProvider implements SchemaProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultSchemaProvider.class);

    private final DatasourceProvider datasourceProvider;
    private final Map<String, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    public DefaultSchemaProvider(DatasourceProvider datasourceProvider) {
        this.datasourceProvider = Objects.requireNonNull(datasourceProvider, "DatasourceProvider cannot be null");
    }

    @PreDestroy
    public void destroy() {
        log.info("DefaultSchemaProvider#destroy - closing {} connection pool(s)", dataSourceCache.size());
        dataSourceCache.values().forEach(HikariDataSource::close);
        dataSourceCache.clear();
    }

    @Override
    public List<DatabaseInfoBO> getDatabaseList(String systemId) throws Exception {
        log.debug("DefaultSchemaProvider#getDatabaseList - systemId={}", systemId);

        DatasourceDefinition datasource = findBySystemId(systemId);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource not found for systemId: " + systemId);
        }

        Accessor accessor = AccessorFactory.getAccessor(datasource.getType());

        try (Connection conn = getConnection(datasource)) {
            return accessor.showDatabases(conn);
        }
    }

    @Override
    public List<SchemaInfoBO> getSchemaList(String systemId, String databaseName) throws Exception {
        log.debug("DefaultSchemaProvider#getSchemaList - systemId={}, databaseName={}", systemId, databaseName);

        DatasourceDefinition datasource = findBySystemId(systemId);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource not found for systemId: " + systemId);
        }

        Accessor accessor = AccessorFactory.getAccessor(datasource.getType());
        DbQueryParameter param = DbQueryParameter.builder()
                .databaseName(databaseName)
                .build();

        try (Connection conn = getConnection(datasource)) {
            return accessor.showSchemas(conn, param);
        }
    }

    @Override
    public List<TableInfoBO> getTableList(String systemId, String databaseName, String schemaName) throws Exception {
        log.debug("DefaultSchemaProvider#getTableList - systemId={}, databaseName={}, schemaName={}",
                systemId, databaseName, schemaName);

        DatasourceDefinition datasource = findBySystemId(systemId);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource not found for systemId: " + systemId);
        }

        Accessor accessor = AccessorFactory.getAccessor(datasource.getType());
        DbQueryParameter param = DbQueryParameter.builder()
                .databaseName(databaseName)
                .schemaName(schemaName)
                .build();

        try (Connection conn = getConnection(datasource)) {
            return accessor.showTables(conn, param);
        }
    }

    @Override
    public TableInfoBO getTableStructure(String systemId, String databaseName, String schemaName, String tableName) throws Exception {
        log.debug("DefaultSchemaProvider#getTableStructure - systemId={}, databaseName={}, schemaName={}, tableName={}",
                systemId, databaseName, schemaName, tableName);

        DatasourceDefinition datasource = findBySystemId(systemId);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource not found for systemId: " + systemId);
        }

        Accessor accessor = AccessorFactory.getAccessor(datasource.getType());
        DbQueryParameter param = DbQueryParameter.builder()
                .databaseName(databaseName)
                .schemaName(schemaName)
                .tableName(tableName)
                .build();

        try (Connection conn = getConnection(datasource)) {
            // Get basic table info
            List<TableInfoBO> tables = accessor.showTables(conn, param);
            TableInfoBO table = tables.stream()
                    .filter(t -> tableName.equalsIgnoreCase(t.getName()))
                    .findFirst()
                    .orElse(TableInfoBO.builder().name(tableName).schema(schemaName).build());

            // Get columns
            List<ColumnInfoBO> columns = accessor.showColumns(conn, param);
            table.setColumns(columns);

            // Get primary keys from columns
            List<String> primaryKeys = columns.stream()
                    .filter(ColumnInfoBO::isPrimary)
                    .map(ColumnInfoBO::getName)
                    .collect(Collectors.toList());
            table.setPrimaryKeys(primaryKeys);

            // Get foreign keys
            List<ForeignKeyInfoBO> foreignKeys = accessor.showForeignKeys(conn, param);
            if (!foreignKeys.isEmpty()) {
                String fkDescription = foreignKeys.stream()
                        .map(fk -> String.format("%s -> %s(%s)",
                                fk.getColumnName(),
                                fk.getReferencedTableName(),
                                fk.getReferencedColumnName()))
                        .collect(Collectors.joining(", "));
                table.setForeignKey(fkDescription);
            }

            log.info("DefaultSchemaProvider#getTableStructure - table={}, columns={}, primaryKeys={}, foreignKeys={}",
                    tableName, columns.size(), primaryKeys.size(), foreignKeys.size());

            return table;
        }
    }

    /**
     * Get or create connection pool for datasource.
     */
    private Connection getConnection(DatasourceDefinition datasource) throws Exception {
        String cacheKey = datasource.getConnectionUrl();

        HikariDataSource dataSource = dataSourceCache.computeIfAbsent(cacheKey, key -> {
            log.info("DefaultSchemaProvider#getConnection - creating connection pool for datasource: {}", datasource.getName());

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(datasource.getConnectionUrl());
            config.setUsername(datasource.getUsername());
            config.setPassword(datasource.getPassword());
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(30000);

            return new HikariDataSource(config);
        });

        return dataSource.getConnection();
    }

    /**
     * Helper method to find datasource by system ID.
     */
    private DatasourceDefinition findBySystemId(String systemId) {
        return datasourceProvider.getBySystemId(systemId).orElse(null);
    }
}
