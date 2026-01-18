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
package com.alibaba.assistant.agent.data.accessor;

import com.alibaba.assistant.agent.data.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic database accessor using JDBC DatabaseMetaData API.
 * Works with any JDBC-compliant database.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class GenericAccessor implements Accessor {

    private static final Logger log = LoggerFactory.getLogger(GenericAccessor.class);

    @Override
    public List<DatabaseInfoBO> showDatabases(Connection connection) throws Exception {
        List<DatabaseInfoBO> databases = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet rs = metaData.getCatalogs()) {
            while (rs.next()) {
                String catalogName = rs.getString("TABLE_CAT");
                databases.add(DatabaseInfoBO.builder().name(catalogName).build());
            }
        }

        log.debug("GenericAccessor#showDatabases - found {} databases", databases.size());
        return databases;
    }

    @Override
    public List<SchemaInfoBO> showSchemas(Connection connection, DbQueryParameter param) throws Exception {
        List<SchemaInfoBO> schemas = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet rs = metaData.getSchemas()) {
            while (rs.next()) {
                String schemaName = rs.getString("TABLE_SCHEM");
                String catalogName = rs.getString("TABLE_CATALOG");
                schemas.add(SchemaInfoBO.builder()
                        .name(schemaName)
                        .catalog(catalogName)
                        .build());
            }
        }

        log.debug("GenericAccessor#showSchemas - found {} schemas", schemas.size());
        return schemas;
    }

    @Override
    public List<TableInfoBO> showTables(Connection connection, DbQueryParameter param) throws Exception {
        List<TableInfoBO> tables = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();

        String catalog = param != null ? param.getDatabaseName() : null;
        String schema = param != null ? param.getSchemaName() : null;

        try (ResultSet rs = metaData.getTables(catalog, schema, null, new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String tableSchema = rs.getString("TABLE_SCHEM");
                String tableType = rs.getString("TABLE_TYPE");
                String remarks = rs.getString("REMARKS");

                tables.add(TableInfoBO.builder()
                        .name(tableName)
                        .schema(tableSchema)
                        .type(tableType)
                        .description(remarks)
                        .build());
            }
        }

        log.debug("GenericAccessor#showTables - found {} tables in schema={}", tables.size(), schema);
        return tables;
    }

    @Override
    public List<ColumnInfoBO> showColumns(Connection connection, DbQueryParameter param) throws Exception {
        List<ColumnInfoBO> columns = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();

        String catalog = param != null ? param.getDatabaseName() : null;
        String schema = param != null ? param.getSchemaName() : null;
        String table = param != null ? param.getTableName() : null;

        // Get primary keys
        List<String> primaryKeys = new ArrayList<>();
        try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }

        // Get columns
        try (ResultSet rs = metaData.getColumns(catalog, schema, table, null)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String columnType = rs.getString("TYPE_NAME");
                String remarks = rs.getString("REMARKS");
                int nullable = rs.getInt("NULLABLE");

                columns.add(ColumnInfoBO.builder()
                        .name(columnName)
                        .tableName(table)
                        .type(columnType)
                        .description(remarks)
                        .primary(primaryKeys.contains(columnName))
                        .notnull(nullable == DatabaseMetaData.columnNoNulls)
                        .build());
            }
        }

        log.debug("GenericAccessor#showColumns - found {} columns for table={}", columns.size(), table);
        return columns;
    }

    @Override
    public List<ForeignKeyInfoBO> showForeignKeys(Connection connection, DbQueryParameter param) throws Exception {
        List<ForeignKeyInfoBO> foreignKeys = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();

        String catalog = param != null ? param.getDatabaseName() : null;
        String schema = param != null ? param.getSchemaName() : null;
        String table = param != null ? param.getTableName() : null;

        try (ResultSet rs = metaData.getImportedKeys(catalog, schema, table)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String fkTableName = rs.getString("FKTABLE_NAME");
                String fkColumnName = rs.getString("FKCOLUMN_NAME");
                String pkTableName = rs.getString("PKTABLE_NAME");
                String pkColumnName = rs.getString("PKCOLUMN_NAME");

                foreignKeys.add(ForeignKeyInfoBO.builder()
                        .constraintName(fkName)
                        .tableName(fkTableName)
                        .columnName(fkColumnName)
                        .referencedTableName(pkTableName)
                        .referencedColumnName(pkColumnName)
                        .build());
            }
        }

        log.debug("GenericAccessor#showForeignKeys - found {} foreign keys for table={}", foreignKeys.size(), table);
        return foreignKeys;
    }

    @Override
    public String getDatabaseType() {
        return "generic";
    }
}
