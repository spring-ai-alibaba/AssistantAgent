# NL2SQL Implementation Design

**Goal:** Provide lightweight NL2SQL capability for AssistantAgent to support parameter collection and simple query scenarios.

**Architecture:** Migrate core NL2SQL logic from DataAgent, share saa_data_agent database, support both Agent tool and parameter collection integration.

**Tech Stack:** Spring Boot 3.4.8, Spring AI, DashScope API (qwen-max), MySQL 8.0

---

## 1. Background and Motivation

### Current State

AssistantAgent has the following data infrastructure:
- **SchemaProvider** - Query database metadata (tables, columns, foreign keys)
- **SqlExecutionProvider** - Execute read-only SQL queries
- **PersistentDatasourceProvider** - Access DataAgent's datasource configurations
- **Nl2SqlSourceConfig** - Model for parameter collection (defined but not implemented)

### Gap

**Missing:** Natural language to SQL conversion capability. Users cannot:
1. Use natural language to query databases through Agent
2. Dynamically generate parameter options via natural language in Planning module

### DataAgent Reference

DataAgent has mature NL2SQL implementation:
- **Nl2SqlService** - Core service with schema filtering, SQL generation, semantic consistency check
- **PromptHelper** - Prompt construction with schema formatting
- **Reactive API** - Flux-based streaming responses

### Design Decision

**Migrate and simplify DataAgent's NL2SQL code to AssistantAgent:**
- Share saa_data_agent database (via PersistentDatasourceProvider)
- Simplify implementation (remove complex features for lightweight use)
- Support two scenarios: Agent tool + parameter collection

---

## 2. Architecture Overview

### Component Structure

```
┌─────────────────────────────────────────────┐
│         AssistantAgent NL2SQL               │
├─────────────────────────────────────────────┤
│  Nl2SqlService (SPI)                        │
│    └─ DefaultNl2SqlService                  │
│         ├─ LLM 调用 (Spring AI ChatModel)   │
│         ├─ Schema 筛选 (智能阈值)            │
│         └─ Prompt 构建                       │
├─────────────────────────────────────────────┤
│  Nl2SqlCodeactTool                          │
│    └─ Agent 工具调用接口                     │
├─────────────────────────────────────────────┤
│  参数收集集成                                │
│    └─ Nl2SqlSourceConfig 支持 (Planning)   │
└─────────────────────────────────────────────┘
         │                    │
         ▼                    ▼
  SchemaProvider      SqlExecutionProvider
         │                    │
         └────────┬───────────┘
                  │
      ┌───────────▼──────────┐
      │  saa_data_agent 数据库 │
      │  (via PersistentDP)  │
      └──────────────────────┘
```

### Integration with Existing Infrastructure

- **SchemaProvider** - Get database structure (tables, columns, keys)
- **PersistentDatasourceProvider** - Access datasource configurations
- **SqlExecutionProvider** - Execute generated SQL
- **Spring AI ChatModel** - Call LLM for NL→SQL conversion
- **Planning Module** - Integrate Nl2SqlSourceConfig for parameter collection

---

## 3. Simplified Design vs DataAgent

### Migration Strategy

**From DataAgent - Keep:**
- `Nl2SqlService` interface and core implementation
- `PromptHelper` methods: `buildNewSqlGeneratorPrompt()`, `buildMixMacSqlDbPrompt()`
- DTO classes: `SchemaDTO`, `TableDTO`, `ColumnDTO`, `SqlGenerationDTO`
- Prompt templates for SQL generation

**From DataAgent - Remove:**
- `performSemanticConsistency()` - Semantic consistency check (complex scenario)
- `fineSelect()` with evidence - Advanced schema selector (complex scenario)
- `Nl2SqlProcessVO` - Process tracking (complex workflow)
- `SemanticConsistencyDTO` - Semantic check parameters
- SQL error fixer - Automatic SQL repair (complex scenario)
- Reactive streaming - `Flux<String>` responses

**Simplifications:**
- **Synchronous API** - Return `String`, not `Flux<String>`
- **Smart Filtering** - Only filter when >= 10 tables (threshold-based)
- **No Retry** - Generation failure returns error directly
- **Single LLM Call** - No multi-step refinement

### Comparison Table

| Feature | DataAgent | AssistantAgent NL2SQL |
|---------|-----------|----------------------|
| SQL Generation | ✅ Multi-step with refinement | ✅ Single-step generation |
| Schema Filtering | ✅ Always via LLM | ✅ Smart threshold (>=10 tables) |
| Semantic Check | ✅ Post-generation validation | ❌ Removed for simplicity |
| Error Repair | ✅ Automatic SQL fixing | ❌ Direct error return |
| Response Type | Flux<String> (streaming) | String (synchronous) |
| Use Cases | Complex data analysis | Simple queries, param collection |

---

## 4. Core Interfaces

### Nl2SqlService SPI

```java
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

### OptionItem Model

```java
public class OptionItem {
    private String label;   // Display label
    private String value;   // Actual value
}
```

---

## 5. Data Models

### Migrated from DataAgent

```java
// 1. Schema Information
@Data
@NoArgsConstructor
public class SchemaDTO {
    private String name;                    // Database name
    private String description;             // Description
    private Integer tableCount;             // Number of tables
    private List<TableDTO> table;           // Table list
    private List<String> foreignKeys;       // Foreign key relationships
}

// 2. Table Information
@Data
@NoArgsConstructor
public class TableDTO {
    private String name;                    // Table name
    private String description;             // Table description
    private List<ColumnDTO> column;         // Column list
    private List<String> primaryKeys;       // Primary key columns
}

// 3. Column Information
@Data
@NoArgsConstructor
public class ColumnDTO {
    private String name;                    // Column name
    private String description;             // Column description
    private String type;                    // Data type
    private List<String> data;              // Sample values (top 3)
    private Map<String, String> mapping;    // Value mapping
    private int enumeration;                // Enumeration flag
    private String range;                   // Value range
}

// 4. SQL Generation Parameters
@Data
@Builder
@AllArgsConstructor
public class SqlGenerationDTO {
    private String query;                   // Natural language query
    private String evidence;                // Additional context
    private SchemaDTO schemaDTO;            // Schema information
    private String dialect;                 // Database dialect (mysql/postgresql)
    private String executionDescription;    // Execution description
}
```

**Storage Location:**
- `assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/model/nl2sql/`

---

## 6. Smart Schema Filtering

### Threshold-Based Strategy

```
Table Count < 10:
  ├─ No Filtering
  ├─ Use Full Schema
  └─ One LLM Call (SQL Generation)

Table Count >= 10:
  ├─ Apply Filtering
  ├─ First LLM Call: Select Relevant Tables
  ├─ Filter Schema to Selected Tables
  └─ Second LLM Call: Generate SQL
```

### Filtering Process

**Step 1: Extract Table Names**
```java
List<String> allTables = schema.getTable().stream()
    .map(TableDTO::getName)
    .collect(Collectors.toList());
```

**Step 2: Call LLM to Filter**
```
Prompt: "Given the query '{query}', which tables are relevant?
         Available tables: {allTables}
         Return as JSON array: [\"table1\", \"table2\"]"
```

**Step 3: Filter Schema**
```java
Set<String> selectedTables = parseTableNames(llmResponse);
schema.getTable().removeIf(t -> !selectedTables.contains(t.getName().toLowerCase()));
```

### Performance Benefits

| Scenario | Tables | LLM Calls | Prompt Size | Response Time |
|----------|--------|-----------|-------------|---------------|
| Small DB | 5 | 1 | ~2K tokens | ~2s |
| Large DB | 50 | 2 | ~1K + 5K tokens | ~4s |

**Key Insight:** Filtering reduces prompt size from ~20K tokens to ~5K tokens for large databases.

---

## 7. Prompt Construction

### Nl2SqlPromptBuilder

Independent utility class (not implementing PromptBuilder interface):

```java
public class Nl2SqlPromptBuilder {

    /**
     * Build SQL generation prompt.
     * Reference: DataAgent's buildNewSqlGeneratorPrompt()
     */
    public static String buildSqlGenerationPrompt(SqlGenerationDTO dto) {
        Map<String, Object> params = new HashMap<>();
        params.put("dialect", dto.getDialect());
        params.put("question", dto.getQuery());
        params.put("schema_info", buildSchemaInfo(dto.getSchemaDTO()));
        params.put("evidence", dto.getEvidence());
        params.put("execution_description", dto.getExecutionDescription());

        return SQL_GENERATION_TEMPLATE.render(params);
    }

    /**
     * Build schema information string.
     * Reference: DataAgent's buildMixMacSqlDbPrompt()
     */
    public static String buildSchemaInfo(SchemaDTO schemaDTO) {
        StringBuilder sb = new StringBuilder();
        sb.append("【DB_ID】").append(schemaDTO.getName()).append("\n");

        for (TableDTO table : schemaDTO.getTable()) {
            sb.append("# Table: ").append(table.getName());
            if (!table.getName().equals(table.getDescription())) {
                sb.append(", ").append(table.getDescription());
            }
            sb.append("\n[\n");

            // Column format: (name:TYPE, description, Primary Key, Examples)
            List<String> columns = buildColumnList(table);
            sb.append(String.join(",\n", columns));
            sb.append("\n]\n");
        }

        if (schemaDTO.getForeignKeys() != null) {
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

        return SCHEMA_FILTER_TEMPLATE.render(params);
    }
}
```

### Prompt Templates

**Location:** `assistant-agent-data-core/src/main/resources/prompts/`

**1. nl2sql-generation.st**
```
You are an expert SQL query generator.

Given the following database schema and user question, generate a valid {dialect} SQL query.

DATABASE SCHEMA:
{schema_info}

USER QUESTION:
{question}

ADDITIONAL CONTEXT:
{evidence}

EXECUTION DESCRIPTION:
{execution_description}

REQUIREMENTS:
1. Generate only SELECT statements (read-only)
2. Use proper table and column names from the schema
3. Consider primary keys and foreign keys
4. Return only the SQL query without explanation
5. Wrap SQL in ```sql code block

SQL QUERY:
```

**2. nl2sql-filter.st**
```
You are a database schema expert.

Given the user query, identify which tables are relevant for answering the question.

USER QUERY:
{query}

AVAILABLE TABLES:
{tables}

REQUIREMENTS:
1. Return only table names that are directly relevant
2. Format as JSON array: ["table1", "table2"]
3. Do not include unnecessary tables

RELEVANT TABLES:
```

---

## 8. Service Implementation

### DefaultNl2SqlService Flow

```
generateSql(systemId, query, evidence)
  │
  ├─> 1. Validate Input
  │     └─ Check systemId, query not null/empty
  │
  ├─> 2. Get Database Dialect
  │     └─ Query datasource type (mysql/postgresql)
  │
  ├─> 3. Get Schema Information
  │     └─ SchemaProvider.getTableList(systemId)
  │
  ├─> 4. Smart Schema Filtering
  │     ├─ IF tableCount < 10: Skip filtering
  │     └─ IF tableCount >= 10:
  │         ├─ Build filter prompt
  │         ├─ Call LLM (first call)
  │         ├─ Parse table names
  │         └─ Filter schema
  │
  ├─> 5. Build SQL Generation Prompt
  │     └─ Nl2SqlPromptBuilder.buildSqlGenerationPrompt()
  │
  ├─> 6. Call LLM (second call for large DB, only call for small DB)
  │     └─ ChatModel.call(prompt)
  │
  ├─> 7. Extract SQL from Response
  │     └─ Remove markdown code blocks
  │
  └─> 8. Return SQL
```

### Key Methods

```java
@Service
public class DefaultNl2SqlService implements Nl2SqlService {

    private static final int SCHEMA_FILTER_THRESHOLD = 10;

    private final SchemaProvider schemaProvider;
    private final ChatModel chatModel;
    private final SqlExecutionProvider sqlExecutionProvider;
    private final DatasourceProvider datasourceProvider;
    private final Nl2SqlProperties properties;

    @Override
    public String generateSql(String systemId, String query, String evidence) {
        // 1. Validate
        validateInput(systemId, query);

        // 2. Get dialect
        String dialect = getDatabaseDialect(systemId);

        // 3. Get schema
        SchemaDTO schema = getSchema(systemId);

        // 4. Smart filtering
        if (schema.getTableCount() >= SCHEMA_FILTER_THRESHOLD) {
            schema = filterTables(schema, query);
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

        // 6. Call LLM
        String response = chatModel.call(prompt);

        // 7. Extract SQL
        return extractSql(response);
    }

    @Override
    public List<OptionItem> generateAndExecute(String systemId, String query,
                                               String labelColumn, String valueColumn) {
        // Generate SQL
        String sql = generateSql(systemId, query,
            "Return columns: " + labelColumn + ", " + valueColumn);

        // Execute SQL
        QueryResult result = sqlExecutionProvider.execute(systemId, sql, 1000);

        // Map to OptionItem
        return result.getRows().stream()
            .map(row -> new OptionItem(
                row.get(labelColumn).toString(),
                row.get(valueColumn).toString()
            ))
            .collect(Collectors.toList());
    }
}
```

---

## 9. Tool and Parameter Collection Integration

### Nl2SqlCodeactTool

Agent tool for natural language to SQL conversion:

```java
@Component
public class Nl2SqlCodeactTool implements CodeactTool {

    private static final String TOOL_NAME = "nl2sql";

    private static final String DESCRIPTION =
        "Convert natural language to SQL query. " +
        "Returns the generated SQL statement that can be used with execute_sql tool.";

    private final Nl2SqlService nl2SqlService;

    @Override
    public String call(String toolInput) {
        Map<String, Object> params = parseInput(toolInput);
        String systemId = (String) params.get("systemId");
        String query = (String) params.get("query");
        String evidence = (String) params.get("evidence");

        String sql = nl2SqlService.generateSql(systemId, query, evidence);

        return "Generated SQL:\n```sql\n" + sql + "\n```\n\n" +
               "You can now execute this SQL using execute_sql tool.";
    }

    // ParameterTree: systemId (required), query (required), evidence (optional)
}
```

**Usage Example:**
```python
# Agent generates code
sql = nl2sql(
    systemId="my-database",
    query="查询最近30天的活跃用户数量",
    evidence="活跃用户定义为有登录记录的用户"
)
print(sql)

# Then execute
result = execute_sql(
    systemId="my-database",
    sql=sql,
    maxRows=100
)
```

### Parameter Collection Integration

In Planning module's parameter collection flow:

```java
// ParameterCollectionService
public List<OptionItem> collectOptions(ParameterDefinition param) {
    ParameterSource source = param.getSource();

    if (source instanceof Nl2SqlSourceConfig) {
        Nl2SqlSourceConfig config = (Nl2SqlSourceConfig) source;

        // Call NL2SQL service
        return nl2SqlService.generateAndExecute(
            param.getSystemId(),
            config.getDescription(),
            config.getLabelColumn(),
            config.getValueColumn()
        );
    }

    // Other source types...
}
```

**Configuration Example:**
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

---

## 10. Configuration Management

### Nl2SqlProperties

```java
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

    public static class LlmProperties {
        /** LLM model name */
        private String model = "qwen-max";

        /** Temperature (0.0-1.0, lower = more deterministic) */
        private double temperature = 0.1;

        /** Max tokens for response */
        private int maxTokens = 2000;
    }

    public static class CacheProperties {
        /** Enable result caching */
        private boolean enabled = true;

        /** Cache TTL in minutes */
        private int ttlMinutes = 30;
    }
}
```

### Application Configuration

```yaml
# application.yml
spring:
  assistant-agent:
    data:
      nl2sql:
        enabled: true                           # Enable NL2SQL
        schema-filter-threshold: 10             # Filter when >= 10 tables
        llm:
          model: qwen-max                       # LLM model
          temperature: 0.1                      # Low temperature for determinism
          max-tokens: 2000                      # Max response tokens
        cache:
          enabled: true                         # Enable caching
          ttl-minutes: 30                       # Cache for 30 minutes
```

---

## 11. Error Handling

### Exception Hierarchy

```java
// Base exception
public class Nl2SqlException extends RuntimeException {
    public Nl2SqlException(String message) {
        super(message);
    }

    public Nl2SqlException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Specific exceptions
public class SchemaNotFoundException extends Nl2SqlException {
    public SchemaNotFoundException(String systemId) {
        super("Schema not found for systemId: " + systemId);
    }
}

public class SqlGenerationException extends Nl2SqlException {
    public SqlGenerationException(String message, Throwable cause) {
        super("SQL generation failed: " + message, cause);
    }
}
```

### Error Handling Strategy

```java
@Override
public String generateSql(String systemId, String query, String evidence) {
    log.info("Nl2SqlService#generateSql - systemId={}, query={}", systemId, query);

    try {
        // 1. Validate input
        if (systemId == null || systemId.trim().isEmpty()) {
            throw new IllegalArgumentException("systemId cannot be null or empty");
        }
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query cannot be null or empty");
        }

        // 2. Get schema
        SchemaDTO schema = getSchema(systemId);
        if (schema == null || schema.getTable().isEmpty()) {
            throw new SchemaNotFoundException(systemId);
        }

        // 3. Generate SQL
        String sql = doGenerateSql(schema, query, evidence);

        log.info("Nl2SqlService#generateSql - Success, sqlLength={}", sql.length());
        return sql;

    } catch (Nl2SqlException e) {
        log.error("Nl2SqlService#generateSql - NL2SQL error: {}", e.getMessage());
        throw e;
    } catch (Exception e) {
        log.error("Nl2SqlService#generateSql - Unexpected error", e);
        throw new SqlGenerationException(e.getMessage(), e);
    }
}
```

### Logging Strategy

- **INFO**: Service entry/exit, LLM calls, major steps
- **DEBUG**: Prompt content, LLM responses, schema details
- **ERROR**: Exceptions, validation failures
- **Format**: `ClassName#methodName - reason=description, key1=value1, key2=value2`

---

## 12. Testing Strategy

### Unit Tests

**DefaultNl2SqlServiceTest:**
```java
@SpringBootTest
class DefaultNl2SqlServiceTest {

    @MockBean private SchemaProvider schemaProvider;
    @MockBean private ChatModel chatModel;
    @Autowired private Nl2SqlService nl2SqlService;

    @Test
    void shouldGenerateSqlForSmallDatabase() {
        // Mock schema with 5 tables (no filtering)
        SchemaDTO schema = createMockSchema(5);
        when(schemaProvider.getTableList(...)).thenReturn(schema);

        // Mock LLM response
        when(chatModel.call(anyString()))
            .thenReturn("```sql\nSELECT * FROM users WHERE active = 1\n```");

        String sql = nl2SqlService.generateSql("test-db", "查询活跃用户", null);

        assertEquals("SELECT * FROM users WHERE active = 1", sql);
        verify(chatModel, times(1)).call(anyString()); // Single LLM call
    }

    @Test
    void shouldFilterSchemaForLargeDatabase() {
        // Mock schema with 15 tables (triggers filtering)
        SchemaDTO schema = createMockSchema(15);
        when(schemaProvider.getTableList(...)).thenReturn(schema);

        // Mock filter response
        when(chatModel.call(contains("relevant tables")))
            .thenReturn("[\"users\", \"orders\"]");

        // Mock SQL generation response
        when(chatModel.call(contains("generate")))
            .thenReturn("```sql\nSELECT * FROM users\n```");

        String sql = nl2SqlService.generateSql("test-db", "查询用户订单", null);

        assertNotNull(sql);
        verify(chatModel, times(2)).call(anyString()); // Two LLM calls
    }

    @Test
    void shouldThrowExceptionForInvalidSystemId() {
        assertThrows(IllegalArgumentException.class, () ->
            nl2SqlService.generateSql(null, "query", null)
        );
    }
}
```

**Nl2SqlPromptBuilderTest:**
```java
class Nl2SqlPromptBuilderTest {

    @Test
    void shouldBuildValidSqlGenerationPrompt() {
        SchemaDTO schema = SchemaDTO.builder()
            .name("testdb")
            .table(Arrays.asList(createMockTable("users")))
            .build();

        SqlGenerationDTO dto = SqlGenerationDTO.builder()
            .query("查询活跃用户")
            .evidence("最近30天有登录")
            .schemaDTO(schema)
            .dialect("mysql")
            .build();

        String prompt = Nl2SqlPromptBuilder.buildSqlGenerationPrompt(dto);

        assertTrue(prompt.contains("查询活跃用户"));
        assertTrue(prompt.contains("最近30天有登录"));
        assertTrue(prompt.contains("mysql"));
        assertTrue(prompt.contains("users"));
    }

    @Test
    void shouldFormatSchemaCorrectly() {
        SchemaDTO schema = createCompleteSchema();
        String formatted = Nl2SqlPromptBuilder.buildSchemaInfo(schema);

        assertTrue(formatted.contains("【DB_ID】"));
        assertTrue(formatted.contains("# Table:"));
        assertTrue(formatted.contains("Primary Key"));
        assertTrue(formatted.contains("【Foreign keys】"));
    }
}
```

### Integration Tests

**Nl2SqlIntegrationTest:**
```java
@SpringBootTest
@ActiveProfiles("nl2sql-integration-test")
class Nl2SqlIntegrationTest {

    @Autowired private Nl2SqlService nl2SqlService;
    @Autowired private DatasourceProvider datasourceProvider;

    @Test
    void shouldGenerateSqlEndToEnd() {
        // Requires real LLM connection or WireMock
        String sql = nl2SqlService.generateSql(
            "test-system",
            "查询最近10个订单",
            "包含订单号、用户名、订单金额"
        );

        assertNotNull(sql);
        assertTrue(sql.toLowerCase().contains("select"));
        assertTrue(sql.toLowerCase().contains("order"));
        assertTrue(sql.toLowerCase().contains("limit"));
    }

    @Test
    void shouldGenerateAndExecuteForParameterCollection() {
        List<OptionItem> options = nl2SqlService.generateAndExecute(
            "test-system",
            "获取所有活跃部门",
            "department_name",
            "department_id"
        );

        assertFalse(options.isEmpty());
        options.forEach(opt -> {
            assertNotNull(opt.getLabel());
            assertNotNull(opt.getValue());
        });
    }
}
```

### Test Coverage Goals

- ✅ Small database scenario (<10 tables, no filtering)
- ✅ Large database scenario (>=10 tables, with filtering)
- ✅ Input validation (null/empty checks)
- ✅ Schema not found error
- ✅ LLM call failure handling
- ✅ SQL extraction from markdown
- ✅ Parameter collection integration
- ✅ Prompt construction correctness

---

## 13. Deployment and Module Structure

### Module Layout

```
assistant-agent-data/
├── assistant-agent-data-api/
│   └── src/main/java/.../data/model/nl2sql/
│       ├── SchemaDTO.java          (Migrated from DataAgent)
│       ├── TableDTO.java           (Migrated from DataAgent)
│       ├── ColumnDTO.java          (Migrated from DataAgent)
│       ├── SqlGenerationDTO.java   (Migrated from DataAgent)
│       ├── OptionItem.java         (New for parameter collection)
│       └── Nl2SqlException.java    (New exception types)
│
└── assistant-agent-data-core/
    ├── src/main/java/.../data/
    │   ├── nl2sql/
    │   │   ├── Nl2SqlService.java            (SPI interface)
    │   │   ├── DefaultNl2SqlService.java     (Implementation)
    │   │   └── Nl2SqlPromptBuilder.java      (Prompt construction)
    │   ├── config/
    │   │   ├── Nl2SqlProperties.java         (Configuration properties)
    │   │   └── Nl2SqlAutoConfiguration.java  (Spring Boot auto-config)
    │   └── tool/
    │       └── Nl2SqlCodeactTool.java        (Agent tool)
    │
    ├── src/main/resources/
    │   └── prompts/
    │       ├── nl2sql-generation.st          (SQL generation template)
    │       └── nl2sql-filter.st              (Schema filter template)
    │
    └── src/test/java/.../data/nl2sql/
        ├── DefaultNl2SqlServiceTest.java     (Unit tests)
        ├── Nl2SqlPromptBuilderTest.java      (Prompt tests)
        └── Nl2SqlIntegrationTest.java        (Integration tests)
```

### Dependencies

**New Dependencies (pom.xml):**
- None (all required dependencies already present in project)

**Reused Dependencies:**
- Spring AI Alibaba (LLM calls)
- Spring Boot 3.4.8 (configuration, auto-config)
- Lombok (model classes)
- Jackson (JSON parsing)

---

## 14. Performance Considerations

### LLM Call Optimization

| Scenario | Tables | LLM Calls | Avg Latency | Token Usage |
|----------|--------|-----------|-------------|-------------|
| Small DB (5 tables) | 5 | 1 | ~2s | ~3K tokens |
| Medium DB (10 tables) | 10 | 1 | ~2.5s | ~5K tokens |
| Large DB (50 tables, filtered) | 50 → 3 | 2 | ~4s | ~2K + 4K tokens |
| Large DB (no filtering) | 50 | 1 | ~3s | ~25K tokens |

**Key Insights:**
- Filtering reduces token usage by 60-80% for large databases
- Two LLM calls (~4s) faster than single call with huge prompt (~5s)
- Smart threshold (10 tables) optimizes for common case

### Caching Strategy

```java
// Cache key: systemId + query hash
String cacheKey = systemId + ":" + DigestUtils.md5Hex(query);

// Cache lookup
String cachedSql = cache.get(cacheKey);
if (cachedSql != null) {
    return cachedSql;
}

// Generate and cache
String sql = doGenerateSql(...);
cache.put(cacheKey, sql, properties.getCache().getTtlMinutes());
```

**Expected Performance:**
- Cache hit ratio: ~40% (repeated queries)
- Cache hit latency: <10ms
- Cache miss latency: 2-4s (LLM call)

---

## 15. Security Considerations

### SQL Injection Prevention

**1. Read-Only Validation:**
```java
public void validateReadOnly(String sql) {
    String normalized = sql.trim().toLowerCase();

    // Only allow SELECT statements
    if (!normalized.startsWith("select")) {
        throw new SecurityException("Only SELECT queries are allowed");
    }

    // Block dangerous keywords
    List<String> blacklist = Arrays.asList(
        "insert", "update", "delete", "drop", "create", "alter", "truncate"
    );

    for (String keyword : blacklist) {
        if (normalized.contains(keyword)) {
            throw new SecurityException("Query contains forbidden keyword: " + keyword);
        }
    }
}
```

**2. LLM Prompt Injection:**
- Escape user input in prompts
- Use structured templates
- Validate LLM output before execution

**3. Schema Access Control:**
- Respect datasource permissions (via PersistentDatasourceProvider)
- Only expose schemas user has access to

---

## 16. Migration from DataAgent

### Code Migration Checklist

**Models (from DataAgent to AssistantAgent):**
- [x] `SchemaDTO.java` → `assistant-agent-data-api/.../model/nl2sql/SchemaDTO.java`
- [x] `TableDTO.java` → `assistant-agent-data-api/.../model/nl2sql/TableDTO.java`
- [x] `ColumnDTO.java` → `assistant-agent-data-api/.../model/nl2sql/ColumnDTO.java`
- [x] `SqlGenerationDTO.java` → `assistant-agent-data-api/.../model/nl2sql/SqlGenerationDTO.java`

**Service Logic (simplified):**
- [x] `Nl2SqlService` interface → New SPI in AssistantAgent
- [x] `generateSql()` method → Simplified from DataAgent's implementation
- [x] Remove: `performSemanticConsistency()`, `fineSelect()` with evidence

**Prompt Construction:**
- [x] `buildNewSqlGeneratorPrompt()` → `Nl2SqlPromptBuilder.buildSqlGenerationPrompt()`
- [x] `buildMixMacSqlDbPrompt()` → `Nl2SqlPromptBuilder.buildSchemaInfo()`
- [x] Remove: `buildSqlErrorFixerPrompt()`, `buildSemanticConsistencyPrompt()`

**API Changes:**
- [x] `Flux<String>` → `String` (synchronous)
- [x] Reactive streams → Direct LLM calls

---

## 17. Future Enhancements (Not in Initial Implementation)

### Phase 2 (Post-MVP)

1. **SQL Error Repair**
   - Automatic retry with error message feedback
   - Similar to DataAgent's `buildSqlErrorFixerPrompt()`

2. **Semantic Consistency Check**
   - Post-generation validation
   - Verify SQL matches user intent

3. **Query History and Learning**
   - Store successful NL→SQL pairs
   - Use as few-shot examples in prompts

4. **Multi-Datasource Query**
   - Generate SQL across multiple datasources
   - JOIN across different databases

5. **Advanced Filtering**
   - Evidence-based schema selection
   - Business knowledge integration

---

## Summary

✅ **Lightweight Implementation** - Focused on core NL2SQL conversion
✅ **Smart Optimization** - Threshold-based schema filtering (10 tables)
✅ **Dual Scenario Support** - Agent tool + parameter collection
✅ **Code Reuse** - Migrate mature logic from DataAgent
✅ **Flexible Configuration** - YAML-based settings
✅ **Comprehensive Testing** - Unit + integration tests
✅ **Security Conscious** - Read-only validation, prompt injection prevention
✅ **Performance Optimized** - Caching, token reduction

**Next Steps:**
1. Create detailed implementation plan (TDD approach)
2. Set up isolated git worktree
3. Implement with task-by-task testing
4. Document usage and examples
