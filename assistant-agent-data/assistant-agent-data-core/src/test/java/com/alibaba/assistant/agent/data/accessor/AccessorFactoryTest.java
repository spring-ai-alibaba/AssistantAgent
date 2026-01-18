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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AccessorFactory.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
class AccessorFactoryTest {

    @Test
    void shouldReturnMySQLAccessorForMySQL() {
        Accessor accessor = AccessorFactory.getAccessor("mysql");
        assertNotNull(accessor);
        assertInstanceOf(MySQLAccessor.class, accessor);
        assertEquals("mysql", accessor.getDatabaseType());
    }

    @Test
    void shouldReturnMySQLAccessorForMariaDB() {
        Accessor accessor = AccessorFactory.getAccessor("mariadb");
        assertNotNull(accessor);
        assertInstanceOf(MySQLAccessor.class, accessor);
    }

    @Test
    void shouldReturnGenericAccessorForH2() {
        Accessor accessor = AccessorFactory.getAccessor("h2");
        assertNotNull(accessor);
        assertInstanceOf(GenericAccessor.class, accessor);
        assertEquals("generic", accessor.getDatabaseType());
    }

    @Test
    void shouldReturnGenericAccessorForUnknownDatabase() {
        Accessor accessor = AccessorFactory.getAccessor("unknown-db");
        assertNotNull(accessor);
        assertInstanceOf(GenericAccessor.class, accessor);
    }

    @Test
    void shouldHandleNullDatabaseType() {
        Accessor accessor = AccessorFactory.getAccessor(null);
        assertNotNull(accessor);
        assertInstanceOf(GenericAccessor.class, accessor);
    }

    @Test
    void shouldBeCaseInsensitive() {
        Accessor accessor1 = AccessorFactory.getAccessor("MySQL");
        Accessor accessor2 = AccessorFactory.getAccessor("MYSQL");
        Accessor accessor3 = AccessorFactory.getAccessor("mysql");

        assertInstanceOf(MySQLAccessor.class, accessor1);
        assertInstanceOf(MySQLAccessor.class, accessor2);
        assertInstanceOf(MySQLAccessor.class, accessor3);
    }
}
