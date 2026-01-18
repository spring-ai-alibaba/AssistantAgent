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
package com.alibaba.assistant.agent.data.integration;

import com.alibaba.assistant.agent.data.model.*;
import com.alibaba.assistant.agent.data.provider.DefaultSchemaProvider;
import com.alibaba.assistant.agent.data.provider.InMemoryDatasourceProvider;
import com.alibaba.assistant.agent.data.tool.QuerySchemaCodeactTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for schema query functionality.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
class SchemaQueryIntegrationTest {

    private InMemoryDatasourceProvider datasourceProvider;
    private DefaultSchemaProvider schemaProvider;
    private QuerySchemaCodeactTool queryTool;
    private static int dbCounter = 0;

    @BeforeEach
    void setUp() throws Exception {
        // Setup H2 test database with unique name for each test
        String dbName = "testdb_schema_" + System.currentTimeMillis() + "_" + (dbCounter++);
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA test_schema");
            stmt.execute("CREATE TABLE test_schema.users (" +
                    "id INT PRIMARY KEY, " +
                    "name VARCHAR(50) NOT NULL, " +
                    "email VARCHAR(100), " +
                    "created_at TIMESTAMP)");
            stmt.execute("CREATE TABLE test_schema.orders (" +
                    "order_id INT PRIMARY KEY, " +
                    "user_id INT NOT NULL, " +
                    "amount DECIMAL(10,2), " +
                    "status VARCHAR(20), " +
                    "FOREIGN KEY (user_id) REFERENCES test_schema.users(id))");
            stmt.execute("INSERT INTO test_schema.users VALUES " +
                    "(1, 'Alice', 'alice@example.com', CURRENT_TIMESTAMP), " +
                    "(2, 'Bob', 'bob@example.com', CURRENT_TIMESTAMP)");
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
        datasourceProvider.register("test-system", ds);

        schemaProvider = new DefaultSchemaProvider(datasourceProvider);
        queryTool = new QuerySchemaCodeactTool(schemaProvider);
    }

    @Test
    void shouldGetTableListViaProvider() throws Exception {
        List<TableInfoBO> tables = schemaProvider.getTableList("test-system", null, "TEST_SCHEMA");

        assertNotNull(tables);
        assertEquals(2, tables.size());
        assertTrue(tables.stream().anyMatch(t -> "USERS".equals(t.getName())));
        assertTrue(tables.stream().anyMatch(t -> "ORDERS".equals(t.getName())));
    }

    @Test
    void shouldGetTableStructureViaProvider() throws Exception {
        TableInfoBO table = schemaProvider.getTableStructure("test-system", null, "TEST_SCHEMA", "USERS");

        assertNotNull(table);
        assertEquals("USERS", table.getName());
        assertEquals("TEST_SCHEMA", table.getSchema());

        // Verify columns
        assertNotNull(table.getColumns());
        assertEquals(4, table.getColumns().size());
        assertTrue(table.getColumns().stream().anyMatch(c -> "ID".equals(c.getName()) && c.isPrimary()));
        assertTrue(table.getColumns().stream().anyMatch(c -> "NAME".equals(c.getName()) && c.isNotnull()));
        assertTrue(table.getColumns().stream().anyMatch(c -> "EMAIL".equals(c.getName())));
        assertTrue(table.getColumns().stream().anyMatch(c -> "CREATED_AT".equals(c.getName())));

        // Verify primary keys
        assertNotNull(table.getPrimaryKeys());
        assertEquals(1, table.getPrimaryKeys().size());
        assertEquals("ID", table.getPrimaryKeys().get(0));
    }

    @Test
    void shouldGetTableStructureWithForeignKeysViaProvider() throws Exception {
        TableInfoBO table = schemaProvider.getTableStructure("test-system", null, "TEST_SCHEMA", "ORDERS");

        assertNotNull(table);
        assertEquals("ORDERS", table.getName());

        // Verify columns
        assertNotNull(table.getColumns());
        assertEquals(4, table.getColumns().size());
        assertTrue(table.getColumns().stream().anyMatch(c -> "ORDER_ID".equals(c.getName()) && c.isPrimary()));
        assertTrue(table.getColumns().stream().anyMatch(c -> "USER_ID".equals(c.getName()) && c.isNotnull()));

        // Verify foreign keys
        assertNotNull(table.getForeignKey());
        assertTrue(table.getForeignKey().contains("USERS"));
    }

    @Test
    void shouldListTablesViaTool() {
        String input = "{\"systemId\":\"test-system\",\"operation\":\"LIST_TABLES\",\"schemaName\":\"TEST_SCHEMA\"}";
        String result = queryTool.call(input);

        assertNotNull(result);
        assertTrue(result.contains("# Tables"));
        assertTrue(result.contains("USERS"));
        assertTrue(result.contains("ORDERS"));
        assertTrue(result.contains("Table Name"));
    }

    @Test
    void shouldDescribeTableViaTool() {
        String input = "{\"systemId\":\"test-system\",\"operation\":\"DESCRIBE_TABLE\"," +
                "\"schemaName\":\"TEST_SCHEMA\",\"tableName\":\"USERS\"}";
        String result = queryTool.call(input);

        assertNotNull(result);
        assertTrue(result.contains("# Table: USERS"));
        assertTrue(result.contains("## Columns"));
        assertTrue(result.contains("ID"));
        assertTrue(result.contains("NAME"));
        assertTrue(result.contains("EMAIL"));
        assertTrue(result.contains("CREATED_AT"));
        assertTrue(result.contains("Primary"));
        assertTrue(result.contains("Not Null"));
    }

    @Test
    void shouldShowForeignKeysInTableDescription() {
        String input = "{\"systemId\":\"test-system\",\"operation\":\"DESCRIBE_TABLE\"," +
                "\"schemaName\":\"TEST_SCHEMA\",\"tableName\":\"ORDERS\"}";
        String result = queryTool.call(input);

        assertNotNull(result);
        assertTrue(result.contains("# Table: ORDERS"));
        assertTrue(result.contains("Foreign Keys"));
        assertTrue(result.contains("USERS"));
    }

    @Test
    void shouldHandleNonExistentSystem() {
        String input = "{\"systemId\":\"non-existent\",\"operation\":\"LIST_TABLES\",\"schemaName\":\"TEST_SCHEMA\"}";
        String result = queryTool.call(input);

        assertTrue(result.contains("Error"));
    }

    @Test
    void shouldHandleInvalidOperation() {
        String input = "{\"systemId\":\"test-system\",\"operation\":\"INVALID_OP\"}";
        String result = queryTool.call(input);

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("Invalid operation"));
    }
}
