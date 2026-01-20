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
