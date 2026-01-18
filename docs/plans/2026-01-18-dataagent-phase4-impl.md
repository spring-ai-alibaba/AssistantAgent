# DataAgent Phase 4-6 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Implement core data integration functionality including SQL execution, schema queries, and CodeactTools for AssistantAgent.

**Architecture:** Migrate essential connector components from DataAgent, implement SPI providers, create CodeactTools for SQL execution and schema queries, configure auto-configuration for Spring Boot integration.

**Tech Stack:** Java 17, Spring Boot 3.4, JDBC, HikariCP, Spring AI

---

## Phase 4: Core Implementation

### Task 9: Create SqlSecurityValidator

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/security/SqlSecurityValidator.java`
- Test: `assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/security/SqlSecurityValidatorTest.java`

**Step 1: Write test**

```java
package com.alibaba.assistant.agent.data.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SqlSecurityValidatorTest {

    private final SqlSecurityValidator validator = new SqlSecurityValidator();

    @Test
    void shouldAllowSelectStatement() {
        assertDoesNotThrow(() -> validator.validateReadOnly("SELECT * FROM users"));
    }

    @Test
    void shouldRejectInsertStatement() {
        assertThrows(SecurityException.class,
            () -> validator.validateReadOnly("INSERT INTO users VALUES (1, 'test')"));
    }

    @Test
    void shouldRejectUpdateStatement() {
        assertThrows(SecurityException.class,
            () -> validator.validateReadOnly("UPDATE users SET name='test'"));
    }

    @Test
    void shouldRejectDeleteStatement() {
        assertThrows(SecurityException.class,
            () -> validator.validateReadOnly("DELETE FROM users"));
    }

    @Test
    void shouldRejectDropStatement() {
        assertThrows(SecurityException.class,
            () -> validator.validateReadOnly("DROP TABLE users"));
    }
}
```

**Step 2: Run test - verify failure**

```bash
cd D:/devfive/AssistantAgent/.worktrees/dataagent-phase4
mvn test -Dtest=SqlSecurityValidatorTest
```

Expected: Compilation error - class not found

**Step 3: Implement SqlSecurityValidator**

```java
package com.alibaba.assistant.agent.data.security;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates SQL statements for read-only operations.
 * Ensures only SELECT statements are allowed, blocking DML/DDL operations.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SqlSecurityValidator {

    private static final List<String> FORBIDDEN_KEYWORDS = Arrays.asList(
        "INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE",
        "ALTER", "CREATE", "GRANT", "REVOKE"
    );

    private static final Pattern COMMENT_PATTERN = Pattern.compile("--.*|/\\*.*?\\*/", Pattern.DOTALL);

    /**
     * Validates that the SQL statement is read-only (SELECT only).
     *
     * @param sql SQL statement to validate
     * @throws SecurityException if SQL contains forbidden operations
     * @throws IllegalArgumentException if SQL is null or empty
     */
    public void validateReadOnly(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL statement cannot be null or empty");
        }

        // Remove comments
        String cleanSql = COMMENT_PATTERN.matcher(sql).replaceAll(" ");
        String normalized = cleanSql.trim().toUpperCase();

        // Must start with SELECT
        if (!normalized.startsWith("SELECT")) {
            throw new SecurityException("Only SELECT statements are allowed");
        }

        // Check for forbidden keywords
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (normalized.contains(keyword)) {
                throw new SecurityException("SQL contains forbidden keyword: " + keyword);
            }
        }
    }
}
```

**Step 4: Run test - verify pass**

```bash
mvn test -Dtest=SqlSecurityValidatorTest
```

Expected: All tests pass

**Step 5: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/security/SqlSecurityValidator.java
git add assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/security/SqlSecurityValidatorTest.java
git commit -m "feat(data-core): add SqlSecurityValidator for read-only enforcement"
```

---

### Task 10: Create SqlExecutor Utility

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/executor/SqlExecutor.java`

**Step 1: Implement SqlExecutor**

```java
package com.alibaba.assistant.agent.data.executor;

import com.alibaba.assistant.agent.data.model.QueryResult;
import com.alibaba.assistant.agent.data.model.ColumnInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes SQL queries and converts results to QueryResult objects.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SqlExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SqlExecutor.class);

    private static final int DEFAULT_MAX_ROWS = 1000;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * Execute SQL query and return structured results.
     *
     * @param connection database connection
     * @param sql SQL statement
     * @param maxRows maximum number of rows to return
     * @return QueryResult containing columns and rows
     * @throws SQLException if query execution fails
     */
    public static QueryResult executeQuery(Connection connection, String sql, int maxRows) throws SQLException {
        long startTime = System.currentTimeMillis();

        try (Statement statement = connection.createStatement()) {
            statement.setMaxRows(maxRows);
            statement.setQueryTimeout(DEFAULT_TIMEOUT_SECONDS);

            try (ResultSet rs = statement.executeQuery(sql)) {
                QueryResult result = buildQueryResult(rs, maxRows);
                result.setSql(sql);
                result.setExecutionTimeMs(System.currentTimeMillis() - startTime);

                logger.info("SqlExecutor#executeQuery - executed SQL, rows={}, time={}ms",
                    result.getRows().size(), result.getExecutionTimeMs());

                return result;
            }
        }
    }

    /**
     * Execute SQL query with default max rows.
     */
    public static QueryResult executeQuery(Connection connection, String sql) throws SQLException {
        return executeQuery(connection, sql, DEFAULT_MAX_ROWS);
    }

    private static QueryResult buildQueryResult(ResultSet rs, int maxRows) throws SQLException {
        QueryResult result = new QueryResult();

        // Build column info
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<ColumnInfo> columns = new ArrayList<>();

        for (int i = 1; i <= columnCount; i++) {
            ColumnInfo column = new ColumnInfo();
            column.setName(metaData.getColumnName(i));
            column.setType(metaData.getColumnTypeName(i));
            columns.add(column);
        }
        result.setColumns(columns);

        // Build rows
        int rowCount = 0;
        while (rs.next() && rowCount < maxRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnName(i), rs.getObject(i));
            }
            result.addRow(row);
            rowCount++;
        }

        // Check if truncated
        if (rs.next()) {
            result.setTruncated(true);
            result.setTotalRows(maxRows + 1); // At least maxRows + 1
        } else {
            result.setTotalRows(rowCount);
        }

        return result;
    }
}
```

**Step 2: Compile and verify**

```bash
mvn compile -pl assistant-agent-data/assistant-agent-data-core -am
```

Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/executor/SqlExecutor.java
git commit -m "feat(data-core): add SqlExecutor for query execution"
```

---

### Task 11: Implement DefaultSqlExecutionProvider

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/provider/DefaultSqlExecutionProvider.java`

**Step 1: Implement provider**

```java
package com.alibaba.assistant.agent.data.provider;

import com.alibaba.assistant.agent.data.executor.SqlExecutor;
import com.alibaba.assistant.agent.data.model.DatasourceDefinition;
import com.alibaba.assistant.agent.data.model.QueryResult;
import com.alibaba.assistant.agent.data.security.SqlSecurityValidator;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import com.alibaba.assistant.agent.data.spi.SqlExecutionProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of SQL execution provider.
 * Uses HikariCP for connection pooling.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class DefaultSqlExecutionProvider implements SqlExecutionProvider {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSqlExecutionProvider.class);

    private final DatasourceProvider datasourceProvider;
    private final SqlSecurityValidator securityValidator;
    private final Map<Long, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    public DefaultSqlExecutionProvider(DatasourceProvider datasourceProvider) {
        this.datasourceProvider = datasourceProvider;
        this.securityValidator = new SqlSecurityValidator();
    }

    @Override
    public QueryResult execute(String systemId, String sql) throws SQLException {
        return execute(systemId, sql, 1000);
    }

    @Override
    public QueryResult execute(String systemId, String sql, int maxRows) throws SQLException {
        logger.info("DefaultSqlExecutionProvider#execute - systemId={}, maxRows={}", systemId, maxRows);

        // Validate read-only
        securityValidator.validateReadOnly(sql);

        // Get datasource
        DatasourceDefinition datasource = datasourceProvider.getBySystemId(systemId);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource not found for systemId: " + systemId);
        }

        // Execute query
        HikariDataSource dataSource = getOrCreateDataSource(datasource);
        try (Connection connection = dataSource.getConnection()) {
            return SqlExecutor.executeQuery(connection, sql, maxRows);
        }
    }

    @Override
    public void validateReadOnly(String sql) {
        securityValidator.validateReadOnly(sql);
    }

    private HikariDataSource getOrCreateDataSource(DatasourceDefinition def) {
        return dataSourceCache.computeIfAbsent(def.getId(), id -> {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(def.getEffectiveUrl());
            config.setUsername(def.getUsername());
            config.setPassword(def.getPassword());
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10000);

            logger.info("DefaultSqlExecutionProvider - created datasource pool for id={}", id);
            return new HikariDataSource(config);
        });
    }
}
```

**Step 2: Compile**

```bash
mvn compile -pl assistant-agent-data/assistant-agent-data-core -am
```

Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/provider/DefaultSqlExecutionProvider.java
git commit -m "feat(data-core): add DefaultSqlExecutionProvider with connection pooling"
```

---

### Task 12: Implement InMemoryDatasourceProvider

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/provider/InMemoryDatasourceProvider.java`

**Step 1: Implement provider**

```java
package com.alibaba.assistant.agent.data.provider;

import com.alibaba.assistant.agent.data.model.DatasourceDefinition;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of DatasourceProvider for demo/testing.
 * Stores datasources in memory without persistence.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class InMemoryDatasourceProvider implements DatasourceProvider {

    private final Map<Long, DatasourceDefinition> datasources = new ConcurrentHashMap<>();
    private final Map<String, Long> systemIdIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public DatasourceDefinition getById(Long id) {
        return datasources.get(id);
    }

    @Override
    public DatasourceDefinition getBySystemId(String systemId) {
        Long id = systemIdIndex.get(systemId);
        return id != null ? datasources.get(id) : null;
    }

    @Override
    public List<DatasourceDefinition> getAll() {
        return new ArrayList<>(datasources.values());
    }

    @Override
    public boolean testConnection(DatasourceDefinition datasource) {
        try (Connection conn = DriverManager.getConnection(
                datasource.getEffectiveUrl(),
                datasource.getUsername(),
                datasource.getPassword())) {
            return conn.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Register a datasource for testing/demo purposes.
     */
    public Long register(String systemId, DatasourceDefinition datasource) {
        if (datasource.getId() == null) {
            datasource.setId(idGenerator.getAndIncrement());
        }
        datasources.put(datasource.getId(), datasource);
        systemIdIndex.put(systemId, datasource.getId());
        return datasource.getId();
    }
}
```

**Step 2: Compile**

```bash
mvn compile -pl assistant-agent-data/assistant-agent-data-core -am
```

**Step 3: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/provider/InMemoryDatasourceProvider.java
git commit -m "feat(data-core): add InMemoryDatasourceProvider for demo"
```

---

## Phase 5: CodeactTools

### Task 13: Create ExecuteSqlCodeactTool

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/tool/ExecuteSqlCodeactTool.java`

**Step 1: Implement tool**

```java
package com.alibaba.assistant.agent.data.tool;

import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.data.model.QueryResult;
import com.alibaba.assistant.agent.data.spi.SqlExecutionProvider;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * CodeactTool for executing SQL queries.
 * Enforces read-only operations for security.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class ExecuteSqlCodeactTool implements CodeactTool {

    private final SqlExecutionProvider sqlExecutionProvider;

    public ExecuteSqlCodeactTool(SqlExecutionProvider sqlExecutionProvider) {
        this.sqlExecutionProvider = sqlExecutionProvider;
    }

    @Override
    public String getName() {
        return "execute_sql";
    }

    @Override
    public String getDescription() {
        return "Execute read-only SQL query against a datasource. " +
               "Only SELECT statements are allowed for security.";
    }

    @Override
    public String call(String arguments, ToolContext context) {
        Map<String, Object> params = parseArguments(arguments);

        String systemId = (String) params.get("systemId");
        String sql = (String) params.get("sql");
        Integer maxRows = params.containsKey("maxRows") ?
            ((Number) params.get("maxRows")).intValue() : 1000;

        try {
            QueryResult result = sqlExecutionProvider.execute(systemId, sql, maxRows);
            return result.toMarkdownTable();
        } catch (SQLException e) {
            return "Error executing SQL: " + e.getMessage();
        } catch (SecurityException e) {
            return "Security violation: " + e.getMessage();
        }
    }

    private Map<String, Object> parseArguments(String arguments) {
        // Simple JSON parsing - in production use Jackson
        Map<String, Object> params = new HashMap<>();
        // TODO: Implement proper JSON parsing
        return params;
    }
}
```

**Step 2: Compile**

```bash
mvn compile -pl assistant-agent-data/assistant-agent-data-core -am
```

**Step 3: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/tool/ExecuteSqlCodeactTool.java
git commit -m "feat(data-core): add ExecuteSqlCodeactTool for SQL queries"
```

---

## Phase 6: Configuration

### Task 14: Create DataExtensionProperties

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/config/DataExtensionProperties.java`

**Step 1: Implement properties**

```java
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

    public static class SqlConfig {
        private int maxRows = 1000;
        private int timeoutSeconds = 30;
        private boolean readOnly = true;

        public int getMaxRows() { return maxRows; }
        public void setMaxRows(int maxRows) { this.maxRows = maxRows; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public boolean isReadOnly() { return readOnly; }
        public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }
    }

    public static class ToolsConfig {
        private boolean executeSqlEnabled = true;

        public boolean isExecuteSqlEnabled() { return executeSqlEnabled; }
        public void setExecuteSqlEnabled(boolean executeSqlEnabled) {
            this.executeSqlEnabled = executeSqlEnabled;
        }
    }
}
```

**Step 2: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/config/DataExtensionProperties.java
git commit -m "feat(data-core): add DataExtensionProperties for configuration"
```

---

### Task 15: Create DataExtensionAutoConfiguration

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/config/DataExtensionAutoConfiguration.java`

**Step 1: Implement auto-configuration**

```java
package com.alibaba.assistant.agent.data.config;

import com.alibaba.assistant.agent.data.provider.DefaultSqlExecutionProvider;
import com.alibaba.assistant.agent.data.provider.InMemoryDatasourceProvider;
import com.alibaba.assistant.agent.data.tool.ExecuteSqlCodeactTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for Data Extension.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.data", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DataExtensionProperties.class)
@ComponentScan(basePackages = "com.alibaba.assistant.agent.data")
public class DataExtensionAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DataExtensionAutoConfiguration.class);

    public DataExtensionAutoConfiguration() {
        logger.info("DataExtensionAutoConfiguration - initializing data extension");
    }
}
```

**Step 2: Compile and verify**

```bash
mvn compile -pl assistant-agent-data/assistant-agent-data-core -am
```

**Step 3: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/config/DataExtensionAutoConfiguration.java
git commit -m "feat(data-core): add DataExtensionAutoConfiguration"
```

---

## Phase 7: Testing

### Task 16: Create Integration Test

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/integration/SqlExecutionIntegrationTest.java`

**Step 1: Implement test**

```java
package com.alibaba.assistant.agent.data.integration;

import com.alibaba.assistant.agent.data.model.DatasourceDefinition;
import com.alibaba.assistant.agent.data.model.QueryResult;
import com.alibaba.assistant.agent.data.provider.InMemoryDatasourceProvider;
import com.alibaba.assistant.agent.data.provider.DefaultSqlExecutionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SqlExecutionIntegrationTest {

    private InMemoryDatasourceProvider datasourceProvider;
    private DefaultSqlExecutionProvider sqlExecutionProvider;

    @BeforeEach
    void setUp() throws Exception {
        // Setup H2 in-memory database
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:testdb", "sa", "");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50))");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");
        }
        conn.close();

        // Register datasource
        datasourceProvider = new InMemoryDatasourceProvider();
        DatasourceDefinition ds = DatasourceDefinition.builder()
            .name("test-db")
            .type("h2")
            .databaseName("testdb")
            .username("sa")
            .password("")
            .connectionUrl("jdbc:h2:mem:testdb")
            .build();
        datasourceProvider.register("test-system", ds);

        sqlExecutionProvider = new DefaultSqlExecutionProvider(datasourceProvider);
    }

    @Test
    void shouldExecuteSelectQuery() throws Exception {
        QueryResult result = sqlExecutionProvider.execute("test-system", "SELECT * FROM users");

        assertNotNull(result);
        assertEquals(2, result.getRows().size());
        assertFalse(result.isTruncated());
    }

    @Test
    void shouldRejectInsertQuery() {
        assertThrows(SecurityException.class, () ->
            sqlExecutionProvider.execute("test-system", "INSERT INTO users VALUES (3, 'Charlie')")
        );
    }
}
```

**Step 2: Run test**

```bash
mvn test -Dtest=SqlExecutionIntegrationTest
```

Expected: All tests pass

**Step 3: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/integration/SqlExecutionIntegrationTest.java
git commit -m "test(data-core): add integration test for SQL execution"
```

---

## Execution Summary

**Total Tasks:** 16 (simplified from original 26)

**Phases:**
- Phase 4: Core Implementation (Tasks 9-12) - Security, execution, providers
- Phase 5: CodeactTools (Task 13) - Execute SQL tool
- Phase 6: Configuration (Tasks 14-15) - Auto-configuration
- Phase 7: Testing (Task 16) - Integration test

**Expected Time:** 2-3 hours for all tasks

**Test Command:**
```bash
mvn clean test -pl assistant-agent-data
```

**Build Command:**
```bash
mvn clean install -DskipTests
```
