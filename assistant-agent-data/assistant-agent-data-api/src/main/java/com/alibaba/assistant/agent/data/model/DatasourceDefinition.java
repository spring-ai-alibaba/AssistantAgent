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
 * Data source definition for database connections.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class DatasourceDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String type;  // mysql, postgresql, sqlserver, h2, dm
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;
    private String connectionUrl;
    private String status;

    public DatasourceDefinition() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConnectionUrl() {
        return connectionUrl;
    }

    public void setConnectionUrl(String connectionUrl) {
        this.connectionUrl = connectionUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Build JDBC URL from components if connectionUrl is not set.
     *
     * @return the effective JDBC URL either from connectionUrl or built from host/port/database components
     * @throws IllegalStateException if connectionUrl is not set and required fields (type, host, port, databaseName) are null
     */
    public String getEffectiveUrl() {
        if (connectionUrl != null && !connectionUrl.isEmpty()) {
            return connectionUrl;
        }
        if (type == null || host == null || port == null || databaseName == null) {
            throw new IllegalStateException("Type, host, port, and databaseName are required when connectionUrl is not set");
        }
        return switch (type.toLowerCase()) {
            case "mysql" -> String.format("jdbc:mysql://%s:%d/%s", host, port, databaseName);
            case "postgresql" -> String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
            case "sqlserver" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s", host, port, databaseName);
            case "h2" -> String.format("jdbc:h2:mem:%s", databaseName);
            default -> connectionUrl;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatasourceDefinition that = (DatasourceDefinition) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(type, that.type) &&
                Objects.equals(host, that.host) &&
                Objects.equals(port, that.port) &&
                Objects.equals(databaseName, that.databaseName) &&
                Objects.equals(username, that.username) &&
                Objects.equals(connectionUrl, that.connectionUrl) &&
                Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, host, port, databaseName, username, connectionUrl, status);
    }

    @Override
    public String toString() {
        return "DatasourceDefinition{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", databaseName='" + databaseName + '\'' +
                ", username='" + username + '\'' +
                ", connectionUrl='" + connectionUrl + '\'' +
                ", status='" + status + '\'' +
                '}';
    }

    /**
     * Creates a new builder for constructing DatasourceDefinition instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing DatasourceDefinition instances.
     * Provides a fluent API for setting datasource properties.
     */
    public static class Builder {
        private final DatasourceDefinition instance = new DatasourceDefinition();

        public Builder id(Long id) {
            instance.id = id;
            return this;
        }

        public Builder name(String name) {
            instance.name = name;
            return this;
        }

        public Builder type(String type) {
            instance.type = type;
            return this;
        }

        public Builder host(String host) {
            instance.host = host;
            return this;
        }

        public Builder port(Integer port) {
            instance.port = port;
            return this;
        }

        public Builder databaseName(String databaseName) {
            instance.databaseName = databaseName;
            return this;
        }

        public Builder username(String username) {
            instance.username = username;
            return this;
        }

        public Builder password(String password) {
            instance.password = password;
            return this;
        }

        public Builder connectionUrl(String connectionUrl) {
            instance.connectionUrl = connectionUrl;
            return this;
        }

        public Builder status(String status) {
            instance.status = status;
            return this;
        }

        /**
         * Builds and returns the configured DatasourceDefinition instance.
         *
         * @return the constructed DatasourceDefinition
         */
        public DatasourceDefinition build() {
            return instance;
        }
    }
}
