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
