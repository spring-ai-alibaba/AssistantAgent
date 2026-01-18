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
 * Business object representing schema metadata information.
 * This class is designed to be compatible with DataAgent's data structures
 * for schema catalog metadata.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SchemaInfoBO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The name of the schema.
     */
    private String name;

    /**
     * The catalog (database) this schema belongs to.
     */
    private String catalog;

    /**
     * Default constructor.
     */
    public SchemaInfoBO() {
    }

    /**
     * Gets the name of the schema.
     *
     * @return the schema name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the schema.
     *
     * @param name the schema name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the catalog this schema belongs to.
     *
     * @return the catalog name
     */
    public String getCatalog() {
        return catalog;
    }

    /**
     * Sets the catalog this schema belongs to.
     *
     * @param catalog the catalog name to set
     */
    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchemaInfoBO that = (SchemaInfoBO) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(catalog, that.catalog);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, catalog);
    }

    @Override
    public String toString() {
        return "SchemaInfoBO{" +
                "name='" + name + '\'' +
                ", catalog='" + catalog + '\'' +
                '}';
    }

    /**
     * Creates a new builder for constructing SchemaInfoBO instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing SchemaInfoBO instances.
     * Provides a fluent API for setting schema properties.
     */
    public static class Builder {
        private final SchemaInfoBO instance = new SchemaInfoBO();

        /**
         * Sets the schema name.
         *
         * @param name the schema name
         * @return this builder instance
         */
        public Builder name(String name) {
            instance.name = name;
            return this;
        }

        /**
         * Sets the catalog name.
         *
         * @param catalog the catalog name
         * @return this builder instance
         */
        public Builder catalog(String catalog) {
            instance.catalog = catalog;
            return this;
        }

        /**
         * Builds and returns the configured SchemaInfoBO instance.
         *
         * @return the constructed SchemaInfoBO
         */
        public SchemaInfoBO build() {
            return instance;
        }
    }
}
