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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating database-specific Accessor implementations.
 * Selects optimized accessor based on database type, falls back to generic JDBC.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class AccessorFactory {

    private static final Logger log = LoggerFactory.getLogger(AccessorFactory.class);

    /**
     * Get accessor for the specified database type.
     *
     * @param databaseType database type (e.g., "mysql", "postgresql", "h2")
     * @return appropriate accessor implementation
     */
    public static Accessor getAccessor(String databaseType) {
        if (databaseType == null) {
            log.debug("AccessorFactory#getAccessor - null database type, using GenericAccessor");
            return new GenericAccessor();
        }

        String normalizedType = databaseType.toLowerCase();

        switch (normalizedType) {
            case "mysql":
            case "mariadb":
                log.debug("AccessorFactory#getAccessor - using MySQLAccessor for type={}", databaseType);
                return new MySQLAccessor();

            // Future: Add PostgreSQL, Oracle, SQL Server accessors
            // case "postgresql":
            //     return new PostgreSQLAccessor();

            default:
                log.debug("AccessorFactory#getAccessor - using GenericAccessor for type={}", databaseType);
                return new GenericAccessor();
        }
    }
}
