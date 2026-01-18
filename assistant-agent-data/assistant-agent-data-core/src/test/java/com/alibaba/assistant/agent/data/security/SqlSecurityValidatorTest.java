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
package com.alibaba.assistant.agent.data.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SqlSecurityValidator.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
class SqlSecurityValidatorTest {

    private final SqlSecurityValidator validator = new SqlSecurityValidator();

    @Test
    void shouldAllowSelectStatement() {
        assertDoesNotThrow(() -> validator.validateReadOnly("SELECT * FROM users"));
    }

    @Test
    void shouldRejectInsertStatement() {
        assertThrows(SecurityException.class,
            () -> validator.validateReadOnly("INSERT INTO users VALUES (1, 'test')"));
    }

    @Test
    void shouldRejectUpdateStatement() {
        assertThrows(SecurityException.class,
            () -> validator.validateReadOnly("UPDATE users SET name='test'"));
    }

    @Test
    void shouldRejectDeleteStatement() {
        assertThrows(SecurityException.class,
            () -> validator.validateReadOnly("DELETE FROM users"));
    }

    @Test
    void shouldRejectDropStatement() {
        assertThrows(SecurityException.class,
            () -> validator.validateReadOnly("DROP TABLE users"));
    }

    @Test
    void shouldRejectNullSql() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateReadOnly(null));
    }

    @Test
    void shouldRejectEmptySql() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateReadOnly(""));
    }
}
