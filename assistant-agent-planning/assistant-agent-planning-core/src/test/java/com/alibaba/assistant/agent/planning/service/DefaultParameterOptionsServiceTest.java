/*
 * Copyright 2025 Alibaba Group Holding Limited.
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for DefaultParameterOptionsService.
 *
 * @author Claude
 * @since 2026-01-20
 */
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
        // Given
        OptionsSourceConfig config = new OptionsSourceConfig();
        config.setType(null);
        config.setSystemId("test-system");
        config.setConfig(Map.of("query", "SELECT * FROM users"));

        List<OptionItem> expectedOptions = List.of(
                new OptionItem("1", "User 1")
        );

        when(cache.get(anyString())).thenReturn(null);
        when(nl2sqlHandler.handle(eq("test-system"), any())).thenReturn(expectedOptions);

        // When
        List<OptionItem> result = service.fetchOptions(config);

        // Then
        assertThat(result).isEqualTo(expectedOptions);
        verify(nl2sqlHandler).handle(eq("test-system"), any());
        verify(staticHandler, never()).handle(anyString(), any());
    }

    @Test
    void shouldRouteToCorrectHandler() {
        // Given
        OptionsSourceConfig staticConfig = new OptionsSourceConfig();
        staticConfig.setType(OptionsSourceConfig.SourceType.STATIC);
        staticConfig.setSystemId("static-system");
        staticConfig.setConfig(Map.of("values", List.of("A", "B")));

        List<OptionItem> expectedOptions = List.of(
                new OptionItem("A", "Option A"),
                new OptionItem("B", "Option B")
        );

        when(cache.get(anyString())).thenReturn(null);
        when(staticHandler.handle(eq("static-system"), any())).thenReturn(expectedOptions);

        // When
        List<OptionItem> result = service.fetchOptions(staticConfig);

        // Then
        assertThat(result).isEqualTo(expectedOptions);
        verify(staticHandler).handle(eq("static-system"), any());
        verify(nl2sqlHandler, never()).handle(anyString(), any());
    }

    @Test
    void shouldUseCacheOnSecondCall() {
        // Given
        OptionsSourceConfig config = new OptionsSourceConfig();
        config.setType(OptionsSourceConfig.SourceType.NL2SQL);
        config.setSystemId("test-system");
        config.setConfig(Map.of("query", "SELECT * FROM users"));

        List<OptionItem> cachedOptions = List.of(
                new OptionItem("1", "Cached User")
        );

        when(cache.get(anyString())).thenReturn(cachedOptions);

        // When
        List<OptionItem> result = service.fetchOptions(config);

        // Then
        assertThat(result).isEqualTo(cachedOptions);
        verify(cache).get(anyString());
        verify(nl2sqlHandler, never()).handle(anyString(), any());
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    void shouldReturnEmptyListOnHandlerException() {
        // Given
        OptionsSourceConfig config = new OptionsSourceConfig();
        config.setType(OptionsSourceConfig.SourceType.NL2SQL);
        config.setSystemId("test-system");
        config.setConfig(Map.of("query", "SELECT * FROM users"));

        when(cache.get(anyString())).thenReturn(null);
        when(nl2sqlHandler.handle(anyString(), any()))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When
        List<OptionItem> result = service.fetchOptions(config);

        // Then
        assertThat(result).isEmpty();
        verify(nl2sqlHandler).handle(anyString(), any());
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    void shouldThrowExceptionWhenNoHandlerFound() {
        // Given
        OptionsSourceConfig config = new OptionsSourceConfig();
        config.setType(OptionsSourceConfig.SourceType.HTTP);
        config.setSystemId("test-system");
        config.setConfig(Map.of("url", "https://api.example.com/data"));

        when(cache.get(anyString())).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> service.fetchOptions(config))
                .isInstanceOf(OptionsSourceException.class)
                .hasMessageContaining("No handler found for type: HTTP");
    }

    @Test
    void shouldSupportAllRegisteredTypes() {
        // When & Then
        assertThat(service.supports(OptionsSourceConfig.SourceType.NL2SQL)).isTrue();
        assertThat(service.supports(OptionsSourceConfig.SourceType.STATIC)).isTrue();
        assertThat(service.supports(OptionsSourceConfig.SourceType.HTTP)).isFalse();
        assertThat(service.supports(OptionsSourceConfig.SourceType.ENUM)).isFalse();
    }

    @Test
    void shouldReturnServiceName() {
        // When
        String name = service.getName();

        // Then
        assertThat(name).isEqualTo("DefaultParameterOptionsService");
    }

    @Test
    void shouldCacheResultAfterSuccessfulFetch() {
        // Given
        OptionsSourceConfig config = new OptionsSourceConfig();
        config.setType(OptionsSourceConfig.SourceType.NL2SQL);
        config.setSystemId("test-system");
        config.setConfig(Map.of("query", "SELECT * FROM users"));

        List<OptionItem> expectedOptions = List.of(
                new OptionItem("1", "User 1")
        );

        when(cache.get(anyString())).thenReturn(null);
        when(nl2sqlHandler.handle(eq("test-system"), any())).thenReturn(expectedOptions);

        // When
        service.fetchOptions(config);

        // Then
        verify(cache).put(anyString(), eq(expectedOptions));
    }
}
