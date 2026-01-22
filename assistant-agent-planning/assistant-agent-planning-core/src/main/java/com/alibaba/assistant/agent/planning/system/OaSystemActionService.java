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

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OA系统Action服务（供InternalExecutor调用）
 *
 * <p>这个类提供一个简化的方法签名，适配InternalExecutor的调用方式。
 * 内部委托给OaSystemHandler执行。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component("oaSystemActionService")
public class OaSystemActionService {

    private final OaSystemHandler oaSystemHandler;

    public OaSystemActionService(OaSystemHandler oaSystemHandler) {
        this.oaSystemHandler = oaSystemHandler;
    }

    /**
     * 执行OA系统Action（适配方法）
     *
     * <p>这个方法签名适配InternalExecutor的调用方式：
     * <ul>
     * <li>actionId: 从params中提取，或使用默认值</li>
     * <li>params: 从params中提取</li>
     * <li>context: 从params中提取，或创建空context</li>
     * </ul>
     *
     * @param params 包含actionId、用户参数、context的Map
     * @return 执行结果
     */
    public Map<String, Object> execute(Map<String, Object> params) {
        // 1. 提取actionId（有默认值）
        String actionId = (String) params.getOrDefault("action_id", "oa:leave:request");

        // 2. 提取用户参数（排除action_id和context）
        Map<String, Object> actionParams = extractActionParams(params);

        // 3. 提取context（或创建空的）
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) params.get("context");

        // 4. 委托给OaSystemHandler
        return oaSystemHandler.execute(actionId, actionParams, context);
    }

    /**
     * 提取Action参数（排除系统参数）
     */
    private Map<String, Object> extractActionParams(Map<String, Object> params) {
        Map<String, Object> actionParams = new java.util.HashMap<>(params);

        // 移除系统参数
        actionParams.remove("action_id");
        actionParams.remove("context");

        return actionParams;
    }
}
