# DataAgent 集成设计方案

> 创建时间: 2026-01-18
> 状态: 待实施

## 1. 背景与目标

### 1.1 背景

- **DataAgent**: 基于数据源管理的智能体，一个智能体对应一个数据源和一组业务表，专注数据分析和 NL2SQL
- **AssistantAgent**: 通用智能助手框架，采用 Code-as-Action 范式

### 1.2 目标

- 数据分析场景 → DataAgent
- 简单查询场景 → AssistantAgent 扩展
- 两者共享数据库 (MySQL, ES, Redis)，数据互通
- 将 DataAgent 的核心能力迁移到 AssistantAgent

### 1.3 简单查询场景定义

1. **Action 参数查询** - 执行 Action 时查询数据库获取参数选项（如：添加单位时查询部门列表）
2. **快速数据检索** - 用户问"查一下张三的订单"这类简单 SQL 直接执行
3. **元数据查询** - 查询表结构、字段信息等 Schema 元数据

---

## 2. 架构设计

### 2.1 集成方式

采用**模块迁移**方案：将 DataAgent 的 connector 层迁移为 AssistantAgent 的新模块 `assistant-agent-data`。

### 2.2 新增模块结构

```
assistant-agent-data/                    # 新模块
├── assistant-agent-data-api/            # API 层
│   ├── model/
│   │   ├── DatasourceDefinition.java    # 数据源定义
│   │   ├── SchemaInfo.java              # Schema 元数据
│   │   ├── TableInfo.java               # 表信息
│   │   ├── ColumnInfo.java              # 列信息
│   │   └── QueryResult.java             # 查询结果
│   └── spi/
│       ├── DatasourceProvider.java      # 数据源 SPI
│       ├── SchemaProvider.java          # Schema SPI
│       └── SqlExecutionProvider.java    # SQL 执行 SPI
│
└── assistant-agent-data-core/           # 实现层
    ├── connector/                       # 从 DataAgent 迁移
    │   ├── accessor/                    # 数据库访问器
    │   ├── pool/                        # 连接池
    │   └── ddl/                         # DDL 处理
    ├── nl2sql/                          # NL2SQL 服务
    ├── service/                         # 业务服务
    └── tools/                           # CodeactTool 实现
```

---

## 3. 数据模型

### 3.1 核心实体关系

```
┌─────────────────┐       ┌─────────────────┐
│  ActionDefinition│       │    Datasource   │
├─────────────────┤       ├─────────────────┤
│ id              │       │ id              │
│ name            │       │ name            │
│ systemId ←──────┼───┐   │ type (mysql/pg) │
│ parameters      │   │   │ host/port       │
│ ...             │   │   │ database        │
└─────────────────┘   │   │ username/pwd    │
                      │   └─────────────────┘
┌─────────────────┐   │           ▲
│     Agent       │   │           │
├─────────────────┤   │   ┌───────┴─────────┐
│ id              │◄──┘   │ AgentDatasource │
│ systemId        │       ├─────────────────┤
│ name            │◄──────┤ agentId         │
│ description     │       │ datasourceId    │
│ ...             │       │ tables (JSON)   │
└─────────────────┘       └─────────────────┘
```

### 3.2 关联逻辑

1. **Agent（系统标识）**: 代表一个业务域，关联一组数据源和表
2. **Action.systemId → Agent.id**: Action 通过 systemId 关联到 Agent
3. **执行时数据流**: Action 执行 → 获取 systemId → 查找 Agent → 获取关联的 Datasource → 执行查询

### 3.3 共享数据库表

复用 DataAgent 现有表，AssistantAgent 连接同一数据库：

- `datasource` - 数据源配置
- `agent` - Agent/系统定义
- `agent_datasource` - Agent 与数据源关联
- `semantic_model` - 表的语义描述（NL2SQL 用）

---

## 4. 参数选项数据来源

### 4.1 三种数据来源模式

```java
public class OptionsSourceConfig {

    private SourceType sourceType;  // SQL, API, NL2SQL

    // 模式1: 配置 SQL
    private SqlSourceConfig sql;

    // 模式2: 调用 API
    private ApiSourceConfig api;

    // 模式3: 自然语言转 SQL
    private Nl2SqlSourceConfig nl2sql;
}

public enum SourceType {
    SQL,      // 直接执行配置的 SQL
    API,      // 调用外部 HTTP 接口
    NL2SQL    // 自然语言描述，自动转 SQL 执行
}
```

### 4.2 模式1: SQL 配置

```java
public class SqlSourceConfig {
    private String sql;           // SELECT id, name FROM departments WHERE status = 1
    private String labelColumn;   // name (显示给用户)
    private String valueColumn;   // id (实际值)
    private Map<String, String> paramMapping;  // SQL 参数映射
}
```

### 4.3 模式2: API 调用

```java
public class ApiSourceConfig {
    private String url;           // https://api.example.com/departments
    private String method;        // GET / POST
    private Map<String, String> headers;
    private String requestBody;   // POST 请求体模板
    private String responsePath;  // JSON Path: $.data.list
    private String labelField;    // name
    private String valueField;    // id
}
```

### 4.4 模式3: NL2SQL 自动转换

```java
public class Nl2SqlSourceConfig {
    private String description;   // "查询所有启用状态的部门列表，返回部门ID和名称"
    private String labelColumn;   // 结果中的显示列
    private String valueColumn;   // 结果中的值列
}
```

### 4.5 执行流程

```
参数收集阶段
    │
    ▼
检查 optionsSource
    │
    ├─ SQL ──────► SqlExecutionProvider.execute(sql) ──────┐
    │                                                       │
    ├─ API ──────► HttpClient.call(url) ───────────────────┤
    │                                                       ▼
    └─ NL2SQL ───► Nl2SqlService.generate() ► execute() ──► 转换为选项列表
                                                            │
                                                            ▼
                                                    返回给参数收集流程
```

---

## 5. CodeactTool 设计

### 5.1 Tool 清单

| Tool 名称 | 功能 | 参数 |
|-----------|------|------|
| `execute_sql` | 直接执行 SQL | systemId, sql, maxRows |
| `nl2sql` | 自然语言查询 | systemId, query |
| `query_schema` | 查询元数据 | systemId, tableName(可选) |

### 5.2 安全约束

```java
public class SqlSecurityValidator {

    // 只允许 SELECT 语句
    public void validateReadOnly(String sql) {
        String normalized = sql.trim().toUpperCase();
        if (!normalized.startsWith("SELECT")) {
            throw new SecurityException("仅允许 SELECT 查询");
        }
        // 禁止危险关键字
        List<String> forbidden = List.of("INSERT", "UPDATE", "DELETE",
            "DROP", "TRUNCATE", "ALTER", "CREATE", "GRANT");
        for (String keyword : forbidden) {
            if (normalized.contains(keyword)) {
                throw new SecurityException("SQL 包含禁止的操作: " + keyword);
            }
        }
    }
}
```

---

## 6. 内置数据查询服务

### 6.1 服务接口

```java
public interface ParameterOptionsService {

    List<OptionItem> fetchOptions(String systemId,
                                   OptionsSourceConfig config,
                                   Map<String, Object> context);
}

public class OptionItem {
    private String label;   // 显示文本
    private Object value;   // 实际值
    private String description;  // 可选描述
}
```

### 6.2 与参数收集流程集成

在 `UnifiedIntentRecognitionHook` 中集成，参数收集阶段自动获取选项列表并注入到 Prompt 中。

---

## 7. 迁移清单

### 7.1 从 DataAgent 迁移的组件

| 目录 | 内容 | 迁移方式 |
|------|------|----------|
| `connector/accessor/` | 数据库访问器 | 完整迁移 |
| `connector/pool/` | 连接池 | 完整迁移 |
| `connector/ddl/` | DDL 处理 | 完整迁移 |
| `connector/SqlExecutor.java` | SQL 执行器 | 完整迁移 |
| `bo/schema/` | Schema BO 对象 | 完整迁移 |
| `entity/Datasource.java` | 数据源实体 | 迁移 |
| `entity/Agent.java` | Agent 实体 | 迁移/复用 |
| `service/nl2sql/` | NL2SQL 服务 | 完整迁移 |
| `service/schema/` | Schema 服务 | 完整迁移 |
| `prompt/PromptHelper.java` | Prompt 模板 | 部分迁移 |

### 7.2 依赖配置

```xml
<dependencies>
    <!-- 数据库驱动 (optional) -->
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- 连接池 -->
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
    </dependency>

    <!-- Spring AI (NL2SQL) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-core</artifactId>
    </dependency>
</dependencies>
```

---

## 8. 配置

### 8.1 配置属性

```yaml
spring:
  ai:
    alibaba:
      codeact:
        extension:
          data:
            enabled: true

            datasource:
              management-db:
                url: jdbc:mysql://localhost:3306/dataagent
                username: root
                password: xxx

            sql:
              max-rows: 1000
              timeout-seconds: 30
              read-only: true

            nl2sql:
              enabled: true
              model: qwen-max

            tools:
              execute-sql-enabled: true
              nl2sql-enabled: true
              query-schema-enabled: true
```

---

## 9. 实施步骤

### Phase 1: 基础模块搭建

- 1.1 创建模块结构 (pom.xml, 目录)
- 1.2 迁移 API 层 (BO 对象, SPI 接口)

### Phase 2: 迁移 Connector 层

- 2.1 迁移连接池
- 2.2 迁移 Accessor
- 2.3 迁移 SQL 执行器

### Phase 3: 实现核心服务

- 3.1 数据源管理服务
- 3.2 Schema 服务
- 3.3 SQL 执行服务

### Phase 4: NL2SQL 迁移

- 4.1 迁移 Prompt 模板
- 4.2 迁移 NL2SQL 服务

### Phase 5: Tool 与集成

- 5.1 实现 CodeactTool
- 5.2 实现参数选项服务
- 5.3 集成到参数收集流程

### Phase 6: 配置与测试

- 6.1 自动配置
- 6.2 测试

---

## 10. 设计决策记录

| 决策 | 选择 | 原因 |
|------|------|------|
| 集成方式 | 模块迁移 | AssistantAgent 直接具备数据库访问能力，避免服务间调用 |
| 数据共享 | 完整数据层 + NL2SQL | 覆盖所有简单查询场景 |
| Tool 设计 | 分离专用 Tool + Action 内置 | 符合架构原则，兼顾灵活性和效率 |
| 参数数据来源 | SQL / API / NL2SQL | 覆盖配置、接口调用、智能转换三种模式 |
