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
package com.alibaba.assistant.agent.data.integration;

import com.alibaba.assistant.agent.data.model.DatasourceDefinition;
import com.alibaba.assistant.agent.data.model.QueryResult;
import com.alibaba.assistant.agent.data.provider.DefaultSqlExecutionProvider;
import com.alibaba.assistant.agent.data.provider.InMemoryDatasourceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for SQL execution functionality.
 * Uses H2 in-memory database for testing.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
class SqlExecutionIntegrationTest {

    private InMemoryDatasourceProvider datasourceProvider;
    private DefaultSqlExecutionProvider sqlExecutionProvider;
    private static int dbCounter = 0;
    private String dbName;

    @BeforeEach
    void setUp() throws Exception {
        // Use unique database name for each test
        dbName = "testdb" + (dbCounter++);
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        // Setup H2 in-memory database
        Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50))");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Charlie'), (4, 'David')");
        }
        conn.close();

        // Register datasource
        datasourceProvider = new InMemoryDatasourceProvider();
        DatasourceDefinition ds = DatasourceDefinition.builder()
                .name("test-db")
                .type("h2")
                .databaseName(dbName)
                .username("sa")
                .password("")
                .connectionUrl(jdbcUrl)
                .build();
        datasourceProvider.register("test-system", ds);

        sqlExecutionProvider = new DefaultSqlExecutionProvider(datasourceProvider);
    }

    @Test
    void shouldExecuteSelectQuery() throws Exception {
        QueryResult result = sqlExecutionProvider.execute("test-system", "SELECT * FROM users");

        assertNotNull(result);
        assertEquals(4, result.getRows().size());
        assertFalse(result.isTruncated());
    }

    @Test
    void shouldRejectInsertQuery() {
        assertThrows(SecurityException.class, () ->
                sqlExecutionProvider.execute("test-system", "INSERT INTO users VALUES (3, 'Charlie')")
        );
    }

    @Test
    void shouldRejectUpdateQuery() {
        assertThrows(SecurityException.class, () ->
                sqlExecutionProvider.execute("test-system", "UPDATE users SET name='test'")
        );
    }

    @Test
    void shouldRejectDeleteQuery() {
        assertThrows(SecurityException.class, () ->
                sqlExecutionProvider.execute("test-system", "DELETE FROM users")
        );
    }

    @Test
    void shouldRejectDropQuery() {
        assertThrows(SecurityException.class, () ->
                sqlExecutionProvider.execute("test-system", "DROP TABLE users")
        );
    }

    @Test
    void shouldRespectMaxRowsLimit() throws Exception {
        QueryResult result = sqlExecutionProvider.execute("test-system", "SELECT * FROM users", 1);

        assertNotNull(result);
        assertEquals(1, result.getRows().size());
        assertTrue(result.isTruncated());
    }

    @Test
    void shouldReturnMarkdownTable() throws Exception {
        QueryResult result = sqlExecutionProvider.execute("test-system", "SELECT * FROM users");

        String markdown = result.toMarkdownTable();
        assertNotNull(markdown);
        assertTrue(markdown.contains("ID"));
        assertTrue(markdown.contains("NAME"));
        assertTrue(markdown.contains("Alice"));
        assertTrue(markdown.contains("Bob"));
    }
}
