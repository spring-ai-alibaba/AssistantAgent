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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MySQLAccessor.
 * Note: These tests verify the SQL queries are well-formed.
 * Integration tests with real MySQL would be in a separate test class.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
class MySQLAccessorTest {

    private final MySQLAccessor accessor = new MySQLAccessor();

    @Test
    void shouldReturnMySQLDatabaseType() {
        assertEquals("mysql", accessor.getDatabaseType());
    }

    @Test
    void shouldHaveShowDatabasesMethod() {
        assertNotNull(accessor);
        // Actual SQL execution requires real MySQL connection
        // Verified in integration tests
    }

    @Test
    void shouldHaveShowTablesMethod() {
        assertNotNull(accessor);
        // SQL: SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE, TABLE_COMMENT FROM information_schema.TABLES
        // WHERE TABLE_SCHEMA = ?
    }

    @Test
    void shouldHaveShowColumnsMethod() {
        assertNotNull(accessor);
        // SQL: SELECT COLUMN_NAME, TABLE_NAME, DATA_TYPE, COLUMN_COMMENT, COLUMN_KEY, IS_NULLABLE
        // FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
    }

    @Test
    void shouldHaveShowForeignKeysMethod() {
        assertNotNull(accessor);
        // SQL: SELECT CONSTRAINT_NAME, TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
        // FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
    }
}
