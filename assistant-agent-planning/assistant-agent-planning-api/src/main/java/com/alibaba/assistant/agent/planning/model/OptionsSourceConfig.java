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
package com.alibaba.assistant.agent.planning.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Configuration wrapper for parameter option sources.
 * Supports multiple source types with type-safe configuration.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class OptionsSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Source type enum (default: NL2SQL if null).
     */
    private SourceType type;

    /**
     * Datasource identifier (for NL2SQL/HTTP).
     */
    private String systemId;

    /**
     * Specific config object (type-dependent).
     */
    private Object config;

    /**
     * Source type enumeration.
     */
    public enum SourceType {
        /**
         * Natural language to SQL query.
         */
        NL2SQL,

        /**
         * Static configuration list.
         */
        STATIC,

        /**
         * HTTP API call.
         */
        HTTP,

        /**
         * Java enum reflection.
         */
        ENUM
    }

    public SourceType getType() {
        return type;
    }

    public void setType(SourceType type) {
        this.type = type;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public Object getConfig() {
        return config;
    }

    public void setConfig(Object config) {
        this.config = config;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OptionsSourceConfig that = (OptionsSourceConfig) o;
        return type == that.type &&
                Objects.equals(systemId, that.systemId) &&
                Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, systemId, config);
    }

    @Override
    public String toString() {
        return "OptionsSourceConfig{" +
                "type=" + type +
                ", systemId='" + systemId + '\'' +
                ", config=" + config +
                '}';
    }
}
