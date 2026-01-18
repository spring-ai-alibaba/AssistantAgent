# PersistentDatasourceProvider Implementation Design

**Goal:** Replace InMemoryDatasourceProvider with MySQL-backed persistent storage that shares DataAgent's saa_data_agent database

**Architecture:** Read datasources from DataAgent's MySQL database (datasource, agent, agent_datasource tables), cache results in memory with TTL

**Tech Stack:** Spring Boot 3.4.8, Spring JDBC, HikariCP, MySQL 8.0

---

## 1. Database Schema Analysis

### DataAgent Tables

**datasource table** (datasource.java lines 97-118):
```sql
CREATE TABLE datasource (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL,              -- mysql, postgresql
  host VARCHAR(255) NOT NULL,
  port INT NOT NULL,
  database_name VARCHAR(255) NOT NULL,
  username VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL,         -- encrypted
  connection_url VARCHAR(1000),
  status VARCHAR(50) DEFAULT 'inactive',  -- active/inactive
  test_status VARCHAR(50),
  description TEXT,
  creator_id BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
)
```

**agent table** (schema.sql lines 4-24):
```sql
CREATE TABLE agent (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  status VARCHAR(50) DEFAULT 'draft',     -- draft/published/offline
  api_key VARCHAR(255),
  prompt TEXT,
  ...
)
```

**agent_datasource table** (schema.sql lines 140-154):
```sql
CREATE TABLE agent_datasource (
  id INT PRIMARY KEY AUTO_INCREMENT,
  agent_id INT NOT NULL,
  datasource_id INT NOT NULL,
  is_active TINYINT DEFAULT 0,            -- 0=disabled, 1=enabled
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_agent_datasource (agent_id, datasource_id),
  FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE,
  FOREIGN KEY (datasource_id) REFERENCES datasource(id) ON DELETE CASCADE
)
```

### Mapping Strategy

**systemId → agentId:**
- The DatasourceProvider SPI uses `systemId` parameter
- In AssistantAgent context, `systemId` maps to DataAgent's `agent.id`
- This allows each agent to have its own configured datasources

**Field Mapping (DataAgent → DatasourceDefinition):**
```
datasource.id           → DatasourceDefinition.id
datasource.name         → DatasourceDefinition.name
datasource.type         → DatasourceDefinition.type
datasource.host         → DatasourceDefinition.host
datasource.port         → DatasourceDefinition.port
datasource.database_name → DatasourceDefinition.databaseName
datasource.username     → DatasourceDefinition.username
datasource.password     → DatasourceDefinition.password
datasource.connection_url → DatasourceDefinition.connectionUrl
datasource.status       → DatasourceDefinition.status
```

## 2. Architecture Design

### Component Structure

```
PersistentDatasourceProvider (@Component)
  ├── JdbcTemplate (injected, connects to saa_data_agent)
  ├── PersistentDatasourceProperties (configuration)
  └── DatasourceCache (in-memory cache with TTL)
```

### Query Strategies

**findBySystemId(String systemId):**
```sql
SELECT
  d.id, d.name, d.type, d.host, d.port,
  d.database_name, d.username, d.password,
  d.connection_url, d.status
FROM datasource d
INNER JOIN agent_datasource ad ON d.id = ad.datasource_id
WHERE ad.agent_id = ?                    -- systemId parsed as agentId
  AND ad.is_active = 1                   -- only active associations
  AND d.status = 'active'                -- only active datasources
LIMIT 1
```

**findById(Long id):**
```sql
SELECT
  id, name, type, host, port,
  database_name, username, password,
  connection_url, status
FROM datasource
WHERE id = ?
  AND status = 'active'
```

**register(String systemId, DatasourceDefinition datasource):**
- **Behavior**: Throw `UnsupportedOperationException` with message:
  "Datasources must be managed through DataAgent UI. Use findBySystemId() or findById() to query existing datasources."
- **Rationale**: Datasources are created/updated via DataAgent's management interface, not programmatically

### Caching Strategy

**Hybrid Approach (Lazy Load + TTL Cache):**

1. **Cache Structure:**
   ```java
   class DatasourceCache {
       ConcurrentHashMap<String, CacheEntry<DatasourceDefinition>> bySystemId;
       ConcurrentHashMap<Long, CacheEntry<DatasourceDefinition>> byId;

       static class CacheEntry<T> {
           T value;
           long expirationTime;
       }
   }
   ```

2. **Cache Behavior:**
   - **Miss**: Query database → Store in cache with TTL → Return result
   - **Hit**: Check expiration → If expired, refresh from database
   - **TTL**: Configurable (default 5 minutes)

3. **Benefits:**
   - Reduces database load for repeated queries
   - Fresh data within TTL window
   - No startup delay (lazy loading)

4. **Eviction:**
   - Automatic on expiration (checked on access)
   - Manual via `@Scheduled` cleanup task (every minute)

## 3. Configuration Properties

**Spring Boot Properties:**
```yaml
# application.yml
spring:
  assistant-agent:
    data:
      persistent-datasource:
        enabled: true                    # Enable persistent provider
        cache:
          enabled: true                  # Enable caching
          ttl-minutes: 5                 # Cache TTL in minutes
          cleanup-interval-seconds: 60   # Cleanup task interval
        connection:
          url: jdbc:mysql://127.0.0.1:3306/saa_data_agent?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
          username: root
          password: StrongRootPwd
          pool:
            maximum-pool-size: 10
            minimum-idle: 2
            connection-timeout: 30000
```

**Properties Class:**
```java
@ConfigurationProperties(prefix = "spring.assistant-agent.data.persistent-datasource")
public class PersistentDatasourceProperties {
    private boolean enabled = true;
    private CacheProperties cache = new CacheProperties();
    private ConnectionProperties connection = new ConnectionProperties();

    public static class CacheProperties {
        private boolean enabled = true;
        private int ttlMinutes = 5;
        private int cleanupIntervalSeconds = 60;
        // getters/setters
    }

    public static class ConnectionProperties {
        private String url;
        private String username;
        private String password;
        private PoolProperties pool = new PoolProperties();
        // getters/setters

        public static class PoolProperties {
            private int maximumPoolSize = 10;
            private int minimumIdle = 2;
            private int connectionTimeout = 30000;
            // getters/setters
        }
    }
}
```

## 4. Implementation Details

### PersistentDatasourceProvider

**Bean Configuration:**
```java
@Component
@ConditionalOnProperty(
    prefix = "spring.assistant-agent.data.persistent-datasource",
    name = "enabled",
    havingValue = "true"
)
public class PersistentDatasourceProvider implements DatasourceProvider {
    private final JdbcTemplate jdbcTemplate;
    private final DatasourceCache cache;
    private final PersistentDatasourceProperties properties;

    // Constructor injection
}
```

**Key Methods:**

1. **findBySystemId:**
   ```java
   @Override
   public DatasourceDefinition findBySystemId(String systemId) {
       if (cache.isEnabled()) {
           DatasourceDefinition cached = cache.getBySystemId(systemId);
           if (cached != null) return cached;
       }

       Long agentId = parseSystemId(systemId);
       DatasourceDefinition result = queryByAgentId(agentId);

       if (result != null && cache.isEnabled()) {
           cache.putBySystemId(systemId, result);
           cache.putById(result.getId(), result);
       }

       return result;
   }
   ```

2. **findById:**
   ```java
   @Override
   public DatasourceDefinition findById(Long id) {
       if (cache.isEnabled()) {
           DatasourceDefinition cached = cache.getById(id);
           if (cached != null) return cached;
       }

       DatasourceDefinition result = queryById(id);

       if (result != null && cache.isEnabled()) {
           cache.putById(id, result);
       }

       return result;
   }
   ```

3. **register:**
   ```java
   @Override
   public Long register(String systemId, DatasourceDefinition datasource) {
       throw new UnsupportedOperationException(
           "Datasources must be managed through DataAgent UI. " +
           "Use findBySystemId() or findById() to query existing datasources."
       );
   }
   ```

### DatasourceCache

**Responsibilities:**
- Store cached DatasourceDefinition objects with expiration
- Provide get/put operations with TTL check
- Scheduled cleanup of expired entries

**Implementation:**
```java
@Component
@ConditionalOnProperty(
    prefix = "spring.assistant-agent.data.persistent-datasource.cache",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
class DatasourceCache {
    private final ConcurrentHashMap<String, CacheEntry> bySystemId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CacheEntry> byId = new ConcurrentHashMap<>();
    private final long ttlMillis;

    static class CacheEntry {
        final DatasourceDefinition value;
        final long expirationTime;
    }

    @Scheduled(fixedDelayString = "${spring.assistant-agent.data.persistent-datasource.cache.cleanup-interval-seconds}000")
    void cleanupExpired() {
        long now = System.currentTimeMillis();
        bySystemId.entrySet().removeIf(e -> e.getValue().expirationTime < now);
        byId.entrySet().removeIf(e -> e.getValue().expirationTime < now);
    }
}
```

### DataSource Configuration

**Separate DataSource Bean:**
```java
@Configuration
@ConditionalOnProperty(
    prefix = "spring.assistant-agent.data.persistent-datasource",
    name = "enabled",
    havingValue = "true"
)
public class PersistentDatasourceConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "spring.assistant-agent.data.persistent-datasource.connection.pool")
    public HikariConfig dataAgentHikariConfig(PersistentDatasourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getConnection().getUrl());
        config.setUsername(properties.getConnection().getUsername());
        config.setPassword(properties.getConnection().getPassword());
        return config;
    }

    @Bean(name = "dataAgentDataSource")
    public DataSource dataAgentDataSource(HikariConfig dataAgentHikariConfig) {
        return new HikariDataSource(dataAgentHikariConfig);
    }

    @Bean(name = "dataAgentJdbcTemplate")
    public JdbcTemplate dataAgentJdbcTemplate(@Qualifier("dataAgentDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

## 5. Error Handling

**Error Scenarios:**

1. **Database Connection Failure:**
   ```java
   try {
       return jdbcTemplate.queryForObject(sql, rowMapper, params);
   } catch (DataAccessException e) {
       logger.error("PersistentDatasourceProvider#findBySystemId - Database query failed: systemId={}", systemId, e);
       return null;  // Graceful degradation
   }
   ```

2. **Invalid systemId Format:**
   ```java
   private Long parseSystemId(String systemId) {
       try {
           return Long.parseLong(systemId);
       } catch (NumberFormatException e) {
           logger.warn("PersistentDatasourceProvider#parseSystemId - Invalid systemId format: {}", systemId);
           throw new IllegalArgumentException("systemId must be a valid agent ID (numeric)");
       }
   }
   ```

3. **No Datasource Found:**
   ```java
   // Return null to allow fallback behavior
   logger.debug("PersistentDatasourceProvider#findBySystemId - No datasource found: systemId={}", systemId);
   return null;
   ```

4. **Cache Errors:**
   ```java
   // Catch any cache errors and fall through to database
   try {
       DatasourceDefinition cached = cache.getBySystemId(systemId);
       if (cached != null) return cached;
   } catch (Exception e) {
       logger.warn("PersistentDatasourceProvider#findBySystemId - Cache error, querying database: {}", e.getMessage());
   }
   ```

## 6. Testing Strategy

### Unit Tests

**PersistentDatasourceProviderTest:**
- Test findById with valid ID
- Test findById with nonexistent ID
- Test findBySystemId with valid agent ID
- Test findBySystemId with invalid agent ID format
- Test findBySystemId with agent having no datasources
- Test register throws UnsupportedOperationException
- Test database connection failure handling

**DatasourceCacheTest:**
- Test cache hit within TTL
- Test cache miss after expiration
- Test cache put and get
- Test concurrent access
- Test cleanup task removes expired entries

### Integration Tests

**PersistentDatasourceIntegrationTest:**
- Use real MySQL connection to saa_data_agent
- Create test agent and datasource records
- Verify findBySystemId returns correct datasource
- Verify findById returns correct datasource
- Verify caching behavior with TTL
- Test cleanup after test (delete test data)

## 7. Migration Notes

### Bean Precedence

**Current (InMemoryDatasourceProvider):**
```java
@Component
@ConditionalOnMissingBean(DatasourceProvider.class)
public class InMemoryDatasourceProvider implements DatasourceProvider { ... }
```

**New (PersistentDatasourceProvider):**
```java
@Component
@ConditionalOnProperty(name = "spring.assistant-agent.data.persistent-datasource.enabled", havingValue = "true")
public class PersistentDatasourceProvider implements DatasourceProvider { ... }
```

**Behavior:**
- When `persistent-datasource.enabled=true`: PersistentDatasourceProvider wins
- When `persistent-datasource.enabled=false` or not set: InMemoryDatasourceProvider wins (fallback)
- No breaking changes to existing code

### Configuration Migration

**Before (In-Memory):**
```java
// No configuration needed
DatasourceProvider provider = context.getBean(DatasourceProvider.class);
Long id = provider.register("test-system", datasource);
```

**After (Persistent):**
```yaml
# application.yml
spring:
  assistant-agent:
    data:
      persistent-datasource:
        enabled: true
        connection:
          url: jdbc:mysql://127.0.0.1:3306/saa_data_agent...
          username: root
          password: StrongRootPwd
```

```java
// Query-only mode
DatasourceProvider provider = context.getBean(DatasourceProvider.class);
DatasourceDefinition ds = provider.findBySystemId("1");  // Query agent ID 1's datasource
```

## 8. Performance Considerations

**Database Queries:**
- Simple indexed queries (agent_id, datasource_id, is_active)
- JOINs are on primary/foreign keys
- Expected query time: <10ms on local MySQL

**Caching:**
- In-memory cache eliminates repeated database queries
- TTL (5 minutes) balances freshness vs performance
- Concurrent access via ConcurrentHashMap (thread-safe)

**Connection Pooling:**
- HikariCP provides fast, efficient connection pooling
- Default pool size: 10 (configurable)
- Reuses connections across requests

**Expected Load:**
- Low query frequency (datasource lookups are rare)
- Cache hit ratio: >90% (assuming repeated queries within TTL)
- Database load: <1 query/minute per agent (with cache)

## 9. Security Considerations

**Password Handling:**
- DataAgent stores encrypted passwords
- PersistentDatasourceProvider reads as-is
- DatasourceDefinition.password is plain text (decryption handled by DataAgent layer)
- **Action**: Document that password encryption/decryption is DataAgent's responsibility

**SQL Injection:**
- All queries use parameterized PreparedStatements via JdbcTemplate
- No string concatenation in SQL
- systemId validated as numeric before query

**Connection Security:**
- MySQL connection uses credentials from configuration
- Connection pooling limits concurrent connections
- No exposure of DataAgent database credentials to end users

## 10. Future Enhancements

**Phase 2 (Not in Initial Implementation):**
1. **Write Support**: Allow programmatic datasource creation via `register()`
2. **Multi-Datasource Support**: Return List instead of single datasource for agents with multiple datasources
3. **Cache Invalidation**: Webhook/event listener to invalidate cache when DataAgent updates datasources
4. **Metrics**: Expose cache hit/miss metrics via Spring Boot Actuator
5. **Distributed Cache**: Replace in-memory cache with Redis for multi-instance deployments

---

## Summary

This design provides a production-ready PersistentDatasourceProvider that:
- ✅ Reads from DataAgent's MySQL database (saa_data_agent)
- ✅ Shares datasource configuration with DataAgent
- ✅ Maintains compatibility with existing DatasourceProvider SPI
- ✅ Provides caching for performance
- ✅ Gracefully falls back to InMemoryDatasourceProvider when disabled
- ✅ Follows Spring Boot configuration patterns
- ✅ Includes comprehensive error handling and logging
- ✅ Ready for testing with real DataAgent database

**Next Step:** Create detailed implementation plan with TDD approach (write tests first, then implementation).
