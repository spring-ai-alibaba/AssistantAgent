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
package com.alibaba.assistant.agent.planning.spi;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ExecutionResult;

import java.util.Map;

/**
 * Action 执行器 SPI 接口
 *
 * <p>定义 Action 执行器的标准接口，支持多种执行类型：
 * <ul>
 * <li>HTTP: RESTful API 调用</li>
 * <li>MCP: Model Context Protocol 工具调用</li>
 * <li>INTERNAL: Spring Bean 方法调用</li>
 * <li>DATA_AGENT: DataAgent 查询执行</li>
 * </ul>
 *
 * <h3>扩展方式</h3>
 * <p>实现此接口并注册为 Spring Bean 即可扩展新的执行器类型。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Component
 * public class CustomExecutor implements ActionExecutor {
 *     &#64;Override
 *     public String getExecutorType() {
 *         return "CUSTOM";
 *     }
 *
 *     &#64;Override
 *     public ExecutionResult execute(ActionDefinition action, Map&lt;String, Object&gt; params, Integer timeoutSeconds) {
 *         // 实现自定义执行逻辑
 *         return ExecutionResult.builder()
 *             .success(true)
 *             .response("执行成功")
 *             .build();
 *     }
 *
 *     &#64;Override
 *     public int getPriority() {
 *         return 100;
 *     }
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface ActionExecutor {

    /**
     * 获取执行器类型
     *
     * <p>此值与 {@link ActionDefinition.ActionBinding#getType() 对应，
     * 用于路由 Action 到正确的执行器。
     *
     * @return 执行器类型标识（如：HTTP, MCP, INTERNAL, DATA_AGENT）
     */
    String getExecutorType();

    /**
     * 执行 Action
     *
     * @param action         Action 定义
     * @param params         参数值
     * @param timeoutSeconds 超时时间（秒），如果为 null 则使用默认值
     * @return 执行结果
     */
    ExecutionResult execute(ActionDefinition action, Map<String, Object> params, Integer timeoutSeconds);

    /**
     * 获取优先级
     *
     * <p>当多个执行器支持同一种类型时，优先级高的优先使用。
     * 默认优先级为 0，值越大优先级越高。
     *
     * @return 优先级
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 检查是否支持指定的 Action 类型
     *
     * @param type Action 绑定类型
     * @return 如果支持返回 true
     */
    default boolean supports(String type) {
        return getExecutorType().equalsIgnoreCase(type);
    }
}
