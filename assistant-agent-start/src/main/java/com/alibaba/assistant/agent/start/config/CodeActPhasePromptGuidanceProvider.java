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
 * CodeAct 阶段 Prompt 指导提供者
 *
 * <p>根据 CodeAct 阶段的评估结果生成 prompt 指导：
 * <ul>
 *   <li>purpose=咨询：只需要写一次"查询"的代码并执行，随后根据结果回答用户即可</li>
 *   <li>purpose=操作：需要先写代码，然后反问用户是否执行，用户说执行再开始执行代码</li>
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
public class CodeActPhasePromptGuidanceProvider extends EvaluationBasedPromptContributor {

    private static final Logger log = LoggerFactory.getLogger(CodeActPhasePromptGuidanceProvider.class);

    private static final String CRITERION_PURPOSE = "purpose";
    private static final String SUITE_ID = "codeact-phase-suite";

    public CodeActPhasePromptGuidanceProvider() {
        super("CodeActPhasePromptGuidanceProvider", SUITE_ID, 10);
    }

    @Override
    protected boolean shouldContributeBasedOnResult(EvaluationResult result, PromptContributorContext context) {
        // 只要有 purpose 评估结果就处理
        return result.getCriterionResult(CRITERION_PURPOSE) != null;
    }

    @Override
    protected PromptContribution generatePrompt(EvaluationResult result, PromptContributorContext context) {
        Object purposeValue = getCriterionValue(result, CRITERION_PURPOSE).orElse(null);
        String purpose = purposeValue != null ? purposeValue.toString() : null;

        log.info("CodeActPhasePromptGuidanceProvider#generatePrompt - reason=生成CodeAct阶段指导, purpose={}", purpose);

        StringBuilder sb = new StringBuilder();
        sb.append("\n【CodeAct阶段执行指导】\n\n");

        if ("咨询".equals(purpose)) {
            sb.append("【咨询类请求处理策略】\n");
            sb.append("当前请求被识别为**咨询类**请求，请按以下策略处理：\n\n");
            sb.append("1. 只需要编写一次「查询」类型的代码并执行\n");
            sb.append("2. 获取执行结果后，直接根据结果回答用户的问题\n");
            sb.append("3. 不需要反问用户是否执行，直接执行查询即可\n");
            sb.append("4. 回答时要清晰、简洁、专业\n");
        } else if ("操作".equals(purpose)) {
            sb.append("【操作类请求处理策略】\n");
            sb.append("当前请求被识别为**操作类**请求，请按以下策略处理：\n\n");
            sb.append("1. 首先编写要执行的代码，但**不要立即执行**\n");
            sb.append("2. 向用户展示代码内容，并询问用户是否确认执行\n");
            sb.append("3. 等待用户明确说「执行」、「确认」、「是」等肯定词后，再开始执行代码\n");
            sb.append("4. 执行完成后，向用户报告执行结果\n");
            sb.append("5. 对于可能产生副作用的操作（如删除、修改），务必先确认\n\n");
            sb.append("示例对话流程：\n");
            sb.append("- 用户：帮我创建一个新文件\n");
            sb.append("- 助手：我已准备好创建文件的代码：[代码内容]。请确认是否执行？\n");
            sb.append("- 用户：执行\n");
            sb.append("- 助手：[执行代码] 文件创建成功！\n");
        } else {
            // 未知值，返回空
            return PromptContribution.empty();
        }

        sb.append("\n【CodeAct阶段指导结束】\n");

        // 由于当前 Hook 机制限制，无法通过 systemTextToAppend 修改 systemMessage
        // 改为使用 messagesToAppend，Hook 会将其转换为 AssistantMessage + ToolResponseMessage 注入
        return PromptContribution.builder()
                .append(new UserMessage(sb.toString()))
                .build();
    }
}
