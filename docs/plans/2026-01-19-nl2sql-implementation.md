# NL2SQL Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement lightweight NL2SQL capability for AssistantAgent to support parameter collection and simple query scenarios.

**Architecture:** Migrate core NL2SQL logic from DataAgent, integrate with existing SchemaProvider and SqlExecutionProvider, use smart threshold-based schema filtering (>=10 tables).

**Tech Stack:** Spring Boot 3.4.8, Spring AI, DashScope API, JUnit 5, Mockito

---

## Task 1: Create Data Models (DTO Classes)

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/SchemaDTO.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/TableDTO.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/ColumnDTO.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/SqlGenerationDTO.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/OptionItem.java`

**Step 1: Create SchemaDTO**

```java
package com.alibaba.assistant.agent.data.model.nl2sql;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class SchemaDTO {
    private String name;
    private String description;
    private Integer tableCount;
    private List<TableDTO> table;
    private List<String> foreignKeys;
}
```

**Step 2: Create TableDTO**

```java
package com.alibaba.assistant.agent.data.model.nl2sql;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class TableDTO {
    private String name;
    private String description;
    private List<ColumnDTO> column = new ArrayList<>();
    private List<String> primaryKeys;
}
```

**Step 3: Create ColumnDTO**

```java
package com.alibaba.assistant.agent.data.model.nl2sql;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class ColumnDTO {
    private String name;
    private String description;
    private String type;
    private List<String> data;
    private Map<String, String> mapping;
    private int enumeration;
    private String range;
}
```

**Step 4: Create SqlGenerationDTO**

```java
package com.alibaba.assistant.agent.data.model.nl2sql;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class SqlGenerationDTO {
    private String query;
    private String evidence;
    private SchemaDTO schemaDTO;
    private String dialect;
    private String executionDescription;
}
```

**Step 5: Create OptionItem**

```java
package com.alibaba.assistant.agent.data.model.nl2sql;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptionItem {
    private String label;
    private String value;
}
```

**Step 6: Verify compilation**

Run: `mvn compile -pl assistant-agent-data/assistant-agent-data-api`
Expected: BUILD SUCCESS

**Step 7: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/
git commit -m "feat(nl2sql): add data models (DTO classes)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Create Exception Classes

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/Nl2SqlException.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/SchemaNotFoundException.java`
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/SqlGenerationException.java`

**Step 1: Create Nl2SqlException**

```java
package com.alibaba.assistant.agent.data.model.nl2sql;

public class Nl2SqlException extends RuntimeException {
    public Nl2SqlException(String message) {
        super(message);
    }

    public Nl2SqlException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Step 2: Create SchemaNotFoundException**

```java
package com.alibaba.assistant.agent.data.model.nl2sql;

public class SchemaNotFoundException extends Nl2SqlException {
    public SchemaNotFoundException(String systemId) {
        super("Schema not found for systemId: " + systemId);
    }
}
```

**Step 3: Create SqlGenerationException**

```java
package com.alibaba.assistant.agent.data.model.nl2sql;

public class SqlGenerationException extends Nl2SqlException {
    public SqlGenerationException(String message, Throwable cause) {
        super("SQL generation failed: " + message, cause);
    }
}
```

**Step 4: Verify compilation**

Run: `mvn compile -pl assistant-agent-data/assistant-agent-data-api`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/Nl2SqlException.java
git add assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/SchemaNotFoundException.java
git add assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/SqlGenerationException.java
git commit -m "feat(nl2sql): add exception classes

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Create Nl2SqlService Interface

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/spi/Nl2SqlService.java`

**Step 1: Write interface**

```java
package com.alibaba.assistant.agent.data.spi;

import com.alibaba.assistant.agent.data.model.nl2sql.Nl2SqlException;
import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;

import java.util.List;

/**
 * Service Provider Interface for NL2SQL conversion.
 * Converts natural language queries to SQL statements.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface Nl2SqlService {

    /**
     * Convert natural language to SQL query.
     *
     * @param systemId Datasource system ID
     * @param query Natural language query
     * @param evidence Additional context/evidence (optional)
     * @return Generated SQL statement
     * @throws Nl2SqlException if generation fails
     */
    String generateSql(String systemId, String query, String evidence) throws Nl2SqlException;

    /**
     * Generate SQL and execute (for parameter collection).
     * Executes the generated SQL and maps results to option items.
     *
     * @param systemId Datasource system ID
     * @param query Natural language query
     * @param labelColumn Column name for display label
     * @param valueColumn Column name for actual value
     * @return List of option items (label-value pairs)
     * @throws Nl2SqlException if generation or execution fails
     */
    List<OptionItem> generateAndExecute(String systemId, String query,
                                        String labelColumn, String valueColumn) throws Nl2SqlException;
}
```

**Step 2: Verify compilation**

Run: `mvn compile -pl assistant-agent-data/assistant-agent-data-api`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/spi/Nl2SqlService.java
git commit -m "feat(nl2sql): add Nl2SqlService SPI interface

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Create Configuration Properties

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/config/Nl2SqlProperties.java`
- Test: `assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/config/Nl2SqlPropertiesTest.java`

**Step 1: Write the failing test**

```java
package com.alibaba.assistant.agent.data.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Nl2SqlPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        Nl2SqlProperties properties = new Nl2SqlProperties();

        assertTrue(properties.isEnabled());
        assertEquals(10, properties.getSchemaFilterThreshold());
        assertNotNull(properties.getLlm());
        assertEquals("qwen-max", properties.getLlm().getModel());
        assertEquals(0.1, properties.getLlm().getTemperature());
        assertEquals(2000, properties.getLlm().getMaxTokens());
        assertNotNull(properties.getCache());
        assertTrue(properties.getCache().isEnabled());
        assertEquals(30, properties.getCache().getTtlMinutes());
    }

    @Test
    void shouldSetProperties() {
        Nl2SqlProperties properties = new Nl2SqlProperties();

        properties.setEnabled(false);
        properties.setSchemaFilterThreshold(20);
        properties.getLlm().setModel("qwen-plus");
        properties.getLlm().setTemperature(0.5);
        properties.getCache().setEnabled(false);

        assertFalse(properties.isEnabled());
        assertEquals(20, properties.getSchemaFilterThreshold());
        assertEquals("qwen-plus", properties.getLlm().getModel());
        assertEquals(0.5, properties.getLlm().getTemperature());
        assertFalse(properties.getCache().isEnabled());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=Nl2SqlPropertiesTest -pl assistant-agent-data/assistant-agent-data-core`
Expected: FAIL with "cannot find symbol: class Nl2SqlProperties"

**Step 3: Write minimal implementation**

```java
package com.alibaba.assistant.agent.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for NL2SQL.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "spring.assistant-agent.data.nl2sql")
public class Nl2SqlProperties {

    /** Enable NL2SQL feature */
    private boolean enabled = true;

    /** Schema filter threshold (number of tables) */
    private int schemaFilterThreshold = 10;

    /** LLM configuration */
    private LlmProperties llm = new LlmProperties();

    /** Cache configuration */
    private CacheProperties cache = new CacheProperties();

    @Data
    public static class LlmProperties {
        /** LLM model name */
        private String model = "qwen-max";

        /** Temperature (0.0-1.0, lower = more deterministic) */
        private double temperature = 0.1;

        /** Max tokens for response */
        private int maxTokens = 2000;
    }

    @Data
    public static class CacheProperties {
        /** Enable result caching */
        private boolean enabled = true;

        /** Cache TTL in minutes */
        private int ttlMinutes = 30;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=Nl2SqlPropertiesTest -pl assistant-agent-data/assistant-agent-data-core`
Expected: PASS (2/2 tests)

**Step 5: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/config/Nl2SqlProperties.java
git add assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/config/Nl2SqlPropertiesTest.java
git commit -m "feat(nl2sql): add configuration properties

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Create Prompt Templates

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/resources/prompts/nl2sql-generation.st`
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/resources/prompts/nl2sql-filter.st`

**Step 1: Create SQL generation template**

Create file at `assistant-agent-data/assistant-agent-data-core/src/main/resources/prompts/nl2sql-generation.st`:

```
You are an expert SQL query generator for {dialect} databases.

Given the following database schema and user question, generate a valid SQL query.

DATABASE SCHEMA:
{schema_info}

USER QUESTION:
{question}

ADDITIONAL CONTEXT:
{evidence}

EXECUTION DESCRIPTION:
{execution_description}

REQUIREMENTS:
1. Generate only SELECT statements (read-only queries)
2. Use proper table and column names exactly as shown in the schema
3. Consider primary keys and foreign keys for JOIN operations
4. Use appropriate WHERE clauses based on the question
5. Add LIMIT clause if the question implies limiting results
6. Return only the SQL query wrapped in ```sql code block
7. Do not include any explanation or commentary

SQL QUERY:
```

**Step 2: Create schema filter template**

Create file at `assistant-agent-data/assistant-agent-data-core/src/main/resources/prompts/nl2sql-filter.st`:

```
You are a database schema expert.

Given the user query below, identify which tables from the available tables are directly relevant for answering the question.

USER QUERY:
{query}

AVAILABLE TABLES:
{tables}

REQUIREMENTS:
1. Return only table names that are directly relevant to answering the query
2. Do not include tables that are not needed
3. Format as JSON array: ["table1", "table2", "table3"]
4. Return only the JSON array without any explanation

RELEVANT TABLES:
```

**Step 3: Verify files exist**

Run: `ls assistant-agent-data/assistant-agent-data-core/src/main/resources/prompts/nl2sql-*.st`
Expected: Both files listed

**Step 4: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/resources/prompts/
git commit -m "feat(nl2sql): add prompt templates for SQL generation and schema filtering

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Create Nl2SqlPromptBuilder

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/nl2sql/Nl2SqlPromptBuilder.java`
- Test: `assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/nl2sql/Nl2SqlPromptBuilderTest.java`

**Step 1: Write the failing test**

```java
package com.alibaba.assistant.agent.data.nl2sql;

import com.alibaba.assistant.agent.data.model.nl2sql.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Nl2SqlPromptBuilderTest {

    @Test
    void shouldBuildSqlGenerationPrompt() {
        TableDTO table = new TableDTO();
        table.setName("users");
        table.setDescription("User accounts");

        ColumnDTO column = new ColumnDTO();
        column.setName("id");
        column.setType("INT");
        column.setDescription("User ID");
        table.setColumn(Arrays.asList(column));
        table.setPrimaryKeys(Arrays.asList("id"));

        SchemaDTO schema = new SchemaDTO();
        schema.setName("testdb");
        schema.setTable(Arrays.asList(table));

        SqlGenerationDTO dto = SqlGenerationDTO.builder()
                .query("查询所有用户")
                .evidence("用户表名为users")
                .schemaDTO(schema)
                .dialect("mysql")
                .executionDescription("Generate SQL for user query")
                .build();

        String prompt = Nl2SqlPromptBuilder.buildSqlGenerationPrompt(dto);

        assertNotNull(prompt);
        assertTrue(prompt.contains("查询所有用户"));
        assertTrue(prompt.contains("用户表名为users"));
        assertTrue(prompt.contains("mysql"));
        assertTrue(prompt.contains("users"));
    }

    @Test
    void shouldBuildSchemaInfo() {
        TableDTO table = new TableDTO();
        table.setName("orders");
        table.setDescription("Order records");

        ColumnDTO col1 = new ColumnDTO();
        col1.setName("order_id");
        col1.setType("BIGINT");
        col1.setDescription("Order ID");

        ColumnDTO col2 = new ColumnDTO();
        col2.setName("user_id");
        col2.setType("INT");
        col2.setDescription("User ID");
        col2.setData(Arrays.asList("1", "2", "3"));

        table.setColumn(Arrays.asList(col1, col2));
        table.setPrimaryKeys(Arrays.asList("order_id"));

        SchemaDTO schema = new SchemaDTO();
        schema.setName("ecommerce");
        schema.setTable(Arrays.asList(table));
        schema.setForeignKeys(Arrays.asList("orders.user_id -> users.id"));

        String schemaInfo = Nl2SqlPromptBuilder.buildSchemaInfo(schema);

        assertTrue(schemaInfo.contains("【DB_ID】ecommerce"));
        assertTrue(schemaInfo.contains("# Table: orders"));
        assertTrue(schemaInfo.contains("order_id:BIGINT"));
        assertTrue(schemaInfo.contains("Primary Key"));
        assertTrue(schemaInfo.contains("Examples: [1,2,3]"));
        assertTrue(schemaInfo.contains("【Foreign keys】"));
    }

    @Test
    void shouldBuildSchemaFilterPrompt() {
        List<String> tables = Arrays.asList("users", "orders", "products", "categories");
        String query = "查询用户的订单信息";

        String prompt = Nl2SqlPromptBuilder.buildSchemaFilterPrompt(query, tables);

        assertNotNull(prompt);
        assertTrue(prompt.contains("查询用户的订单信息"));
        assertTrue(prompt.contains("users"));
        assertTrue(prompt.contains("orders"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=Nl2SqlPromptBuilderTest -pl assistant-agent-data/assistant-agent-data-core`
Expected: FAIL with "cannot find symbol: class Nl2SqlPromptBuilder"

**Step 3: Write minimal implementation**

```java
package com.alibaba.assistant.agent.data.nl2sql;

import com.alibaba.assistant.agent.data.model.nl2sql.ColumnDTO;
import com.alibaba.assistant.agent.data.model.nl2sql.SchemaDTO;
import com.alibaba.assistant.agent.data.model.nl2sql.SqlGenerationDTO;
import com.alibaba.assistant.agent.data.model.nl2sql.TableDTO;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for building NL2SQL prompts.
 * Reference: DataAgent's PromptHelper.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class Nl2SqlPromptBuilder {

    private static final String SQL_GENERATION_TEMPLATE_PATH = "prompts/nl2sql-generation.st";
    private static final String SCHEMA_FILTER_TEMPLATE_PATH = "prompts/nl2sql-filter.st";

    /**
     * Build SQL generation prompt.
     */
    public static String buildSqlGenerationPrompt(SqlGenerationDTO dto) {
        Map<String, Object> params = new HashMap<>();
        params.put("dialect", dto.getDialect());
        params.put("question", dto.getQuery());
        params.put("schema_info", buildSchemaInfo(dto.getSchemaDTO()));
        params.put("evidence", dto.getEvidence() != null ? dto.getEvidence() : "无");
        params.put("execution_description", dto.getExecutionDescription());

        String template = loadTemplate(SQL_GENERATION_TEMPLATE_PATH);
        return new PromptTemplate(template).render(params);
    }

    /**
     * Build schema information string.
     * Reference: DataAgent's buildMixMacSqlDbPrompt().
     */
    public static String buildSchemaInfo(SchemaDTO schemaDTO) {
        StringBuilder sb = new StringBuilder();
        sb.append("【DB_ID】").append(schemaDTO.getName() != null ? schemaDTO.getName() : "").append("\n");

        for (TableDTO table : schemaDTO.getTable()) {
            sb.append("# Table: ").append(table.getName());
            if (table.getDescription() != null && !table.getName().equals(table.getDescription())) {
                sb.append(", ").append(table.getDescription());
            }
            sb.append("\n[\n");

            // Build column list
            List<String> columnLines = new ArrayList<>();
            for (ColumnDTO column : table.getColumn()) {
                StringBuilder line = new StringBuilder();
                line.append("(").append(column.getName());

                // Add type
                if (column.getType() != null && !column.getType().isEmpty()) {
                    line.append(":").append(column.getType().toUpperCase());
                }

                // Add description
                if (column.getDescription() != null && !column.getName().equals(column.getDescription())) {
                    line.append(", ").append(column.getDescription());
                }

                // Add Primary Key marker
                if (table.getPrimaryKeys() != null && table.getPrimaryKeys().contains(column.getName())) {
                    line.append(", Primary Key");
                }

                // Add sample values
                if (column.getData() != null && !column.getData().isEmpty() && !"id".equals(column.getName())) {
                    line.append(", Examples: [");
                    List<String> samples = column.getData().stream()
                            .filter(d -> d != null && !d.isEmpty())
                            .limit(3)
                            .collect(Collectors.toList());
                    line.append(String.join(",", samples));
                    line.append("]");
                }

                // Add value mapping
                if (column.getMapping() != null && !column.getMapping().isEmpty()) {
                    line.append(", ValueMapping: {");
                    List<String> mappings = column.getMapping().entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.toList());
                    line.append(String.join(", ", mappings));
                    line.append("}");
                }

                line.append(")");
                columnLines.add(line.toString());
            }

            sb.append(String.join(",\n", columnLines));
            sb.append("\n]\n");
        }

        // Add foreign keys
        if (schemaDTO.getForeignKeys() != null && !schemaDTO.getForeignKeys().isEmpty()) {
            sb.append("【Foreign keys】\n");
            sb.append(String.join("\n", schemaDTO.getForeignKeys()));
        }

        return sb.toString();
    }

    /**
     * Build schema filter prompt.
     */
    public static String buildSchemaFilterPrompt(String query, List<String> tableNames) {
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("tables", String.join(", ", tableNames));

        String template = loadTemplate(SCHEMA_FILTER_TEMPLATE_PATH);
        return new PromptTemplate(template).render(params);
    }

    /**
     * Load template from classpath.
     */
    private static String loadTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + path, e);
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=Nl2SqlPromptBuilderTest -pl assistant-agent-data/assistant-agent-data-core`
Expected: PASS (3/3 tests)

**Step 5: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/nl2sql/Nl2SqlPromptBuilder.java
git add assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/nl2sql/Nl2SqlPromptBuilderTest.java
git commit -m "feat(nl2sql): add prompt builder utility class

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 7: Create DefaultNl2SqlService (Part 1: Basic Structure)

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlService.java`
- Test: `assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlServiceTest.java`

**Step 1: Write the failing test for input validation**

```java
package com.alibaba.assistant.agent.data.nl2sql;

import com.alibaba.assistant.agent.data.model.nl2sql.Nl2SqlException;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import com.alibaba.assistant.agent.data.spi.SchemaProvider;
import com.alibaba.assistant.agent.data.spi.SqlExecutionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.*;

class DefaultNl2SqlServiceTest {

    @Mock
    private SchemaProvider schemaProvider;

    @Mock
    private ChatModel chatModel;

    @Mock
    private SqlExecutionProvider sqlExecutionProvider;

    @Mock
    private DatasourceProvider datasourceProvider;

    private DefaultNl2SqlService nl2SqlService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        nl2SqlService = new DefaultNl2SqlService(
                schemaProvider,
                chatModel,
                sqlExecutionProvider,
                datasourceProvider
        );
    }

    @Test
    void shouldThrowExceptionForNullSystemId() {
        assertThrows(IllegalArgumentException.class, () ->
                nl2SqlService.generateSql(null, "query", null)
        );
    }

    @Test
    void shouldThrowExceptionForEmptySystemId() {
        assertThrows(IllegalArgumentException.class, () ->
                nl2SqlService.generateSql("", "query", null)
        );
    }

    @Test
    void shouldThrowExceptionForNullQuery() {
        assertThrows(IllegalArgumentException.class, () ->
                nl2SqlService.generateSql("test-system", null, null)
        );
    }

    @Test
    void shouldThrowExceptionForEmptyQuery() {
        assertThrows(IllegalArgumentException.class, () ->
                nl2SqlService.generateSql("test-system", "", null)
        );
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DefaultNl2SqlServiceTest -pl assistant-agent-data/assistant-agent-data-core`
Expected: FAIL with "cannot find symbol: class DefaultNl2SqlService"

**Step 3: Write minimal implementation (validation only)**

```java
package com.alibaba.assistant.agent.data.nl2sql;

import com.alibaba.assistant.agent.data.model.nl2sql.Nl2SqlException;
import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import com.alibaba.assistant.agent.data.spi.Nl2SqlService;
import com.alibaba.assistant.agent.data.spi.SchemaProvider;
import com.alibaba.assistant.agent.data.spi.SqlExecutionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default implementation of Nl2SqlService.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Service
public class DefaultNl2SqlService implements Nl2SqlService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNl2SqlService.class);

    private final SchemaProvider schemaProvider;
    private final ChatModel chatModel;
    private final SqlExecutionProvider sqlExecutionProvider;
    private final DatasourceProvider datasourceProvider;

    public DefaultNl2SqlService(SchemaProvider schemaProvider,
                                ChatModel chatModel,
                                SqlExecutionProvider sqlExecutionProvider,
                                DatasourceProvider datasourceProvider) {
        this.schemaProvider = schemaProvider;
        this.chatModel = chatModel;
        this.sqlExecutionProvider = sqlExecutionProvider;
        this.datasourceProvider = datasourceProvider;
    }

    @Override
    public String generateSql(String systemId, String query, String evidence) throws Nl2SqlException {
        log.info("DefaultNl2SqlService#generateSql - systemId={}, query={}", systemId, query);

        // Validate input
        validateInput(systemId, query);

        // TODO: Implement SQL generation
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public List<OptionItem> generateAndExecute(String systemId, String query,
                                               String labelColumn, String valueColumn) throws Nl2SqlException {
        // TODO: Implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void validateInput(String systemId, String query) {
        if (systemId == null || systemId.trim().isEmpty()) {
            throw new IllegalArgumentException("systemId cannot be null or empty");
        }
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query cannot be null or empty");
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DefaultNl2SqlServiceTest -pl assistant-agent-data/assistant-agent-data-core`
Expected: PASS (4/4 tests)

**Step 5: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlService.java
git add assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlServiceTest.java
git commit -m "feat(nl2sql): add DefaultNl2SqlService with input validation

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 8: Implement Schema Retrieval in DefaultNl2SqlService

**Files:**
- Modify: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlService.java`
- Modify: `assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlServiceTest.java`

**Step 1: Add test for schema retrieval**

Add to `DefaultNl2SqlServiceTest.java`:

```java
@Test
void shouldThrowExceptionWhenSchemaNotFound() {
    when(schemaProvider.getTableList(eq("invalid-system"), isNull(), isNull()))
            .thenReturn(Collections.emptyList());

    Nl2SqlException exception = assertThrows(Nl2SqlException.class, () ->
            nl2SqlService.generateSql("invalid-system", "query", null)
    );

    assertTrue(exception.getMessage().contains("Schema not found"));
}
```

Add mock dependencies:
```java
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Collections;
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DefaultNl2SqlServiceTest#shouldThrowExceptionWhenSchemaNotFound -pl assistant-agent-data/assistant-agent-data-core`
Expected: FAIL (UnsupportedOperationException)

**Step 3: Implement schema retrieval**

Add to `DefaultNl2SqlService.java`:

```java
// Add imports
import com.alibaba.assistant.agent.data.model.*;
import com.alibaba.assistant.agent.data.model.nl2sql.*;
import java.util.stream.Collectors;

// Add constant
private static final int SCHEMA_FILTER_THRESHOLD = 10;

// Update generateSql method
@Override
public String generateSql(String systemId, String query, String evidence) throws Nl2SqlException {
    log.info("DefaultNl2SqlService#generateSql - systemId={}, query={}", systemId, query);

    try {
        // 1. Validate input
        validateInput(systemId, query);

        // 2. Get schema
        SchemaDTO schema = getSchema(systemId);

        // TODO: Rest of implementation
        throw new UnsupportedOperationException("Not fully implemented yet");

    } catch (Nl2SqlException e) {
        log.error("DefaultNl2SqlService#generateSql - NL2SQL error: {}", e.getMessage());
        throw e;
    } catch (Exception e) {
        log.error("DefaultNl2SqlService#generateSql - Unexpected error", e);
        throw new SqlGenerationException(e.getMessage(), e);
    }
}

// Add helper methods
private SchemaDTO getSchema(String systemId) {
    try {
        List<TableInfoBO> tables = schemaProvider.getTableList(systemId, null, null);

        if (tables == null || tables.isEmpty()) {
            throw new SchemaNotFoundException(systemId);
        }

        return convertToSchemaDTO(systemId, tables);

    } catch (Exception e) {
        throw new SchemaNotFoundException(systemId);
    }
}

private SchemaDTO convertToSchemaDTO(String systemId, List<TableInfoBO> tables) {
    SchemaDTO schema = new SchemaDTO();
    schema.setName(systemId);
    schema.setTableCount(tables.size());

    List<TableDTO> tableDTOs = tables.stream()
            .map(this::convertToTableDTO)
            .collect(Collectors.toList());
    schema.setTable(tableDTOs);

    return schema;
}

private TableDTO convertToTableDTO(TableInfoBO tableInfo) {
    TableDTO table = new TableDTO();
    table.setName(tableInfo.getTableName());
    table.setDescription(tableInfo.getDescription());

    if (tableInfo.getColumns() != null) {
        List<ColumnDTO> columns = tableInfo.getColumns().stream()
                .map(this::convertToColumnDTO)
                .collect(Collectors.toList());
        table.setColumn(columns);
    }

    if (tableInfo.getPrimaryKeys() != null) {
        table.setPrimaryKeys(tableInfo.getPrimaryKeys().stream()
                .map(ColumnInfoBO::getColumnName)
                .collect(Collectors.toList()));
    }

    return table;
}

private ColumnDTO convertToColumnDTO(ColumnInfoBO columnInfo) {
    ColumnDTO column = new ColumnDTO();
    column.setName(columnInfo.getColumnName());
    column.setDescription(columnInfo.getDescription());
    column.setType(columnInfo.getDataType());
    return column;
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DefaultNl2SqlServiceTest#shouldThrowExceptionWhenSchemaNotFound -pl assistant-agent-data/assistant-agent-data-core`
Expected: PASS

**Step 5: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlService.java
git add assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlServiceTest.java
git commit -m "feat(nl2sql): implement schema retrieval in DefaultNl2SqlService

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 9: Implement SQL Generation (No Filtering Path)

**Files:**
- Modify: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlService.java`
- Modify: `assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlServiceTest.java`

**Step 1: Add test for SQL generation (small schema)**

Add to `DefaultNl2SqlServiceTest.java`:

```java
@Test
void shouldGenerateSqlForSmallSchema() throws Exception {
    // Mock schema with 5 tables (no filtering)
    List<TableInfoBO> tables = createMockTables(5);
    when(schemaProvider.getTableList(eq("test-system"), isNull(), isNull()))
            .thenReturn(tables);

    // Mock datasource for dialect
    DatasourceDefinition datasource = DatasourceDefinition.builder()
            .type("mysql")
            .build();
    when(datasourceProvider.getById(anyLong()))
            .thenReturn(Optional.of(datasource));

    // Mock LLM response
    when(chatModel.call(anyString()))
            .thenReturn("```sql\nSELECT * FROM users WHERE active = 1\n```");

    String sql = nl2SqlService.generateSql("test-system", "查询活跃用户", null);

    assertEquals("SELECT * FROM users WHERE active = 1", sql);
    verify(chatModel, times(1)).call(anyString());
}

private List<TableInfoBO> createMockTables(int count) {
    List<TableInfoBO> tables = new ArrayList<>();
    for (int i = 0; i < count; i++) {
        TableInfoBO table = new TableInfoBO();
        table.setTableName("table" + i);
        table.setDescription("Table " + i);
        table.setColumns(Arrays.asList(createMockColumn("id"), createMockColumn("name")));
        tables.add(table);
    }
    return tables;
}

private ColumnInfoBO createMockColumn(String name) {
    ColumnInfoBO column = new ColumnInfoBO();
    column.setColumnName(name);
    column.setDescription(name + " column");
    column.setDataType("VARCHAR");
    return column;
}
```

Add imports:
```java
import com.alibaba.assistant.agent.data.model.*;
import java.util.*;
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DefaultNl2SqlServiceTest#shouldGenerateSqlForSmallSchema -pl assistant-agent-data/assistant-agent-data-core`
Expected: FAIL (UnsupportedOperationException)

**Step 3: Implement SQL generation**

Update `generateSql` method in `DefaultNl2SqlService.java`:

```java
@Override
public String generateSql(String systemId, String query, String evidence) throws Nl2SqlException {
    log.info("DefaultNl2SqlService#generateSql - systemId={}, query={}", systemId, query);

    try {
        // 1. Validate input
        validateInput(systemId, query);

        // 2. Get dialect
        String dialect = getDatabaseDialect(systemId);

        // 3. Get schema
        SchemaDTO schema = getSchema(systemId);

        // 4. Smart filtering (skip for now, will implement in next task)
        if (schema.getTableCount() >= SCHEMA_FILTER_THRESHOLD) {
            log.debug("DefaultNl2SqlService#generateSql - Schema has {} tables, filtering required",
                    schema.getTableCount());
            // TODO: Implement filtering in next task
        }

        // 5. Build prompt
        SqlGenerationDTO dto = SqlGenerationDTO.builder()
                .query(query)
                .evidence(evidence)
                .schemaDTO(schema)
                .dialect(dialect)
                .executionDescription("Generate SQL based on natural language")
                .build();

        String prompt = Nl2SqlPromptBuilder.buildSqlGenerationPrompt(dto);
        log.debug("DefaultNl2SqlService#generateSql - Prompt built, length={}", prompt.length());

        // 6. Call LLM
        String response = chatModel.call(prompt);
        log.debug("DefaultNl2SqlService#generateSql - LLM response received, length={}", response.length());

        // 7. Extract SQL
        String sql = extractSql(response);
        log.info("DefaultNl2SqlService#generateSql - SQL generated successfully, length={}", sql.length());

        return sql;

    } catch (Nl2SqlException e) {
        log.error("DefaultNl2SqlService#generateSql - NL2SQL error: {}", e.getMessage());
        throw e;
    } catch (Exception e) {
        log.error("DefaultNl2SqlService#generateSql - Unexpected error", e);
        throw new SqlGenerationException(e.getMessage(), e);
    }
}

private String getDatabaseDialect(String systemId) {
    try {
        // Get datasource to determine dialect
        Optional<DatasourceDefinition> datasource = datasourceProvider.getBySystemId(systemId);
        if (datasource.isPresent()) {
            String type = datasource.get().getType();
            return type != null ? type.toLowerCase() : "mysql";
        }
        return "mysql"; // default
    } catch (Exception e) {
        log.warn("DefaultNl2SqlService#getDatabaseDialect - Failed to get dialect, using default: mysql");
        return "mysql";
    }
}

private String extractSql(String response) {
    if (response == null || response.isEmpty()) {
        throw new SqlGenerationException("Empty response from LLM", null);
    }

    // Remove markdown code blocks
    String sql = response.trim();

    // Extract from ```sql ... ``` block
    if (sql.contains("```sql")) {
        int start = sql.indexOf("```sql") + 6;
        int end = sql.indexOf("```", start);
        if (end > start) {
            sql = sql.substring(start, end).trim();
        }
    } else if (sql.startsWith("```")) {
        // Extract from ``` ... ``` block
        int start = sql.indexOf("```") + 3;
        int end = sql.indexOf("```", start);
        if (end > start) {
            sql = sql.substring(start, end).trim();
        }
    }

    return sql;
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DefaultNl2SqlServiceTest#shouldGenerateSqlForSmallSchema -pl assistant-agent-data/assistant-agent-data-core`
Expected: PASS

**Step 5: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlService.java
git add assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlServiceTest.java
git commit -m "feat(nl2sql): implement SQL generation for small schemas

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 10: Implement Schema Filtering for Large Schemas

**Files:**
- Modify: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlService.java`
- Modify: `assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlServiceTest.java`

**Step 1: Add test for schema filtering**

Add to `DefaultNl2SqlServiceTest.java`:

```java
@Test
void shouldFilterSchemaForLargeDatabase() throws Exception {
    // Mock schema with 15 tables (triggers filtering)
    List<TableInfoBO> tables = createMockTables(15);
    when(schemaProvider.getTableList(eq("test-system"), isNull(), isNull()))
            .thenReturn(tables);

    // Mock datasource
    DatasourceDefinition datasource = DatasourceDefinition.builder()
            .type("mysql")
            .build();
    when(datasourceProvider.getById(anyLong()))
            .thenReturn(Optional.of(datasource));

    // Mock LLM responses
    // First call: schema filter
    when(chatModel.call(contains("RELEVANT TABLES")))
            .thenReturn("[\"table1\", \"table2\"]");

    // Second call: SQL generation
    when(chatModel.call(contains("SELECT")))
            .thenReturn("```sql\nSELECT * FROM table1\n```");

    String sql = nl2SqlService.generateSql("test-system", "查询table1数据", null);

    assertNotNull(sql);
    assertEquals("SELECT * FROM table1", sql);
    verify(chatModel, times(2)).call(anyString()); // Two LLM calls
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DefaultNl2SqlServiceTest#shouldFilterSchemaForLargeDatabase -pl assistant-agent-data/assistant-agent-data-core`
Expected: FAIL (only 1 LLM call, not 2)

**Step 3: Implement schema filtering**

Update `generateSql` method in `DefaultNl2SqlService.java`:

Replace the TODO section with:

```java
// 4. Smart filtering
if (schema.getTableCount() >= SCHEMA_FILTER_THRESHOLD) {
    log.debug("DefaultNl2SqlService#generateSql - Schema has {} tables, applying filter",
            schema.getTableCount());
    schema = filterTables(schema, query);
    log.debug("DefaultNl2SqlService#generateSql - Filtered to {} tables", schema.getTableCount());
}
```

Add the `filterTables` method:

```java
private SchemaDTO filterTables(SchemaDTO schema, String query) {
    try {
        // Get all table names
        List<String> allTableNames = schema.getTable().stream()
                .map(TableDTO::getName)
                .collect(Collectors.toList());

        // Build filter prompt
        String filterPrompt = Nl2SqlPromptBuilder.buildSchemaFilterPrompt(query, allTableNames);
        log.debug("DefaultNl2SqlService#filterTables - Calling LLM for table filtering");

        // Call LLM
        String response = chatModel.call(filterPrompt);
        log.debug("DefaultNl2SqlService#filterTables - Filter response: {}", response);

        // Parse table names
        Set<String> selectedTables = parseTableNames(response);
        log.info("DefaultNl2SqlService#filterTables - Selected {} tables: {}",
                selectedTables.size(), selectedTables);

        // Filter schema
        schema.getTable().removeIf(t -> !selectedTables.contains(t.getName().toLowerCase()));
        schema.setTableCount(schema.getTable().size());

        return schema;

    } catch (Exception e) {
        log.warn("DefaultNl2SqlService#filterTables - Filtering failed, using full schema: {}",
                e.getMessage());
        return schema; // Fallback to full schema
    }
}

private Set<String> parseTableNames(String response) {
    try {
        // Extract JSON array from response
        String json = response.trim();

        // Remove markdown code blocks if present
        if (json.contains("```")) {
            int start = json.indexOf("[");
            int end = json.lastIndexOf("]") + 1;
            if (start >= 0 && end > start) {
                json = json.substring(start, end);
            }
        }

        // Parse JSON array: ["table1", "table2"]
        json = json.trim();
        if (json.startsWith("[") && json.endsWith("]")) {
            json = json.substring(1, json.length() - 1);
            String[] tables = json.split(",");

            return Arrays.stream(tables)
                    .map(s -> s.trim().replaceAll("\"", "").toLowerCase())
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }

        throw new IllegalArgumentException("Invalid JSON array format");

    } catch (Exception e) {
        log.error("DefaultNl2SqlService#parseTableNames - Failed to parse: {}", response, e);
        return Collections.emptySet();
    }
}
```

Add imports:
```java
import java.util.Set;
import java.util.Collections;
import java.util.Arrays;
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DefaultNl2SqlServiceTest#shouldFilterSchemaForLargeDatabase -pl assistant-agent-data/assistant-agent-data-core`
Expected: PASS

**Step 5: Run all tests**

Run: `mvn test -Dtest=DefaultNl2SqlServiceTest -pl assistant-agent-data/assistant-agent-data-core`
Expected: All tests pass

**Step 6: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlService.java
git add assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlServiceTest.java
git commit -m "feat(nl2sql): implement smart schema filtering for large databases

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 11: Implement generateAndExecute Method

**Files:**
- Modify: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlService.java`
- Modify: `assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlServiceTest.java`

**Step 1: Add test for generateAndExecute**

Add to `DefaultNl2SqlServiceTest.java`:

```java
@Test
void shouldGenerateAndExecuteForParameterCollection() throws Exception {
    // Mock schema
    List<TableInfoBO> tables = createMockTables(3);
    when(schemaProvider.getTableList(eq("test-system"), isNull(), isNull()))
            .thenReturn(tables);

    // Mock datasource
    DatasourceDefinition datasource = DatasourceDefinition.builder()
            .type("mysql")
            .build();
    when(datasourceProvider.getById(anyLong()))
            .thenReturn(Optional.of(datasource));

    // Mock LLM response
    when(chatModel.call(anyString()))
            .thenReturn("```sql\nSELECT dept_name, dept_id FROM departments\n```");

    // Mock query execution
    QueryResult queryResult = new QueryResult();
    queryResult.setColumns(Arrays.asList("dept_name", "dept_id"));
    Map<String, Object> row1 = new HashMap<>();
    row1.put("dept_name", "Engineering");
    row1.put("dept_id", "1");
    Map<String, Object> row2 = new HashMap<>();
    row2.put("dept_name", "Sales");
    row2.put("dept_id", "2");
    queryResult.setRows(Arrays.asList(row1, row2));

    when(sqlExecutionProvider.execute(eq("test-system"), anyString(), anyInt()))
            .thenReturn(queryResult);

    List<OptionItem> options = nl2SqlService.generateAndExecute(
            "test-system",
            "获取所有部门",
            "dept_name",
            "dept_id"
    );

    assertEquals(2, options.size());
    assertEquals("Engineering", options.get(0).getLabel());
    assertEquals("1", options.get(0).getValue());
    assertEquals("Sales", options.get(1).getLabel());
    assertEquals("2", options.get(1).getValue());
}
```

Add imports:
```java
import com.alibaba.assistant.agent.data.model.QueryResult;
import java.util.Map;
import java.util.HashMap;
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DefaultNl2SqlServiceTest#shouldGenerateAndExecuteForParameterCollection -pl assistant-agent-data/assistant-agent-data-core`
Expected: FAIL (UnsupportedOperationException)

**Step 3: Implement generateAndExecute**

Replace the `generateAndExecute` method in `DefaultNl2SqlService.java`:

```java
@Override
public List<OptionItem> generateAndExecute(String systemId, String query,
                                           String labelColumn, String valueColumn) throws Nl2SqlException {
    log.info("DefaultNl2SqlService#generateAndExecute - systemId={}, query={}, labelColumn={}, valueColumn={}",
            systemId, query, labelColumn, valueColumn);

    try {
        // Build evidence to guide SQL generation
        String evidence = String.format("Return columns: %s (for display label), %s (for value)",
                labelColumn, valueColumn);

        // Generate SQL
        String sql = generateSql(systemId, query, evidence);
        log.debug("DefaultNl2SqlService#generateAndExecute - Generated SQL: {}", sql);

        // Execute SQL
        QueryResult result = sqlExecutionProvider.execute(systemId, sql, 1000);
        log.info("DefaultNl2SqlService#generateAndExecute - Query executed, rows={}", result.getRows().size());

        // Map to OptionItem
        return result.getRows().stream()
                .map(row -> {
                    Object label = row.get(labelColumn);
                    Object value = row.get(valueColumn);
                    return new OptionItem(
                            label != null ? label.toString() : "",
                            value != null ? value.toString() : ""
                    );
                })
                .collect(Collectors.toList());

    } catch (Nl2SqlException e) {
        log.error("DefaultNl2SqlService#generateAndExecute - NL2SQL error: {}", e.getMessage());
        throw e;
    } catch (Exception e) {
        log.error("DefaultNl2SqlService#generateAndExecute - Execution error", e);
        throw new SqlGenerationException(e.getMessage(), e);
    }
}
```

Add import:
```java
import com.alibaba.assistant.agent.data.model.QueryResult;
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DefaultNl2SqlServiceTest#shouldGenerateAndExecuteForParameterCollection -pl assistant-agent-data/assistant-agent-data-core`
Expected: PASS

**Step 5: Run all tests**

Run: `mvn test -Dtest=DefaultNl2SqlServiceTest -pl assistant-agent-data/assistant-agent-data-core`
Expected: All tests pass (7 tests)

**Step 6: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlService.java
git add assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/nl2sql/DefaultNl2SqlServiceTest.java
git commit -m "feat(nl2sql): implement generateAndExecute for parameter collection

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 12: Create Nl2SqlCodeactTool

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/tool/Nl2SqlCodeactTool.java`
- Test: `assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/tool/Nl2SqlCodeactToolTest.java`

**Step 1: Write the failing test**

```java
package com.alibaba.assistant.agent.data.tool;

import com.alibaba.assistant.agent.common.tools.CodeactToolDefinition;
import com.alibaba.assistant.agent.data.spi.Nl2SqlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Nl2SqlCodeactToolTest {

    @Mock
    private Nl2SqlService nl2SqlService;

    private Nl2SqlCodeactTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new Nl2SqlCodeactTool(nl2SqlService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldGenerateSql() throws Exception {
        when(nl2SqlService.generateSql(eq("test-system"), eq("查询用户"), isNull()))
                .thenReturn("SELECT * FROM users");

        Map<String, Object> params = new HashMap<>();
        params.put("systemId", "test-system");
        params.put("query", "查询用户");

        String result = tool.call(objectMapper.writeValueAsString(params));

        assertTrue(result.contains("SELECT * FROM users"));
        assertTrue(result.contains("```sql"));
    }

    @Test
    void shouldReturnErrorForMissingSystemId() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("query", "查询用户");

        String result = tool.call(objectMapper.writeValueAsString(params));

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("systemId"));
    }

    @Test
    void shouldReturnErrorForMissingQuery() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("systemId", "test-system");

        String result = tool.call(objectMapper.writeValueAsString(params));

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("query"));
    }

    @Test
    void shouldHaveValidCodeactDefinition() {
        CodeactToolDefinition definition = tool.getCodeactDefinition();

        assertNotNull(definition);
        assertEquals("nl2sql", definition.name());
        assertTrue(definition.description().contains("natural language"));
        assertNotNull(definition.parameterTree());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=Nl2SqlCodeactToolTest -pl assistant-agent-data/assistant-agent-data-core`
Expected: FAIL with "cannot find symbol: class Nl2SqlCodeactTool"

**Step 3: Write minimal implementation**

```java
package com.alibaba.assistant.agent.data.tool;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeExample;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.DefaultCodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.definition.CodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.DefaultCodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.ParameterNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.common.tools.definition.ParameterType;
import com.alibaba.assistant.agent.data.spi.Nl2SqlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * CodeactTool for converting natural language to SQL.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class Nl2SqlCodeactTool implements CodeactTool {

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlCodeactTool.class);

    private static final String TOOL_NAME = "nl2sql";

    private static final String DESCRIPTION =
            "Convert natural language to SQL query. " +
            "Returns the generated SQL statement that can be used with execute_sql tool. " +
            "Useful for translating user questions into database queries.";

    private final Nl2SqlService nl2SqlService;
    private final CodeactToolMetadata codeactMetadata;
    private final ToolDefinition toolDefinition;
    private final CodeactToolDefinition codeactDefinition;
    private final ObjectMapper objectMapper;

    public Nl2SqlCodeactTool(Nl2SqlService nl2SqlService) {
        this.nl2SqlService = nl2SqlService;
        this.objectMapper = new ObjectMapper();
        this.toolDefinition = buildToolDefinition();
        this.codeactDefinition = buildCodeactDefinition();
        this.codeactMetadata = buildCodeactMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        log.debug("Nl2SqlCodeactTool#call - toolInput={}", toolInput);

        try {
            Map<String, Object> params = objectMapper.readValue(toolInput, Map.class);

            String systemId = (String) params.get("systemId");
            String query = (String) params.get("query");
            String evidence = (String) params.get("evidence");

            if (systemId == null || systemId.trim().isEmpty()) {
                return "Error: systemId parameter is required";
            }

            if (query == null || query.trim().isEmpty()) {
                return "Error: query parameter is required";
            }

            String sql = nl2SqlService.generateSql(systemId, query, evidence);

            log.info("Nl2SqlCodeactTool#call - SQL generated successfully, systemId={}", systemId);

            return "Generated SQL:\n```sql\n" + sql + "\n```\n\n" +
                   "You can now execute this SQL using execute_sql tool.";

        } catch (Exception e) {
            log.error("Nl2SqlCodeactTool#call - Error generating SQL", e);
            return "Error generating SQL: " + e.getMessage();
        }
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public CodeactToolDefinition getCodeactDefinition() {
        return codeactDefinition;
    }

    @Override
    public CodeactToolMetadata getCodeactMetadata() {
        return codeactMetadata;
    }

    private ToolDefinition buildToolDefinition() {
        String inputSchema = buildInputSchema();
        return ToolDefinition.builder()
                .name(TOOL_NAME)
                .description(DESCRIPTION)
                .inputSchema(inputSchema)
                .build();
    }

    private String buildInputSchema() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "systemId": {
                            "type": "string",
                            "description": "System ID identifying the datasource"
                        },
                        "query": {
                            "type": "string",
                            "description": "Natural language query description"
                        },
                        "evidence": {
                            "type": "string",
                            "description": "Additional context or evidence (optional)"
                        }
                    },
                    "required": ["systemId", "query"]
                }
                """;
    }

    private CodeactToolDefinition buildCodeactDefinition() {
        String inputSchema = toolDefinition.inputSchema();

        ParameterTree parameterTree = ParameterTree.builder()
                .rawInputSchema(inputSchema)
                .addParameter(ParameterNode.builder()
                        .name("systemId")
                        .type(ParameterType.STRING)
                        .description("System ID identifying the datasource")
                        .required(true)
                        .build())
                .addParameter(ParameterNode.builder()
                        .name("query")
                        .type(ParameterType.STRING)
                        .description("Natural language query description")
                        .required(true)
                        .build())
                .addParameter(ParameterNode.builder()
                        .name("evidence")
                        .type(ParameterType.STRING)
                        .description("Additional context or evidence (optional)")
                        .required(false)
                        .build())
                .addRequiredName("systemId")
                .addRequiredName("query")
                .build();

        return DefaultCodeactToolDefinition.builder()
                .name(TOOL_NAME)
                .description(DESCRIPTION)
                .inputSchema(inputSchema)
                .parameterTree(parameterTree)
                .returnDescription("Generated SQL statement wrapped in markdown code block")
                .returnTypeHint("str")
                .build();
    }

    private CodeactToolMetadata buildCodeactMetadata() {
        CodeExample pythonExample = new CodeExample(
                "Convert natural language to SQL",
                """
                # Convert natural language query to SQL
                result = nl2sql(
                    systemId="my-database",
                    query="查询最近30天的活跃用户数量",
                    evidence="活跃用户定义为有登录记录的用户"
                )
                print(result)

                # Then execute the generated SQL
                # sql_result = execute_sql(systemId="my-database", sql=generated_sql)
                """,
                "Returns generated SQL statement in markdown code block"
        );

        return DefaultCodeactToolMetadata.builder()
                .supportedLanguages(Collections.singletonList(Language.PYTHON))
                .addFewShot(pythonExample)
                .build();
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=Nl2SqlCodeactToolTest -pl assistant-agent-data/assistant-agent-data-core`
Expected: PASS (4/4 tests)

**Step 5: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/tool/Nl2SqlCodeactTool.java
git add assistant-agent-data/assistant-agent-data-core/src/test/java/com/alibaba/assistant/agent/data/tool/Nl2SqlCodeactToolTest.java
git commit -m "feat(nl2sql): add Nl2SqlCodeactTool for Agent integration

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 13: Create Auto-Configuration

**Files:**
- Create: `assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/config/Nl2SqlAutoConfiguration.java`

**Step 1: Create auto-configuration class**

```java
package com.alibaba.assistant.agent.data.config;

import com.alibaba.assistant.agent.data.nl2sql.DefaultNl2SqlService;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import com.alibaba.assistant.agent.data.spi.Nl2SqlService;
import com.alibaba.assistant.agent.data.spi.SchemaProvider;
import com.alibaba.assistant.agent.data.spi.SqlExecutionProvider;
import com.alibaba.assistant.agent.data.tool.Nl2SqlCodeactTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for NL2SQL.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(ChatModel.class)
@ConditionalOnProperty(
        prefix = "spring.assistant-agent.data.nl2sql",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@EnableConfigurationProperties(Nl2SqlProperties.class)
public class Nl2SqlAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public Nl2SqlService nl2SqlService(SchemaProvider schemaProvider,
                                       ChatModel chatModel,
                                       SqlExecutionProvider sqlExecutionProvider,
                                       DatasourceProvider datasourceProvider,
                                       Nl2SqlProperties properties) {
        log.info("Nl2SqlAutoConfiguration - Initializing NL2SQL service with schema filter threshold: {}",
                properties.getSchemaFilterThreshold());
        return new DefaultNl2SqlService(schemaProvider, chatModel, sqlExecutionProvider, datasourceProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public Nl2SqlCodeactTool nl2SqlCodeactTool(Nl2SqlService nl2SqlService) {
        log.info("Nl2SqlAutoConfiguration - Registering nl2sql tool for Agent");
        return new Nl2SqlCodeactTool(nl2SqlService);
    }
}
```

**Step 2: Verify compilation**

Run: `mvn compile -pl assistant-agent-data/assistant-agent-data-core`
Expected: BUILD SUCCESS

**Step 3: Register auto-configuration**

Create/update file at `assistant-agent-data/assistant-agent-data-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

Add line:
```
com.alibaba.assistant.agent.data.config.Nl2SqlAutoConfiguration
```

**Step 4: Commit**

```bash
git add assistant-agent-data/assistant-agent-data-core/src/main/java/com/alibaba/assistant/agent/data/config/Nl2SqlAutoConfiguration.java
git add assistant-agent-data/assistant-agent-data-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git commit -m "feat(nl2sql): add auto-configuration for Spring Boot

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 14: Add Application Configuration

**Files:**
- Modify: `assistant-agent-start/src/main/resources/application.yml`

**Step 1: Add NL2SQL configuration section**

Add to `application.yml`:

```yaml
# NL2SQL Configuration
spring:
  assistant-agent:
    data:
      nl2sql:
        enabled: false                          # Disabled by default, enable to use NL2SQL
        schema-filter-threshold: 10             # Filter schema when >= 10 tables
        llm:
          model: qwen-max                       # LLM model for SQL generation
          temperature: 0.1                      # Low temperature for deterministic output
          max-tokens: 2000                      # Maximum response tokens
        cache:
          enabled: true                         # Enable result caching
          ttl-minutes: 30                       # Cache TTL in minutes
```

**Step 2: Verify YAML syntax**

Run: `mvn validate -pl assistant-agent-start`
Expected: No YAML parsing errors

**Step 3: Commit**

```bash
git add assistant-agent-start/src/main/resources/application.yml
git commit -m "config(nl2sql): add NL2SQL configuration to application.yml

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 15: Run Full Test Suite

**Step 1: Run all tests in data module**

Run: `mvn clean test -pl assistant-agent-data/assistant-agent-data-core`
Expected: All tests pass

**Step 2: Verify test count**

Expected test count should include:
- Nl2SqlPropertiesTest: 2 tests
- Nl2SqlPromptBuilderTest: 3 tests
- DefaultNl2SqlServiceTest: 7 tests
- Nl2SqlCodeactToolTest: 4 tests
- Existing tests: 78 tests (from baseline)

Total: ~94 tests

**Step 3: Check for any compilation issues**

Run: `mvn clean install -DskipTests`
Expected: BUILD SUCCESS

---

## Task 16: Create Documentation

**Files:**
- Create: `assistant-agent-data/README-NL2SQL.md`

**Step 1: Create user documentation**

Create file at `assistant-agent-data/README-NL2SQL.md`:

```markdown
# NL2SQL - Natural Language to SQL

## Overview

NL2SQL provides lightweight natural language to SQL conversion for AssistantAgent. It supports:

- **Agent Tool Integration** - `nl2sql` tool for Agent to convert natural language queries to SQL
- **Parameter Collection** - Automatic option generation via `Nl2SqlSourceConfig`
- **Smart Schema Filtering** - Threshold-based filtering for large databases (>=10 tables)
- **Multi-Database Support** - MySQL, PostgreSQL, and other databases

## Configuration

### Enable NL2SQL

Add to `application.yml`:

```yaml
spring:
  assistant-agent:
    data:
      nl2sql:
        enabled: true                           # Enable NL2SQL feature
        schema-filter-threshold: 10             # Filter when >= 10 tables
        llm:
          model: qwen-max                       # LLM model
          temperature: 0.1                      # Low temperature for deterministic output
          max-tokens: 2000
        cache:
          enabled: true
          ttl-minutes: 30
```

## Usage

### 1. Agent Tool Usage

The `nl2sql` tool is automatically available to the Agent when NL2SQL is enabled.

**Example Agent Code:**
```python
# Generate SQL from natural language
result = nl2sql(
    systemId="my-database",
    query="查询最近30天的活跃用户数量",
    evidence="活跃用户定义为有登录记录的用户"
)
print(result)

# Execute the generated SQL
sql_result = execute_sql(
    systemId="my-database",
    sql=generated_sql,
    maxRows=100
)
```

### 2. Parameter Collection Integration

Use `Nl2SqlSourceConfig` in parameter definitions:

```java
ParameterDefinition param = ParameterDefinition.builder()
    .name("departmentId")
    .type(ParameterType.STRING)
    .source(new Nl2SqlSourceConfig()
        .setDescription("Get all active departments")
        .setLabelColumn("department_name")
        .setValueColumn("department_id"))
    .build();
```

The Planning module will automatically:
1. Convert the description to SQL
2. Execute the query
3. Map results to options (label-value pairs)
4. Present options to the user

### 3. Programmatic Usage

```java
@Autowired
private Nl2SqlService nl2SqlService;

// Generate SQL
String sql = nl2SqlService.generateSql(
    "my-database",
    "查询所有活跃用户",
    "活跃用户指最近30天有登录的用户"
);

// Generate and execute for parameter collection
List<OptionItem> options = nl2SqlService.generateAndExecute(
    "my-database",
    "获取所有部门",
    "department_name",
    "department_id"
);
```

## Architecture

### Smart Schema Filtering

**Small Database (< 10 tables):**
```
User Query → Get Full Schema → Generate SQL (1 LLM call)
```

**Large Database (>= 10 tables):**
```
User Query → Filter Tables (LLM call 1) → Get Filtered Schema → Generate SQL (LLM call 2)
```

### Components

```
Nl2SqlService (SPI)
  ├─ DefaultNl2SqlService (Implementation)
  │   ├─ SchemaProvider (get database structure)
  │   ├─ ChatModel (call LLM for generation)
  │   └─ SqlExecutionProvider (execute generated SQL)
  │
  ├─ Nl2SqlCodeactTool (Agent integration)
  └─ Nl2SqlPromptBuilder (Prompt construction)
```

## Examples

### Example 1: Simple Query

**Input:**
```
Query: "查询所有用户"
Evidence: null
Database: 5 tables
```

**Process:**
1. Get full schema (5 tables, no filtering)
2. Build prompt with schema
3. Call LLM
4. Extract SQL

**Output:**
```sql
SELECT * FROM users
```

### Example 2: Complex Query with Filtering

**Input:**
```
Query: "查询用户的订单信息"
Evidence: "需要包含用户姓名和订单金额"
Database: 50 tables
```

**Process:**
1. Filter tables (LLM selects: users, orders)
2. Get schema for selected tables
3. Build prompt with filtered schema
4. Call LLM
5. Extract SQL

**Output:**
```sql
SELECT u.name, o.amount
FROM users u
JOIN orders o ON u.id = o.user_id
```

## Performance

| Scenario | Tables | LLM Calls | Avg Latency | Token Usage |
|----------|--------|-----------|-------------|-------------|
| Small DB | 5 | 1 | ~2s | ~3K tokens |
| Medium DB | 10 | 1 | ~2.5s | ~5K tokens |
| Large DB (filtered) | 50 → 3 | 2 | ~4s | ~2K + 4K tokens |

## Troubleshooting

### NL2SQL not working

**Check configuration:**
```yaml
spring.assistant-agent.data.nl2sql.enabled: true
```

**Check logs:**
```
INFO Nl2SqlAutoConfiguration - Initializing NL2SQL service
```

### Poor SQL quality

**Try adjusting:**
1. Add more evidence/context in the query
2. Ensure schema has good table/column descriptions
3. Lower temperature for more deterministic output

### Schema filtering issues

**Adjust threshold:**
```yaml
spring.assistant-agent.data.nl2sql.schema-filter-threshold: 15
```

## See Also

- Design Document: `docs/plans/2026-01-19-nl2sql-design.md`
- Implementation Plan: `docs/plans/2026-01-19-nl2sql-implementation.md`
- DataAgent NL2SQL: Reference implementation
```

**Step 2: Commit**

```bash
git add assistant-agent-data/README-NL2SQL.md
git commit -m "docs(nl2sql): add user documentation

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Summary

**Total Tasks:** 16
**Estimated Time:** 8-12 hours
**Test Coverage:** ~94 tests (16 new tests + 78 existing)

**Execution Options:**

1. **Subagent-Driven (Recommended)** - Execute in this session with task-by-task review
2. **Parallel Session** - Open new session with executing-plans skill

**Files Created:**
- 5 DTO model classes
- 3 exception classes
- 1 SPI interface
- 1 service implementation
- 1 prompt builder
- 1 CodeactTool
- 2 configuration classes
- 2 prompt templates
- 16 test classes
- 1 documentation file

**Total Lines:** ~3000 lines of code + tests + docs
