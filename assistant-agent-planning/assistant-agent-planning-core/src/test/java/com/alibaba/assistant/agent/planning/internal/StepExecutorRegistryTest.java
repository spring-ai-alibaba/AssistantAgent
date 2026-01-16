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

import com.alibaba.assistant.agent.planning.model.ExecutionStep;
import com.alibaba.assistant.agent.planning.model.StepExecutionResult;
import com.alibaba.assistant.agent.planning.model.StepType;
import com.alibaba.assistant.agent.planning.spi.StepExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StepExecutorRegistry 单元测试
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@DisplayName("StepExecutorRegistry Tests")
class StepExecutorRegistryTest {

    private StepExecutorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StepExecutorRegistry();
    }

    @Nested
    @DisplayName("Register Tests")
    class RegisterTests {

        @Test
        @DisplayName("should register executor successfully")
        void shouldRegisterExecutorSuccessfully() {
            StepExecutor executor = createMockExecutor(StepType.EXECUTE, 0);

            registry.register(executor);

            assertThat(registry.hasExecutor(StepType.EXECUTE)).isTrue();
            assertThat(registry.getExecutor(StepType.EXECUTE)).isSameAs(executor);
        }

        @Test
        @DisplayName("should ignore null executor")
        void shouldIgnoreNullExecutor() {
            registry.register(null);

            assertThat(registry.getSupportedTypes()).isEmpty();
        }

        @Test
        @DisplayName("should ignore executor with null type")
        void shouldIgnoreExecutorWithNullType() {
            StepExecutor executor = createMockExecutor(null, 0);

            registry.register(executor);

            assertThat(registry.getSupportedTypes()).isEmpty();
        }

        @Test
        @DisplayName("should register multiple executors for same type")
        void shouldRegisterMultipleExecutorsForSameType() {
            StepExecutor executor1 = createMockExecutor(StepType.EXECUTE, 10);
            StepExecutor executor2 = createMockExecutor(StepType.EXECUTE, 5);

            registry.register(executor1);
            registry.register(executor2);

            List<StepExecutor> executors = registry.getExecutors(StepType.EXECUTE);
            assertThat(executors).hasSize(2);
        }

        @Test
        @DisplayName("should sort executors by priority (highest first)")
        void shouldSortExecutorsByPriorityHighestFirst() {
            StepExecutor lowPriority = createMockExecutor(StepType.EXECUTE, 1);
            StepExecutor highPriority = createMockExecutor(StepType.EXECUTE, 10);

            registry.register(lowPriority);
            registry.register(highPriority);

            StepExecutor first = registry.getExecutor(StepType.EXECUTE);
            assertThat(first.getPriority()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Constructor with List Tests")
    class ConstructorWithListTests {

        @Test
        @DisplayName("should register all executors from list")
        void shouldRegisterAllExecutorsFromList() {
            StepExecutor executor1 = createMockExecutor(StepType.EXECUTE, 0);
            StepExecutor executor2 = createMockExecutor(StepType.QUERY, 0);
            StepExecutor executor3 = createMockExecutor(StepType.API_CALL, 0);

            StepExecutorRegistry registryWithList = new StepExecutorRegistry(
                    List.of(executor1, executor2, executor3));

            assertThat(registryWithList.getSupportedTypes())
                    .containsExactlyInAnyOrder(StepType.EXECUTE, StepType.QUERY, StepType.API_CALL);
        }

        @Test
        @DisplayName("should handle null list gracefully")
        void shouldHandleNullListGracefully() {
            StepExecutorRegistry registryWithNull = new StepExecutorRegistry(null);

            assertThat(registryWithNull.getSupportedTypes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GetExecutor Tests")
    class GetExecutorTests {

        @Test
        @DisplayName("should return null for unregistered type")
        void shouldReturnNullForUnregisteredType() {
            StepExecutor executor = registry.getExecutor(StepType.EXECUTE);

            assertThat(executor).isNull();
        }

        @Test
        @DisplayName("should return highest priority executor")
        void shouldReturnHighestPriorityExecutor() {
            StepExecutor low = createMockExecutor(StepType.EXECUTE, 1);
            StepExecutor medium = createMockExecutor(StepType.EXECUTE, 5);
            StepExecutor high = createMockExecutor(StepType.EXECUTE, 10);

            registry.register(low);
            registry.register(high);
            registry.register(medium);

            StepExecutor result = registry.getExecutor(StepType.EXECUTE);

            assertThat(result.getPriority()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("GetExecutors Tests")
    class GetExecutorsTests {

        @Test
        @DisplayName("should return empty list for unregistered type")
        void shouldReturnEmptyListForUnregisteredType() {
            List<StepExecutor> executors = registry.getExecutors(StepType.EXECUTE);

            assertThat(executors).isEmpty();
        }

        @Test
        @DisplayName("should return all executors for type sorted by priority")
        void shouldReturnAllExecutorsForTypeSortedByPriority() {
            StepExecutor low = createMockExecutor(StepType.EXECUTE, 1);
            StepExecutor high = createMockExecutor(StepType.EXECUTE, 10);

            registry.register(low);
            registry.register(high);

            List<StepExecutor> executors = registry.getExecutors(StepType.EXECUTE);

            assertThat(executors).hasSize(2);
            assertThat(executors.get(0).getPriority()).isEqualTo(10);
            assertThat(executors.get(1).getPriority()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("GetAllExecutors Tests")
    class GetAllExecutorsTests {

        @Test
        @DisplayName("should return empty map when no executors registered")
        void shouldReturnEmptyMapWhenNoExecutorsRegistered() {
            Map<StepType, List<StepExecutor>> all = registry.getAllExecutors();

            assertThat(all).isEmpty();
        }

        @Test
        @DisplayName("should return unmodifiable map")
        void shouldReturnUnmodifiableMap() {
            StepExecutor executor = createMockExecutor(StepType.EXECUTE, 0);
            registry.register(executor);

            Map<StepType, List<StepExecutor>> all = registry.getAllExecutors();

            assertThat(all).isNotNull();
            // Map should be unmodifiable
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class,
                    () -> all.put(StepType.QUERY, List.of()));
        }
    }

    @Nested
    @DisplayName("HasExecutor Tests")
    class HasExecutorTests {

        @Test
        @DisplayName("should return false for unregistered type")
        void shouldReturnFalseForUnregisteredType() {
            assertThat(registry.hasExecutor(StepType.EXECUTE)).isFalse();
        }

        @Test
        @DisplayName("should return true for registered type")
        void shouldReturnTrueForRegisteredType() {
            registry.register(createMockExecutor(StepType.EXECUTE, 0));

            assertThat(registry.hasExecutor(StepType.EXECUTE)).isTrue();
        }
    }

    @Nested
    @DisplayName("GetSupportedTypes Tests")
    class GetSupportedTypesTests {

        @Test
        @DisplayName("should return empty set when no executors registered")
        void shouldReturnEmptySetWhenNoExecutorsRegistered() {
            Set<StepType> types = registry.getSupportedTypes();

            assertThat(types).isEmpty();
        }

        @Test
        @DisplayName("should return all registered types")
        void shouldReturnAllRegisteredTypes() {
            registry.register(createMockExecutor(StepType.EXECUTE, 0));
            registry.register(createMockExecutor(StepType.QUERY, 0));
            registry.register(createMockExecutor(StepType.API_CALL, 0));

            Set<StepType> types = registry.getSupportedTypes();

            assertThat(types).containsExactlyInAnyOrder(
                    StepType.EXECUTE, StepType.QUERY, StepType.API_CALL);
        }
    }

    /**
     * 创建模拟的 StepExecutor
     */
    private StepExecutor createMockExecutor(StepType type, int priority) {
        return new StepExecutor() {
            @Override
            public StepType getSupportedType() {
                return type;
            }

            @Override
            public int getPriority() {
                return priority;
            }

            @Override
            public StepExecutionResult execute(ExecutionStep step, StepExecutionContext context) {
                return StepExecutionResult.success(Map.of());
            }
        };
    }
}
