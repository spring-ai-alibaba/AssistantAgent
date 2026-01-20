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

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for parameter options with TTL (time-to-live) support.
 * <p>
 * This cache provides thread-safe storage for option lists with automatic expiration.
 * Expired entries are removed lazily when accessed via {@link #get(String)}.
 * </p>
 *
 * @author Assistant Agent Team
 */
public class OptionsCache {

    private static final Logger logger = LoggerFactory.getLogger(OptionsCache.class);

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;

    /**
     * Creates a new OptionsCache with the specified TTL.
     *
     * @param ttlMillis time-to-live in milliseconds for cached entries
     * @throws IllegalArgumentException if ttlMillis is not positive
     */
    public OptionsCache(long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("TTL must be positive: " + ttlMillis);
        }
        this.ttlMillis = ttlMillis;
    }

    /**
     * Retrieves cached options for the given key.
     * <p>
     * Returns null if the key is not in the cache or if the entry has expired.
     * Expired entries are automatically removed from the cache.
     * </p>
     *
     * @param key the cache key
     * @return the cached option list, or null if not found or expired
     */
    public List<OptionItem> get(String key) {
        if (key == null) {
            return null;
        }
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key, entry);
            logger.debug("OptionsCache#get - Cache entry expired: key={}", key);
            return null;
        }
        logger.debug("OptionsCache#get - Cache hit: key={}", key);
        return entry.value;
    }

    /**
     * Stores options in the cache with the configured TTL.
     *
     * @param key     the cache key
     * @param options the option list to cache
     * @throws IllegalArgumentException if key or options is null
     */
    public void put(String key, List<OptionItem> options) {
        if (key == null || options == null) {
            throw new IllegalArgumentException("Key and options must not be null");
        }
        long expirationTime = System.currentTimeMillis() + ttlMillis;
        cache.put(key, new CacheEntry(options, expirationTime));
        logger.debug("OptionsCache#put - Cached: key={}, size={}", key, options.size());
    }

    /**
     * Internal cache entry with expiration time.
     */
    private static class CacheEntry {
        final List<OptionItem> value;
        final long expirationTime;

        CacheEntry(List<OptionItem> value, long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}
