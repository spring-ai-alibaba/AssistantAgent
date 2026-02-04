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
package com.alibaba.assistant.agent.prompt;

import java.util.List;

/**
 * Prompt 贡献者管理器接口
 * 协调多个 PromptContributor 生成最终的 PromptContribution
 *
 * @author Assistant Agent Team
 */
public interface PromptContributorManager {

    /**
     * 组装所有贡献者的内容
     *
     * @param context 上下文
     * @return 合并后的 PromptContribution
     */
    PromptContribution assemble(PromptContributorContext context);

    /**
     * 获取所有注册的贡献者
     *
     * @return 贡献者列表
     */
    List<PromptContributor> getContributors();

    /**
     * 注册贡献者
     *
     * @param contributor 要注册的贡献者
     */
    void register(PromptContributor contributor);

    /**
     * 移除贡献者
     *
     * @param contributorName 贡献者名称
     */
    void unregister(String contributorName);
}

