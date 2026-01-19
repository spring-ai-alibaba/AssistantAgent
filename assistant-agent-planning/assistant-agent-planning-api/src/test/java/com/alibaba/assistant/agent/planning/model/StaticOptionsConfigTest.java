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
