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
 * Exception thrown when the LLM fails to generate valid SQL from natural language input.
 * This may occur due to ambiguous queries, unsupported SQL patterns, or LLM API errors.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SqlGenerationException extends Nl2SqlException {

    /**
     * Constructs a new SqlGenerationException with the specified detail message and cause.
     *
     * @param message the detail message describing why SQL generation failed
     * @param cause the underlying cause of the failure
     */
    public SqlGenerationException(String message, Throwable cause) {
        super("SQL generation failed: " + message, cause);
    }

}
