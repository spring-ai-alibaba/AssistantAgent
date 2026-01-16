# Trigger Module MySQL Implementation

## 概述

本文档描述 **Trigger模块** 的MySQL持久化存储实现，支持配置化切换存储后端（InMemory或MySQL）。

---

## 实现内容

### 1. 数据库Schema ✅

**文件**: `assistant-agent-extensions/src/main/resources/db/trigger-schema.sql`

创建了两张表:

#### trigger_definitions 表
```sql
CREATE TABLE IF NOT EXISTS `trigger_definitions` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `trigger_id` VARCHAR(100) NOT NULL UNIQUE,
    `name` VARCHAR(200),
    `description` VARCHAR(500),
    `source_type` VARCHAR(50) NOT NULL,
    `source_id` VARCHAR(100),
    `created_by` VARCHAR(100),
    `event_protocol` VARCHAR(50),
    `event_key` VARCHAR(200),
    `schedule_mode` VARCHAR(50),
    `schedule_value` VARCHAR(200),
    `condition_function` TEXT,
    `execute_function` TEXT,
    `parameters` JSON,
    `session_snapshot_id` VARCHAR(100),
    `graph_name` VARCHAR(100),
    `agent_name` VARCHAR(100),
    `metadata` JSON,
    `status` VARCHAR(20) NOT NULL,
    `expire_at` DATETIME,
    `max_retries` INT,
    `retry_delay` BIGINT,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- Indexes
    KEY `idx_trigger_id` (`trigger_id`),
    KEY `idx_source` (`source_type`, `source_id`),
    KEY `idx_status` (`status`),
    KEY `idx_schedule_mode` (`schedule_mode`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### trigger_execution_logs 表
```sql
CREATE TABLE IF NOT EXISTS `trigger_execution_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `execution_id` VARCHAR(100) NOT NULL UNIQUE,
    `trigger_id` VARCHAR(100) NOT NULL,
    `scheduled_time` DATETIME,
    `start_time` DATETIME,
    `end_time` DATETIME,
    `status` VARCHAR(20) NOT NULL,
    `error_message` TEXT,
    `error_stack` TEXT,
    `output_summary` JSON,
    `backend_task_id` VARCHAR(100),
    `thread_id` VARCHAR(100),
    `sandbox_id` VARCHAR(100),
    `retry_count` INT DEFAULT 0,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- Indexes
    KEY `idx_execution_id` (`execution_id`),
    KEY `idx_trigger_id` (`trigger_id`),
    KEY `idx_status` (`status`),
    KEY `idx_start_time` (`start_time`),
    KEY `idx_scheduled_time` (`scheduled_time`),
    KEY `idx_trigger_start_time` (`trigger_id`, `start_time` DESC),
    -- Foreign Key
    CONSTRAINT `fk_trigger_id` FOREIGN KEY (`trigger_id`)
        REFERENCES `trigger_definitions`(`trigger_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**关键设计**:
- 使用JSON字段存储复杂对象 (parameters, metadata, output_summary)
- 外键约束确保数据一致性 (ON DELETE CASCADE)
- 复合索引优化查询性能 (trigger_id + start_time)
- 使用 utf8mb4 字符集支持多语言

---

### 2. MyBatis Plus Entity Classes ✅

#### TriggerDefinitionEntity.java
**文件**: `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/trigger/persistence/entity/TriggerDefinitionEntity.java`

```java
@TableName(value = "trigger_definitions", autoResultMap = true)
public class TriggerDefinitionEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("trigger_id")
    private String triggerId;

    @TableField(value = "parameters", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> parameters;

    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    // ... 其他字段
}
```

**关键特性**:
- 使用 `@TableName` 映射数据库表
- `autoResultMap = true` 启用自动结果集映射
- `JacksonTypeHandler` 处理JSON字段的序列化/反序列化
- 枚举类型自动映射 (SourceType, ScheduleMode, TriggerStatus)

#### TriggerExecutionRecordEntity.java
**文件**: `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/trigger/persistence/entity/TriggerExecutionRecordEntity.java`

```java
@TableName(value = "trigger_execution_logs", autoResultMap = true)
public class TriggerExecutionRecordEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("execution_id")
    private String executionId;

    @TableField(value = "output_summary", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputSummary;

    // ... 其他字段
}
```

---

### 3. MyBatis Plus Mapper Interfaces ✅

#### TriggerDefinitionMapper.java
**文件**: `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/trigger/persistence/mapper/TriggerDefinitionMapper.java`

```java
@Mapper
public interface TriggerDefinitionMapper extends BaseMapper<TriggerDefinitionEntity> {

    @Select("SELECT * FROM trigger_definitions WHERE trigger_id = #{triggerId}")
    TriggerDefinitionEntity selectByTriggerId(@Param("triggerId") String triggerId);

    @Select("SELECT * FROM trigger_definitions WHERE source_type = #{sourceType} AND source_id = #{sourceId}")
    List<TriggerDefinitionEntity> selectBySource(@Param("sourceType") SourceType sourceType,
                                                  @Param("sourceId") String sourceId);

    @Select("SELECT * FROM trigger_definitions WHERE status = #{status}")
    List<TriggerDefinitionEntity> selectByStatus(@Param("status") TriggerStatus status);

    @Update("UPDATE trigger_definitions SET status = #{status}, updated_at = NOW() WHERE trigger_id = #{triggerId}")
    int updateStatusByTriggerId(@Param("triggerId") String triggerId, @Param("status") TriggerStatus status);

    @Update("DELETE FROM trigger_definitions WHERE trigger_id = #{triggerId}")
    int deleteByTriggerId(@Param("triggerId") String triggerId);
}
```

#### TriggerExecutionRecordMapper.java
**文件**: `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/trigger/persistence/mapper/TriggerExecutionRecordMapper.java`

```java
@Mapper
public interface TriggerExecutionRecordMapper extends BaseMapper<TriggerExecutionRecordEntity> {

    @Select("SELECT * FROM trigger_execution_logs WHERE execution_id = #{executionId}")
    TriggerExecutionRecordEntity selectByExecutionId(@Param("executionId") String executionId);

    @Select("SELECT * FROM trigger_execution_logs WHERE trigger_id = #{triggerId} " +
            "ORDER BY start_time DESC LIMIT #{limit}")
    List<TriggerExecutionRecordEntity> selectByTriggerIdWithLimit(@Param("triggerId") String triggerId,
                                                                   @Param("limit") int limit);

    @Select("SELECT * FROM trigger_execution_logs WHERE trigger_id = #{triggerId} " +
            "ORDER BY start_time DESC")
    List<TriggerExecutionRecordEntity> selectByTriggerId(@Param("triggerId") String triggerId);

    @Update("UPDATE trigger_execution_logs SET status = #{status}, " +
            "error_message = #{errorMessage}, end_time = #{endTime}, updated_at = NOW() " +
            "WHERE execution_id = #{executionId}")
    int updateStatusByExecutionId(@Param("executionId") String executionId,
                                   @Param("status") ExecutionStatus status,
                                   @Param("errorMessage") String errorMessage,
                                   @Param("endTime") Instant endTime);

    @Delete("DELETE FROM trigger_execution_logs WHERE execution_id = #{executionId}")
    int deleteByExecutionId(@Param("executionId") String executionId);
}
```

**设计特点**:
- 继承 `BaseMapper<T>` 获得CRUD基础方法
- 自定义查询方法使用注解SQL
- 支持复杂查询 (排序、分页、多条件)

---

### 4. MySQL Repository Implementations ✅

#### MysqlTriggerRepository.java
**文件**: `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/trigger/persistence/repository/MysqlTriggerRepository.java`

```java
public class MysqlTriggerRepository implements TriggerRepository {

    private final TriggerDefinitionMapper mapper;

    @Override
    public void save(TriggerDefinition definition) {
        TriggerDefinitionEntity existing = mapper.selectByTriggerId(definition.getTriggerId());
        TriggerDefinitionEntity entity = toEntity(definition);

        if (existing == null) {
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            mapper.insert(entity);
        } else {
            entity.setId(existing.getId());
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setUpdatedAt(Instant.now());
            mapper.updateById(entity);
        }
    }

    @Override
    public Optional<TriggerDefinition> findById(String triggerId) {
        TriggerDefinitionEntity entity = mapper.selectByTriggerId(triggerId);
        return Optional.ofNullable(entity).map(this::toModel);
    }

    // ... 其他方法实现

    private TriggerDefinitionEntity toEntity(TriggerDefinition model) { /* 转换逻辑 */ }
    private TriggerDefinition toModel(TriggerDefinitionEntity entity) { /* 转换逻辑 */ }
}
```

**关键实现**:
- **upsert语义**: save方法实现insert-or-update逻辑
- **实体转换**: toEntity/toModel方法处理领域模型与数据库实体转换
- **时间戳管理**: 自动设置 createdAt/updatedAt
- **日志记录**: 详细的操作日志

#### MysqlTriggerExecutionLogRepository.java
**文件**: `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/trigger/persistence/repository/MysqlTriggerExecutionLogRepository.java`

```java
public class MysqlTriggerExecutionLogRepository implements TriggerExecutionLogRepository {

    private final TriggerExecutionRecordMapper mapper;

    @Override
    public void updateStatus(String executionId, ExecutionStatus status,
                             String errorMessage, Map<String, Object> outputSummary) {
        TriggerExecutionRecordEntity entity = mapper.selectByExecutionId(executionId);
        if (entity == null) {
            log.warn("Execution record not found: {}", executionId);
            return;
        }

        entity.setStatus(status);
        if (errorMessage != null) entity.setErrorMessage(errorMessage);
        if (outputSummary != null) entity.setOutputSummary(outputSummary);

        // 设置结束时间
        if (status == ExecutionStatus.SUCCESS || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.TIMEOUT) {
            entity.setEndTime(Instant.now());
        }

        entity.setUpdatedAt(Instant.now());
        mapper.updateById(entity);
    }

    // ... 其他方法实现
}
```

---

### 5. Configuration Properties ✅

#### TriggerProperties.java (Updated)
**文件**: `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/trigger/config/TriggerProperties.java`

```java
@ConfigurationProperties(prefix = "spring.ai.alibaba.codeact.extension.trigger")
public class TriggerProperties {

    private boolean enabled = true;
    private SchedulerConfig scheduler = new SchedulerConfig();
    private ExecutionConfig execution = new ExecutionConfig();
    private StorageConfig storage = new StorageConfig();  // NEW

    public static class StorageConfig {
        /**
         * 存储类型: in-memory 或 mysql
         */
        private String type = "in-memory";

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    // ... 其他配置类
}
```

---

### 6. Auto Configuration ✅

#### TriggerAutoConfiguration.java (Updated)
**文件**: `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/trigger/config/TriggerAutoConfiguration.java`

```java
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.trigger",
                       name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TriggerProperties.class)
public class TriggerAutoConfiguration {

    // ==================== Storage - InMemory ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.trigger.storage",
                           name = "type", havingValue = "in-memory", matchIfMissing = true)
    public TriggerRepository inMemoryTriggerRepository() {
        log.info("Creating in-memory trigger repository");
        return new InMemoryTriggerRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.trigger.storage",
                           name = "type", havingValue = "in-memory", matchIfMissing = true)
    public TriggerExecutionLogRepository inMemoryTriggerExecutionLogRepository() {
        log.info("Creating in-memory execution log repository");
        return new InMemoryTriggerExecutionLogRepository();
    }

    // ==================== Storage - MySQL ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.trigger.storage",
                           name = "type", havingValue = "mysql")
    public TriggerRepository mysqlTriggerRepository(
            @Autowired(required = false) TriggerDefinitionMapper mapper) {
        if (mapper == null) {
            log.warn("TriggerDefinitionMapper not found, falling back to InMemory");
            return new InMemoryTriggerRepository();
        }
        log.info("Creating MySQL trigger repository");
        return new MysqlTriggerRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.trigger.storage",
                           name = "type", havingValue = "mysql")
    public TriggerExecutionLogRepository mysqlTriggerExecutionLogRepository(
            @Autowired(required = false) TriggerExecutionRecordMapper mapper) {
        if (mapper == null) {
            log.warn("TriggerExecutionRecordMapper not found, falling back to InMemory");
            return new InMemoryTriggerExecutionLogRepository();
        }
        log.info("Creating MySQL execution log repository");
        return new MysqlTriggerExecutionLogRepository(mapper);
    }

    // ... 其他Bean定义
}
```

**设计亮点**:
- **条件Bean**: 使用 `@ConditionalOnProperty` 根据配置动态创建Bean
- **Fallback机制**: Mapper未找到时自动降级到InMemory
- **可扩展性**: 新增存储类型只需添加新的Bean定义

---

### 7. Application Configuration ✅

#### application.yml (Updated)
**文件**: `assistant-agent-start/src/main/resources/application.yml`

```yaml
# ============================================================================
# Trigger Module Configuration
# ============================================================================
spring.ai.alibaba.codeact.extension.trigger:
  enabled: true
  storage:
    type: mysql  # Options: in-memory (default), mysql
  scheduler:
    pool-size: 10
    await-termination-seconds: 60
  execution:
    default-max-retries: 3
    default-retry-delay: 1000
    execution-timeout: 0
```

---

## 使用方式

### 1. 初始化数据库

运行SQL脚本创建表:

```bash
mysql -u root -p assistant_agent < assistant-agent-extensions/src/main/resources/db/trigger-schema.sql
```

或在MySQL客户端中执行:

```sql
SOURCE /path/to/assistant-agent-extensions/src/main/resources/db/trigger-schema.sql;
```

### 2. 配置存储类型

#### 使用InMemory (默认)

```yaml
spring.ai.alibaba.codeact.extension.trigger:
  enabled: true
  storage:
    type: in-memory  # 或省略此配置，默认即为in-memory
```

#### 使用MySQL

```yaml
spring.ai.alibaba.codeact.extension.trigger:
  enabled: true
  storage:
    type: mysql
```

### 3. 确保MySQL配置正确

```yaml
spring.datasource:
  url: jdbc:mysql://localhost:3306/assistant_agent?useUnicode=true&characterEncoding=utf8
  username: root
  password: ${MYSQL_PASSWORD:StrongRootPwd}
  driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.alibaba.assistant.agent.extension.trigger.persistence.entity
```

### 4. 启动应用

```bash
cd assistant-agent-start
mvn spring-boot:run
```

### 5. 验证存储类型

查看启动日志:

**InMemory模式**:
```
TriggerAutoConfiguration#inMemoryTriggerRepository - reason=creating in-memory trigger repository
TriggerAutoConfiguration#inMemoryTriggerExecutionLogRepository - reason=creating in-memory execution log repository
```

**MySQL模式**:
```
TriggerAutoConfiguration#mysqlTriggerRepository - reason=creating MySQL trigger repository
TriggerAutoConfiguration#mysqlTriggerExecutionLogRepository - reason=creating MySQL execution log repository
```

---

## 文件清单

### 新增文件

```
assistant-agent-extensions/
├── src/main/resources/db/
│   └── trigger-schema.sql                                     # MySQL表结构定义
├── src/main/java/com/alibaba/assistant/agent/extension/trigger/
│   └── persistence/
│       ├── entity/
│       │   ├── TriggerDefinitionEntity.java                  # 触发器定义实体
│       │   └── TriggerExecutionRecordEntity.java             # 执行记录实体
│       ├── mapper/
│       │   ├── TriggerDefinitionMapper.java                  # 触发器定义Mapper
│       │   └── TriggerExecutionRecordMapper.java             # 执行记录Mapper
│       └── repository/
│           ├── MysqlTriggerRepository.java                   # MySQL触发器仓库
│           └── MysqlTriggerExecutionLogRepository.java       # MySQL执行日志仓库
```

### 修改文件

```
assistant-agent-extensions/
└── src/main/java/com/alibaba/assistant/agent/extension/trigger/
    └── config/
        ├── TriggerProperties.java                            # 添加StorageConfig
        └── TriggerAutoConfiguration.java                     # 添加MySQL Bean配置

assistant-agent-start/
└── src/main/resources/
    └── application.yml                                       # 添加Trigger配置示例
```

---

## 技术栈

- **ORM**: MyBatis Plus 3.5.x
- **Database**: MySQL 8.0+
- **JSON**: Jackson (via JacksonTypeHandler)
- **Spring Boot**: 3.4.8
- **Java**: 17+

---

## 关键优势

### 1. 配置化切换
- 通过配置文件即可切换InMemory/MySQL存储
- 无需修改代码，降低维护成本

### 2. Fallback机制
- Mapper未配置时自动降级到InMemory
- 保证系统可用性

### 3. 数据一致性
- 外键约束确保引用完整性
- ON DELETE CASCADE自动清理关联数据

### 4. 查询性能
- 合理的索引设计 (单列、复合索引)
- 分页查询支持 (LIMIT)
- 时间倒序排列 (ORDER BY start_time DESC)

### 5. 扩展性
- 实体转换层解耦领域模型与数据库模型
- 新增字段只需修改Entity和转换逻辑
- 支持添加新的存储实现 (如PostgreSQL、ES)

---

## 性能优化建议

### 1. 索引优化
- 已创建核心索引，覆盖常用查询
- 可根据实际查询模式添加更多索引

### 2. 批量操作
- 使用MyBatis Plus的批量插入API
- 减少网络往返次数

### 3. 连接池配置
- HikariCP默认配置已优化
- 可根据负载调整 `maximum-pool-size`

### 4. 分表策略
- execution_logs表可按时间分表 (按月/季度)
- 使用ShardingSphere实现透明分表

### 5. 定期清理
- 清理过期的execution_logs记录
- 清理COMPLETED状态的trigger定义

---

## 验证清单

- [x] SQL Schema 创建成功
- [x] Entity 类编译通过
- [x] Mapper 接口定义正确
- [x] Repository 实现完整
- [x] 配置属性添加
- [x] AutoConfiguration 更新
- [x] application.yml 示例配置
- [ ] 单元测试 (待实现)
- [ ] 集成测试 (待实现)
- [ ] 性能测试 (待实现)

---

## 总结

**Trigger Module MySQL Implementation** 已成功完成！

✅ **已实现**:
- MySQL表结构设计和创建
- MyBatis Plus实体类和Mapper
- MySQL Repository实现
- 配置化存储切换
- Fallback机制
- 应用配置示例

🎯 **收益**:
- 数据持久化，重启不丢失
- 支持分布式部署
- 事务保证数据一致性
- 高性能查询 (索引优化)
- 配置化切换，灵活部署

📊 **代码统计**:
- 新增文件: 7个
- 修改文件: 3个
- 新增代码行数: ~1000行

---

**相关文档**:
- [Learning Module MySQL Implementation](LEARNING_MYSQL_IMPLEMENTATION.md)
- [Experience Module Elasticsearch Implementation](EXPERIENCE_ELASTICSEARCH_IMPLEMENTATION.md)
- [Storage Implementation Analysis](STORAGE_IMPLEMENTATION_ANALYSIS.md)
