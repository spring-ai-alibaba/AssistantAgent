# DataAgent Integration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Integrate DataAgent's core data querying capabilities into AssistantAgent, enabling SQL execution, NL2SQL, and dynamic parameter options.

**Architecture:** Create new `assistant-agent-data` module with api and core submodules. Migrate connector layer from DataAgent. Implement three CodeactTools (execute_sql, nl2sql, query_schema) and ParameterOptionsService for Action parameter collection.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring AI, HikariCP, MySQL/PostgreSQL JDBC

---

## Phase 1: Module Structure Setup

### Task 1: Create Parent POM for assistant-agent-data

**Files:**
- Create: `assistant-agent-data/pom.xml`
- Modify: `pom.xml` (root)

**Step 1: Create assistant-agent-data directory**

```bash
mkdir -p assistant-agent-data
```

**Step 2: Create parent pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.alibaba.agent.assistant</groupId>
        <artifactId>assistant-agent</artifactId>
        <version>0.1.1</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>assistant-agent-data</artifactId>
    <packaging>pom</packaging>
    <name>Assistant Agent Data</name>
    <description>Data integration module for AssistantAgent</description>

    <modules>
        <module>assistant-agent-data-api</module>
        <module>assistant-agent-data-core</module>
    </modules>

</project>
```

**Step 3: Add module to root pom.xml**

In root `pom.xml`, add `<module>assistant-agent-data</module>` to the `<modules>` section.

**Step 4: Verify structure**

```bash
mvn validate -pl assistant-agent-data
```

Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add assistant-agent-data/pom.xml pom.xml
git commit -m "feat(data): create assistant-agent-data parent module"
```

---

### Task 2: Create assistant-agent-data-api Module

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-api/pom.xml`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/package-info.java`

**Step 1: Create directory structure**

```bash
mkdir -p assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model
mkdir -p assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/spi
```

**Step 2: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.alibaba.agent.assistant</groupId>
        <artifactId>assistant-agent-data</artifactId>
        <version>0.1.1</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>assistant-agent-data-api</artifactId>
    <name>Assistant Agent Data API</name>
    <description>API and SPI definitions for data integration</description>

    <dependencies>
        <dependency>
            <groupId>com.alibaba.agent.assistant</groupId>
            <artifactId>assistant-agent-common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
        </dependency>
    </dependencies>

</project>
```

**Step 3: Create package-info.java**

```java
/**
 * Data integration API module for AssistantAgent.
 * Contains model classes and SPI interfaces.
 */
package com.alibaba.assistant.agent.data;
```

**Step 4: Verify build**

```bash
mvn compile -pl assistant-agent-data/assistant-agent-data-api -am
```

Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-api/
git commit -m "feat(data-api): create assistant-agent-data-api module"
```

---

### Task 3: Create assistant-agent-data-core Module

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/pom.xml`

**Step 1: Create directory structure**

```bash
mkdir -p assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data
mkdir -p assistant-agent-data/assistant-agent-data-core/src/main/resources/META-INF/spring
mkdir -p assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data
```

**Step 2: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.alibaba.agent.assistant</groupId>
        <artifactId>assistant-agent-data</artifactId>
        <version>0.1.1</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>assistant-agent-data-core</artifactId>
    <name>Assistant Agent Data Core</name>
    <description>Core implementation for data integration</description>

    <dependencies>
        <!-- Internal -->
        <dependency>
            <groupId>com.alibaba.agent.assistant</groupId>
            <artifactId>assistant-agent-data-api</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.agent.assistant</groupId>
            <artifactId>assistant-agent-common</artifactId>
        </dependency>

        <!-- Spring -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring AI -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-core</artifactId>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
        </dependency>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>8.0.33</version>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Utilities -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

</project>
```

**Step 3: Create AutoConfiguration.imports**

```
# assistant-agent-data-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.alibaba.assistant.agent.data.config.DataExtensionAutoConfiguration
```

**Step 4: Verify build**

```bash
mvn compile -pl assistant-agent-data/assistant-agent-data-core -am
```

Expected: BUILD SUCCESS (may have missing class warnings, OK for now)

**Step 5: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/
git commit -m "feat(data-core): create assistant-agent-data-core module"
```

---

## Phase 2: API Layer - Model Classes

### Task 4: Create Data Source Model

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/DatasourceDefinition.java`

**Step 1: Create DatasourceDefinition.java**

```java
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
     */
    public String getEffectiveUrl() {
        if (connectionUrl != null && !connectionUrl.isEmpty()) {
            return connectionUrl;
        }
        return switch (type.toLowerCase()) {
            case "mysql" -> String.format("jdbc:mysql://%s:%d/%s", host, port, databaseName);
            case "postgresql" -> String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
            case "sqlserver" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s", host, port, databaseName);
            case "h2" -> String.format("jdbc:h2:mem:%s", databaseName);
            default -> connectionUrl;
        };
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

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

        public DatasourceDefinition build() {
            return instance;
        }
    }
}
```

**Step 2: Verify compilation**

```bash
mvn compile -pl assistant-agent-data/assistant-agent-data-api -am
```

Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/DatasourceDefinition.java
git commit -m "feat(data-api): add DatasourceDefinition model"
```

---

### Task 5: Create Schema Model Classes

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/TableInfo.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/ColumnInfo.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/SchemaInfo.java`

**Step 1: Create TableInfo.java**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Table metadata information.
 */
public class TableInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String comment;
    private String schema;
    private List<ColumnInfo> columns = new ArrayList<>();

    public TableInfo() {
    }

    public TableInfo(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public List<ColumnInfo> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns;
    }

    public void addColumn(ColumnInfo column) {
        this.columns.add(column);
    }
}
```

**Step 2: Create ColumnInfo.java**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;

/**
 * Column metadata information.
 */
public class ColumnInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String type;
    private String comment;
    private boolean primaryKey;
    private boolean nullable;
    private String defaultValue;

    public ColumnInfo() {
    }

    public ColumnInfo(String name, String type) {
        this.name = name;
        this.type = type;
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

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
```

**Step 3: Create SchemaInfo.java**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Database schema information containing tables.
 */
public class SchemaInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String databaseName;
    private List<TableInfo> tables = new ArrayList<>();

    public SchemaInfo() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public List<TableInfo> getTables() {
        return tables;
    }

    public void setTables(List<TableInfo> tables) {
        this.tables = tables;
    }

    public void addTable(TableInfo table) {
        this.tables.add(table);
    }
}
```

**Step 4: Verify compilation**

```bash
mvn compile -pl assistant-agent-data/assistant-agent-data-api -am
```

Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/
git commit -m "feat(data-api): add TableInfo, ColumnInfo, SchemaInfo models"
```

---

### Task 6: Create QueryResult Model

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/QueryResult.java`

**Step 1: Create QueryResult.java**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL query result containing columns and rows.
 */
public class QueryResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<ColumnInfo> columns = new ArrayList<>();
    private List<Map<String, Object>> rows = new ArrayList<>();
    private int totalRows;
    private long executionTimeMs;
    private String sql;
    private boolean truncated;

    public QueryResult() {
    }

    public List<ColumnInfo> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public void addRow(Map<String, Object> row) {
        this.rows.add(row);
    }

    /**
     * Convert to simple string array format for display.
     */
    public String[][] toStringArray() {
        if (rows.isEmpty()) {
            return new String[0][];
        }
        String[][] result = new String[rows.size()][columns.size()];
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            for (int j = 0; j < columns.size(); j++) {
                Object value = row.get(columns.get(j).getName());
                result[i][j] = value == null ? "NULL" : value.toString();
            }
        }
        return result;
    }

    /**
     * Format as markdown table.
     */
    public String toMarkdownTable() {
        if (columns.isEmpty()) {
            return "No results.";
        }
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("| ");
        for (ColumnInfo col : columns) {
            sb.append(col.getName()).append(" | ");
        }
        sb.append("\n|");
        for (int i = 0; i < columns.size(); i++) {
            sb.append("---|");
        }
        sb.append("\n");

        // Rows
        for (Map<String, Object> row : rows) {
            sb.append("| ");
            for (ColumnInfo col : columns) {
                Object value = row.get(col.getName());
                sb.append(value == null ? "NULL" : value.toString()).append(" | ");
            }
            sb.append("\n");
        }

        if (truncated) {
            sb.append("\n*Results truncated. Total rows: ").append(totalRows).append("*");
        }

        return sb.toString();
    }
}
```

**Step 2: Verify compilation**

```bash
mvn compile -pl assistant-agent-data/assistant-agent-data-api -am
```

Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/QueryResult.java
git commit -m "feat(data-api): add QueryResult model with markdown formatting"
```

---

### Task 7: Create OptionsSource Configuration Models

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/OptionsSourceConfig.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/SqlSourceConfig.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/ApiSourceConfig.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/Nl2SqlSourceConfig.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/OptionItem.java`

**Step 1: Create SourceType enum and OptionsSourceConfig**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;

/**
 * Configuration for parameter options data source.
 */
public class OptionsSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum SourceType {
        SQL,      // Direct SQL query
        API,      // HTTP API call
        NL2SQL    // Natural language to SQL
    }

    private SourceType sourceType;
    private SqlSourceConfig sql;
    private ApiSourceConfig api;
    private Nl2SqlSourceConfig nl2sql;

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public SqlSourceConfig getSql() {
        return sql;
    }

    public void setSql(SqlSourceConfig sql) {
        this.sql = sql;
    }

    public ApiSourceConfig getApi() {
        return api;
    }

    public void setApi(ApiSourceConfig api) {
        this.api = api;
    }

    public Nl2SqlSourceConfig getNl2sql() {
        return nl2sql;
    }

    public void setNl2sql(Nl2SqlSourceConfig nl2sql) {
        this.nl2sql = nl2sql;
    }
}
```

**Step 2: Create SqlSourceConfig**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * SQL-based options source configuration.
 */
public class SqlSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sql;
    private String labelColumn;
    private String valueColumn;
    private Map<String, String> paramMapping = new HashMap<>();

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getLabelColumn() {
        return labelColumn;
    }

    public void setLabelColumn(String labelColumn) {
        this.labelColumn = labelColumn;
    }

    public String getValueColumn() {
        return valueColumn;
    }

    public void setValueColumn(String valueColumn) {
        this.valueColumn = valueColumn;
    }

    public Map<String, String> getParamMapping() {
        return paramMapping;
    }

    public void setParamMapping(Map<String, String> paramMapping) {
        this.paramMapping = paramMapping;
    }
}
```

**Step 3: Create ApiSourceConfig**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * API-based options source configuration.
 */
public class ApiSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String url;
    private String method = "GET";
    private Map<String, String> headers = new HashMap<>();
    private String requestBody;
    private String responsePath;  // JSON Path to extract list
    private String labelField;
    private String valueField;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponsePath() {
        return responsePath;
    }

    public void setResponsePath(String responsePath) {
        this.responsePath = responsePath;
    }

    public String getLabelField() {
        return labelField;
    }

    public void setLabelField(String labelField) {
        this.labelField = labelField;
    }

    public String getValueField() {
        return valueField;
    }

    public void setValueField(String valueField) {
        this.valueField = valueField;
    }
}
```

**Step 4: Create Nl2SqlSourceConfig**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;

/**
 * NL2SQL-based options source configuration.
 */
public class Nl2SqlSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String description;  // Natural language description
    private String labelColumn;
    private String valueColumn;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLabelColumn() {
        return labelColumn;
    }

    public void setLabelColumn(String labelColumn) {
        this.labelColumn = labelColumn;
    }

    public String getValueColumn() {
        return valueColumn;
    }

    public void setValueColumn(String valueColumn) {
        this.valueColumn = valueColumn;
    }
}
```

**Step 5: Create OptionItem**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;

/**
 * Single option item for parameter selection.
 */
public class OptionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String label;
    private Object value;
    private String description;

    public OptionItem() {
    }

    public OptionItem(String label, Object value) {
        this.label = label;
        this.value = value;
    }

    public OptionItem(String label, Object value, String description) {
        this.label = label;
        this.value = value;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return label + " (" + value + ")";
    }
}
```

**Step 6: Verify compilation**

```bash
mvn compile -pl assistant-agent-data/assistant-agent-data-api -am
```

Expected: BUILD SUCCESS

**Step 7: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/
git commit -m "feat(data-api): add OptionsSource configuration models"
```

---

## Phase 3: API Layer - SPI Interfaces

### Task 8: Create SPI Interfaces

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/spi/DatasourceProvider.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/spi/SchemaProvider.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/spi/SqlExecutionProvider.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/spi/ParameterOptionsProvider.java`

**Step 1: Create DatasourceProvider**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.spi;

import com.alibaba.assistant.agent.data.model.DatasourceDefinition;

import java.util.List;
import java.util.Optional;

/**
 * SPI for data source management.
 */
public interface DatasourceProvider {

    /**
     * Get data source by ID.
     */
    Optional<DatasourceDefinition> getById(Long id);

    /**
     * Get data source by system ID (agent ID).
     */
    Optional<DatasourceDefinition> getBySystemId(String systemId);

    /**
     * Get all available data sources.
     */
    List<DatasourceDefinition> getAll();

    /**
     * Test data source connection.
     */
    boolean testConnection(DatasourceDefinition datasource);
}
```

**Step 2: Create SchemaProvider**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.spi;

import com.alibaba.assistant.agent.data.model.ColumnInfo;
import com.alibaba.assistant.agent.data.model.SchemaInfo;
import com.alibaba.assistant.agent.data.model.TableInfo;

import java.util.List;

/**
 * SPI for database schema metadata.
 */
public interface SchemaProvider {

    /**
     * Get schema info for a system.
     */
    SchemaInfo getSchema(String systemId);

    /**
     * Get all tables for a system.
     */
    List<TableInfo> getTables(String systemId);

    /**
     * Get table info including columns.
     */
    TableInfo getTable(String systemId, String tableName);

    /**
     * Get columns for a table.
     */
    List<ColumnInfo> getColumns(String systemId, String tableName);
}
```

**Step 3: Create SqlExecutionProvider**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.spi;

import com.alibaba.assistant.agent.data.model.QueryResult;

/**
 * SPI for SQL execution.
 */
public interface SqlExecutionProvider {

    /**
     * Execute SQL query.
     *
     * @param systemId System identifier
     * @param sql SQL statement (must be SELECT)
     * @return Query result
     */
    QueryResult execute(String systemId, String sql);

    /**
     * Execute SQL query with row limit.
     *
     * @param systemId System identifier
     * @param sql SQL statement
     * @param maxRows Maximum rows to return
     * @return Query result
     */
    QueryResult execute(String systemId, String sql, int maxRows);

    /**
     * Validate SQL is read-only.
     *
     * @param sql SQL statement
     * @throws SecurityException if SQL contains write operations
     */
    void validateReadOnly(String sql);
}
```

**Step 4: Create ParameterOptionsProvider**

```java
/*
 * Copyright 2024-2025 the original author or authors.
 */
package com.alibaba.assistant.agent.data.spi;

import com.alibaba.assistant.agent.data.model.OptionItem;
import com.alibaba.assistant.agent.data.model.OptionsSourceConfig;

import java.util.List;
import java.util.Map;

/**
 * SPI for fetching parameter options from various sources.
 */
public interface ParameterOptionsProvider {

    /**
     * Fetch options based on configuration.
     *
     * @param systemId System identifier
     * @param config Options source configuration
     * @param context Context containing already collected parameters
     * @return List of option items
     */
    List<OptionItem> fetchOptions(String systemId,
                                   OptionsSourceConfig config,
                                   Map<String, Object> context);
}
```

**Step 5: Verify compilation**

```bash
mvn compile -pl assistant-agent-data/assistant-agent-data-api -am
```

Expected: BUILD SUCCESS

**Step 6: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/spi/
git commit -m "feat(data-api): add SPI interfaces for datasource, schema, sql execution"
```

---

## Phase 4: Core Implementation (Summary)

> Note: The remaining tasks follow the same pattern. Due to length, summarizing key tasks:

### Task 9-12: Migrate Connector Layer from DataAgent
- Copy and adapt `SqlExecutor`, `ResultSetBuilder` from DataAgent
- Implement `DefaultSqlExecutionProvider`
- Add `SqlSecurityValidator` for read-only enforcement

### Task 13-15: Implement Schema Provider
- Implement `DefaultSchemaProvider` using JDBC metadata
- Support MySQL, PostgreSQL, H2

### Task 16-18: Implement CodeactTools
- `ExecuteSqlCodeactTool`
- `Nl2SqlCodeactTool`
- `QuerySchemaCodeactTool`

### Task 19-20: Implement ParameterOptionsService
- `DefaultParameterOptionsProvider`
- Integration with UnifiedIntentRecognitionHook

### Task 21-23: Auto Configuration
- `DataExtensionProperties`
- `DataExtensionAutoConfiguration`
- Wire up all components

### Task 24-26: Testing
- Unit tests for SqlSecurityValidator
- Integration tests with H2
- End-to-end test for parameter options

---

## Execution Checklist

- [ ] Phase 1: Module structure (Tasks 1-3)
- [ ] Phase 2: Model classes (Tasks 4-7)
- [ ] Phase 3: SPI interfaces (Task 8)
- [ ] Phase 4: Core implementation (Tasks 9-20)
- [ ] Phase 5: Configuration (Tasks 21-23)
- [ ] Phase 6: Testing (Tasks 24-26)

---

**Total estimated tasks:** 26

**Each task:** 5-15 minutes

**Review checkpoints:** After each Phase
