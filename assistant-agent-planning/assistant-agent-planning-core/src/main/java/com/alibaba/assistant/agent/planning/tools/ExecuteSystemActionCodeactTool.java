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
package com.alibaba.assistant.agent.planning.tools;

import com.alibaba.assistant.agent.common.tools.CodeExample;
import com.alibaba.assistant.agent.common.tools.definition.ParameterNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterType;
import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.spi.ActionProvider;
import com.alibaba.assistant.agent.planning.system.SystemHandler;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.context.ApplicationContext;

import java.util.*;

/**
 * 执行系统Action工具
 *
 * <p>用于执行基于System的Action（如OA请假、ERP操作等）。
 *
 * <p>功能：
 * <ul>
 * <li>根据action_id获取Action定义</li>
 * <li>查找对应的SystemHandler Bean</li>
 * <li>调用Handler.execute()方法</li>
 * <li>返回执行结果</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ExecuteSystemActionCodeactTool extends BasePlanningCodeactTool {

    private final ActionProvider actionProvider;
    private final ApplicationContext applicationContext;

    public ExecuteSystemActionCodeactTool(
            ActionProvider actionProvider,
            ApplicationContext applicationContext) {
        super(
            "execute_system_action",
            "执行系统Action（如OA请假、ERP操作等）。根据action_id调用相应的系统Handler。"
        );
        this.actionProvider = actionProvider;
        this.applicationContext = applicationContext;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        String actionId = (String) params.get("action_id");

        if (actionId == null || actionId.isBlank()) {
            return Map.of(
                "success", false,
                "error", "action_id is required"
            );
        }

        logger.info("ExecuteSystemActionCodeactTool#execute - reason=executing system action, actionId={}", actionId);

        try {
            // 1. 获取Action定义
            ActionDefinition action = actionProvider.getAction(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action不存在: " + actionId));

            // 2. 获取Handler Bean名称
            String handlerBeanName = action.getHandler();
            if (handlerBeanName == null || handlerBeanName.isBlank()) {
                throw new IllegalArgumentException("Action未配置handler: " + actionId);
            }

            // 3. 获取Handler Bean
            SystemHandler handler = getHandlerBean(handlerBeanName);

            // 4. 准备上下文
            Map<String, Object> context = prepareContext(toolContext);

            // 5. 提取用户参数（排除action_id）
            Map<String, Object> actionParams = extractActionParams(params);

            // 6. 执行Action
            Map<String, Object> result = handler.execute(actionId, actionParams, context);

            logger.info("ExecuteSystemActionCodeactTool#execute - reason=action executed, actionId={}, success={}",
                actionId, result.get("success"));

            return result;

        } catch (Exception e) {
            logger.error("ExecuteSystemActionCodeactTool#execute - reason=execution failed, actionId={}, error={}",
                actionId, e.getMessage(), e);

            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "action_id", actionId
            );
        }
    }

    /**
     * 获取Handler Bean
     */
    private SystemHandler getHandlerBean(String beanName) {
        try {
            Object bean = applicationContext.getBean(beanName);
            if (!(bean instanceof SystemHandler)) {
                throw new IllegalArgumentException("Bean不是SystemHandler类型: " + beanName);
            }
            return (SystemHandler) bean;
        } catch (Exception e) {
            throw new IllegalArgumentException("Handler Bean不存在或类型错误: " + beanName, e);
        }
    }

    /**
     * 准备上下文
     */
    private Map<String, Object> prepareContext(ToolContext toolContext) {
        Map<String, Object> context = new HashMap<>();

        // 从toolContext获取用户信息
        if (toolContext != null) {
            // ToolContext.getContext() 返回内部Map
            Map<String, Object> internalContext = toolContext.getContext();
            if (internalContext != null) {
                Object userId = internalContext.get("userId");
                if (userId != null) {
                    context.put("assistantUserId", userId);
                    context.put("userId", userId);
                }
            }
        }

        return context;
    }

    /**
     * 提取Action参数（排除action_id）
     */
    private Map<String, Object> extractActionParams(Map<String, Object> params) {
        Map<String, Object> actionParams = new HashMap<>(params);
        actionParams.remove("action_id");
        return actionParams;
    }

    @Override
    protected List<ParameterNode> getParameters() {
        return List.of(
            ParameterNode.builder()
                .name("action_id")
                .type(ParameterType.STRING)
                .description("Action ID（如: oa:leave:request）")
                .required(true)
                .build(),
            ParameterNode.builder()
                .name("start_date")
                .type(ParameterType.STRING)
                .description("开始日期（根据Action定义）")
                .required(false)
                .build(),
            ParameterNode.builder()
                .name("end_date")
                .type(ParameterType.STRING)
                .description("结束日期（根据Action定义）")
                .required(false)
                .build(),
            ParameterNode.builder()
                .name("types")
                .type(ParameterType.INTEGER)
                .description("请假类型（根据Action定义）")
                .required(false)
                .build(),
            ParameterNode.builder()
                .name("reason")
                .type(ParameterType.STRING)
                .description("请假理由（根据Action定义）")
                .required(false)
                .build(),
            ParameterNode.builder()
                .name("check_uids")
                .type(ParameterType.STRING)
                .description("审批人ID（根据Action定义）")
                .required(false)
                .build()
        );
    }

    @Override
    protected String getReturnDescription() {
        return "返回Action执行结果，包含success、data/error等字段";
    }

    @Override
    protected List<CodeExample> getCodeExamples() {
        List<CodeExample> examples = new ArrayList<>();

        examples.add(new CodeExample(
            "提交OA请假申请",
            """
            # 提交OA请假申请
            result = execute_system_action(
                action_id="oa:leave:request",
                start_date="2026-01-21 09:00",
                end_date="2026-01-22 18:00",
                types=1,
                reason="家中有事",
                check_uids="2"
            )
            if result['success']:
                print("请假申请提交成功")
                print(result.get('message', ''))
            else:
                print("提交失败: " + result.get('error', ''))
            """,
            "提交OA请假申请"
        ));

        return examples;
    }
}
