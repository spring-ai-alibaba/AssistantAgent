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

import com.alibaba.assistant.agent.data.model.*;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DefaultSchemaProvider.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
class DefaultSchemaProviderTest {

    private DatasourceProvider datasourceProvider;
    private DefaultSchemaProvider schemaProvider;
    private static final Object lock = new Object();
    private static int dbCounter = 0;

    @BeforeEach
    void setUp() throws Exception {
        // Setup H2 test database - use synchronized counter to avoid collisions
        int dbId;
        synchronized (lock) {
            dbId = dbCounter++;
        }
        String dbName = "testdbschema" + dbId;
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA test_schema");
            stmt.execute("CREATE TABLE test_schema.users (id INT PRIMARY KEY, name VARCHAR(50) NOT NULL, email VARCHAR(100))");
            stmt.execute("CREATE TABLE test_schema.orders (order_id INT PRIMARY KEY, user_id INT, amount DECIMAL(10,2), FOREIGN KEY (user_id) REFERENCES test_schema.users(id))");
            stmt.execute("INSERT INTO test_schema.users VALUES (1, 'Alice', 'alice@example.com'), (2, 'Bob', 'bob@example.com')");
        }
        conn.close();

        // Register datasource
        datasourceProvider = new InMemoryDatasourceProvider();
        DatasourceDefinition ds = DatasourceDefinition.builder()
                .name("test-h2")
                .type("h2")
                .databaseName(dbName)
                .username("sa")
                .password("")
                .connectionUrl(jdbcUrl)
                .build();
        ((InMemoryDatasourceProvider) datasourceProvider).register("test-system", ds);

        schemaProvider = new DefaultSchemaProvider(datasourceProvider);
    }

    @Test
    void shouldGetTableList() throws Exception {
        List<TableInfoBO> tables = schemaProvider.getTableList("test-system", null, "TEST_SCHEMA");

        assertNotNull(tables);
        assertEquals(2, tables.size());
        assertTrue(tables.stream().anyMatch(t -> "USERS".equals(t.getName())));
        assertTrue(tables.stream().anyMatch(t -> "ORDERS".equals(t.getName())));
    }

    @Test
    void shouldGetTableStructure() throws Exception {
        TableInfoBO table = schemaProvider.getTableStructure("test-system", null, "TEST_SCHEMA", "USERS");

        assertNotNull(table);
        assertEquals("USERS", table.getName());
        assertEquals("TEST_SCHEMA", table.getSchema());

        assertNotNull(table.getColumns());
        assertEquals(3, table.getColumns().size());

        // Verify columns
        assertTrue(table.getColumns().stream().anyMatch(c -> "ID".equals(c.getName()) && c.isPrimary()));
        assertTrue(table.getColumns().stream().anyMatch(c -> "NAME".equals(c.getName()) && c.isNotnull()));
        assertTrue(table.getColumns().stream().anyMatch(c -> "EMAIL".equals(c.getName())));

        // Verify primary keys
        assertNotNull(table.getPrimaryKeys());
        assertEquals(1, table.getPrimaryKeys().size());
        assertTrue(table.getPrimaryKeys().contains("ID"));
    }

    @Test
    void shouldGetTableStructureWithForeignKeys() throws Exception {
        TableInfoBO table = schemaProvider.getTableStructure("test-system", null, "TEST_SCHEMA", "ORDERS");

        assertNotNull(table);
        assertEquals("ORDERS", table.getName());

        // Should have foreign key information
        assertNotNull(table.getColumns());
        assertTrue(table.getColumns().stream().anyMatch(c -> "USER_ID".equals(c.getName())));
    }

    @Test
    void shouldThrowExceptionForNonExistentSystem() {
        assertThrows(Exception.class, () ->
                schemaProvider.getTableList("non-existent-system", null, "TEST_SCHEMA"));
    }
}
