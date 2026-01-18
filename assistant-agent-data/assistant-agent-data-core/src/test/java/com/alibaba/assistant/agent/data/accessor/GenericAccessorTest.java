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

import com.alibaba.assistant.agent.data.model.ColumnInfoBO;
import com.alibaba.assistant.agent.data.model.ForeignKeyInfoBO;
import com.alibaba.assistant.agent.data.model.TableInfoBO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for GenericAccessor using H2 in-memory database.
 *
 * @author Assistant Agent Team
 */
class GenericAccessorTest {

    private static int dbCounter = 0;
    private Connection connection;
    private GenericAccessor accessor;

    @BeforeEach
    void setUp() throws Exception {
        // Create unique database name to avoid conflicts
        String dbName = "testdb" + (++dbCounter);
        connection = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1", "sa", "");

        // Create test schema
        try (Statement stmt = connection.createStatement()) {
            // Create a specific schema for testing
            stmt.execute("CREATE SCHEMA TEST_SCHEMA");

            // Create users table in TEST_SCHEMA
            stmt.execute(
                "CREATE TABLE TEST_SCHEMA.users (" +
                "  id BIGINT PRIMARY KEY," +
                "  name VARCHAR(100) NOT NULL," +
                "  email VARCHAR(100)" +
                ")"
            );

            // Create orders table with foreign key in TEST_SCHEMA
            stmt.execute(
                "CREATE TABLE TEST_SCHEMA.orders (" +
                "  id BIGINT PRIMARY KEY," +
                "  user_id BIGINT," +
                "  amount DECIMAL(10,2)," +
                "  FOREIGN KEY (user_id) REFERENCES TEST_SCHEMA.users(id)" +
                ")"
            );
        }

        accessor = new GenericAccessor();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void shouldShowTables() throws Exception {
        DbQueryParameter param = DbQueryParameter.builder()
                .schemaName("TEST_SCHEMA")
                .build();

        List<TableInfoBO> tables = accessor.showTables(connection, param);

        assertNotNull(tables);
        assertEquals(2, tables.size());

        // Verify table names (H2 returns uppercase by default)
        assertTrue(tables.stream().anyMatch(t -> "USERS".equalsIgnoreCase(t.getName())));
        assertTrue(tables.stream().anyMatch(t -> "ORDERS".equalsIgnoreCase(t.getName())));
    }

    @Test
    void shouldShowColumns() throws Exception {
        DbQueryParameter param = DbQueryParameter.builder()
                .schemaName("TEST_SCHEMA")
                .tableName("USERS")
                .build();

        List<ColumnInfoBO> columns = accessor.showColumns(connection, param);

        assertNotNull(columns);
        assertEquals(3, columns.size());

        // Verify ID column (primary key)
        ColumnInfoBO idColumn = columns.stream()
            .filter(c -> "ID".equalsIgnoreCase(c.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(idColumn);
        assertTrue(idColumn.isPrimary());

        // Verify NAME column (not null)
        ColumnInfoBO nameColumn = columns.stream()
            .filter(c -> "NAME".equalsIgnoreCase(c.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(nameColumn);
        assertTrue(nameColumn.isNotnull());

        // Verify EMAIL column (nullable)
        ColumnInfoBO emailColumn = columns.stream()
            .filter(c -> "EMAIL".equalsIgnoreCase(c.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(emailColumn);
        assertFalse(emailColumn.isNotnull());
    }

    @Test
    void shouldShowForeignKeys() throws Exception {
        DbQueryParameter param = DbQueryParameter.builder()
                .schemaName("TEST_SCHEMA")
                .tableName("ORDERS")
                .build();

        List<ForeignKeyInfoBO> foreignKeys = accessor.showForeignKeys(connection, param);

        assertNotNull(foreignKeys);
        assertEquals(1, foreignKeys.size());

        ForeignKeyInfoBO fk = foreignKeys.get(0);
        assertEquals("USER_ID", fk.getColumnName().toUpperCase());
        assertEquals("USERS", fk.getReferencedTableName().toUpperCase());
        assertEquals("ID", fk.getReferencedColumnName().toUpperCase());
    }

    @Test
    void shouldReturnGenericDatabaseType() {
        String dbType = accessor.getDatabaseType();
        assertEquals("generic", dbType);
    }
}
