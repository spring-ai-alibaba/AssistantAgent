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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL-optimized accessor using information_schema queries.
 * Provides better performance than generic JDBC DatabaseMetaData for MySQL.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class MySQLAccessor implements Accessor {

    private static final Logger log = LoggerFactory.getLogger(MySQLAccessor.class);

    @Override
    public List<DatabaseInfoBO> showDatabases(Connection connection) throws Exception {
        List<DatabaseInfoBO> databases = new ArrayList<>();

        String sql = "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA";

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                databases.add(DatabaseInfoBO.builder()
                        .name(rs.getString("SCHEMA_NAME"))
                        .build());
            }
        }

        log.debug("MySQLAccessor#showDatabases - found {} databases", databases.size());
        return databases;
    }

    @Override
    public List<SchemaInfoBO> showSchemas(Connection connection, DbQueryParameter param) throws Exception {
        List<SchemaInfoBO> schemas = new ArrayList<>();

        String sql = "SELECT SCHEMA_NAME, CATALOG_NAME FROM information_schema.SCHEMATA";

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                schemas.add(SchemaInfoBO.builder()
                        .name(rs.getString("SCHEMA_NAME"))
                        .catalog(rs.getString("CATALOG_NAME"))
                        .build());
            }
        }

        log.debug("MySQLAccessor#showSchemas - found {} schemas", schemas.size());
        return schemas;
    }

    @Override
    public List<TableInfoBO> showTables(Connection connection, DbQueryParameter param) throws Exception {
        List<TableInfoBO> tables = new ArrayList<>();

        String sql = "SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE, TABLE_COMMENT " +
                "FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = ?";

        String schema = param != null ? param.getSchemaName() : param.getDatabaseName();
        if (schema == null) {
            schema = connection.getCatalog();
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tables.add(TableInfoBO.builder()
                            .schema(rs.getString("TABLE_SCHEMA"))
                            .name(rs.getString("TABLE_NAME"))
                            .type(rs.getString("TABLE_TYPE"))
                            .description(rs.getString("TABLE_COMMENT"))
                            .build());
                }
            }
        }

        log.debug("MySQLAccessor#showTables - found {} tables in schema={}", tables.size(), schema);
        return tables;
    }

    @Override
    public List<ColumnInfoBO> showColumns(Connection connection, DbQueryParameter param) throws Exception {
        List<ColumnInfoBO> columns = new ArrayList<>();

        String sql = "SELECT COLUMN_NAME, TABLE_NAME, DATA_TYPE, COLUMN_COMMENT, COLUMN_KEY, IS_NULLABLE " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION";

        String schema = param != null ? param.getSchemaName() : param.getDatabaseName();
        if (schema == null) {
            schema = connection.getCatalog();
        }
        String table = param != null ? param.getTableName() : null;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    columns.add(ColumnInfoBO.builder()
                            .name(rs.getString("COLUMN_NAME"))
                            .tableName(rs.getString("TABLE_NAME"))
                            .type(rs.getString("DATA_TYPE"))
                            .description(rs.getString("COLUMN_COMMENT"))
                            .primary("PRI".equals(rs.getString("COLUMN_KEY")))
                            .notnull("NO".equals(rs.getString("IS_NULLABLE")))
                            .build());
                }
            }
        }

        log.debug("MySQLAccessor#showColumns - found {} columns for table={}", columns.size(), table);
        return columns;
    }

    @Override
    public List<ForeignKeyInfoBO> showForeignKeys(Connection connection, DbQueryParameter param) throws Exception {
        List<ForeignKeyInfoBO> foreignKeys = new ArrayList<>();

        String sql = "SELECT CONSTRAINT_NAME, TABLE_NAME, COLUMN_NAME, " +
                "REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME " +
                "FROM information_schema.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND REFERENCED_TABLE_NAME IS NOT NULL";

        String schema = param != null ? param.getSchemaName() : param.getDatabaseName();
        if (schema == null) {
            schema = connection.getCatalog();
        }
        String table = param != null ? param.getTableName() : null;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    foreignKeys.add(ForeignKeyInfoBO.builder()
                            .constraintName(rs.getString("CONSTRAINT_NAME"))
                            .tableName(rs.getString("TABLE_NAME"))
                            .columnName(rs.getString("COLUMN_NAME"))
                            .referencedTableName(rs.getString("REFERENCED_TABLE_NAME"))
                            .referencedColumnName(rs.getString("REFERENCED_COLUMN_NAME"))
                            .build());
                }
            }
        }

        log.debug("MySQLAccessor#showForeignKeys - found {} foreign keys for table={}", foreignKeys.size(), table);
        return foreignKeys;
    }

    @Override
    public String getDatabaseType() {
        return "mysql";
    }
}
