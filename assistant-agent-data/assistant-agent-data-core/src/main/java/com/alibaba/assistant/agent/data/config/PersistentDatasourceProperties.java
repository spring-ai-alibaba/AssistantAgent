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
package com.alibaba.assistant.agent.data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for persistent datasource provider.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "spring.assistant-agent.data.persistent-datasource")
public class PersistentDatasourceProperties {

    private boolean enabled = true;
    private CacheProperties cache = new CacheProperties();
    private ConnectionProperties connection = new ConnectionProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public CacheProperties getCache() {
        return cache;
    }

    public void setCache(CacheProperties cache) {
        this.cache = cache;
    }

    public ConnectionProperties getConnection() {
        return connection;
    }

    public void setConnection(ConnectionProperties connection) {
        this.connection = connection;
    }

    /**
     * Cache configuration properties.
     */
    public static class CacheProperties {
        private boolean enabled = true;
        private int ttlMinutes = 5;
        private int cleanupIntervalSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }

        public int getCleanupIntervalSeconds() {
            return cleanupIntervalSeconds;
        }

        public void setCleanupIntervalSeconds(int cleanupIntervalSeconds) {
            this.cleanupIntervalSeconds = cleanupIntervalSeconds;
        }
    }

    /**
     * Database connection configuration properties.
     */
    public static class ConnectionProperties {
        private String url;
        private String username;
        private String password;
        private PoolProperties pool = new PoolProperties();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
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

        public PoolProperties getPool() {
            return pool;
        }

        public void setPool(PoolProperties pool) {
            this.pool = pool;
        }

        /**
         * Connection pool configuration properties.
         */
        public static class PoolProperties {
            private int maximumPoolSize = 10;
            private int minimumIdle = 2;
            private int connectionTimeout = 30000;

            public int getMaximumPoolSize() {
                return maximumPoolSize;
            }

            public void setMaximumPoolSize(int maximumPoolSize) {
                this.maximumPoolSize = maximumPoolSize;
            }

            public int getMinimumIdle() {
                return minimumIdle;
            }

            public void setMinimumIdle(int minimumIdle) {
                this.minimumIdle = minimumIdle;
            }

            public int getConnectionTimeout() {
                return connectionTimeout;
            }

            public void setConnectionTimeout(int connectionTimeout) {
                this.connectionTimeout = connectionTimeout;
            }
        }
    }
}
