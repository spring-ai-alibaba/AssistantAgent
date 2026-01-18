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
package com.alibaba.assistant.agent.data.tool;

import com.alibaba.assistant.agent.data.model.DatasourceDefinition;
import com.alibaba.assistant.agent.data.provider.DefaultSchemaProvider;
import com.alibaba.assistant.agent.data.provider.InMemoryDatasourceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QuerySchemaCodeactTool.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
class QuerySchemaCodeactToolTest {

    private QuerySchemaCodeactTool tool;
    private static final Object lock = new Object();
    private static int dbCounter = 0;

    @BeforeEach
    void setUp() throws Exception {
        // Setup H2 test database - use synchronized counter to avoid collisions
        int dbId;
        synchronized (lock) {
            dbId = dbCounter++;
        }
        String dbName = "testdbtool" + dbId;
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA test_schema");
            stmt.execute("CREATE TABLE test_schema.users (id INT PRIMARY KEY, name VARCHAR(50) NOT NULL, email VARCHAR(100))");
            stmt.execute("CREATE TABLE test_schema.orders (order_id INT PRIMARY KEY, user_id INT, FOREIGN KEY (user_id) REFERENCES test_schema.users(id))");
        }
        conn.close();

        // Register datasource
        InMemoryDatasourceProvider datasourceProvider = new InMemoryDatasourceProvider();
        DatasourceDefinition ds = DatasourceDefinition.builder()
                .name("test-h2")
                .type("h2")
                .databaseName(dbName)
                .username("sa")
                .password("")
                .connectionUrl(jdbcUrl)
                .build();
        datasourceProvider.register("test-system", ds);

        DefaultSchemaProvider schemaProvider = new DefaultSchemaProvider(datasourceProvider);
        tool = new QuerySchemaCodeactTool(schemaProvider);
    }

    @Test
    void shouldListTables() {
        String input = "{\"systemId\":\"test-system\",\"operation\":\"LIST_TABLES\",\"schemaName\":\"TEST_SCHEMA\"}";
        String result = tool.call(input);

        assertNotNull(result);
        assertTrue(result.contains("USERS"));
        assertTrue(result.contains("ORDERS"));
        assertFalse(result.contains("Error"));
    }

    @Test
    void shouldDescribeTable() {
        String input = "{\"systemId\":\"test-system\",\"operation\":\"DESCRIBE_TABLE\",\"schemaName\":\"TEST_SCHEMA\",\"tableName\":\"USERS\"}";
        String result = tool.call(input);

        assertNotNull(result);
        assertTrue(result.contains("USERS"));
        assertTrue(result.contains("ID"));
        assertTrue(result.contains("NAME"));
        assertTrue(result.contains("EMAIL"));
        assertTrue(result.contains("Primary Keys"));
        assertFalse(result.contains("Error"));
    }

    @Test
    void shouldReturnErrorForMissingSystemId() {
        String input = "{\"operation\":\"LIST_TABLES\"}";
        String result = tool.call(input);

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("systemId"));
    }

    @Test
    void shouldReturnErrorForInvalidOperation() {
        String input = "{\"systemId\":\"test-system\",\"operation\":\"INVALID_OP\"}";
        String result = tool.call(input);

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("operation"));
    }
}
