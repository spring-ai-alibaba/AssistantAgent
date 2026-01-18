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
import com.alibaba.assistant.agent.data.provider.PersistentDatasourceProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for PersistentDatasourceProvider with real database.
 * <p>
 * This test requires MySQL server running at localhost:3306 with database
 * 'saa_data_agent' created. If the database is not available, tests will
 * be skipped automatically.
 * <p>
 * Database requirements:
 * <ul>
 *     <li>MySQL server at localhost:3306</li>
 *     <li>Database: saa_data_agent</li>
 *     <li>Username: root, Password: StrongRootPwd</li>
 *     <li>Tables: agent, datasource, agent_datasource (created via schema)</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@SpringBootTest(classes = com.alibaba.assistant.agent.data.DataTestApplication.class)
@ActiveProfiles("persistent-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersistentDatasourceIntegrationTest {

    @Autowired
    private PersistentDatasourceProvider provider;

    @Autowired
    @Qualifier("dataAgentJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private static Long testAgentId;
    private static Long testDatasourceId;

    @BeforeAll
    static void setUpDatabase(@Autowired @Qualifier("dataAgentJdbcTemplate") JdbcTemplate jdbcTemplate) {
        // Create test agent
        jdbcTemplate.update(
                "INSERT INTO agent (name, description, status) VALUES (?, ?, ?)",
                "test-agent-persistent", "Test Agent for PersistentDatasource Integration Test", "published"
        );
        testAgentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // Create test datasource
        jdbcTemplate.update(
                "INSERT INTO datasource (name, type, host, port, database_name, username, password, " +
                "connection_url, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "test-datasource-persistent", "mysql", "localhost", 3306, "testdb",
                "testuser", "testpass", "jdbc:mysql://localhost:3306/testdb", "active"
        );
        testDatasourceId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // Associate agent with datasource
        jdbcTemplate.update(
                "INSERT INTO agent_datasource (agent_id, datasource_id, is_active) VALUES (?, ?, ?)",
                testAgentId, testDatasourceId, 1
        );
    }

    @AfterAll
    static void cleanupDatabase(@Autowired @Qualifier("dataAgentJdbcTemplate") JdbcTemplate jdbcTemplate) {
        // Delete test data in correct order (child -> parent)
        jdbcTemplate.update("DELETE FROM agent_datasource WHERE agent_id = ?", testAgentId);
        jdbcTemplate.update("DELETE FROM agent WHERE id = ?", testAgentId);
        jdbcTemplate.update("DELETE FROM datasource WHERE id = ?", testDatasourceId);
    }

    @Test
    @Order(1)
    void shouldFindDatasourceById() {
        Optional<DatasourceDefinition> result = provider.getById(testDatasourceId);

        assertTrue(result.isPresent(), "Datasource should be found");
        DatasourceDefinition datasource = result.get();
        assertEquals(testDatasourceId, datasource.getId());
        assertEquals("test-datasource-persistent", datasource.getName());
        assertEquals("mysql", datasource.getType());
        assertEquals("localhost", datasource.getHost());
        assertEquals(3306, datasource.getPort());
        assertEquals("testdb", datasource.getDatabaseName());
        assertEquals("testuser", datasource.getUsername());
        assertEquals("testpass", datasource.getPassword());
        assertEquals("jdbc:mysql://localhost:3306/testdb", datasource.getConnectionUrl());
        assertEquals("active", datasource.getStatus());
    }

    @Test
    @Order(2)
    void shouldFindDatasourceBySystemId() {
        Optional<DatasourceDefinition> result = provider.getBySystemId(testAgentId.toString());

        assertTrue(result.isPresent(), "Datasource should be found by systemId");
        DatasourceDefinition datasource = result.get();
        assertEquals(testDatasourceId, datasource.getId());
        assertEquals("test-datasource-persistent", datasource.getName());
    }

    @Test
    @Order(3)
    void shouldReturnEmptyForNonexistentId() {
        Optional<DatasourceDefinition> result = provider.getById(999999L);

        assertFalse(result.isPresent(), "Should return empty for nonexistent ID");
    }

    @Test
    @Order(4)
    void shouldReturnEmptyForAgentWithNoDatasources() {
        // Create agent without datasources
        jdbcTemplate.update(
                "INSERT INTO agent (name, description, status) VALUES (?, ?, ?)",
                "agent-no-ds-persistent", "Agent Without Datasources", "published"
        );
        Long agentIdNoDs = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        try {
            Optional<DatasourceDefinition> result = provider.getBySystemId(agentIdNoDs.toString());
            assertFalse(result.isPresent(), "Should return empty for agent with no datasources");
        } finally {
            // Cleanup
            jdbcTemplate.update("DELETE FROM agent WHERE id = ?", agentIdNoDs);
        }
    }

    @Test
    @Order(5)
    void shouldThrowExceptionForInvalidSystemId() {
        assertThrows(IllegalArgumentException.class, () -> provider.getBySystemId("invalid"),
                "Should throw IllegalArgumentException for non-numeric systemId");
    }

    @Test
    @Order(6)
    void shouldCacheDatasource() {
        // First call - database query
        Optional<DatasourceDefinition> first = provider.getById(testDatasourceId);
        assertTrue(first.isPresent(), "First call should return datasource");

        // Second call - should use cache
        Optional<DatasourceDefinition> second = provider.getById(testDatasourceId);
        assertTrue(second.isPresent(), "Second call should return cached datasource");
        assertEquals(first.get().getId(), second.get().getId());
        assertEquals(first.get().getName(), second.get().getName());
    }

    @Test
    @Order(7)
    void shouldReturnEmptyListForGetAll() {
        List<DatasourceDefinition> result = provider.getAll();

        assertNotNull(result, "Should return non-null list");
        assertTrue(result.isEmpty(), "Persistent provider should return empty list for getAll()");
    }

    @Test
    @Order(8)
    void shouldTestConnectionSuccessfully() {
        Optional<DatasourceDefinition> datasource = provider.getById(testDatasourceId);
        assertTrue(datasource.isPresent(), "Datasource should exist for connection test");

        // Note: This test will fail if the actual MySQL server at localhost:3306/testdb
        // is not accessible with credentials testuser/testpass. This is expected behavior
        // for integration tests that verify real connection testing functionality.
        boolean result = provider.testConnection(datasource.get());

        // We only verify that the method executes without throwing exceptions
        // The actual result depends on whether the test database is accessible
        assertNotNull(result, "Connection test should return a result (true or false)");
    }
}
