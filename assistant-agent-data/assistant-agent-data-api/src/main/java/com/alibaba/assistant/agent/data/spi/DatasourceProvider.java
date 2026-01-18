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
package com.alibaba.assistant.agent.data.spi;

import com.alibaba.assistant.agent.data.model.DatasourceDefinition;

import java.util.List;
import java.util.Optional;

/**
 * SPI for data source management.
 * <p>
 * Provides methods to retrieve and manage database connection configurations.
 * Implementations should handle datasource lifecycle including connection pooling,
 * credential management, and connection testing.
 * <p>
 * This is an extension point for integrating with external datasource registries,
 * configuration services, or database management systems.
 * <p>
 * Example implementation:
 * <pre>
 * {@code
 * @Component
 * public class DefaultDatasourceProvider implements DatasourceProvider {
 *     @Override
 *     public Optional<DatasourceDefinition> getById(Long id) {
 *         // Fetch from database or configuration
 *     }
 * }
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface DatasourceProvider {

    /**
     * Retrieves a data source by its unique ID.
     * <p>
     * This method should return the datasource configuration including
     * connection details, credentials, and status information.
     *
     * @param id the unique identifier of the datasource
     * @return an Optional containing the datasource definition if found, or empty if not found
     */
    Optional<DatasourceDefinition> getById(Long id);

    /**
     * Retrieves a data source by system ID (agent ID).
     * <p>
     * System ID is typically used to identify datasources associated with
     * specific agents or business systems. This allows for scoped datasource
     * access based on the agent context.
     *
     * @param systemId the system identifier (agent ID)
     * @return an Optional containing the datasource definition if found, or empty if not found
     */
    Optional<DatasourceDefinition> getBySystemId(String systemId);

    /**
     * Retrieves all available data sources.
     * <p>
     * This method should return all datasources that the current user or agent
     * has permission to access. Implementations may apply filtering based on
     * security context, tenant isolation, or other access control rules.
     *
     * @return a list of all accessible datasource definitions, never null but may be empty
     */
    List<DatasourceDefinition> getAll();

    /**
     * Tests the connection to a data source.
     * <p>
     * Attempts to establish a connection to the specified datasource using
     * the provided configuration. This is useful for validating datasource
     * configurations before saving or during health checks.
     * <p>
     * Implementations should:
     * <ul>
     *     <li>Establish a connection using the datasource configuration</li>
     *     <li>Execute a simple validation query (e.g., SELECT 1)</li>
     *     <li>Close the connection properly</li>
     *     <li>Return true if successful, false otherwise</li>
     * </ul>
     *
     * @param datasource the datasource definition to test
     * @return true if the connection test succeeds, false if it fails
     */
    boolean testConnection(DatasourceDefinition datasource);
}
