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
package com.alibaba.assistant.agent.extension.prompt;

import com.alibaba.assistant.agent.common.hook.AgentPhase;
import com.alibaba.assistant.agent.common.hook.HookPhases;
import com.alibaba.assistant.agent.prompt.PromptContributorManager;

/**
 * CodeAct 阶段的 PromptContributor Hook
 * 
 * <p>在 CodeAct Agent（代码生成子 Agent）的 BEFORE_MODEL 阶段执行，
 * 将 PromptContribution 注入到 messages。
 * 
 * <p>通过 {@code @HookPhases(AgentPhase.CODEACT)} 注解声明阶段，
 * 基类会自动从注解中读取阶段信息。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 * @see PromptContributorModelHook
 */
@HookPhases(AgentPhase.CODEACT)
public class CodeactPromptContributorModelHook extends PromptContributorModelHook {

    public CodeactPromptContributorModelHook(PromptContributorManager contributorManager) {
        super(contributorManager);
    }

    public CodeactPromptContributorModelHook(PromptContributorManager contributorManager, int order) {
        super(contributorManager, order);
    }
}
