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
