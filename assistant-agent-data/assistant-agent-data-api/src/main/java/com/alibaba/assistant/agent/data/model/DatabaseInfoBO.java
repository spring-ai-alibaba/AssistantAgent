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
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Business object representing database metadata information.
 * This class is designed to be compatible with DataAgent's data structures
 * for database catalog metadata.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class DatabaseInfoBO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The name of the database.
     */
    private String name;

    /**
     * Default constructor.
     */
    public DatabaseInfoBO() {
    }

    /**
     * Gets the name of the database.
     *
     * @return the database name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the database.
     *
     * @param name the database name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatabaseInfoBO that = (DatabaseInfoBO) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "DatabaseInfoBO{" +
                "name='" + name + '\'' +
                '}';
    }

    /**
     * Creates a new builder for constructing DatabaseInfoBO instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing DatabaseInfoBO instances.
     * Provides a fluent API for setting database properties.
     */
    public static class Builder {
        private final DatabaseInfoBO instance = new DatabaseInfoBO();

        /**
         * Sets the database name.
         *
         * @param name the database name
         * @return this builder instance
         */
        public Builder name(String name) {
            instance.name = name;
            return this;
        }

        /**
         * Builds and returns the configured DatabaseInfoBO instance.
         *
         * @return the constructed DatabaseInfoBO
         */
        public DatabaseInfoBO build() {
            return instance;
        }
    }
}
