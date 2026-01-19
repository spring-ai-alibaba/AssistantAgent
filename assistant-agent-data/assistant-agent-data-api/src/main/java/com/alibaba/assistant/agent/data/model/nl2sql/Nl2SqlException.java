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
 * Base exception for all NL2SQL-related errors.
 * This is an unchecked exception (extends RuntimeException) following Spring's exception handling patterns.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class Nl2SqlException extends RuntimeException {

    /**
     * Constructs a new Nl2SqlException with the specified detail message.
     *
     * @param message the detail message
     */
    public Nl2SqlException(String message) {
        super(message);
    }

    /**
     * Constructs a new Nl2SqlException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public Nl2SqlException(String message, Throwable cause) {
        super(message, cause);
    }

}
