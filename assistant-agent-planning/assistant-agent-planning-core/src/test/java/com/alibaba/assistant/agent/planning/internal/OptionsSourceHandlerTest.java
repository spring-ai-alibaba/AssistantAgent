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
