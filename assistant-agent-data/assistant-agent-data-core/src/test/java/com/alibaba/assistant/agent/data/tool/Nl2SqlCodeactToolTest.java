/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.data.tool;

import com.alibaba.assistant.agent.common.tools.definition.CodeactToolDefinition;
import com.alibaba.assistant.agent.data.spi.Nl2SqlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Nl2SqlCodeactToolTest {

    @Mock
    private Nl2SqlService nl2SqlService;

    private Nl2SqlCodeactTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new Nl2SqlCodeactTool(nl2SqlService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldGenerateSql() throws Exception {
        when(nl2SqlService.generateSql(eq("test-system"), eq("查询用户"), isNull()))
                .thenReturn("SELECT * FROM users");

        Map<String, Object> params = new HashMap<>();
        params.put("systemId", "test-system");
        params.put("query", "查询用户");

        String result = tool.call(objectMapper.writeValueAsString(params));

        assertTrue(result.contains("SELECT * FROM users"));
        assertTrue(result.contains("```sql"));
    }

    @Test
    void shouldReturnErrorForMissingSystemId() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("query", "查询用户");

        String result = tool.call(objectMapper.writeValueAsString(params));

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("systemId"));
    }

    @Test
    void shouldReturnErrorForMissingQuery() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("systemId", "test-system");

        String result = tool.call(objectMapper.writeValueAsString(params));

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("query"));
    }

    @Test
    void shouldHaveValidCodeactDefinition() {
        CodeactToolDefinition definition = tool.getCodeactDefinition();

        assertNotNull(definition);
        assertEquals("nl2sql", definition.name());
        assertTrue(definition.description().contains("natural language"));
        assertNotNull(definition.parameterTree());
    }
}
