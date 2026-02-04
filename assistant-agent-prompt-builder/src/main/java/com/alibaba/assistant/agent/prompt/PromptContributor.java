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

/**
 * Prompt 贡献者接口
 *
 * @author Assistant Agent Team
 */
public interface PromptContributor {

    /**
     * 获取贡献者名称
     *
     * @return 贡献者名称
     */
    String getName();

    /**
     * 判断是否应该贡献内容
     *
     * @param context 上下文
     * @return true 表示应该贡献
     */
    boolean shouldContribute(PromptContributorContext context);

    /**
     * 生成 Prompt 贡献内容
     *
     * @param context 上下文
     * @return Prompt 贡献，返回 null 或 empty 表示不贡献
     */
    PromptContribution contribute(PromptContributorContext context);

    /**
     * 获取优先级（数值越小优先级越高）
     *
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }
}

