/*
 * Copyright 2025 Alibaba Group Holding Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.planning.cache;

import com.alibaba.assistant.agent.data.model.OptionItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class OptionsCacheTest {

    @Test
    void shouldReturnNullOnCacheMiss() {
        OptionsCache cache = new OptionsCache(1000);

        List<OptionItem> result = cache.get("nonexistent");

        assertNull(result);
    }

    @Test
    void shouldReturnCachedValue() {
        OptionsCache cache = new OptionsCache(1000);
        List<OptionItem> options = createTestOptions(3);

        cache.put("test-key", options);
        List<OptionItem> result = cache.get("test-key");

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("Option 0", result.get(0).getLabel());
        assertEquals("value0", result.get(0).getValue());
    }

    @Test
    void shouldExpireAfterTtl() throws InterruptedException {
        OptionsCache cache = new OptionsCache(100); // 100ms TTL
        List<OptionItem> options = createTestOptions(2);

        cache.put("expiring-key", options);

        // Should be available immediately
        assertNotNull(cache.get("expiring-key"));

        // Wait for expiration
        Thread.sleep(150);

        // Should be expired now
        List<OptionItem> result = cache.get("expiring-key");
        assertNull(result);

        // Second get should also return null (entry removed)
        result = cache.get("expiring-key");
        assertNull(result);
    }

    @Test
    void shouldHandleConcurrentAccess() throws InterruptedException {
        OptionsCache cache = new OptionsCache(5000);
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Each thread puts and gets its own key
                    String key = "thread-" + threadId;
                    List<OptionItem> options = createTestOptions(threadId + 1);
                    cache.put(key, options);

                    List<OptionItem> result = cache.get(key);
                    assertNotNull(result);
                    assertEquals(threadId + 1, result.size());

                    // Also try to get other threads' keys
                    for (int j = 0; j < threadCount; j++) {
                        cache.get("thread-" + j);
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete within timeout");
        assertTrue(exceptions.isEmpty(), "No exceptions should occur: " + exceptions);
    }

    @Test
    void shouldReturnNullForNullKey() {
        OptionsCache cache = new OptionsCache(1000);

        List<OptionItem> result = cache.get(null);

        assertThat(result).isNull();
    }

    @Test
    void shouldRejectNullOptions() {
        OptionsCache cache = new OptionsCache(1000);

        assertThatThrownBy(() -> cache.put("key", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Key and options must not be null");
    }

    @Test
    void shouldRejectNegativeTtl() {
        assertThatThrownBy(() -> new OptionsCache(-1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("TTL must be positive");
    }

    private List<OptionItem> createTestOptions(int count) {
        List<OptionItem> options = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OptionItem item = new OptionItem();
            item.setLabel("Option " + i);
            item.setValue("value" + i);
            options.add(item);
        }
        return options;
    }
}
