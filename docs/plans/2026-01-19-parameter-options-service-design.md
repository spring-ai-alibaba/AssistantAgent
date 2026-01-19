# Parameter Options Service Design

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Implement a Parameter Options Service to provide dynamic parameter options from multiple data sources (NL2SQL, Static, HTTP, Enum) for the Planning module's parameter collection.

**Architecture:** SPI-based service with handler delegation pattern. Supports four option sources with unified OptionItem model. Integrates with SessionProvider for automatic parameter collection and provides independent API for direct calls.

**Tech Stack:** Spring Boot 3.4, Spring AI Alibaba, JsonPath (com.jayway.jsonpath), RestTemplate/WebClient, GraalVM, JUnit 5

---

## 1. Overview & Goals

**Purpose:**
Enable the Planning module to dynamically fetch parameter options from multiple data sources instead of hardcoding static enum values.

**Key Features:**
- Multi-source support: NL2SQL (database queries), Static (YAML/JSON), HTTP API (external REST services), Enum values
- Mixed integration: Independent ParameterOptionsService SPI + automatic SessionProvider integration
- Default to NL2SQL when source type not specified
- Caching with configurable TTL
- Graceful error handling and fallback

**Success Criteria:**
- ActionParameter can declare `optionsSource` field
- Parameter collection automatically fetches and presents options
- >70% test coverage on new code
- All four source types working in integration tests

---

## 2. Data Model Design

### OptionsSourceConfig (Wrapper)

**Location:** `assistant-agent-planning-api`

```java
package com.alibaba.assistant.agent.planning.model;

/**
 * Configuration wrapper for parameter option sources.
 * Supports multiple source types with type-safe configuration.
 */
public class OptionsSourceConfig implements Serializable {

    private SourceType type;        // Default: NL2SQL if null
    private String systemId;         // Datasource identifier (for NL2SQL/HTTP)
    private Object config;           // Specific config object (type-dependent)

    public enum SourceType {
        NL2SQL,     // Natural language to SQL query
        STATIC,     // Static configuration list
        HTTP,       // HTTP API call
        ENUM        // Java enum reflection
    }

    // Getters/Setters, equals, hashCode, toString
}
```

### StaticOptionsConfig

**Location:** `assistant-agent-planning-api`

```java
package com.alibaba.assistant.agent.planning.model;

/**
 * Configuration for static option lists.
 */
public class StaticOptionsConfig implements Serializable {

    private List<OptionItem> options;  // Predefined option list

    // Getters/Setters, equals, hashCode, toString
}
```

### HttpOptionsConfig

**Location:** `assistant-agent-planning-api`

```java
package com.alibaba.assistant.agent.planning.model;

/**
 * Configuration for HTTP API-based option sources.
 * Supports full HTTP features with JSONPath extraction.
 */
public class HttpOptionsConfig implements Serializable {

    private String url;                      // API endpoint URL
    private String method = "GET";           // HTTP method (GET/POST/PUT)
    private Map<String, String> headers;     // Request headers
    private String body;                     // Request body (for POST)
    private Integer timeout = 5000;          // Timeout in milliseconds
    private String labelPath;                // JSONPath for label extraction (e.g., "$.data[*].name")
    private String valuePath;                // JSONPath for value extraction (e.g., "$.data[*].id")
    private AuthConfig authentication;       // Authentication configuration

    /**
     * Authentication configuration for HTTP requests.
     */
    public static class AuthConfig implements Serializable {
        private String type;          // BASIC, BEARER, API_KEY
        private String username;      // For BASIC auth
        private String password;      // For BASIC auth
        private String token;         // For BEARER auth
        private String apiKey;        // For API_KEY auth
        private String headerName;    // Header name for API_KEY

        // Getters/Setters
    }

    // Getters/Setters, equals, hashCode, toString
}
```

### OptionItem (Reuse Existing)

**Location:** `assistant-agent-data-api` (already exists from NL2SQL implementation)

```java
package com.alibaba.assistant.agent.data.model.nl2sql;

public class OptionItem implements Serializable {
    private String label;   // Display text
    private String value;   // Actual value

    // Getters/Setters, builder, equals, hashCode, toString
}
```

### ActionParameter Enhancement

**Location:** `assistant-agent-planning-api`

**Modification:**
```java
public class ActionParameter implements Serializable {
    // ... existing fields (name, label, type, required, enumValues, source, etc.)

    /**
     * Dynamic options source configuration.
     * When present, options will be fetched dynamically during parameter collection.
     * If type is null, defaults to NL2SQL.
     */
    private OptionsSourceConfig optionsSource;

    // Add getter/setter
}
```

---

## 3. Service Interface Design

### ParameterOptionsService SPI

**Location:** `assistant-agent-planning-api`

```java
package com.alibaba.assistant.agent.planning.spi;

/**
 * SPI for fetching parameter options from various sources.
 * Implementations should delegate to specific handlers based on source type.
 */
public interface ParameterOptionsService {

    /**
     * Fetch parameter options based on configuration.
     *
     * @param config Options source configuration
     * @return List of option items
     * @throws OptionsSourceException if fetching fails
     */
    List<OptionItem> fetchOptions(OptionsSourceConfig config);

    /**
     * Check if this service supports a specific source type.
     *
     * @param sourceType Source type enum
     * @return true if supported
     */
    boolean supports(SourceType sourceType);

    /**
     * Get service name for logging and identification.
     *
     * @return Service name
     */
    String getName();
}
```

### OptionsSourceHandler (Internal Interface)

**Location:** `assistant-agent-planning-core`

```java
package com.alibaba.assistant.agent.planning.internal;

/**
 * Internal handler interface for specific option source types.
 * Each handler implements fetching logic for one source type.
 */
interface OptionsSourceHandler {

    /**
     * Handle option fetching for specific source type.
     *
     * @param systemId Datasource identifier (nullable)
     * @param specificConfig Type-specific configuration object
     * @return List of option items
     */
    List<OptionItem> handle(String systemId, Object specificConfig);

    /**
     * Source type this handler supports.
     *
     * @return Source type enum
     */
    SourceType supportedType();
}
```

---

## 4. Implementation Components

### Module Structure

**assistant-agent-planning-api:**
- `OptionsSourceConfig` - Main configuration wrapper
- `StaticOptionsConfig` - Static list configuration
- `HttpOptionsConfig` - HTTP API configuration
- `ParameterOptionsService` - SPI interface
- `OptionsSourceException` - Exception for errors

**assistant-agent-planning-core:**
- `DefaultParameterOptionsService` - Main service implementation
- `OptionsSourceHandler` - Internal handler interface
- `Nl2SqlOptionsHandler` - Delegates to Nl2SqlService
- `StaticOptionsHandler` - Returns static list directly
- `HttpOptionsHandler` - Executes HTTP requests with JSONPath extraction
- `EnumOptionsHandler` - Uses Java reflection for enum values
- `ParamCollectionAutoConfiguration` - Conditional bean registration
- `OptionsCache` - Simple cache wrapper

### Handler Implementations

#### Nl2SqlOptionsHandler

```java
@Component
class Nl2SqlOptionsHandler implements OptionsSourceHandler {

    private final Nl2SqlService nl2SqlService;

    @Override
    public List<OptionItem> handle(String systemId, Object specificConfig) {
        Nl2SqlSourceConfig config = (Nl2SqlSourceConfig) specificConfig;

        Nl2SqlRequest request = Nl2SqlRequest.builder()
            .systemId(systemId)
            .query(config.getDescription())
            .build();

        return nl2SqlService.queryForOptions(request,
            config.getLabelColumn(), config.getValueColumn());
    }

    @Override
    public SourceType supportedType() {
        return SourceType.NL2SQL;
    }
}
```

#### StaticOptionsHandler

```java
@Component
class StaticOptionsHandler implements OptionsSourceHandler {

    @Override
    public List<OptionItem> handle(String systemId, Object specificConfig) {
        StaticOptionsConfig config = (StaticOptionsConfig) specificConfig;
        return config.getOptions();
    }

    @Override
    public SourceType supportedType() {
        return SourceType.STATIC;
    }
}
```

#### HttpOptionsHandler

```java
@Component
class HttpOptionsHandler implements OptionsSourceHandler {

    private final RestTemplate restTemplate;
    private final int defaultTimeout;

    @Override
    public List<OptionItem> handle(String systemId, Object specificConfig) {
        HttpOptionsConfig config = (HttpOptionsConfig) specificConfig;

        // Build HTTP request with headers, auth, timeout
        ResponseEntity<String> response = executeHttpRequest(config);

        // Extract data using JSONPath
        List<String> labels = JsonPath.read(response.getBody(), config.getLabelPath());
        List<String> values = JsonPath.read(response.getBody(), config.getValuePath());

        // Combine into OptionItem list
        return zipToOptionItems(labels, values);
    }

    @Override
    public SourceType supportedType() {
        return SourceType.HTTP;
    }
}
```

#### EnumOptionsHandler

```java
@Component
class EnumOptionsHandler implements OptionsSourceHandler {

    @Override
    public List<OptionItem> handle(String systemId, Object specificConfig) {
        // specificConfig should be enum class name string
        String enumClassName = (String) specificConfig;
        Class<?> enumClass = Class.forName(enumClassName);

        // Use reflection to get enum constants
        Object[] constants = enumClass.getEnumConstants();

        return Arrays.stream(constants)
            .map(c -> new OptionItem(c.toString(), c.toString()))
            .collect(Collectors.toList());
    }

    @Override
    public SourceType supportedType() {
        return SourceType.ENUM;
    }
}
```

### DefaultParameterOptionsService

```java
@Service
public class DefaultParameterOptionsService implements ParameterOptionsService {

    private final Map<SourceType, OptionsSourceHandler> handlers;
    private final OptionsCache cache;

    public DefaultParameterOptionsService(List<OptionsSourceHandler> handlerList,
                                         OptionsCache cache) {
        this.handlers = handlerList.stream()
            .collect(Collectors.toMap(OptionsSourceHandler::supportedType, h -> h));
        this.cache = cache;
    }

    @Override
    public List<OptionItem> fetchOptions(OptionsSourceConfig config) {
        // Default to NL2SQL if type not specified
        SourceType type = config.getType();
        if (type == null) {
            type = SourceType.NL2SQL;
            logger.debug("No source type specified, using default: NL2SQL");
        }

        // Check cache
        String cacheKey = buildCacheKey(config);
        List<OptionItem> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Find handler and execute
        OptionsSourceHandler handler = handlers.get(type);
        if (handler == null) {
            throw new OptionsSourceException("No handler found for type: " + type);
        }

        try {
            List<OptionItem> options = handler.handle(config.getSystemId(), config.getConfig());
            cache.put(cacheKey, options);
            return options;
        } catch (Exception e) {
            logger.error("Failed to fetch options: type={}, systemId={}",
                type, config.getSystemId(), e);
            return Collections.emptyList();  // Graceful degradation
        }
    }

    @Override
    public boolean supports(SourceType sourceType) {
        return handlers.containsKey(sourceType);
    }

    @Override
    public String getName() {
        return "DefaultParameterOptionsService";
    }
}
```

### Configuration

**application.yml:**
```yaml
spring.ai.alibaba.codeact.extension.planning:
  param-collection:
    enabled: true
    default-source-type: NL2SQL    # Default when type is null
    cache-ttl: 300000              # 5 minutes cache TTL
    http-timeout: 5000             # Default HTTP timeout in ms
    http-retry-count: 1            # Retry once on HTTP failure
```

---

## 5. Integration with Planning Module

### Parameter Collection Flow

**In SessionProvider implementation:**

```java
public class ParamCollectionSessionImpl implements ParamCollectionSession {

    private final ParameterOptionsService parameterOptionsService;

    @Override
    public void collectParameter(ActionParameter parameter) {
        if (parameter.getOptionsSource() != null) {
            // Dynamic options via ParameterOptionsService
            List<OptionItem> options = parameterOptionsService
                .fetchOptions(parameter.getOptionsSource());

            if (options.isEmpty()) {
                logger.warn("No options fetched for parameter: {}", parameter.getName());
                // Fallback to free text input
                promptForTextInput(parameter);
            } else {
                // Present options to user for selection
                presentOptionsToUser(parameter, options);
            }
        } else if (parameter.getEnumValues() != null) {
            // Static enum values (existing behavior)
            presentEnumOptions(parameter);
        } else {
            // Free text input (existing behavior)
            promptForTextInput(parameter);
        }
    }
}
```

### Example ActionParameter with optionsSource

**NL2SQL Source:**
```java
ActionParameter param = ActionParameter.builder()
    .name("departmentId")
    .label("Select Department")
    .type("string")
    .required(true)
    .optionsSource(OptionsSourceConfig.builder()
        .type(SourceType.NL2SQL)  // Or null to use default
        .systemId("hr-database")
        .config(Nl2SqlSourceConfig.builder()
            .description("Get all active departments with their names and IDs")
            .labelColumn("name")
            .valueColumn("id")
            .build())
        .build())
    .build();
```

**HTTP API Source:**
```java
ActionParameter param = ActionParameter.builder()
    .name("region")
    .label("Select Region")
    .type("string")
    .required(true)
    .optionsSource(OptionsSourceConfig.builder()
        .type(SourceType.HTTP)
        .systemId("external-api")
        .config(HttpOptionsConfig.builder()
            .url("https://api.example.com/regions")
            .method("GET")
            .timeout(3000)
            .labelPath("$.data[*].regionName")
            .valuePath("$.data[*].regionCode")
            .build())
        .build())
    .build();
```

---

## 6. Error Handling & Caching

### Default Source Strategy

When `type` is null in `OptionsSourceConfig`, default to `NL2SQL`:

```java
SourceType type = config.getType();
if (type == null) {
    type = SourceType.NL2SQL;
    logger.debug("DefaultParameterOptionsService#fetchOptions - Using default source type: NL2SQL");
}
```

### Error Handling

**HTTP Failures:**
- Retry once on network error (configurable)
- Log error and return empty list (graceful degradation)
- Timeout after configured duration (default 5s)

**NL2SQL Errors:**
- Log error with query details
- Return empty list (user can input manually)

**Invalid JSONPath:**
- Throw `OptionsSourceException` (fail fast during configuration)
- Include helpful error message with path and response structure

**Enum Reflection Errors:**
- Throw `OptionsSourceException` if class not found
- Validate that class is actually an enum

**General Pattern:**
```java
try {
    return handler.handle(systemId, config);
} catch (Exception e) {
    logger.error("DefaultParameterOptionsService#fetchOptions - Failed: type={}, systemId={}, error={}",
        type, systemId, e.getMessage(), e);
    return Collections.emptyList();  // Graceful degradation
}
```

### Caching Strategy

**Cache Implementation:**

Reuse existing cache infrastructure or create lightweight wrapper:

```java
public class OptionsCache {
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public List<OptionItem> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.value;
    }

    public void put(String key, List<OptionItem> value) {
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + ttlMillis));
    }

    private static class CacheEntry {
        final List<OptionItem> value;
        final long expirationTime;

        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}
```

**Cache Key Construction:**
```java
private String buildCacheKey(OptionsSourceConfig config) {
    return String.format("%s:%s:%s",
        config.getType(),
        config.getSystemId(),
        hashConfig(config.getConfig()));
}
```

**Configuration:**
```yaml
spring.ai.alibaba.codeact.extension.planning.param-collection:
  cache-ttl: 300000          # 5 minutes default
  cache-enabled: true
```

---

## 7. Testing Strategy

### Unit Tests

#### Handler Tests

**Nl2SqlOptionsHandlerTest:**
```java
@Test
void shouldDelegateToNl2SqlService() {
    // Mock Nl2SqlService
    when(nl2SqlService.queryForOptions(any(), eq("name"), eq("id")))
        .thenReturn(List.of(new OptionItem("Dept A", "1")));

    // Execute
    List<OptionItem> result = handler.handle("hr-db", nl2SqlConfig);

    // Verify
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getLabel()).isEqualTo("Dept A");
    verify(nl2SqlService).queryForOptions(any(), eq("name"), eq("id"));
}
```

**StaticOptionsHandlerTest:**
```java
@Test
void shouldReturnStaticList() {
    StaticOptionsConfig config = new StaticOptionsConfig();
    config.setOptions(List.of(
        new OptionItem("Option 1", "val1"),
        new OptionItem("Option 2", "val2")
    ));

    List<OptionItem> result = handler.handle(null, config);

    assertThat(result).hasSize(2);
}
```

**HttpOptionsHandlerTest:**
```java
@Test
void shouldExtractDataViaJsonPath() {
    // Mock HTTP response
    String jsonResponse = "{\"data\": [{\"name\": \"A\", \"id\": \"1\"}]}";
    when(restTemplate.exchange(any(), eq(String.class)))
        .thenReturn(ResponseEntity.ok(jsonResponse));

    HttpOptionsConfig config = createHttpConfig("$.data[*].name", "$.data[*].id");

    List<OptionItem> result = handler.handle(null, config);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getLabel()).isEqualTo("A");
}

@Test
void shouldRetryOnNetworkFailure() {
    // First call fails, second succeeds
    when(restTemplate.exchange(any(), eq(String.class)))
        .thenThrow(new RestClientException("Network error"))
        .thenReturn(ResponseEntity.ok("{\"data\": []}"));

    List<OptionItem> result = handler.handle(null, httpConfig);

    verify(restTemplate, times(2)).exchange(any(), eq(String.class));
}
```

**EnumOptionsHandlerTest:**
```java
@Test
void shouldConvertEnumToOptions() {
    String enumClassName = "com.example.Status";

    List<OptionItem> result = handler.handle(null, enumClassName);

    assertThat(result).isNotEmpty();
    assertThat(result.get(0).getLabel()).isIn("ACTIVE", "INACTIVE", "PENDING");
}
```

#### Service Tests

**DefaultParameterOptionsServiceTest:**
```java
@Test
void shouldDefaultToNl2SqlWhenTypeIsNull() {
    OptionsSourceConfig config = new OptionsSourceConfig();
    config.setType(null);  // Type not specified
    config.setSystemId("test-db");
    config.setConfig(nl2SqlConfig);

    service.fetchOptions(config);

    verify(nl2SqlHandler).handle(eq("test-db"), eq(nl2SqlConfig));
}

@Test
void shouldUseCacheOnSecondCall() {
    OptionsSourceConfig config = createNl2SqlConfig();

    // First call - should hit handler
    service.fetchOptions(config);
    verify(nl2SqlHandler, times(1)).handle(any(), any());

    // Second call - should hit cache
    service.fetchOptions(config);
    verify(nl2SqlHandler, times(1)).handle(any(), any());  // Still 1 time
}

@Test
void shouldReturnEmptyListOnHandlerException() {
    when(nl2SqlHandler.handle(any(), any()))
        .thenThrow(new RuntimeException("Database error"));

    List<OptionItem> result = service.fetchOptions(nl2SqlConfig);

    assertThat(result).isEmpty();
}
```

### Integration Tests

**ParamCollectionIntegrationTest:**
```java
@SpringBootTest
class ParamCollectionIntegrationTest {

    @Autowired
    private SessionProvider sessionProvider;

    @Autowired
    private ParameterOptionsService parameterOptionsService;

    @Test
    void shouldFetchDynamicOptionsViaNl2Sql() {
        // Create ActionParameter with NL2SQL source
        ActionParameter param = createParamWithNl2SqlSource(
            "departmentId",
            "Get all departments",
            "name",
            "id"
        );

        // Create session and collect parameter
        ParamCollectionSession session = sessionProvider.createSession(...);
        session.collectParameter(param);

        // Verify options fetched and presented
        List<OptionItem> options = session.getCollectedOptions();
        assertThat(options).isNotEmpty();
    }

    @Test
    void shouldFetchFromHttpApi() {
        // Start mock HTTP server
        mockServer.expect(requestTo("http://api.test.com/data"))
            .andRespond(withSuccess("{\"items\": [{\"label\": \"A\", \"value\": \"1\"}]}",
                MediaType.APPLICATION_JSON));

        ActionParameter param = createParamWithHttpSource(...);

        List<OptionItem> options = parameterOptionsService.fetchOptions(
            param.getOptionsSource());

        assertThat(options).hasSize(1);
    }

    @Test
    void shouldFallbackToEmptyOnTimeout() {
        // Configure HTTP source with unreachable URL
        HttpOptionsConfig config = HttpOptionsConfig.builder()
            .url("http://unreachable.test.com")
            .timeout(100)  // Very short timeout
            .build();

        OptionsSourceConfig sourceConfig = OptionsSourceConfig.builder()
            .type(SourceType.HTTP)
            .config(config)
            .build();

        List<OptionItem> result = parameterOptionsService.fetchOptions(sourceConfig);

        assertThat(result).isEmpty();  // Graceful degradation
    }

    @Test
    void shouldUseStaticOptions() {
        StaticOptionsConfig staticConfig = new StaticOptionsConfig();
        staticConfig.setOptions(List.of(
            new OptionItem("Yes", "true"),
            new OptionItem("No", "false")
        ));

        OptionsSourceConfig sourceConfig = OptionsSourceConfig.builder()
            .type(SourceType.STATIC)
            .config(staticConfig)
            .build();

        List<OptionItem> result = parameterOptionsService.fetchOptions(sourceConfig);

        assertThat(result).hasSize(2);
    }
}
```

### Test Coverage Target

**New Code Coverage:** >70%

**Critical Paths to Cover:**
- All four handler implementations
- Default to NL2SQL behavior
- Cache hit/miss scenarios
- Error handling and graceful degradation
- JSONPath extraction with various response structures
- HTTP retry logic
- Timeout behavior

### Manual Testing Checklist

- [ ] NL2SQL source with valid database query (multiple rows)
- [ ] NL2SQL source with empty result set
- [ ] Static options from configuration
- [ ] HTTP API source with real external endpoint
- [ ] HTTP API with authentication (Bearer token)
- [ ] HTTP API with JSONPath extraction (nested data)
- [ ] Enum values from Java enum class
- [ ] Cache behavior (first fetch slow, second fast)
- [ ] Cache expiration after TTL
- [ ] Error scenarios (database timeout, HTTP 500, invalid JSONPath)
- [ ] Default to NL2SQL when type is null
- [ ] Parameter collection integration (options presented to user)

---

## Implementation Phases

### Phase 1: Core Models & Interfaces (2-3 days)
- Create OptionsSourceConfig, StaticOptionsConfig, HttpOptionsConfig
- Create ParameterOptionsService SPI interface
- Create OptionsSourceHandler internal interface
- Modify ActionParameter to add optionsSource field
- Write unit tests for models

### Phase 2: Handler Implementations (3-4 days)
- Implement Nl2SqlOptionsHandler
- Implement StaticOptionsHandler
- Implement HttpOptionsHandler (with JsonPath)
- Implement EnumOptionsHandler
- Write unit tests for each handler

### Phase 3: Service & Caching (2-3 days)
- Implement DefaultParameterOptionsService
- Implement OptionsCache
- Add default NL2SQL logic
- Add error handling
- Write service unit tests

### Phase 4: Integration (2-3 days)
- Integrate with SessionProvider
- Update ParamCollectionSessionImpl
- Add configuration properties
- Write integration tests

### Phase 5: Testing & Documentation (1-2 days)
- Achieve >70% coverage
- Manual testing of all scenarios
- Update CLAUDE.md with new extension point
- Create usage examples

**Total Estimate:** 10-15 days

---

## Dependencies

**External Libraries:**
- `com.jayway.jsonpath:json-path` - For HTTP response data extraction
- Spring Web (RestTemplate/WebClient) - Already available
- Spring Boot Test - Already available

**Internal Dependencies:**
- `assistant-agent-data` module (Nl2SqlService, OptionItem)
- `assistant-agent-planning-api` (ActionParameter, SessionProvider)

---

## Future Enhancements

**Out of Scope for Initial Implementation:**
- GraphQL API source support
- Database direct query source (bypass NL2SQL)
- Redis cache backend (current: in-memory)
- Option dependency chains (option B depends on selection of option A)
- Option search/filtering for large result sets
- Lazy loading with pagination for HTTP sources

These can be added as follow-up tasks based on user feedback.
