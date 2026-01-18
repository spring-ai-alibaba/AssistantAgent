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
package com.alibaba.assistant.agent.data.provider;

import com.alibaba.assistant.agent.data.cache.DatasourceCache;
import com.alibaba.assistant.agent.data.model.DatasourceDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PersistentDatasourceProvider.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class PersistentDatasourceProviderTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DatasourceCache cache;

    private PersistentDatasourceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new PersistentDatasourceProvider(jdbcTemplate, cache);
    }

    @Test
    void shouldGetByIdFromDatabase() {
        // Given
        Long datasourceId = 100L;
        DatasourceDefinition expected = createTestDatasource(datasourceId);

        when(cache.getById(datasourceId)).thenReturn(null);
        when(jdbcTemplate.queryForObject(
                anyString(),
                any(RowMapper.class),
                eq(datasourceId)
        )).thenReturn(expected);

        // When
        Optional<DatasourceDefinition> result = provider.getById(datasourceId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(datasourceId);
        assertThat(result.get().getName()).isEqualTo("test-datasource");

        verify(cache).getById(datasourceId);
        verify(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), eq(datasourceId));
        verify(cache).putById(datasourceId, expected);
    }

    @Test
    void shouldGetByIdFromCache() {
        // Given
        Long datasourceId = 100L;
        DatasourceDefinition cached = createTestDatasource(datasourceId);

        when(cache.getById(datasourceId)).thenReturn(cached);

        // When
        Optional<DatasourceDefinition> result = provider.getById(datasourceId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(datasourceId);

        verify(cache).getById(datasourceId);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        // Given
        Long datasourceId = 999L;

        when(cache.getById(datasourceId)).thenReturn(null);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(datasourceId)))
                .thenThrow(new EmptyResultDataAccessException(1));

        // When
        Optional<DatasourceDefinition> result = provider.getById(datasourceId);

        // Then
        assertThat(result).isEmpty();

        verify(cache).getById(datasourceId);
        verify(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), eq(datasourceId));
        verify(cache, never()).putById(anyLong(), any());
    }

    @Test
    void shouldGetBySystemIdFromDatabase() {
        // Given
        String systemId = "100";
        Long datasourceId = 100L;
        DatasourceDefinition expected = createTestDatasource(datasourceId);

        when(cache.getBySystemId(systemId)).thenReturn(null);
        when(jdbcTemplate.queryForObject(
                anyString(),
                any(RowMapper.class),
                eq(datasourceId)
        )).thenReturn(expected);

        // When
        Optional<DatasourceDefinition> result = provider.getBySystemId(systemId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(datasourceId);
        assertThat(result.get().getName()).isEqualTo("test-datasource");

        verify(cache).getBySystemId(systemId);
        verify(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), eq(datasourceId));
        verify(cache).putBySystemId(systemId, expected);
        verify(cache).putById(datasourceId, expected);
    }

    @Test
    void shouldGetBySystemIdFromCache() {
        // Given
        String systemId = "100";
        DatasourceDefinition cached = createTestDatasource(100L);

        when(cache.getBySystemId(systemId)).thenReturn(cached);

        // When
        Optional<DatasourceDefinition> result = provider.getBySystemId(systemId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(100L);

        verify(cache).getBySystemId(systemId);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldThrowExceptionForInvalidSystemId() {
        // Given
        String invalidSystemId = "invalid";

        // When & Then
        assertThatThrownBy(() -> provider.getBySystemId(invalidSystemId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid systemId");

        verifyNoInteractions(cache);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldReturnEmptyListForGetAll() {
        // When
        List<DatasourceDefinition> result = provider.getAll();

        // Then
        assertThat(result).isEmpty();

        verifyNoInteractions(cache);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldTestConnectionSuccessfully() {
        // Given
        DatasourceDefinition datasource = DatasourceDefinition.builder()
                .id(1L)
                .name("h2-test")
                .type("h2")
                .connectionUrl("jdbc:h2:mem:testdb")
                .username("sa")
                .password("")
                .status("active")
                .build();

        // When
        boolean result = provider.testConnection(datasource);

        // Then
        assertThat(result).isTrue();
    }

    private DatasourceDefinition createTestDatasource(Long id) {
        return DatasourceDefinition.builder()
                .id(id)
                .name("test-datasource")
                .type("mysql")
                .host("localhost")
                .port(3306)
                .databaseName("testdb")
                .username("testuser")
                .password("testpass")
                .status("active")
                .build();
    }
}
