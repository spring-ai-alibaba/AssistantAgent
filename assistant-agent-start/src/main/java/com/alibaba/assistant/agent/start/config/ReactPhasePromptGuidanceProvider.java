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
package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.evaluation.model.EvaluationResult;
import com.alibaba.assistant.agent.extension.prompt.EvaluationBasedPromptContributor;
import com.alibaba.assistant.agent.prompt.PromptContribution;
import com.alibaba.assistant.agent.prompt.PromptContributorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * React 阶段 Prompt 指导提供者
 *
 * <p>根据 React 阶段的评估结果生成 prompt 指导：
 * <ul>
 *   <li>is_fuzzy=模糊：需要让用户明确需求</li>
 *   <li>is_fuzzy=清晰：按照用户的要求进行操作</li>
 * </ul>
 *
 * <p><b>实现说明</b>：由于当前 Hook 机制的限制（{@code PromptContributorModelHook}，
 * 返回的 updates 只能更新 OverAllState，而 {@code AgentLlmNode} 的 systemMessage 来自构建时的固定值，
 * 无法通过 {@code systemTextToAppend} 修改 systemMessage。
 * <p>因此，本实现采用 {@code messagesToAppend} 方式，将指导内容作为 Message 追加到对话历史中。
 * Hook 会将其转换为 AssistantMessage + ToolResponseMessage 的形式注入，LLM 会将其作为上下文信息理解。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class ReactPhasePromptGuidanceProvider extends EvaluationBasedPromptContributor {

    private static final Logger log = LoggerFactory.getLogger(ReactPhasePromptGuidanceProvider.class);

    private static final String CRITERION_IS_FUZZY = "is_fuzzy";
    private static final String SUITE_ID = "react-phase-suite";

    public ReactPhasePromptGuidanceProvider() {
        super("ReactPhasePromptGuidanceProvider", SUITE_ID, 10);
    }

    @Override
    protected boolean shouldContributeBasedOnResult(EvaluationResult result, PromptContributorContext context) {
        // 只要有 is_fuzzy 评估结果就处理
        return result.getCriterionResult(CRITERION_IS_FUZZY) != null;
    }

    @Override
    protected PromptContribution generatePrompt(EvaluationResult result, PromptContributorContext context) {
        Object isFuzzyValue = getCriterionValue(result, CRITERION_IS_FUZZY).orElse(null);
        String isFuzzy = isFuzzyValue != null ? isFuzzyValue.toString() : null;

        log.info("ReactPhasePromptGuidanceProvider#generatePrompt - reason=生成React阶段指导, is_fuzzy={}", isFuzzy);

        StringBuilder sb = new StringBuilder();
        sb.append("\n【React阶段执行指导】\n\n");

        if ("模糊".equals(isFuzzy)) {
            sb.append("【模糊意图处理策略】\n");
            sb.append("当前用户意图被识别为**模糊**，请按以下策略处理：\n\n");
            sb.append("1. 不要立即执行代码或做出重要决策\n");
            sb.append("2. 先向用户澄清具体的需求和意图\n");
            sb.append("3. 可以提供几个可能的理解方向，让用户选择\n");
            sb.append("4. 等用户明确需求后，再进行下一步操作\n\n");
            sb.append("示例回复：「我注意到您的需求还不太明确，请问您是想要...还是...？」\n");
        } else if ("清晰".equals(isFuzzy)) {
            sb.append("【清晰意图处理策略】\n");
            sb.append("当前用户意图被识别为**清晰**，请按以下策略处理：\n\n");
            sb.append("1. 直接按照用户的要求进行操作\n");
            sb.append("2. 无需额外澄清，直接执行\n");
            sb.append("3. 高效完成任务，给出清晰的结果反馈\n");
        } else {
            // 未知值，返回空
            return PromptContribution.empty();
        }

        sb.append("\n【React阶段指导结束】\n");

        // 由于当前 Hook 机制限制，无法通过 systemTextToAppend 修改 systemMessage
        // 改为使用 messagesToAppend，Hook 会将其转换为 AssistantMessage + ToolResponseMessage 注入
        return PromptContribution.builder()
                .append(new UserMessage(sb.toString()))
                .build();
    }
}
