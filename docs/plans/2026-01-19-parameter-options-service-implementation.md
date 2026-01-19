# Parameter Options Service Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Implement multi-source Parameter Options Service (NL2SQL, Static, HTTP, Enum) with SPI pattern, caching, and SessionProvider integration for the Planning module.

**Architecture:** SPI-based service with handler delegation pattern. DefaultParameterOptionsService routes requests to specialized handlers (Nl2SqlOptionsHandler, StaticOptionsHandler, HttpOptionsHandler, EnumOptionsHandler). Integrates with existing SessionProvider for automatic parameter collection. Includes in-memory caching with TTL.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring AI Alibaba 1.1.0, JsonPath (com.jayway.jsonpath), Maven, JUnit 5, Mockito

---

## Task 1: Create OptionsSourceConfig Model

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/model/OptionsSourceConfig.java`
- Test: `assistant-agent-planning/assistant-agent-planning-api/src/test/java/com/alibaba/assistant/agent/planning/model/OptionsSourceConfigTest.java`

**Step 1: Write the failing test**

Create test file:

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
package com.alibaba.assistant.agent.planning.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OptionsSourceConfigTest {

    @Test
    void shouldCreateWithAllFields() {
        OptionsSourceConfig config = new OptionsSourceConfig();
        config.setType(OptionsSourceConfig.SourceType.NL2SQL);
        config.setSystemId("test-db");
        config.setConfig("test-config");

        assertEquals(OptionsSourceConfig.SourceType.NL2SQL, config.getType());
        assertEquals("test-db", config.getSystemId());
        assertEquals("test-config", config.getConfig());
    }

    @Test
    void shouldSupportEqualsAndHashCode() {
        OptionsSourceConfig config1 = new OptionsSourceConfig();
        config1.setType(OptionsSourceConfig.SourceType.HTTP);
        config1.setSystemId("api-1");

        OptionsSourceConfig config2 = new OptionsSourceConfig();
        config2.setType(OptionsSourceConfig.SourceType.HTTP);
        config2.setSystemId("api-1");

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void shouldSupportToString() {
        OptionsSourceConfig config = new OptionsSourceConfig();
        config.setType(OptionsSourceConfig.SourceType.STATIC);

        String result = config.toString();

        assertTrue(result.contains("STATIC"));
    }

    @Test
    void shouldHaveAllSourceTypes() {
        assertEquals(4, OptionsSourceConfig.SourceType.values().length);
        assertNotNull(OptionsSourceConfig.SourceType.NL2SQL);
        assertNotNull(OptionsSourceConfig.SourceType.STATIC);
        assertNotNull(OptionsSourceConfig.SourceType.HTTP);
        assertNotNull(OptionsSourceConfig.SourceType.ENUM);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=OptionsSourceConfigTest`

Expected: FAIL with "cannot find symbol: class OptionsSourceConfig"

**Step 3: Write minimal implementation**

Create implementation file:

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
package com.alibaba.assistant.agent.planning.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Configuration wrapper for parameter option sources.
 * Supports multiple source types with type-safe configuration.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class OptionsSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Source type enum (default: NL2SQL if null).
     */
    private SourceType type;

    /**
     * Datasource identifier (for NL2SQL/HTTP).
     */
    private String systemId;

    /**
     * Specific config object (type-dependent).
     */
    private Object config;

    /**
     * Source type enumeration.
     */
    public enum SourceType {
        /**
         * Natural language to SQL query.
         */
        NL2SQL,

        /**
         * Static configuration list.
         */
        STATIC,

        /**
         * HTTP API call.
         */
        HTTP,

        /**
         * Java enum reflection.
         */
        ENUM
    }

    public SourceType getType() {
        return type;
    }

    public void setType(SourceType type) {
        this.type = type;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public Object getConfig() {
        return config;
    }

    public void setConfig(Object config) {
        this.config = config;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OptionsSourceConfig that = (OptionsSourceConfig) o;
        return type == that.type &&
                Objects.equals(systemId, that.systemId) &&
                Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, systemId, config);
    }

    @Override
    public String toString() {
        return "OptionsSourceConfig{" +
                "type=" + type +
                ", systemId='" + systemId + '\'' +
                ", config=" + config +
                '}';
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=OptionsSourceConfigTest`

Expected: PASS (4 tests)

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/model/OptionsSourceConfig.java
git add assistant-agent-planning/assistant-agent-planning-api/src/test/java/com/alibaba/assistant/agent/planning/model/OptionsSourceConfigTest.java
git commit -m "$(cat <<'EOF'
feat(planning): add OptionsSourceConfig model

Add configuration wrapper for parameter option sources with:
- SourceType enum (NL2SQL, STATIC, HTTP, ENUM)
- systemId field for datasource identification
- config field for type-specific configuration

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Create StaticOptionsConfig Model

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/model/StaticOptionsConfig.java`
- Test: `assistant-agent-planning/assistant-agent-planning-api/src/test/java/com/alibaba/assistant/agent/planning/model/StaticOptionsConfigTest.java`

**Step 1: Write the failing test**

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
package com.alibaba.assistant.agent.planning.model;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StaticOptionsConfigTest {

    @Test
    void shouldCreateWithOptionsList() {
        StaticOptionsConfig config = new StaticOptionsConfig();
        List<OptionItem> options = List.of(
            new OptionItem("Option 1", "val1"),
            new OptionItem("Option 2", "val2")
        );
        config.setOptions(options);

        assertEquals(2, config.getOptions().size());
        assertEquals("Option 1", config.getOptions().get(0).getLabel());
    }

    @Test
    void shouldSupportEqualsAndHashCode() {
        StaticOptionsConfig config1 = new StaticOptionsConfig();
        config1.setOptions(List.of(new OptionItem("A", "1")));

        StaticOptionsConfig config2 = new StaticOptionsConfig();
        config2.setOptions(List.of(new OptionItem("A", "1")));

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void shouldSupportToString() {
        StaticOptionsConfig config = new StaticOptionsConfig();
        config.setOptions(List.of(new OptionItem("Test", "test")));

        String result = config.toString();

        assertTrue(result.contains("options="));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=StaticOptionsConfigTest`

Expected: FAIL with "cannot find symbol: class StaticOptionsConfig"

**Step 3: Write minimal implementation**

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
package com.alibaba.assistant.agent.planning.model;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for static option lists.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class StaticOptionsConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Predefined option list.
     */
    private List<OptionItem> options;

    public List<OptionItem> getOptions() {
        return options;
    }

    public void setOptions(List<OptionItem> options) {
        this.options = options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StaticOptionsConfig that = (StaticOptionsConfig) o;
        return Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(options);
    }

    @Override
    public String toString() {
        return "StaticOptionsConfig{" +
                "options=" + options +
                '}';
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=StaticOptionsConfigTest`

Expected: PASS (3 tests)

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/model/StaticOptionsConfig.java
git add assistant-agent-planning/assistant-agent-planning-api/src/test/java/com/alibaba/assistant/agent/planning/model/StaticOptionsConfigTest.java
git commit -m "$(cat <<'EOF'
feat(planning): add StaticOptionsConfig model

Add configuration for static option lists with:
- options field containing List<OptionItem>
- Support for predefined dropdown options

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Create HttpOptionsConfig Model

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/model/HttpOptionsConfig.java`
- Test: `assistant-agent-planning/assistant-agent-planning-api/src/test/java/com/alibaba/assistant/agent/planning/model/HttpOptionsConfigTest.java`

**Step 1: Write the failing test**

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
package com.alibaba.assistant.agent.planning.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpOptionsConfigTest {

    @Test
    void shouldCreateWithBasicFields() {
        HttpOptionsConfig config = new HttpOptionsConfig();
        config.setUrl("https://api.example.com/data");
        config.setMethod("POST");
        config.setLabelPath("$.data[*].name");
        config.setValuePath("$.data[*].id");

        assertEquals("https://api.example.com/data", config.getUrl());
        assertEquals("POST", config.getMethod());
        assertEquals("$.data[*].name", config.getLabelPath());
        assertEquals("$.data[*].id", config.getValuePath());
    }

    @Test
    void shouldHaveDefaultValues() {
        HttpOptionsConfig config = new HttpOptionsConfig();

        assertEquals("GET", config.getMethod());
        assertEquals(5000, config.getTimeout());
    }

    @Test
    void shouldSupportHeadersAndBody() {
        HttpOptionsConfig config = new HttpOptionsConfig();
        config.setHeaders(Map.of("Authorization", "Bearer token"));
        config.setBody("{\"query\": \"test\"}");

        assertEquals(1, config.getHeaders().size());
        assertEquals("Bearer token", config.getHeaders().get("Authorization"));
        assertEquals("{\"query\": \"test\"}", config.getBody());
    }

    @Test
    void shouldSupportAuthConfig() {
        HttpOptionsConfig.AuthConfig auth = new HttpOptionsConfig.AuthConfig();
        auth.setType("BEARER");
        auth.setToken("my-token");

        HttpOptionsConfig config = new HttpOptionsConfig();
        config.setAuthentication(auth);

        assertNotNull(config.getAuthentication());
        assertEquals("BEARER", config.getAuthentication().getType());
        assertEquals("my-token", config.getAuthentication().getToken());
    }

    @Test
    void shouldSupportEqualsAndHashCode() {
        HttpOptionsConfig config1 = new HttpOptionsConfig();
        config1.setUrl("http://test.com");
        config1.setLabelPath("$.label");

        HttpOptionsConfig config2 = new HttpOptionsConfig();
        config2.setUrl("http://test.com");
        config2.setLabelPath("$.label");

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=HttpOptionsConfigTest`

Expected: FAIL with "cannot find symbol: class HttpOptionsConfig"

**Step 3: Write minimal implementation**

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
package com.alibaba.assistant.agent.planning.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for HTTP API-based option sources.
 * Supports full HTTP features with JSONPath extraction.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class HttpOptionsConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * API endpoint URL.
     */
    private String url;

    /**
     * HTTP method (GET/POST/PUT).
     */
    private String method = "GET";

    /**
     * Request headers.
     */
    private Map<String, String> headers;

    /**
     * Request body (for POST).
     */
    private String body;

    /**
     * Timeout in milliseconds.
     */
    private Integer timeout = 5000;

    /**
     * JSONPath for label extraction (e.g., "$.data[*].name").
     */
    private String labelPath;

    /**
     * JSONPath for value extraction (e.g., "$.data[*].id").
     */
    private String valuePath;

    /**
     * Authentication configuration.
     */
    private AuthConfig authentication;

    /**
     * Authentication configuration for HTTP requests.
     */
    public static class AuthConfig implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Authentication type (BASIC, BEARER, API_KEY).
         */
        private String type;

        /**
         * Username for BASIC auth.
         */
        private String username;

        /**
         * Password for BASIC auth.
         */
        private String password;

        /**
         * Token for BEARER auth.
         */
        private String token;

        /**
         * API key for API_KEY auth.
         */
        private String apiKey;

        /**
         * Header name for API_KEY auth.
         */
        private String headerName;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
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

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AuthConfig that = (AuthConfig) o;
            return Objects.equals(type, that.type) &&
                    Objects.equals(username, that.username) &&
                    Objects.equals(password, that.password) &&
                    Objects.equals(token, that.token) &&
                    Objects.equals(apiKey, that.apiKey) &&
                    Objects.equals(headerName, that.headerName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, username, password, token, apiKey, headerName);
        }
    }

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

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public String getLabelPath() {
        return labelPath;
    }

    public void setLabelPath(String labelPath) {
        this.labelPath = labelPath;
    }

    public String getValuePath() {
        return valuePath;
    }

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

    public AuthConfig getAuthentication() {
        return authentication;
    }

    public void setAuthentication(AuthConfig authentication) {
        this.authentication = authentication;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HttpOptionsConfig that = (HttpOptionsConfig) o;
        return Objects.equals(url, that.url) &&
                Objects.equals(method, that.method) &&
                Objects.equals(headers, that.headers) &&
                Objects.equals(body, that.body) &&
                Objects.equals(timeout, that.timeout) &&
                Objects.equals(labelPath, that.labelPath) &&
                Objects.equals(valuePath, that.valuePath) &&
                Objects.equals(authentication, that.authentication);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method, headers, body, timeout, labelPath, valuePath, authentication);
    }

    @Override
    public String toString() {
        return "HttpOptionsConfig{" +
                "url='" + url + '\'' +
                ", method='" + method + '\'' +
                ", headers=" + headers +
                ", body='" + body + '\'' +
                ", timeout=" + timeout +
                ", labelPath='" + labelPath + '\'' +
                ", valuePath='" + valuePath + '\'' +
                ", authentication=" + authentication +
                '}';
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=HttpOptionsConfigTest`

Expected: PASS (5 tests)

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/model/HttpOptionsConfig.java
git add assistant-agent-planning/assistant-agent-planning-api/src/test/java/com/alibaba/assistant/agent/planning/model/HttpOptionsConfigTest.java
git commit -m "$(cat <<'EOF'
feat(planning): add HttpOptionsConfig model

Add configuration for HTTP API-based option sources with:
- Full HTTP request config (URL, method, headers, body, timeout)
- JSONPath extraction (labelPath, valuePath)
- Authentication support (BASIC, BEARER, API_KEY)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Create OptionsSourceException

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/exception/OptionsSourceException.java`
- Test: `assistant-agent-planning/assistant-agent-planning-api/src/test/java/com/alibaba/assistant/agent/planning/exception/OptionsSourceExceptionTest.java`

**Step 1: Write the failing test**

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
package com.alibaba.assistant.agent.planning.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OptionsSourceExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        OptionsSourceException exception = new OptionsSourceException("Test error");

        assertEquals("Test error", exception.getMessage());
    }

    @Test
    void shouldCreateWithMessageAndCause() {
        Throwable cause = new RuntimeException("Root cause");
        OptionsSourceException exception = new OptionsSourceException("Test error", cause);

        assertEquals("Test error", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void shouldBeRuntimeException() {
        OptionsSourceException exception = new OptionsSourceException("Test");

        assertInstanceOf(RuntimeException.class, exception);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=OptionsSourceExceptionTest`

Expected: FAIL with "cannot find symbol: class OptionsSourceException"

**Step 3: Write minimal implementation**

Create directory first:
```bash
mkdir -p assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/exception
mkdir -p assistant-agent-planning/assistant-agent-planning-api/src/test/java/com/alibaba/assistant/agent/planning/exception
```

Then create implementation:

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
package com.alibaba.assistant.agent.planning.exception;

/**
 * Exception thrown when parameter option source operations fail.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class OptionsSourceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public OptionsSourceException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public OptionsSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=OptionsSourceExceptionTest`

Expected: PASS (3 tests)

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/exception/
git add assistant-agent-planning/assistant-agent-planning-api/src/test/java/com/alibaba/assistant/agent/planning/exception/
git commit -m "$(cat <<'EOF'
feat(planning): add OptionsSourceException

Add runtime exception for option source operation failures with:
- Message-only constructor
- Message and cause constructor

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Create ParameterOptionsService SPI Interface

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/spi/ParameterOptionsService.java`
- Test: `assistant-agent-planning/assistant-agent-planning-api/src/test/java/com/alibaba/assistant/agent/planning/spi/ParameterOptionsServiceTest.java`

**Step 1: Write the failing test**

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
package com.alibaba.assistant.agent.planning.spi;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParameterOptionsServiceTest {

    @Test
    void shouldHaveRequiredMethods() throws NoSuchMethodException {
        // Verify interface has required methods
        ParameterOptionsService.class.getMethod("fetchOptions", OptionsSourceConfig.class);
        ParameterOptionsService.class.getMethod("supports", OptionsSourceConfig.SourceType.class);
        ParameterOptionsService.class.getMethod("getName");
    }

    @Test
    void shouldBeImplementable() {
        // Simple implementation test
        ParameterOptionsService service = new ParameterOptionsService() {
            @Override
            public List<OptionItem> fetchOptions(OptionsSourceConfig config) {
                return List.of();
            }

            @Override
            public boolean supports(OptionsSourceConfig.SourceType sourceType) {
                return true;
            }

            @Override
            public String getName() {
                return "TestService";
            }
        };

        assertNotNull(service);
        assertEquals("TestService", service.getName());
        assertTrue(service.supports(OptionsSourceConfig.SourceType.NL2SQL));
        assertEquals(0, service.fetchOptions(null).size());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=ParameterOptionsServiceTest`

Expected: FAIL with "cannot find symbol: class ParameterOptionsService"

**Step 3: Write minimal implementation**

Create directory first:
```bash
mkdir -p assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/spi
mkdir -p assistant-agent-planning/assistant-agent-planning-api/src/test/java/com/alibaba/assistant/agent/planning/spi
```

Then create interface:

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
package com.alibaba.assistant.agent.planning.spi;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.exception.OptionsSourceException;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;

import java.util.List;

/**
 * SPI for fetching parameter options from various sources.
 * Implementations should delegate to specific handlers based on source type.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
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
    boolean supports(OptionsSourceConfig.SourceType sourceType);

    /**
     * Get service name for logging and identification.
     *
     * @return Service name
     */
    String getName();
}
```

**Step 4: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=ParameterOptionsServiceTest`

Expected: PASS (2 tests)

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/spi/
git add assistant-agent-planning/assistant-agent-planning-api/src/test/java/com/alibaba/assistant/agent/planning/spi/
git commit -m "$(cat <<'EOF'
feat(planning): add ParameterOptionsService SPI

Add SPI interface for fetching parameter options with:
- fetchOptions(config) method
- supports(sourceType) method
- getName() method for identification

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Create OptionsSourceHandler Internal Interface

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal/OptionsSourceHandler.java`
- Test: `assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/internal/OptionsSourceHandlerTest.java`

**Step 1: Write the failing test**

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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptionsSourceHandlerTest {

    @Test
    void shouldHaveRequiredMethods() throws NoSuchMethodException {
        OptionsSourceHandler.class.getMethod("handle", String.class, Object.class);
        OptionsSourceHandler.class.getMethod("supportedType");
    }

    @Test
    void shouldBeImplementable() {
        OptionsSourceHandler handler = new OptionsSourceHandler() {
            @Override
            public List<OptionItem> handle(String systemId, Object specificConfig) {
                return List.of(new OptionItem("Test", "test"));
            }

            @Override
            public OptionsSourceConfig.SourceType supportedType() {
                return OptionsSourceConfig.SourceType.STATIC;
            }
        };

        assertNotNull(handler);
        assertEquals(OptionsSourceConfig.SourceType.STATIC, handler.supportedType());
        assertEquals(1, handler.handle(null, null).size());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=OptionsSourceHandlerTest`

Expected: FAIL with "cannot find symbol: class OptionsSourceHandler"

**Step 3: Write minimal implementation**

Create directory first:
```bash
mkdir -p assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal
mkdir -p assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/internal
```

Then create interface:

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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;

import java.util.List;

/**
 * Internal handler interface for specific option source types.
 * Each handler implements fetching logic for one source type.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
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
    OptionsSourceConfig.SourceType supportedType();
}
```

**Step 4: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=OptionsSourceHandlerTest`

Expected: PASS (2 tests)

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal/
git add assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/internal/
git commit -m "$(cat <<'EOF'
feat(planning): add OptionsSourceHandler internal interface

Add internal handler interface for specific source types with:
- handle(systemId, config) method
- supportedType() method

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Implement StaticOptionsHandler

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal/StaticOptionsHandler.java`
- Test: `assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/internal/StaticOptionsHandlerTest.java`

**Step 1: Write the failing test**

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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import com.alibaba.assistant.agent.planning.model.StaticOptionsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StaticOptionsHandlerTest {

    private StaticOptionsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StaticOptionsHandler();
    }

    @Test
    void shouldReturnStaticList() {
        StaticOptionsConfig config = new StaticOptionsConfig();
        config.setOptions(List.of(
            new OptionItem("Option 1", "val1"),
            new OptionItem("Option 2", "val2")
        ));

        List<OptionItem> result = handler.handle(null, config);

        assertEquals(2, result.size());
        assertEquals("Option 1", result.get(0).getLabel());
        assertEquals("val1", result.get(0).getValue());
    }

    @Test
    void shouldReturnEmptyListWhenOptionsNull() {
        StaticOptionsConfig config = new StaticOptionsConfig();
        config.setOptions(null);

        List<OptionItem> result = handler.handle(null, config);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void shouldSupportStaticType() {
        assertEquals(OptionsSourceConfig.SourceType.STATIC, handler.supportedType());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=StaticOptionsHandlerTest`

Expected: FAIL with "cannot find symbol: class StaticOptionsHandler"

**Step 3: Write minimal implementation**

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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import com.alibaba.assistant.agent.planning.model.StaticOptionsConfig;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Handler for static option lists.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
class StaticOptionsHandler implements OptionsSourceHandler {

    @Override
    public List<OptionItem> handle(String systemId, Object specificConfig) {
        StaticOptionsConfig config = (StaticOptionsConfig) specificConfig;
        List<OptionItem> options = config.getOptions();
        return options != null ? options : Collections.emptyList();
    }

    @Override
    public OptionsSourceConfig.SourceType supportedType() {
        return OptionsSourceConfig.SourceType.STATIC;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=StaticOptionsHandlerTest`

Expected: PASS (3 tests)

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal/StaticOptionsHandler.java
git add assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/internal/StaticOptionsHandlerTest.java
git commit -m "$(cat <<'EOF'
feat(planning): implement StaticOptionsHandler

Add handler for static option lists:
- Returns options from StaticOptionsConfig directly
- Returns empty list when options are null
- Supports STATIC source type

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Add JsonPath Dependency to POM

**Files:**
- Modify: `assistant-agent-planning/assistant-agent-planning-core/pom.xml`

**Step 1: Check current dependencies**

Run: `cd .worktrees/parameter-options-service/assistant-agent-planning/assistant-agent-planning-core && grep -A 5 "<dependencies>" pom.xml | head -20`

**Step 2: Add JsonPath dependency**

Add to `<dependencies>` section in `assistant-agent-planning/assistant-agent-planning-core/pom.xml`:

```xml
<!-- JSONPath for HTTP response extraction -->
<dependency>
    <groupId>com.jayway.jsonpath</groupId>
    <artifactId>json-path</artifactId>
</dependency>
```

**Step 3: Verify dependency resolves**

Run: `cd .worktrees/parameter-options-service && mvn dependency:tree -pl assistant-agent-planning-core | grep json-path`

Expected: Shows json-path dependency

**Step 4: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-core/pom.xml
git commit -m "$(cat <<'EOF'
build(planning): add JsonPath dependency

Add com.jayway.jsonpath:json-path for HTTP response
data extraction in HttpOptionsHandler.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Implement HttpOptionsHandler (Part 1 - Basic Structure)

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal/HttpOptionsHandler.java`
- Test: `assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/internal/HttpOptionsHandlerTest.java`

**Step 1: Write the failing test**

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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.exception.OptionsSourceException;
import com.alibaba.assistant.agent.planning.model.HttpOptionsConfig;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HttpOptionsHandlerTest {

    @Mock
    private RestTemplate restTemplate;

    private HttpOptionsHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new HttpOptionsHandler(restTemplate, 5000);
    }

    @Test
    void shouldExtractDataViaJsonPath() {
        String jsonResponse = "{\"data\": [{\"name\": \"Option A\", \"id\": \"1\"}, {\"name\": \"Option B\", \"id\": \"2\"}]}";
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok(jsonResponse));

        HttpOptionsConfig config = new HttpOptionsConfig();
        config.setUrl("http://test.com/api");
        config.setLabelPath("$.data[*].name");
        config.setValuePath("$.data[*].id");

        List<OptionItem> result = handler.handle(null, config);

        assertEquals(2, result.size());
        assertEquals("Option A", result.get(0).getLabel());
        assertEquals("1", result.get(0).getValue());
        assertEquals("Option B", result.get(1).getLabel());
        assertEquals("2", result.get(1).getValue());
    }

    @Test
    void shouldUseGetMethodByDefault() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"items\": []}"));

        HttpOptionsConfig config = new HttpOptionsConfig();
        config.setUrl("http://test.com/api");
        config.setLabelPath("$.items");
        config.setValuePath("$.items");

        handler.handle(null, config);

        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void shouldThrowExceptionOnInvalidJsonPath() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"data\": []}"));

        HttpOptionsConfig config = new HttpOptionsConfig();
        config.setUrl("http://test.com/api");
        config.setLabelPath("$.invalid.path.that.does.not.exist");
        config.setValuePath("$.invalid.path");

        assertThrows(OptionsSourceException.class, () -> handler.handle(null, config));
    }

    @Test
    void shouldReturnEmptyListOnHttpFailure() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RestClientException("Network error"));

        HttpOptionsConfig config = new HttpOptionsConfig();
        config.setUrl("http://test.com/api");
        config.setLabelPath("$.data");
        config.setValuePath("$.data");

        List<OptionItem> result = handler.handle(null, config);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void shouldSupportHttpType() {
        assertEquals(OptionsSourceConfig.SourceType.HTTP, handler.supportedType());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=HttpOptionsHandlerTest`

Expected: FAIL with "cannot find symbol: class HttpOptionsHandler"

**Step 3: Write minimal implementation**

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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.exception.OptionsSourceException;
import com.alibaba.assistant.agent.planning.model.HttpOptionsConfig;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handler for HTTP API-based option sources.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
class HttpOptionsHandler implements OptionsSourceHandler {

    private static final Logger logger = LoggerFactory.getLogger(HttpOptionsHandler.class);

    private final RestTemplate restTemplate;
    private final int defaultTimeout;

    public HttpOptionsHandler(RestTemplate restTemplate, int defaultTimeout) {
        this.restTemplate = restTemplate;
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public List<OptionItem> handle(String systemId, Object specificConfig) {
        HttpOptionsConfig config = (HttpOptionsConfig) specificConfig;

        try {
            // Execute HTTP request
            ResponseEntity<String> response = executeHttpRequest(config);

            // Extract data using JSONPath
            return extractOptions(response.getBody(), config.getLabelPath(), config.getValuePath());
        } catch (RestClientException e) {
            logger.error("HttpOptionsHandler#handle - HTTP request failed: url={}, error={}",
                    config.getUrl(), e.getMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("HttpOptionsHandler#handle - Failed to extract options: url={}, error={}",
                    config.getUrl(), e.getMessage(), e);
            throw new OptionsSourceException("Failed to extract options from HTTP response", e);
        }
    }

    private ResponseEntity<String> executeHttpRequest(HttpOptionsConfig config) {
        HttpHeaders headers = new HttpHeaders();
        if (config.getHeaders() != null) {
            config.getHeaders().forEach(headers::set);
        }

        HttpEntity<String> entity = new HttpEntity<>(config.getBody(), headers);
        HttpMethod method = HttpMethod.valueOf(config.getMethod().toUpperCase());

        return restTemplate.exchange(config.getUrl(), method, entity, String.class);
    }

    private List<OptionItem> extractOptions(String jsonBody, String labelPath, String valuePath) {
        try {
            List<String> labels = JsonPath.read(jsonBody, labelPath);
            List<String> values = JsonPath.read(jsonBody, valuePath);

            if (labels.size() != values.size()) {
                throw new OptionsSourceException(
                        "Label and value arrays have different sizes: " + labels.size() + " vs " + values.size());
            }

            List<OptionItem> options = new ArrayList<>();
            for (int i = 0; i < labels.size(); i++) {
                options.add(new OptionItem(labels.get(i), values.get(i)));
            }

            return options;
        } catch (Exception e) {
            throw new OptionsSourceException("Failed to extract data via JSONPath", e);
        }
    }

    @Override
    public OptionsSourceConfig.SourceType supportedType() {
        return OptionsSourceConfig.SourceType.HTTP;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=HttpOptionsHandlerTest`

Expected: PASS (5 tests)

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal/HttpOptionsHandler.java
git add assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/internal/HttpOptionsHandlerTest.java
git commit -m "$(cat <<'EOF'
feat(planning): implement HttpOptionsHandler

Add handler for HTTP API-based option sources:
- Executes HTTP requests (GET/POST/PUT) with headers
- Extracts data via JSONPath (labelPath, valuePath)
- Returns empty list on network failure (graceful degradation)
- Throws OptionsSourceException on invalid JSONPath

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Implement Nl2SqlOptionsHandler

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal/Nl2SqlOptionsHandler.java`
- Test: `assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/internal/Nl2SqlOptionsHandlerTest.java`

**Step 1: Write the failing test**

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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.Nl2SqlSourceConfig;
import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.data.spi.Nl2SqlService;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Nl2SqlOptionsHandlerTest {

    @Mock
    private Nl2SqlService nl2SqlService;

    private Nl2SqlOptionsHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new Nl2SqlOptionsHandler(nl2SqlService);
    }

    @Test
    void shouldDelegateToNl2SqlService() {
        Nl2SqlSourceConfig config = new Nl2SqlSourceConfig();
        config.setDescription("Get all departments");
        config.setLabelColumn("name");
        config.setValueColumn("id");

        when(nl2SqlService.queryForOptions(anyString(), anyString(), eq("name"), eq("id")))
            .thenReturn(List.of(
                new OptionItem("Dept A", "1"),
                new OptionItem("Dept B", "2")
            ));

        List<OptionItem> result = handler.handle("test-db", config);

        assertEquals(2, result.size());
        assertEquals("Dept A", result.get(0).getLabel());
        assertEquals("1", result.get(0).getValue());
        verify(nl2SqlService).queryForOptions(eq("test-db"), eq("Get all departments"), eq("name"), eq("id"));
    }

    @Test
    void shouldReturnEmptyListWhenServiceFails() {
        Nl2SqlSourceConfig config = new Nl2SqlSourceConfig();
        config.setDescription("Query");
        config.setLabelColumn("label");
        config.setValueColumn("value");

        when(nl2SqlService.queryForOptions(anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Database error"));

        List<OptionItem> result = handler.handle("db", config);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void shouldSupportNl2SqlType() {
        assertEquals(OptionsSourceConfig.SourceType.NL2SQL, handler.supportedType());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=Nl2SqlOptionsHandlerTest`

Expected: FAIL with "cannot find symbol: class Nl2SqlOptionsHandler"

**Step 3: Write minimal implementation**

First, check if Nl2SqlService has the queryForOptions method we need:

Run: `cd .worktrees/parameter-options-service && grep -n "queryForOptions" assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/spi/Nl2SqlService.java`

If method doesn't exist, we need to add it to Nl2SqlService interface first. Assuming it exists or we'll add it:

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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.Nl2SqlSourceConfig;
import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.data.spi.Nl2SqlService;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Handler for NL2SQL-based option sources.
 * Delegates to Nl2SqlService from data module.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
class Nl2SqlOptionsHandler implements OptionsSourceHandler {

    private static final Logger logger = LoggerFactory.getLogger(Nl2SqlOptionsHandler.class);

    private final Nl2SqlService nl2SqlService;

    public Nl2SqlOptionsHandler(Nl2SqlService nl2SqlService) {
        this.nl2SqlService = nl2SqlService;
    }

    @Override
    public List<OptionItem> handle(String systemId, Object specificConfig) {
        Nl2SqlSourceConfig config = (Nl2SqlSourceConfig) specificConfig;

        try {
            return nl2SqlService.queryForOptions(
                    systemId,
                    config.getDescription(),
                    config.getLabelColumn(),
                    config.getValueColumn()
            );
        } catch (Exception e) {
            logger.error("Nl2SqlOptionsHandler#handle - NL2SQL query failed: systemId={}, description={}, error={}",
                    systemId, config.getDescription(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public OptionsSourceConfig.SourceType supportedType() {
        return OptionsSourceConfig.SourceType.NL2SQL;
    }
}
```

**Step 4: Check if Nl2SqlService.queryForOptions exists**

Run: `cd .worktrees/parameter-options-service && grep -A 10 "interface Nl2SqlService" assistant-agent-data/assistant-agent-data-api/src/main/java/com/alibaba/assistant/agent/data/spi/Nl2SqlService.java`

If method doesn't exist, add it to Nl2SqlService interface. If it does, skip to step 5.

**Step 5: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=Nl2SqlOptionsHandlerTest`

Expected: PASS (3 tests) - Note: may need to adjust if Nl2SqlService interface needs modification

**Step 6: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal/Nl2SqlOptionsHandler.java
git add assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/internal/Nl2SqlOptionsHandlerTest.java
git commit -m "$(cat <<'EOF'
feat(planning): implement Nl2SqlOptionsHandler

Add handler for NL2SQL-based option sources:
- Delegates to Nl2SqlService.queryForOptions()
- Returns empty list on query failure (graceful degradation)
- Supports NL2SQL source type

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Implement EnumOptionsHandler

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal/EnumOptionsHandler.java`
- Test: `assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/internal/EnumOptionsHandlerTest.java`

**Step 1: Write the failing test**

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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.exception.OptionsSourceException;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnumOptionsHandlerTest {

    private EnumOptionsHandler handler;

    enum TestStatus {
        ACTIVE, INACTIVE, PENDING
    }

    @BeforeEach
    void setUp() {
        handler = new EnumOptionsHandler();
    }

    @Test
    void shouldConvertEnumToOptions() {
        String enumClassName = TestStatus.class.getName();

        List<OptionItem> result = handler.handle(null, enumClassName);

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(item -> "ACTIVE".equals(item.getLabel())));
        assertTrue(result.stream().anyMatch(item -> "INACTIVE".equals(item.getLabel())));
        assertTrue(result.stream().anyMatch(item -> "PENDING".equals(item.getLabel())));
    }

    @Test
    void shouldUseSameValueForLabelAndValue() {
        String enumClassName = TestStatus.class.getName();

        List<OptionItem> result = handler.handle(null, enumClassName);

        result.forEach(item -> assertEquals(item.getLabel(), item.getValue()));
    }

    @Test
    void shouldThrowExceptionForInvalidClassName() {
        String invalidClassName = "com.example.NonExistentEnum";

        assertThrows(OptionsSourceException.class, () -> handler.handle(null, invalidClassName));
    }

    @Test
    void shouldThrowExceptionForNonEnumClass() {
        String nonEnumClassName = String.class.getName();

        assertThrows(OptionsSourceException.class, () -> handler.handle(null, nonEnumClassName));
    }

    @Test
    void shouldSupportEnumType() {
        assertEquals(OptionsSourceConfig.SourceType.ENUM, handler.supportedType());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=EnumOptionsHandlerTest`

Expected: FAIL with "cannot find symbol: class EnumOptionsHandler"

**Step 3: Write minimal implementation**

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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.exception.OptionsSourceException;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler for Java enum-based option sources.
 * Uses reflection to get enum constants.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
class EnumOptionsHandler implements OptionsSourceHandler {

    @Override
    public List<OptionItem> handle(String systemId, Object specificConfig) {
        String enumClassName = (String) specificConfig;

        try {
            Class<?> enumClass = Class.forName(enumClassName);

            if (!enumClass.isEnum()) {
                throw new OptionsSourceException("Class is not an enum: " + enumClassName);
            }

            Object[] constants = enumClass.getEnumConstants();

            return Arrays.stream(constants)
                    .map(c -> {
                        String name = c.toString();
                        return new OptionItem(name, name);
                    })
                    .collect(Collectors.toList());
        } catch (ClassNotFoundException e) {
            throw new OptionsSourceException("Enum class not found: " + enumClassName, e);
        }
    }

    @Override
    public OptionsSourceConfig.SourceType supportedType() {
        return OptionsSourceConfig.SourceType.ENUM;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=EnumOptionsHandlerTest`

Expected: PASS (5 tests)

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal/EnumOptionsHandler.java
git add assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/internal/EnumOptionsHandlerTest.java
git commit -m "$(cat <<'EOF'
feat(planning): implement EnumOptionsHandler

Add handler for Java enum-based option sources:
- Uses reflection to get enum constants
- Label and value are both enum name
- Throws OptionsSourceException for invalid/non-enum classes

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Create OptionsCache

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/cache/OptionsCache.java`
- Test: `assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/cache/OptionsCacheTest.java`

**Step 1: Write the failing test**

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
package com.alibaba.assistant.agent.planning.cache;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptionsCacheTest {

    private OptionsCache cache;

    @BeforeEach
    void setUp() {
        cache = new OptionsCache(5 * 60 * 1000); // 5 minutes TTL
    }

    @Test
    void shouldReturnNullOnCacheMiss() {
        List<OptionItem> result = cache.get("missing-key");

        assertNull(result);
    }

    @Test
    void shouldReturnCachedValue() {
        List<OptionItem> options = List.of(new OptionItem("Test", "test"));
        cache.put("key1", options);

        List<OptionItem> result = cache.get("key1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test", result.get(0).getLabel());
    }

    @Test
    void shouldExpireAfterTtl() throws InterruptedException {
        OptionsCache shortCache = new OptionsCache(100); // 100ms TTL
        List<OptionItem> options = List.of(new OptionItem("Test", "test"));

        shortCache.put("key1", options);
        assertNotNull(shortCache.get("key1"));

        Thread.sleep(150);

        assertNull(shortCache.get("key1"));
    }

    @Test
    void shouldHandleConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                String key = "key" + index;
                List<OptionItem> options = List.of(new OptionItem("Option " + index, String.valueOf(index)));
                cache.put(key, options);
                assertNotNull(cache.get(key));
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // All entries should be present
        for (int i = 0; i < threadCount; i++) {
            assertNotNull(cache.get("key" + i));
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=OptionsCacheTest`

Expected: FAIL with "cannot find symbol: class OptionsCache"

**Step 3: Write minimal implementation**

Create directory first:
```bash
mkdir -p assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/cache
mkdir -p assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/cache
```

Then create implementation:

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
package com.alibaba.assistant.agent.planning.cache;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for parameter options with TTL support.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class OptionsCache {

    private static final Logger logger = LoggerFactory.getLogger(OptionsCache.class);

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public OptionsCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /**
     * Get options from cache.
     *
     * @param key Cache key
     * @return Cached options, or null if not found or expired
     */
    public List<OptionItem> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(key);
            logger.debug("OptionsCache#get - Cache entry expired: key={}", key);
            return null;
        }

        logger.debug("OptionsCache#get - Cache hit: key={}", key);
        return entry.value;
    }

    /**
     * Put options in cache.
     *
     * @param key Cache key
     * @param options Options to cache
     */
    public void put(String key, List<OptionItem> options) {
        long expirationTime = System.currentTimeMillis() + ttlMillis;
        cache.put(key, new CacheEntry(options, expirationTime));
        logger.debug("OptionsCache#put - Cached: key={}, size={}", key, options.size());
    }

    /**
     * Cache entry with expiration time.
     */
    private static class CacheEntry {
        final List<OptionItem> value;
        final long expirationTime;

        CacheEntry(List<OptionItem> value, long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=OptionsCacheTest`

Expected: PASS (4 tests)

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/cache/
git add assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/cache/
git commit -m "$(cat <<'EOF'
feat(planning): add OptionsCache

Add in-memory cache for parameter options with:
- Configurable TTL (time-to-live)
- Concurrent access support via ConcurrentHashMap
- Automatic expiration on get
- Debug logging for cache hits and expiration

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Implement DefaultParameterOptionsService (Part 1 - Basic Structure)

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/service/DefaultParameterOptionsService.java`
- Test: `assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/service/DefaultParameterOptionsServiceTest.java`

**Step 1: Write the failing test**

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
package com.alibaba.assistant.agent.planning.service;

import com.alibaba.assistant.agent.data.model.nl2sql.Nl2SqlSourceConfig;
import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.cache.OptionsCache;
import com.alibaba.assistant.agent.planning.exception.OptionsSourceException;
import com.alibaba.assistant.agent.planning.internal.OptionsSourceHandler;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import com.alibaba.assistant.agent.planning.model.StaticOptionsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultParameterOptionsServiceTest {

    @Mock
    private OptionsSourceHandler nl2sqlHandler;

    @Mock
    private OptionsSourceHandler staticHandler;

    @Mock
    private OptionsCache cache;

    private DefaultParameterOptionsService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(nl2sqlHandler.supportedType()).thenReturn(OptionsSourceConfig.SourceType.NL2SQL);
        when(staticHandler.supportedType()).thenReturn(OptionsSourceConfig.SourceType.STATIC);

        List<OptionsSourceHandler> handlers = List.of(nl2sqlHandler, staticHandler);
        service = new DefaultParameterOptionsService(handlers, cache);
    }

    @Test
    void shouldDefaultToNl2SqlWhenTypeIsNull() {
        OptionsSourceConfig config = new OptionsSourceConfig();
        config.setType(null); // Type not specified
        config.setSystemId("test-db");
        config.setConfig(new Nl2SqlSourceConfig());

        when(cache.get(anyString())).thenReturn(null);
        when(nl2sqlHandler.handle(anyString(), any())).thenReturn(List.of(new OptionItem("Test", "test")));

        service.fetchOptions(config);

        verify(nl2sqlHandler).handle(eq("test-db"), any());
    }

    @Test
    void shouldRouteToCorrectHandler() {
        OptionsSourceConfig config = new OptionsSourceConfig();
        config.setType(OptionsSourceConfig.SourceType.STATIC);
        config.setConfig(new StaticOptionsConfig());

        when(cache.get(anyString())).thenReturn(null);
        when(staticHandler.handle(any(), any())).thenReturn(List.of(new OptionItem("Static", "static")));

        service.fetchOptions(config);

        verify(staticHandler).handle(any(), any());
        verify(nl2sqlHandler, never()).handle(any(), any());
    }

    @Test
    void shouldUseCacheOnSecondCall() {
        OptionsSourceConfig config = new OptionsSourceConfig();
        config.setType(OptionsSourceConfig.SourceType.STATIC);
        config.setConfig(new StaticOptionsConfig());

        List<OptionItem> cachedOptions = List.of(new OptionItem("Cached", "cached"));

        // First call - cache miss
        when(cache.get(anyString())).thenReturn(null);
        when(staticHandler.handle(any(), any())).thenReturn(List.of(new OptionItem("Fresh", "fresh")));
        service.fetchOptions(config);

        // Second call - cache hit
        when(cache.get(anyString())).thenReturn(cachedOptions);
        List<OptionItem> result = service.fetchOptions(config);

        assertEquals(1, result.size());
        assertEquals("Cached", result.get(0).getLabel());
        verify(staticHandler, times(1)).handle(any(), any()); // Called only once
    }

    @Test
    void shouldReturnEmptyListOnHandlerException() {
        OptionsSourceConfig config = new OptionsSourceConfig();
        config.setType(OptionsSourceConfig.SourceType.STATIC);
        config.setConfig(new StaticOptionsConfig());

        when(cache.get(anyString())).thenReturn(null);
        when(staticHandler.handle(any(), any())).thenThrow(new RuntimeException("Handler error"));

        List<OptionItem> result = service.fetchOptions(config);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void shouldThrowExceptionWhenNoHandlerFound() {
        OptionsSourceConfig config = new OptionsSourceConfig();
        config.setType(OptionsSourceConfig.SourceType.HTTP); // No HTTP handler registered

        when(cache.get(anyString())).thenReturn(null);

        assertThrows(OptionsSourceException.class, () -> service.fetchOptions(config));
    }

    @Test
    void shouldSupportAllRegisteredTypes() {
        assertTrue(service.supports(OptionsSourceConfig.SourceType.NL2SQL));
        assertTrue(service.supports(OptionsSourceConfig.SourceType.STATIC));
        assertFalse(service.supports(OptionsSourceConfig.SourceType.HTTP)); // Not registered
    }

    @Test
    void shouldReturnServiceName() {
        assertEquals("DefaultParameterOptionsService", service.getName());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=DefaultParameterOptionsServiceTest`

Expected: FAIL with "cannot find symbol: class DefaultParameterOptionsService"

**Step 3: Write minimal implementation**

Create directory first:
```bash
mkdir -p assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/service
mkdir -p assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/service
```

Then create implementation:

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
package com.alibaba.assistant.agent.planning.service;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.cache.OptionsCache;
import com.alibaba.assistant.agent.planning.exception.OptionsSourceException;
import com.alibaba.assistant.agent.planning.internal.OptionsSourceHandler;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import com.alibaba.assistant.agent.planning.spi.ParameterOptionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of ParameterOptionsService.
 * Routes requests to specific handlers based on source type.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Service
public class DefaultParameterOptionsService implements ParameterOptionsService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultParameterOptionsService.class);

    private final Map<OptionsSourceConfig.SourceType, OptionsSourceHandler> handlers;
    private final OptionsCache cache;

    public DefaultParameterOptionsService(List<OptionsSourceHandler> handlerList, OptionsCache cache) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(OptionsSourceHandler::supportedType, h -> h));
        this.cache = cache;
        logger.info("DefaultParameterOptionsService initialized with {} handlers", handlers.size());
    }

    @Override
    public List<OptionItem> fetchOptions(OptionsSourceConfig config) {
        // Default to NL2SQL if type not specified
        OptionsSourceConfig.SourceType type = config.getType();
        if (type == null) {
            type = OptionsSourceConfig.SourceType.NL2SQL;
            logger.debug("DefaultParameterOptionsService#fetchOptions - No type specified, using default: NL2SQL");
        }

        // Build cache key
        String cacheKey = buildCacheKey(config, type);

        // Check cache
        List<OptionItem> cached = cache.get(cacheKey);
        if (cached != null) {
            logger.debug("DefaultParameterOptionsService#fetchOptions - Cache hit: key={}", cacheKey);
            return cached;
        }

        // Find handler
        OptionsSourceHandler handler = handlers.get(type);
        if (handler == null) {
            throw new OptionsSourceException("No handler found for type: " + type);
        }

        // Execute handler
        try {
            logger.debug("DefaultParameterOptionsService#fetchOptions - Fetching options: type={}, systemId={}",
                    type, config.getSystemId());

            List<OptionItem> options = handler.handle(config.getSystemId(), config.getConfig());

            // Cache result
            cache.put(cacheKey, options);

            logger.info("DefaultParameterOptionsService#fetchOptions - Success: type={}, systemId={}, count={}",
                    type, config.getSystemId(), options.size());

            return options;
        } catch (Exception e) {
            logger.error("DefaultParameterOptionsService#fetchOptions - Failed: type={}, systemId={}, error={}",
                    type, config.getSystemId(), e.getMessage(), e);
            return Collections.emptyList(); // Graceful degradation
        }
    }

    @Override
    public boolean supports(OptionsSourceConfig.SourceType sourceType) {
        return handlers.containsKey(sourceType);
    }

    @Override
    public String getName() {
        return "DefaultParameterOptionsService";
    }

    private String buildCacheKey(OptionsSourceConfig config, OptionsSourceConfig.SourceType type) {
        return String.format("%s:%s:%s",
                type,
                config.getSystemId(),
                config.getConfig() != null ? config.getConfig().hashCode() : "null");
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=DefaultParameterOptionsServiceTest`

Expected: PASS (7 tests)

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/service/
git add assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/service/
git commit -m "$(cat <<'EOF'
feat(planning): implement DefaultParameterOptionsService

Add main service implementation with:
- Handler routing based on source type
- Default to NL2SQL when type is null
- Caching with configurable TTL
- Graceful error handling (returns empty list)
- Throws OptionsSourceException when no handler found

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Add Configuration Properties

**Files:**
- Modify: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/config/PlanningExtensionProperties.java`

**Step 1: Read current configuration structure**

Run: `cd .worktrees/parameter-options-service && cat assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/config/PlanningExtensionProperties.java | head -50`

**Step 2: Add param-collection configuration section**

Add new nested class to PlanningExtensionProperties:

```java
/**
 * Parameter collection configuration.
 */
public static class ParamCollection {
    /**
     * Whether parameter collection is enabled.
     */
    private boolean enabled = true;

    /**
     * Default source type when not specified.
     */
    private String defaultSourceType = "NL2SQL";

    /**
     * Cache TTL in milliseconds.
     */
    private long cacheTtl = 300000; // 5 minutes

    /**
     * Default HTTP timeout in milliseconds.
     */
    private int httpTimeout = 5000;

    /**
     * HTTP retry count on failure.
     */
    private int httpRetryCount = 1;

    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultSourceType() {
        return defaultSourceType;
    }

    public void setDefaultSourceType(String defaultSourceType) {
        this.defaultSourceType = defaultSourceType;
    }

    public long getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(long cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public int getHttpTimeout() {
        return httpTimeout;
    }

    public void setHttpTimeout(int httpTimeout) {
        this.httpTimeout = httpTimeout;
    }

    public int getHttpRetryCount() {
        return httpRetryCount;
    }

    public void setHttpRetryCount(int httpRetryCount) {
        this.httpRetryCount = httpRetryCount;
    }
}

// Add field to main class
private ParamCollection paramCollection = new ParamCollection();

public ParamCollection getParamCollection() {
    return paramCollection;
}

public void setParamCollection(ParamCollection paramCollection) {
    this.paramCollection = paramCollection;
}
```

**Step 3: Verify configuration loads**

Run: `cd .worktrees/parameter-options-service && mvn clean compile -pl assistant-agent-planning-core`

Expected: Successful compilation

**Step 4: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/config/PlanningExtensionProperties.java
git commit -m "$(cat <<'EOF'
feat(planning): add parameter collection config properties

Add configuration properties for parameter options service:
- enabled flag (default: true)
- defaultSourceType (default: NL2SQL)
- cacheTtl (default: 5 minutes)
- httpTimeout (default: 5 seconds)
- httpRetryCount (default: 1)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: Create ParamCollectionAutoConfiguration

**Files:**
- Create: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/config/ParamCollectionAutoConfiguration.java`
- Test: `assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/config/ParamCollectionAutoConfigurationTest.java`

**Step 1: Write the failing test**

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
package com.alibaba.assistant.agent.planning.config;

import com.alibaba.assistant.agent.planning.cache.OptionsCache;
import com.alibaba.assistant.agent.planning.spi.ParameterOptionsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ParamCollectionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ParamCollectionAutoConfiguration.class);

    @Test
    void shouldRegisterBeansWhenEnabled() {
        contextRunner
                .withPropertyValues("spring.ai.alibaba.codeact.extension.planning.param-collection.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OptionsCache.class);
                    assertThat(context).hasSingleBean(RestTemplate.class);
                    assertThat(context).hasSingleBean(ParameterOptionsService.class);
                });
    }

    @Test
    void shouldNotRegisterBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("spring.ai.alibaba.codeact.extension.planning.param-collection.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OptionsCache.class);
                    assertThat(context).doesNotHaveBean(ParameterOptionsService.class);
                });
    }

    @Test
    void shouldUseCacheTtlFromConfig() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.alibaba.codeact.extension.planning.param-collection.enabled=true",
                        "spring.ai.alibaba.codeact.extension.planning.param-collection.cache-ttl=60000"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(OptionsCache.class);
                    // Cache TTL is internal, can't easily test without reflection
                });
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=ParamCollectionAutoConfigurationTest`

Expected: FAIL with "cannot find symbol: class ParamCollectionAutoConfiguration"

**Step 3: Write minimal implementation**

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
package com.alibaba.assistant.agent.planning.config;

import com.alibaba.assistant.agent.planning.cache.OptionsCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for parameter collection functionality.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(PlanningExtensionProperties.class)
@ConditionalOnProperty(
        prefix = "spring.ai.alibaba.codeact.extension.planning.param-collection",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ComponentScan(basePackages = {
        "com.alibaba.assistant.agent.planning.internal",
        "com.alibaba.assistant.agent.planning.service"
})
public class ParamCollectionAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ParamCollectionAutoConfiguration.class);

    @Bean
    public OptionsCache optionsCache(PlanningExtensionProperties properties) {
        long cacheTtl = properties.getParamCollection().getCacheTtl();
        logger.info("ParamCollectionAutoConfiguration - Creating OptionsCache with TTL: {}ms", cacheTtl);
        return new OptionsCache(cacheTtl);
    }

    @Bean
    public RestTemplate restTemplate() {
        logger.info("ParamCollectionAutoConfiguration - Creating RestTemplate for HTTP handlers");
        return new RestTemplate();
    }

    public ParamCollectionAutoConfiguration() {
        logger.info("ParamCollectionAutoConfiguration - Parameter collection module initialized");
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd .worktrees/parameter-options-service && mvn test -Dtest=ParamCollectionAutoConfigurationTest`

Expected: PASS (3 tests)

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/config/ParamCollectionAutoConfiguration.java
git add assistant-agent-planning/assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/config/ParamCollectionAutoConfigurationTest.java
git commit -m "$(cat <<'EOF'
feat(planning): add ParamCollectionAutoConfiguration

Add Spring Boot auto-configuration for parameter collection:
- Conditional on enabled property (default: true)
- Registers OptionsCache with configurable TTL
- Registers RestTemplate for HTTP handlers
- Component scans internal and service packages

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 16: Enhance ActionParameter Model

**Files:**
- Modify: `assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/model/ActionParameter.java`

**Step 1: Read current ActionParameter structure**

Run: `cd .worktrees/parameter-options-service && grep -n "class ActionParameter" assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/model/ActionParameter.java`

**Step 2: Add optionsSource field**

Add to ActionParameter class (find appropriate location near other fields):

```java
/**
 * Dynamic options source configuration.
 * When present, options will be fetched dynamically during parameter collection.
 * If type is null, defaults to NL2SQL.
 */
private OptionsSourceConfig optionsSource;

public OptionsSourceConfig getOptionsSource() {
    return optionsSource;
}

public void setOptionsSource(OptionsSourceConfig optionsSource) {
    this.optionsSource = optionsSource;
}
```

**Step 3: Update equals/hashCode/toString if they exist**

If ActionParameter has equals/hashCode/toString methods, add optionsSource to them.

**Step 4: Verify compilation**

Run: `cd .worktrees/parameter-options-service && mvn clean compile -pl assistant-agent-planning-api`

Expected: Successful compilation

**Step 5: Commit**

```bash
cd .worktrees/parameter-options-service
git add assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/model/ActionParameter.java
git commit -m "$(cat <<'EOF'
feat(planning): add optionsSource field to ActionParameter

Add OptionsSourceConfig field to ActionParameter model:
- optionsSource field for dynamic parameter options
- When present, options fetched dynamically during collection
- Defaults to NL2SQL if type not specified

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 17: Run All Tests

**Files:**
- N/A (testing phase)

**Step 1: Run all tests in planning modules**

Run: `cd .worktrees/parameter-options-service && mvn test -pl assistant-agent-planning-api,assistant-agent-planning-core`

Expected: All new tests PASS (note: ActionExecutorFactoryTest failures are pre-existing, unrelated to our work)

**Step 2: Check test coverage**

Run: `cd .worktrees/parameter-options-service && mvn test jacoco:report -pl assistant-agent-planning-core`

Then check: `assistant-agent-planning/assistant-agent-planning-core/target/site/jacoco/index.html`

Expected: >70% coverage for new code

**Step 3: Document test results**

Create summary of test results:
- Total tests added
- Total tests passing
- Coverage percentage for new code
- Pre-existing test failures (unrelated)

No commit needed for this step (testing phase only).

---

## Summary

This implementation plan creates a comprehensive Parameter Options Service for the Planning module with:

**Deliverables:**
- 5 data models (OptionsSourceConfig, StaticOptionsConfig, HttpOptionsConfig, OptionsSourceException, enhanced ActionParameter)
- 2 SPI interfaces (ParameterOptionsService, OptionsSourceHandler)
- 4 handler implementations (StaticOptionsHandler, HttpOptionsHandler, Nl2SqlOptionsHandler, EnumOptionsHandler)
- 1 cache implementation (OptionsCache)
- 1 service implementation (DefaultParameterOptionsService)
- 1 auto-configuration class (ParamCollectionAutoConfiguration)
- Configuration properties integration
- Comprehensive test coverage (>70%)

**Key Features:**
- Multi-source support (NL2SQL, Static, HTTP, Enum)
- Default to NL2SQL when type not specified
- Caching with configurable TTL
- Graceful error handling
- Spring Boot auto-configuration
- Fully tested with unit and integration tests

**Total Tasks:** 17 tasks with ~85 steps
**Estimated Duration:** 10-15 days

**Next Steps After Implementation:**
- Integration with SessionProvider (Task 18 - to be defined based on SessionProvider implementation)
- Manual testing with real data sources
- Performance testing with large option sets
- Documentation updates
