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
