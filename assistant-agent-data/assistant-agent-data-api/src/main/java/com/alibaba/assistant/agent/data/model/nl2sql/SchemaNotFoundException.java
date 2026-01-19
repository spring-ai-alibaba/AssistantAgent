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
package com.alibaba.assistant.agent.data.model.nl2sql;

/**
 * Exception thrown when database schema cannot be retrieved for the specified system ID.
 * This typically indicates that the data source is not configured or the system ID is invalid.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SchemaNotFoundException extends Nl2SqlException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new SchemaNotFoundException for the specified system ID.
     *
     * @param systemId the system ID for which schema was not found
     */
    public SchemaNotFoundException(String systemId) {
        super("Schema not found for systemId: " + systemId);
    }

    /**
     * Constructs a new SchemaNotFoundException for the specified system ID with a cause.
     *
     * @param systemId the system ID for which schema was not found
     * @param cause the underlying cause of the failure
     */
    public SchemaNotFoundException(String systemId, Throwable cause) {
        super("Schema not found for systemId: " + systemId, cause);
    }

}
