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
package com.alibaba.assistant.agent.data.cache;

import com.alibaba.assistant.agent.data.model.DatasourceDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DatasourceCache.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
class DatasourceCacheTest {

    private DatasourceCache cache;
    private DatasourceDefinition testDatasource;

    @BeforeEach
    void setUp() {
        cache = new DatasourceCache(5 * 60 * 1000); // 5 minutes TTL
        testDatasource = DatasourceDefinition.builder()
                .id(1L)
                .name("test-db")
                .type("mysql")
                .host("localhost")
                .port(3306)
                .databaseName("testdb")
                .username("testuser")
                .password("testpass")
                .connectionUrl("jdbc:mysql://localhost:3306/testdb")
                .status("active")
                .build();
    }

    @Test
    void shouldReturnNullWhenCacheMiss() {
        assertNull(cache.getBySystemId("1"));
        assertNull(cache.getById(1L));
    }

    @Test
    void shouldReturnCachedValueBySystemId() {
        cache.putBySystemId("1", testDatasource);

        DatasourceDefinition result = cache.getBySystemId("1");

        assertNotNull(result);
        assertEquals(testDatasource.getId(), result.getId());
        assertEquals(testDatasource.getName(), result.getName());
    }

    @Test
    void shouldReturnCachedValueById() {
        cache.putById(1L, testDatasource);

        DatasourceDefinition result = cache.getById(1L);

        assertNotNull(result);
        assertEquals(testDatasource.getId(), result.getId());
        assertEquals(testDatasource.getName(), result.getName());
    }

    @Test
    void shouldExpireCacheAfterTtl() throws InterruptedException {
        DatasourceCache shortCache = new DatasourceCache(100); // 100ms TTL
        shortCache.putBySystemId("1", testDatasource);

        assertNotNull(shortCache.getBySystemId("1"));

        Thread.sleep(150);

        assertNull(shortCache.getBySystemId("1"));
    }

    @Test
    void shouldCleanupExpiredEntries() throws InterruptedException {
        DatasourceCache shortCache = new DatasourceCache(100); // 100ms TTL
        shortCache.putBySystemId("1", testDatasource);
        shortCache.putById(1L, testDatasource);

        Thread.sleep(150);

        shortCache.cleanupExpired();

        assertNull(shortCache.getBySystemId("1"));
        assertNull(shortCache.getById(1L));
    }

    @Test
    void shouldHandleConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final String systemId = String.valueOf(i);
            final Long id = (long) i;
            threads[i] = new Thread(() -> {
                DatasourceDefinition ds = DatasourceDefinition.builder()
                        .id(id)
                        .name("test-" + id)
                        .build();
                cache.putBySystemId(systemId, ds);
                cache.putById(id, ds);
                assertNotNull(cache.getBySystemId(systemId));
                assertNotNull(cache.getById(id));
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // All entries should be present
        for (int i = 0; i < threadCount; i++) {
            assertNotNull(cache.getBySystemId(String.valueOf(i)));
            assertNotNull(cache.getById((long) i));
        }
    }

    @Test
    void shouldReturnEnabledStatus() {
        assertTrue(cache.isEnabled());
    }
}
