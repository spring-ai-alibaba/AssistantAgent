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
package com.alibaba.assistant.agent.planning.system;

import java.util.Map;

/**
 * 系统Handler接口
 *
 * <p>基于系统的统一处理器，一个系统对应一个Handler Bean。
 * Handler根据Action的interface_binding配置动态调用不同的系统接口。
 *
 * <h3>设计理念</h3>
 * <ul>
 * <li>一个系统一个Handler（如：oa-system → OaSystemHandler）</li>
 * <li>Handler内部根据Action配置动态调用不同的endpoint</li>
 * <li>统一管理认证、session、错误处理</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Component("oaSystemHandler")
 * public class OaSystemHandler implements SystemHandler {
 *     &#64;Override
 *     public Map<String, Object> execute(
 *             String actionId,
 *             Map<String, Object> params,
 *             Map<String, Object> context) {
 *         // 根据actionId获取配置，调用相应的OA接口
 *     }
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface SystemHandler {

    /**
     * 执行系统Action
     *
     * @param actionId Action ID (如: oa:leave:request)
     * @param params 用户输入参数
     * @param context 上下文（包含assistantUserId等）
     * @return 执行结果
     */
    Map<String, Object> execute(
            String actionId,
            Map<String, Object> params,
            Map<String, Object> context);
}
