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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for datasource definitions with TTL support.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class DatasourceCache {

    private static final Logger logger = LoggerFactory.getLogger(DatasourceCache.class);

    private final ConcurrentHashMap<String, CacheEntry> bySystemId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CacheEntry> byId = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public DatasourceCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /**
     * Get datasource by system ID.
     *
     * @param systemId the system ID
     * @return the cached datasource, or null if not found or expired
     */
    public DatasourceDefinition getBySystemId(String systemId) {
        CacheEntry entry = bySystemId.get(systemId);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            bySystemId.remove(systemId);
            logger.debug("DatasourceCache#getBySystemId - Cache entry expired: systemId={}", systemId);
            return null;
        }
        logger.debug("DatasourceCache#getBySystemId - Cache hit: systemId={}", systemId);
        return entry.value;
    }

    /**
     * Get datasource by ID.
     *
     * @param id the datasource ID
     * @return the cached datasource, or null if not found or expired
     */
    public DatasourceDefinition getById(Long id) {
        CacheEntry entry = byId.get(id);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            byId.remove(id);
            logger.debug("DatasourceCache#getById - Cache entry expired: id={}", id);
            return null;
        }
        logger.debug("DatasourceCache#getById - Cache hit: id={}", id);
        return entry.value;
    }

    /**
     * Put datasource in cache by system ID.
     *
     * @param systemId the system ID
     * @param datasource the datasource definition
     */
    public void putBySystemId(String systemId, DatasourceDefinition datasource) {
        bySystemId.put(systemId, new CacheEntry(datasource, System.currentTimeMillis() + ttlMillis));
        logger.debug("DatasourceCache#putBySystemId - Cached: systemId={}, datasourceId={}", systemId, datasource.getId());
    }

    /**
     * Put datasource in cache by ID.
     *
     * @param id the datasource ID
     * @param datasource the datasource definition
     */
    public void putById(Long id, DatasourceDefinition datasource) {
        byId.put(id, new CacheEntry(datasource, System.currentTimeMillis() + ttlMillis));
        logger.debug("DatasourceCache#putById - Cached: id={}", id);
    }

    /**
     * Clean up expired cache entries.
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        int removedBySystemId = 0;
        int removedById = 0;

        // Count and remove expired entries
        var systemIdIter = bySystemId.entrySet().iterator();
        while (systemIdIter.hasNext()) {
            var entry = systemIdIter.next();
            if (entry.getValue().expirationTime < now) {
                systemIdIter.remove();
                removedBySystemId++;
            }
        }

        var idIter = byId.entrySet().iterator();
        while (idIter.hasNext()) {
            var entry = idIter.next();
            if (entry.getValue().expirationTime < now) {
                idIter.remove();
                removedById++;
            }
        }

        if (removedBySystemId > 0 || removedById > 0) {
            logger.debug("DatasourceCache#cleanupExpired - Removed {} entries by systemId, {} entries by id",
                    removedBySystemId, removedById);
        }
    }

    /**
     * Check if cache is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return true;
    }

    /**
     * Cache entry with expiration time.
     */
    private static class CacheEntry {
        final DatasourceDefinition value;
        final long expirationTime;

        CacheEntry(DatasourceDefinition value, long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}
