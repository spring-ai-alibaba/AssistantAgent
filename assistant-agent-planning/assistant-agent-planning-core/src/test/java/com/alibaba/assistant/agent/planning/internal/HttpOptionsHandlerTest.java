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
