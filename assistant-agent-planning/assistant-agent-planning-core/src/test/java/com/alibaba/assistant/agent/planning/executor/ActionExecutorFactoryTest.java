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
package com.alibaba.assistant.agent.planning.executor;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ExecutionResult;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.alibaba.assistant.agent.planning.spi.ActionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * ActionExecutorFactory 测试
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ActionExecutorFactoryTest {

    @Mock
    private ActionExecutor httpExecutor;

    @Mock
    private ActionExecutor mcpExecutor;

    @Mock
    private ActionExecutor internalExecutor;

    private ActionExecutorFactory factory;

    @BeforeEach
    void setUp() {
        // 配置 mock executor 行为
        when(httpExecutor.getExecutorType()).thenReturn("HTTP");
        when(httpExecutor.getPriority()).thenReturn(100);
        when(httpExecutor.supports("HTTP")).thenReturn(true);

        when(mcpExecutor.getExecutorType()).thenReturn("MCP");
        when(mcpExecutor.getPriority()).thenReturn(90);
        when(mcpExecutor.supports("MCP")).thenReturn(true);

        when(internalExecutor.getExecutorType()).thenReturn("INTERNAL");
        when(internalExecutor.getPriority()).thenReturn(80);
        when(internalExecutor.supports("INTERNAL")).thenReturn(true);

        // 创建工厂
        factory = new ActionExecutorFactory(List.of(httpExecutor, mcpExecutor, internalExecutor));
    }

    @Test
    void testGetExecutor_Http() {
        ActionExecutor executor = factory.getExecutor("HTTP");
        assertNotNull(executor);
        assertEquals("HTTP", executor.getExecutorType());
    }

    @Test
    void testGetExecutor_Mcp() {
        ActionExecutor executor = factory.getExecutor("MCP");
        assertNotNull(executor);
        assertEquals("MCP", executor.getExecutorType());
    }

    @Test
    void testGetExecutor_Internal() {
        ActionExecutor executor = factory.getExecutor("INTERNAL");
        assertNotNull(executor);
        assertEquals("INTERNAL", executor.getExecutorType());
    }

    @Test
    void testGetExecutor_CaseInsensitive() {
        ActionExecutor executor = factory.getExecutor("http");
        assertNotNull(executor);
        assertEquals("HTTP", executor.getExecutorType());
    }

    @Test
    void testGetExecutor_NotFound() {
        ActionExecutor executor = factory.getExecutor("UNKNOWN");
        assertNull(executor);
    }

    @Test
    void testGetExecutor_Null() {
        assertThrows(IllegalArgumentException.class, () -> factory.getExecutor(null));
    }

    @Test
    void testGetExecutor_Empty() {
        assertThrows(IllegalArgumentException.class, () -> factory.getExecutor(""));
    }

    @Test
    void testSupports() {
        assertTrue(factory.supports("HTTP"));
        assertTrue(factory.supports("MCP"));
        assertTrue(factory.supports("INTERNAL"));
        assertFalse(factory.supports("UNKNOWN"));
    }

    @Test
    void testSupports_CaseInsensitive() {
        assertTrue(factory.supports("http"));
        assertTrue(factory.supports("Http"));
    }

    @Test
    void testSupports_Null() {
        assertFalse(factory.supports(null));
    }

    @Test
    void testGetSupportedTypes() {
        var types = factory.getSupportedTypes();
        assertNotNull(types);
        assertEquals(3, types.size());
        assertTrue(types.contains("HTTP"));
        assertTrue(types.contains("MCP"));
        assertTrue(types.contains("INTERNAL"));
    }

    @Test
    void testExecute_Success() throws Exception {
        // 准备测试数据
        ActionDefinition action = createMockAction("HTTP");
        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");

        ExecutionResult mockResult = ExecutionResult.success("test result");
        when(httpExecutor.execute(eq(action), eq(params), isNull())).thenReturn(mockResult);

        // 执行测试
        ExecutionResult result = factory.execute(action, params, null);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test result", result.getResponseData());
    }

    @Test
    void testExecute_ExecutorNotFound() {
        // 准备测试数据 - 未知类型
        ActionDefinition action = createMockAction("UNKNOWN");
        Map<String, Object> params = new HashMap<>();

        // 执行测试
        ExecutionResult result = factory.execute(action, params, null);

        // 验证结果 - 应该返回失败结果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("不支持的 Action 类型"));
    }

    @Test
    void testExecute_NullAction() {
        assertThrows(IllegalArgumentException.class, () -> factory.execute(null, new HashMap<>(), null));
    }

    @Test
    void testExecute_NullBinding() {
        ActionDefinition action = new ActionDefinition();
        action.setActionId("test-action");
        action.setInterfaceBinding(null);

        assertThrows(IllegalArgumentException.class, () -> factory.execute(action, new HashMap<>(), null));
    }

    @Test
    void testExecute_ExceptionHandling() throws Exception {
        // 准备测试数据
        ActionDefinition action = createMockAction("HTTP");
        Map<String, Object> params = new HashMap<>();

        when(httpExecutor.execute(eq(action), eq(params), isNull()))
                .thenThrow(new RuntimeException("Test exception"));

        // 执行测试
        ExecutionResult result = factory.execute(action, params, null);

        // 验证结果 - 应该捕获异常并返回失败结果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("执行失败"));
    }

    @Test
    void testPriority_Selection() {
        // 创建两个相同类型但优先级不同的 executor
        ActionExecutor lowPriority = mockLowPriorityExecutor();
        ActionExecutor highPriority = mockHighPriorityExecutor();

        // 创建工厂
        ActionExecutorFactory priorityFactory = new ActionExecutorFactory(
                List.of(lowPriority, highPriority));

        // 应该选择高优先级的
        ActionExecutor executor = priorityFactory.getExecutor("HTTP");
        assertNotNull(executor);
        assertEquals(200, executor.getPriority());
    }

    /**
     * 创建模拟的 ActionDefinition
     */
    private ActionDefinition createMockAction(String type) {
        ActionDefinition action = new ActionDefinition();
        action.setActionId("test-action");
        action.setActionName("Test Action");

        StepDefinition.InterfaceBinding binding = new StepDefinition.InterfaceBinding();
        binding.setType(type);
        action.setInterfaceBinding(binding);

        return action;
    }

    /**
     * 创建低优先级的 executor
     */
    private ActionExecutor mockLowPriorityExecutor() {
        return new ActionExecutor() {
            @Override
            public String getExecutorType() {
                return "HTTP";
            }

            @Override
            public int getPriority() {
                return 50;
            }

            @Override
            public ExecutionResult execute(ActionDefinition action, Map<String, Object> params, Integer timeoutSeconds) {
                return ExecutionResult.failure("low priority");
            }
        };
    }

    /**
     * 创建高优先级的 executor
     */
    private ActionExecutor mockHighPriorityExecutor() {
        return new ActionExecutor() {
            @Override
            public String getExecutorType() {
                return "HTTP";
            }

            @Override
            public int getPriority() {
                return 200;
            }

            @Override
            public ExecutionResult execute(ActionDefinition action, Map<String, Object> params, Integer timeoutSeconds) {
                return ExecutionResult.success("high priority");
            }
        };
    }
}
