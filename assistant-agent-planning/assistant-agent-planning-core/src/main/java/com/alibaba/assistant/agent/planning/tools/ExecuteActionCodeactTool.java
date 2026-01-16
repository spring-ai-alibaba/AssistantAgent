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
import com.alibaba.assistant.agent.planning.model.ExecutionPlan;
import com.alibaba.assistant.agent.planning.model.PlanExecutionResult;
import com.alibaba.assistant.agent.planning.spi.PlanExecutor;
import org.springframework.ai.chat.model.ToolContext;

import java.util.*;

/**
 * 动作执行工具
 *
 * <p>执行指定的执行计划。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ExecuteActionCodeactTool extends BasePlanningCodeactTool {

    private final PlanExecutor planExecutor;

    public ExecuteActionCodeactTool(PlanExecutor planExecutor) {
        super("execute_action", "执行指定的执行计划，返回执行结果。支持继续执行等待用户输入的计划。");
        this.planExecutor = planExecutor;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        String planId = (String) params.get("plan_id");
        Object planObj = params.get("plan");
        Map<String, Object> userInput = null;
        if (params.containsKey("user_input")) {
            Object inputObj = params.get("user_input");
            if (inputObj instanceof Map) {
                userInput = (Map<String, Object>) inputObj;
            }
        }

        logger.info("ExecuteActionCodeactTool#execute - reason=executing action, planId={}", planId);

        ExecutionPlan plan = null;

        // 如果提供了 plan_id，尝试获取现有计划
        if (planId != null && !planId.isBlank()) {
            plan = planExecutor.getStatus(planId);
            if (plan == null) {
                return Map.of("success", false, "error", "Plan not found: " + planId);
            }
        }

        // 如果提供了 plan 对象，解析它
        if (plan == null && planObj != null && planObj instanceof Map) {
            try {
                plan = convertToPlan((Map<String, Object>) planObj);
            } catch (Exception e) {
                return Map.of("success", false, "error", "Invalid plan object: " + e.getMessage());
            }
        }

        if (plan == null) {
            return Map.of("success", false, "error", "Either plan_id or plan is required");
        }

        // 执行或恢复计划
        PlanExecutionResult result;
        if (userInput != null && !userInput.isEmpty()) {
            // 恢复执行
            result = planExecutor.resume(plan, userInput);
        } else {
            // 执行计划
            Map<String, Object> context = new HashMap<>();
            if (toolContext != null) {
                context.putAll(toolContext.getContext());
            }
            result = planExecutor.execute(plan, context);
        }

        return buildResult(result);
    }

    private ExecutionPlan convertToPlan(Map<String, Object> planMap) {
        ExecutionPlan plan = new ExecutionPlan();
        plan.setPlanId((String) planMap.get("plan_id"));
        plan.setActionId((String) planMap.get("action_id"));
        plan.setActionName((String) planMap.get("action_name"));

        if (planMap.containsKey("extracted_parameters")) {
            plan.setExtractedParameters((Map<String, Object>) planMap.get("extracted_parameters"));
        }

        return plan;
    }

    private Map<String, Object> buildResult(PlanExecutionResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.isSuccess());
        response.put("status", result.getStatus() != null ? result.getStatus().name() : null);

        if (result.getErrorMessage() != null) {
            response.put("error", result.getErrorMessage());
        }

        if (result.isNeedsUserInput()) {
            response.put("needs_user_input", true);
            response.put("user_prompt", result.getUserPrompt());
            response.put("options", result.getOptions());
        }

        if (result.getOutput() != null) {
            response.put("output", result.getOutput());
        }

        if (result.getPlan() != null) {
            Map<String, Object> planInfo = new LinkedHashMap<>();
            planInfo.put("plan_id", result.getPlan().getPlanId());
            planInfo.put("current_step", result.getPlan().getCurrentStepIndex());
            planInfo.put("total_steps", result.getPlan().getSteps() != null ? result.getPlan().getSteps().size() : 0);
            response.put("plan", planInfo);
        }

        if (result.getCompletedSteps() != null) {
            response.put("completed_steps", result.getCompletedSteps());
        }

        if (result.getDurationMs() != null) {
            response.put("duration_ms", result.getDurationMs());
        }

        return response;
    }

    @Override
    protected List<ParameterNode> getParameters() {
        return List.of(
                ParameterNode.builder()
                        .name("plan_id")
                        .type(ParameterType.STRING)
                        .description("要执行的计划 ID")
                        .required(false)
                        .build(),
                ParameterNode.builder()
                        .name("plan")
                        .type(ParameterType.OBJECT)
                        .description("执行计划对象（由 plan_action 返回）")
                        .required(false)
                        .build(),
                ParameterNode.builder()
                        .name("user_input")
                        .type(ParameterType.OBJECT)
                        .description("用户输入（用于继续等待输入的计划）")
                        .required(false)
                        .build()
        );
    }

    @Override
    protected String getReturnDescription() {
        return "返回执行结果，包括状态、输出、是否需要用户输入等";
    }

    @Override
    protected List<CodeExample> getCodeExamples() {
        List<CodeExample> examples = new ArrayList<>();

        examples.add(new CodeExample(
                "执行计划",
                """
                # 执行动作计划
                result = execute_action(plan_id="plan-123")
                if result['success']:
                    print(f"执行状态: {result['status']}")
                    if result.get('needs_user_input'):
                        print(f"等待输入: {result['user_prompt']}")
                    else:
                        print(f"执行结果: {result.get('output')}")
                """,
                "返回执行结果"
        ));

        examples.add(new CodeExample(
                "继续执行等待输入的计划",
                """
                # 提供用户输入继续执行
                result = execute_action(plan_id="plan-123", user_input={"selection": "option_1"})
                print(f"执行状态: {result['status']}")
                """,
                "返回继续执行后的结果"
        ));

        return examples;
    }
}
