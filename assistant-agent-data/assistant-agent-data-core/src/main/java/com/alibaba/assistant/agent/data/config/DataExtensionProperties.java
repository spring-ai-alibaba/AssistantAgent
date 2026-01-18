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
 * Configuration properties for data extension.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "spring.ai.alibaba.codeact.extension.data")
public class DataExtensionProperties {

    private boolean enabled = false;
    private SqlConfig sql = new SqlConfig();
    private ToolsConfig tools = new ToolsConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SqlConfig getSql() {
        return sql;
    }

    public void setSql(SqlConfig sql) {
        this.sql = sql;
    }

    public ToolsConfig getTools() {
        return tools;
    }

    public void setTools(ToolsConfig tools) {
        this.tools = tools;
    }

    /**
     * SQL execution configuration.
     */
    public static class SqlConfig {
        private int maxRows = 1000;
        private int timeoutSeconds = 30;
        private boolean readOnly = true;

        public int getMaxRows() {
            return maxRows;
        }

        public void setMaxRows(int maxRows) {
            this.maxRows = maxRows;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        public void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }
    }

    /**
     * Tools configuration.
     */
    public static class ToolsConfig {
        private boolean executeSqlEnabled = true;

        public boolean isExecuteSqlEnabled() {
            return executeSqlEnabled;
        }

        public void setExecuteSqlEnabled(boolean executeSqlEnabled) {
            this.executeSqlEnabled = executeSqlEnabled;
        }
    }
}
