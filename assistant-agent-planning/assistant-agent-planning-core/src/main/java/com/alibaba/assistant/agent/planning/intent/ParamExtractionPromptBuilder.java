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
package com.alibaba.assistant.agent.planning.intent;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionParameter;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 参数提取 Prompt 构建器
 *
 * <p>统一管理参数提取相关的 LLM Prompt 模板，支持：
 * <ul>
 *     <li>初始参数提取（用户输入包含动作意图）</li>
 *     <li>续轮参数收集（多轮对话中用户提供参数值）</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ParamExtractionPromptBuilder {

    // ====== 系统角色定义 ======
    private static final String SYSTEM_ROLE = "你是一个参数提取专家。";

    // ====== 初始提取场景描述 ======
    private static final String INITIAL_EXTRACTION_TASK = "请根据用户输入和动作定义，提取动作所需的参数。";

    // ====== 续轮收集场景描述 ======
    private static final String CONTINUATION_TASK = "用户正在为一个操作提供参数。";

    // ====== 任务说明（通用） ======
    private static final String TASK_INSTRUCTIONS = """
            ## 任务
            1. 从用户输入中提取动作所需的参数值
            2. 检查是否有必填参数缺失
            3. 如果有缺失的必填参数，生成一个友好的追问问题
            """;

    // ====== 续轮任务说明 ======
    private static final String CONTINUATION_TASK_INSTRUCTIONS = """
            ## 任务
            1. 将用户最新输入解析为合适的参数值
            2. 检查是否还有必填参数缺失
            3. 如果还有缺失的必填参数，生成一个友好的追问问题
            """;

    // ====== 返回格式说明 ======
    private static final String RESPONSE_FORMAT = """
            ## 返回格式（严格 JSON）
            ```json
            {
              "extractedParams": {"参数名": "提取到的值", ...},
              "missingParams": ["缺失的必填参数名", ...],
              "nextQuestion": "如果有缺失参数，生成追问问题，否则为null"
            }
            ```

            请直接返回 JSON，不要有其他内容。
            """;

    /**
     * 构建初始参数提取 Prompt
     *
     * <p>用于用户首次输入包含动作意图的场景。
     *
     * @param action    动作定义
     * @param userInput 用户输入
     * @return 构建好的 Prompt 字符串
     */
    public String buildInitialExtractionPrompt(ActionDefinition action, String userInput) {
        StringBuilder prompt = new StringBuilder();

        // 系统角色 + 场景描述
        prompt.append(SYSTEM_ROLE).append(INITIAL_EXTRACTION_TASK).append("\n\n");

        // 动作定义
        appendActionDefinition(prompt, action);

        // 用户输入
        prompt.append("\n## 用户输入\n");
        prompt.append(userInput).append("\n");

        // 任务说明
        prompt.append("\n").append(TASK_INSTRUCTIONS);

        // 返回格式
        prompt.append("\n").append(RESPONSE_FORMAT);

        return prompt.toString();
    }

    /**
     * 构建续轮参数收集 Prompt
     *
     * <p>用于多轮对话中用户提供参数值的场景。
     *
     * @param action          动作定义
     * @param collectedParams 已收集的参数
     * @param userInput       用户最新输入
     * @return 构建好的 Prompt 字符串
     */
    public String buildContinuationPrompt(ActionDefinition action, Map<String, Object> collectedParams, String userInput) {
        StringBuilder prompt = new StringBuilder();

        // 系统角色 + 场景描述
        prompt.append(SYSTEM_ROLE).append(CONTINUATION_TASK).append("\n\n");

        // 动作定义
        appendActionDefinition(prompt, action);

        // 已收集的参数
        prompt.append("\n## 已收集的参数\n");
        if (collectedParams == null || collectedParams.isEmpty()) {
            prompt.append("（暂无）\n");
        } else {
            collectedParams.forEach((k, v) -> prompt.append("- ").append(k).append(": ").append(v).append("\n"));
        }

        // 用户最新输入
        prompt.append("\n## 用户最新输入\n");
        prompt.append(userInput).append("\n");

        // 任务说明（续轮特定）
        prompt.append("\n").append(CONTINUATION_TASK_INSTRUCTIONS);

        // 返回格式
        prompt.append("\n").append(RESPONSE_FORMAT);

        return prompt.toString();
    }

    /**
     * 追加动作定义到 Prompt
     *
     * @param prompt Prompt StringBuilder
     * @param action 动作定义
     */
    private void appendActionDefinition(StringBuilder prompt, ActionDefinition action) {
        prompt.append("## 动作定义\n");
        prompt.append("- 动作ID: ").append(action.getActionId()).append("\n");
        prompt.append("- 动作名称: ").append(action.getActionName()).append("\n");
        prompt.append("- 描述: ").append(action.getDescription()).append("\n");

        if (action.getParameters() != null && !action.getParameters().isEmpty()) {
            prompt.append("\n## 参数列表\n");
            for (ActionParameter param : action.getParameters()) {
                appendParameterDefinition(prompt, param);
            }
        }
    }

    /**
     * 追加参数定义到 Prompt
     *
     * @param prompt Prompt StringBuilder
     * @param param  参数定义
     */
    private void appendParameterDefinition(StringBuilder prompt, ActionParameter param) {
        prompt.append("- ").append(param.getName());

        // 显示标签
        if (StringUtils.hasText(param.getLabel())) {
            prompt.append(" (").append(param.getLabel()).append(")");
        }

        // 描述
        prompt.append(": ");
        if (StringUtils.hasText(param.getDescription())) {
            prompt.append(param.getDescription());
        } else {
            prompt.append("无描述");
        }

        // 类型和必填信息
        prompt.append(" [类型: ").append(param.getType() != null ? param.getType() : "STRING");
        prompt.append(", 必填: ").append(Boolean.TRUE.equals(param.getRequired()) ? "是" : "否");
        prompt.append("]\n");
    }

    // ====== 单例模式 ======
    private static final ParamExtractionPromptBuilder INSTANCE = new ParamExtractionPromptBuilder();

    private ParamExtractionPromptBuilder() {
    }

    public static ParamExtractionPromptBuilder getInstance() {
        return INSTANCE;
    }
}
