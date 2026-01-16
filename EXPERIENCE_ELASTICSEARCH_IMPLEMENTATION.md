# Experience Module Elasticsearch Implementation

## Overview

This document describes the Elasticsearch storage implementation for the Experience module. The migration moves from InMemory storage to Elasticsearch, enabling full-text search, semantic search capabilities, and persistent experience storage across application restarts.

## Why Elasticsearch?

The Experience module requires:
- **Full-text search**: Search experiences by title and content with fuzzy matching
- **Semantic search**: Vector-based similarity search for finding related experiences (optional)
- **Complex filtering**: Filter by type, scope, owner, project, language, tags
- **Scalability**: Handle large volumes of experiences efficiently
- **High availability**: Distributed storage with replication

Elasticsearch is ideal for these requirements, unlike MySQL which is better suited for structured relational data.

## Architecture

### Components Created

1. **ExperienceDocument** (`persistence/document/ExperienceDocument.java`)
   - Elasticsearch document entity with Spring Data Elasticsearch annotations
   - Stores simple fields as searchable/filterable fields
   - Stores complex objects (artifact, fastIntentConfig, metadata) as JSON strings
   - Includes optional vector embedding field for semantic search (1024 dimensions)

2. **ElasticsearchExperienceRepository** (`persistence/repository/ElasticsearchExperienceRepository.java`)
   - Implements `ExperienceRepository` interface
   - Full CRUD operations using ElasticsearchClient
   - Bulk save operations for batch processing
   - JSON serialization for complex objects using ObjectMapper

3. **ElasticsearchExperienceProvider** (`persistence/provider/ElasticsearchExperienceProvider.java`)
   - Implements `ExperienceProvider` interface
   - Advanced search with multi-field text queries
   - Boolean query composition with filters (type, scope, owner, project, language, tags)
   - Configurable sorting (by creation date, update date, or relevance score)
   - Fuzzy matching with AUTO fuzziness

4. **ElasticsearchConfig** (added to `ExperienceExtensionProperties.java`)
   - Configuration properties for Elasticsearch connection and indexing
   - Includes semantic search toggles and vector dimension configuration

5. **Index Mapping** (`resources/elasticsearch/experience-index-mapping.json`)
   - Defines field mappings with ICU analyzer for multilingual support
   - Title and content fields with full-text indexing
   - Dense vector field for semantic search
   - Complex objects stored but not indexed

## Configuration

### application.yml

```yaml
spring.ai.alibaba.codeact.extension.experience:
  enabled: true
  # Storage configuration
  in-memory:
    enabled: false  # Disable InMemory when using Elasticsearch
  elasticsearch:
    enabled: true  # Enable Elasticsearch storage
    index-name: experiences
    enable-semantic-search: false  # Set to true to enable vector search
    vector-dimension: 1024
    hosts: localhost:9200
    username: ${ES_USERNAME:elastic}
    password: ${ES_PASSWORD:XWuH_rhjfz5ZD+Brrn0D}
  logging:
    enabled: true

# Elasticsearch Connection
spring.elasticsearch:
  uris: http://localhost:9200
  username: ${ES_USERNAME:elastic}
  password: ${ES_PASSWORD:XWuH_rhjfz5ZD+Brrn0D}
  connection-timeout: 5s
  socket-timeout: 30s
```

### Configuration Options

| Property | Default | Description |
|----------|---------|-------------|
| `elasticsearch.enabled` | false | Enable Elasticsearch storage |
| `elasticsearch.index-name` | experiences | Name of the Elasticsearch index |
| `elasticsearch.enable-semantic-search` | false | Enable vector-based semantic search |
| `elasticsearch.vector-dimension` | 1024 | Dimension of embedding vectors |
| `elasticsearch.hosts` | localhost:9200 | Comma-separated Elasticsearch hosts |
| `elasticsearch.username` | - | Authentication username |
| `elasticsearch.password` | - | Authentication password |
| `in-memory.enabled` | true | Enable InMemory storage (disable when using ES) |

## Setup Instructions

### 1. Start Elasticsearch

Using Docker:

```bash
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  docker.elastic.co/elasticsearch/elasticsearch:8.11.0
```

For production, enable security:

```bash
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "ELASTIC_PASSWORD=your-password" \
  docker.elastic.co/elasticsearch/elasticsearch:8.11.0
```

### 2. Install ICU Analysis Plugin

The index mapping uses the ICU analyzer for multilingual text analysis:

```bash
docker exec -it elasticsearch \
  bin/elasticsearch-plugin install analysis-icu

# Restart Elasticsearch
docker restart elasticsearch
```

### 3. Create Index with Mapping

Use the provided mapping file to create the index:

```bash
curl -X PUT "http://localhost:9200/experiences" \
  -H "Content-Type: application/json" \
  -d @assistant-agent-extensions/src/main/resources/elasticsearch/experience-index-mapping.json
```

Or use the Elasticsearch REST client in your application to create the index programmatically.

### 4. Update Dependencies

Ensure your `pom.xml` includes the Elasticsearch client dependencies:

```xml
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.11.0</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-elasticsearch</artifactId>
</dependency>
```

### 5. Configure Application

Update `application.yml` as shown above, then start the application:

```bash
cd assistant-agent-start
mvn spring-boot:run
```

## Usage Examples

### Storing Experiences

```java
@Autowired
private ExperienceRepository experienceRepository;

// Create a new experience
Experience experience = new Experience();
experience.setId(UUID.randomUUID().toString());
experience.setType(ExperienceType.REACT_DECISION);
experience.setTitle("User authentication flow");
experience.setContent("Handled user login with JWT tokens...");
experience.setScope(ExperienceScope.USER);
experience.setOwnerId("user-123");
experience.setLanguage("en");
experience.setTags(Set.of("authentication", "security", "jwt"));
experience.setCreatedAt(Instant.now());
experience.setUpdatedAt(Instant.now());

// Save to Elasticsearch
experienceRepository.save(experience);
```

### Querying Experiences

```java
@Autowired
private ExperienceProvider experienceProvider;

// Build query
ExperienceQuery query = ExperienceQuery.builder()
    .type(ExperienceType.REACT_DECISION)
    .scope(ExperienceScope.USER)
    .text("authentication login")  // Full-text search
    .tags(Set.of("security"))
    .limit(10)
    .orderBy(OrderBy.SCORE)  // Sort by relevance
    .build();

// Execute query
List<Experience> results = experienceProvider.query(query, context);
```

### Batch Operations

```java
// Batch save multiple experiences
List<Experience> experiences = Arrays.asList(exp1, exp2, exp3);
experienceRepository.batchSave(experiences);
```

## Search Capabilities

### Full-Text Search

Searches across title and content fields with:
- **Multi-match query**: Searches multiple fields simultaneously
- **Field boosting**: Title matches are weighted 2x higher than content
- **Fuzzy matching**: AUTO fuzziness for typo tolerance
- **ICU analyzer**: Multilingual tokenization and normalization

### Boolean Filtering

Combine multiple filters:
- **Type**: Filter by experience type (REACT_DECISION, CODE_GENERATION, COMMON_SENSE)
- **Scope**: Filter by scope (USER, PROJECT, ORGANIZATION, GLOBAL)
- **Owner**: Filter by owner ID
- **Project**: Filter by project ID
- **Language**: Filter by programming language
- **Tags**: Filter by tags (AND logic - all tags must match)

### Sorting Options

- **SCORE**: Sort by relevance score (default for text queries)
- **CREATED_AT**: Sort by creation timestamp (descending)
- **UPDATED_AT**: Sort by update timestamp (descending)

## Semantic Search (Optional)

To enable vector-based semantic search:

### 1. Enable in Configuration

```yaml
spring.ai.alibaba.codeact.extension.experience:
  elasticsearch:
    enable-semantic-search: true
    vector-dimension: 1024
```

### 2. Generate Embeddings

Use a text embedding model (e.g., DashScope text-embedding-v3) to generate vector embeddings for experience content:

```java
@Autowired
private EmbeddingModel embeddingModel;

// Generate embedding
String text = experience.getTitle() + " " + experience.getContent();
List<Double> embedding = embeddingModel.embed(text);

// Convert to float array
float[] embeddingArray = embedding.stream()
    .mapToDouble(Double::doubleValue)
    .toArray();

// Store with experience (requires extending ExperienceDocument)
experienceDocument.setEmbedding(embeddingArray);
```

### 3. Implement KNN Search

Extend `ElasticsearchExperienceProvider` to support KNN (k-nearest neighbors) queries using the stored embeddings for similarity-based retrieval.

## Verification

### Check Index Health

```bash
# Get index stats
curl -X GET "http://localhost:9200/experiences/_stats?pretty"

# Get index mapping
curl -X GET "http://localhost:9200/experiences/_mapping?pretty"

# Count documents
curl -X GET "http://localhost:9200/experiences/_count?pretty"
```

### Test Search

```bash
# Simple text search
curl -X POST "http://localhost:9200/experiences/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "multi_match": {
        "query": "authentication",
        "fields": ["title^2", "content"]
      }
    }
  }'
```

### Application Logs

Enable debug logging to verify Elasticsearch operations:

```yaml
logging:
  level:
    com.alibaba.assistant.agent.extension.experience: DEBUG
```

Look for logs like:
```
ElasticsearchExperienceRepository#save - reason=experience saved, id=..., result=CREATED
ElasticsearchExperienceProvider#query - reason=query completed, type=REACT_DECISION, text=authentication, count=5
```

## Performance Considerations

### Indexing Performance

- **Bulk operations**: Use `batchSave()` for inserting multiple experiences
- **Refresh interval**: Default 1s, increase for higher throughput
- **Replica count**: Set to 0 for development, ≥1 for production

### Search Performance

- **Result size**: Use pagination, default limit is reasonable (5-10 items)
- **Field selection**: Only retrieve needed fields to reduce transfer size
- **Cache warming**: Frequently used queries benefit from Elasticsearch's query cache

### Resource Usage

- **Memory**: Elasticsearch recommends 50% of system memory (up to 32GB)
- **Disk**: Plan for 2-3x the raw data size with replicas
- **CPU**: Scale horizontally by adding nodes for high query load

## Troubleshooting

### ElasticsearchClient Bean Not Found

Ensure Spring Boot autoconfiguration is enabled and dependencies are included:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

### Connection Refused

Check Elasticsearch is running:

```bash
curl -X GET "http://localhost:9200"
```

### Index Creation Failed

The application doesn't auto-create indices. Create manually using the provided mapping file.

### ICU Analyzer Not Found

Install the analysis-icu plugin:

```bash
docker exec -it elasticsearch \
  bin/elasticsearch-plugin install analysis-icu
docker restart elasticsearch
```

### JSON Serialization Errors

Ensure complex objects (artifact, fastIntentConfig, metadata) are properly serialized. Check ObjectMapper configuration.

## Fallback Behavior

The configuration includes automatic fallback to InMemory storage if:
- ElasticsearchClient bean is not found
- ObjectMapper bean is not found (creates default instance)
- Elasticsearch connection fails

This ensures the application remains functional during development or when Elasticsearch is unavailable.

## Security Best Practices

1. **Authentication**: Enable xpack.security in production
2. **HTTPS**: Use TLS for Elasticsearch communication
3. **Network isolation**: Run Elasticsearch in a private network
4. **Access control**: Use role-based access control (RBAC)
5. **Audit logging**: Enable audit logs for compliance

## Next Steps

1. **Semantic Search**: Implement vector embedding generation and KNN search
2. **Index Lifecycle Management (ILM)**: Configure automatic index rollover and deletion
3. **Monitoring**: Set up Kibana for index visualization and monitoring
4. **Backup**: Implement snapshot and restore for disaster recovery
5. **Performance Tuning**: Optimize index settings based on workload patterns

## References

- [Elasticsearch Java Client Documentation](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/index.html)
- [Spring Data Elasticsearch](https://docs.spring.io/spring-data/elasticsearch/docs/current/reference/html/)
- [ICU Analysis Plugin](https://www.elastic.co/guide/en/elasticsearch/plugins/current/analysis-icu.html)
- [Dense Vector Field Type](https://www.elastic.co/guide/en/elasticsearch/reference/current/dense-vector.html)
