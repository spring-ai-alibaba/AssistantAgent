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

import com.alibaba.assistant.agent.data.model.Nl2SqlSourceConfig;
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

        when(nl2SqlService.generateAndExecute(anyString(), anyString(), eq("name"), eq("id")))
            .thenReturn(List.of(
                new OptionItem("Dept A", "1"),
                new OptionItem("Dept B", "2")
            ));

        List<OptionItem> result = handler.handle("test-db", config);

        assertEquals(2, result.size());
        assertEquals("Dept A", result.get(0).getLabel());
        assertEquals("1", result.get(0).getValue());
        verify(nl2SqlService).generateAndExecute(eq("test-db"), eq("Get all departments"), eq("name"), eq("id"));
    }

    @Test
    void shouldReturnEmptyListWhenServiceFails() {
        Nl2SqlSourceConfig config = new Nl2SqlSourceConfig();
        config.setDescription("Query");
        config.setLabelColumn("label");
        config.setValueColumn("value");

        when(nl2SqlService.generateAndExecute(anyString(), anyString(), anyString(), anyString()))
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
