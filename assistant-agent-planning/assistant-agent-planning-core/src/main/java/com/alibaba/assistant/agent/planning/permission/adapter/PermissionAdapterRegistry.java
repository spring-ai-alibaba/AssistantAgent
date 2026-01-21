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
package com.alibaba.assistant.agent.planning.permission.adapter;

import com.alibaba.assistant.agent.planning.permission.spi.PermissionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for permission adapters.
 * <p>
 * Manages all registered permission adapters and provides lookup functionality.
 * Adapters are automatically registered through Spring's dependency injection.
 * <p>
 * Example usage:
 * <pre>
 * PermissionAdapter adapter = registry.getAdapter("oa-system");
 * if (adapter != null) {
 *     StandardPermission permission = adapter.adapt(context);
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class PermissionAdapterRegistry {

    private static final Logger logger = LoggerFactory.getLogger(PermissionAdapterRegistry.class);

    /**
     * Map of system ID to adapter.
     */
    private final Map<String, PermissionAdapter> adapterMap = new ConcurrentHashMap<>();

    /**
     * Constructor with automatic adapter registration.
     *
     * @param adapters list of all permission adapters (injected by Spring)
     */
    public PermissionAdapterRegistry(List<PermissionAdapter> adapters) {
        if (adapters != null) {
            // Sort by order (higher priority first) and register
            adapters.stream()
                    .sorted(Comparator.comparingInt(PermissionAdapter::getOrder).reversed())
                    .forEach(this::registerAdapter);
        }
        logger.info("PermissionAdapterRegistry initialized with {} adapters: {}",
                adapterMap.size(), adapterMap.keySet());
    }

    /**
     * Register an adapter.
     *
     * @param adapter the adapter to register
     */
    public void registerAdapter(PermissionAdapter adapter) {
        if (adapter == null || adapter.getSystemId() == null) {
            logger.warn("PermissionAdapterRegistry#registerAdapter - invalid adapter");
            return;
        }

        String systemId = adapter.getSystemId();
        PermissionAdapter existing = adapterMap.put(systemId, adapter);

        if (existing != null) {
            logger.info("PermissionAdapterRegistry#registerAdapter - replaced adapter for system: {}, old={}, new={}",
                    systemId, existing.getName(), adapter.getName());
        } else {
            logger.info("PermissionAdapterRegistry#registerAdapter - registered adapter: system={}, name={}",
                    systemId, adapter.getName());
        }
    }

    /**
     * Unregister an adapter.
     *
     * @param systemId the system ID
     * @return the removed adapter, or null if not found
     */
    public PermissionAdapter unregisterAdapter(String systemId) {
        PermissionAdapter removed = adapterMap.remove(systemId);
        if (removed != null) {
            logger.info("PermissionAdapterRegistry#unregisterAdapter - removed adapter: system={}", systemId);
        }
        return removed;
    }

    /**
     * Get adapter by system ID.
     *
     * @param systemId the system ID
     * @return the adapter, or null if not found
     */
    public PermissionAdapter getAdapter(String systemId) {
        return adapterMap.get(systemId);
    }

    /**
     * Get adapter by system ID (optional).
     *
     * @param systemId the system ID
     * @return optional containing the adapter
     */
    public Optional<PermissionAdapter> findAdapter(String systemId) {
        return Optional.ofNullable(getAdapter(systemId));
    }

    /**
     * Check if an adapter exists for the system.
     *
     * @param systemId the system ID
     * @return true if adapter exists
     */
    public boolean hasAdapter(String systemId) {
        return adapterMap.containsKey(systemId);
    }

    /**
     * Get all registered adapters.
     *
     * @return list of all adapters
     */
    public List<PermissionAdapter> getAllAdapters() {
        return List.copyOf(adapterMap.values());
    }

    /**
     * Get all supported system IDs.
     *
     * @return list of system IDs
     */
    public List<String> getSupportedSystems() {
        return adapterMap.keySet().stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Get adapter count.
     *
     * @return number of registered adapters
     */
    public int getAdapterCount() {
        return adapterMap.size();
    }
}
