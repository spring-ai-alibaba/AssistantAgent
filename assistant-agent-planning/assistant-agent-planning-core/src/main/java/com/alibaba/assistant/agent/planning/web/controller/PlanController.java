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
package com.alibaba.assistant.agent.planning.web.controller;

import com.alibaba.assistant.agent.planning.model.*;
import com.alibaba.assistant.agent.planning.spi.*;
import com.alibaba.assistant.agent.planning.web.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 计划执行 Controller
 *
 * <p>提供动作的规划和执行接口。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private static final Logger logger = LoggerFactory.getLogger(PlanController.class);

    private final ActionProvider actionProvider;
    private final PlanGenerator planGenerator;
    private final PlanExecutor planExecutor;

    // 存储执行计划，用于后续查询和恢复
    private final Map<String, ExecutionPlan> planCache = new ConcurrentHashMap<>();

    public PlanController(ActionProvider actionProvider,
                          PlanGenerator planGenerator,
                          PlanExecutor planExecutor) {
        this.actionProvider = actionProvider;
        this.planGenerator = planGenerator;
        this.planExecutor = planExecutor;
    }

    /**
     * 创建执行计划（不执行）
     */
    @PostMapping("/create")
    public ApiResponse<ExecutionPlanResponse> createPlan(@RequestBody PlanExecuteRequest request) {
        logger.info("PlanController#createPlan - reason=creating plan, actionId={}, userInput={}",
                request.getActionId(), request.getUserInput());

        try {
            // 1. 获取动作定义
            Optional<ActionDefinition> actionOpt = actionProvider.getAction(request.getActionId());
            if (actionOpt.isEmpty()) {
                return ApiResponse.notFound("动作不存在: " + request.getActionId());
            }

            ActionDefinition action = actionOpt.get();
            if (!Boolean.TRUE.equals(action.getEnabled())) {
                return ApiResponse.badRequest("动作已禁用: " + request.getActionId());
            }

            // 2. 生成执行计划
            Map<String, Object> params = request.getParameters() != null ? request.getParameters() : Collections.emptyMap();
            PlanGenerator.PlanGenerationContext context = createGenerationContext(request.getUserInput(), request.getContext());

            ExecutionPlan plan = planGenerator.generate(action, params, context);

            // 3. 缓存计划
            planCache.put(plan.getPlanId(), plan);

            logger.info("PlanController#createPlan - reason=plan created, planId={}, stepCount={}",
                    plan.getPlanId(), plan.getSteps() != null ? plan.getSteps().size() : 0);

            return ApiResponse.success("执行计划创建成功", ExecutionPlanResponse.from(plan));

        } catch (Exception e) {
            logger.error("PlanController#createPlan - reason=failed to create plan, error={}", e.getMessage(), e);
            return ApiResponse.error("创建执行计划失败: " + e.getMessage());
        }
    }

    /**
     * 执行计划
     */
    @PostMapping("/execute")
    public ApiResponse<PlanExecuteResponse> executePlan(@RequestBody PlanExecuteRequest request) {
        logger.info("PlanController#executePlan - reason=executing plan, actionId={}, userInput={}",
                request.getActionId(), request.getUserInput());

        try {
            // 1. 获取动作定义
            Optional<ActionDefinition> actionOpt = actionProvider.getAction(request.getActionId());
            if (actionOpt.isEmpty()) {
                return ApiResponse.notFound("动作不存在: " + request.getActionId());
            }

            ActionDefinition action = actionOpt.get();
            if (!Boolean.TRUE.equals(action.getEnabled())) {
                return ApiResponse.badRequest("动作已禁用: " + request.getActionId());
            }

            // 2. 生成执行计划
            Map<String, Object> params = request.getParameters() != null ? request.getParameters() : Collections.emptyMap();
            PlanGenerator.PlanGenerationContext genContext = createGenerationContext(request.getUserInput(), request.getContext());

            ExecutionPlan plan = planGenerator.generate(action, params, genContext);

            // 3. 执行计划
            Map<String, Object> execContext = request.getContext() != null ? request.getContext() : Collections.emptyMap();
            PlanExecutionResult result = planExecutor.execute(plan, execContext);

            // 4. 缓存计划（用于后续查询）
            planCache.put(plan.getPlanId(), plan);

            logger.info("PlanController#executePlan - reason=plan executed, planId={}, success={}, status={}",
                    plan.getPlanId(), result.isSuccess(), result.getStatus());

            return ApiResponse.success(PlanExecuteResponse.from(result));

        } catch (Exception e) {
            logger.error("PlanController#executePlan - reason=failed to execute plan, error={}", e.getMessage(), e);
            return ApiResponse.error("执行计划失败: " + e.getMessage());
        }
    }

    /**
     * 一键匹配并执行（智能模式）
     *
     * <p>根据用户输入自动匹配动作并执行。
     */
    @PostMapping("/smart-execute")
    public ApiResponse<SmartExecuteResponse> smartExecute(@RequestBody ActionMatchRequest request) {
        logger.info("PlanController#smartExecute - reason=smart executing, userInput={}", request.getUserInput());

        try {
            if (request.getUserInput() == null || request.getUserInput().isBlank()) {
                return ApiResponse.badRequest("用户输入不能为空");
            }

            // 1. 匹配动作
            Map<String, Object> context = request.getContext() != null ? request.getContext() : Collections.emptyMap();
            List<ActionMatch> matches = actionProvider.matchActions(request.getUserInput(), context);

            if (matches.isEmpty()) {
                return ApiResponse.success("未找到匹配的动作", SmartExecuteResponse.builder()
                        .userInput(request.getUserInput())
                        .matched(false)
                        .message("未找到匹配的动作，请尝试其他表述")
                        .build());
            }

            // 2. 选择最佳匹配
            ActionMatch bestMatch = matches.get(0);
            ActionDefinition action = bestMatch.getAction();

            // 3. 生成并执行计划
            PlanGenerator.PlanGenerationContext genContext = createGenerationContext(request.getUserInput(), context);

            ExecutionPlan plan = planGenerator.generate(action, Collections.emptyMap(), genContext);
            PlanExecutionResult result = planExecutor.execute(plan, context);

            // 4. 缓存计划
            planCache.put(plan.getPlanId(), plan);

            logger.info("PlanController#smartExecute - reason=smart execute completed, actionId={}, success={}",
                    action.getActionId(), result.isSuccess());

            return ApiResponse.success(SmartExecuteResponse.builder()
                    .userInput(request.getUserInput())
                    .matched(true)
                    .matchedAction(ActionMatchResponse.MatchResult.from(bestMatch))
                    .confidence(bestMatch.getConfidence())
                    .executionResult(PlanExecuteResponse.from(result))
                    .message(result.isSuccess() ? "执行成功" : "执行失败: " + result.getErrorMessage())
                    .build());

        } catch (Exception e) {
            logger.error("PlanController#smartExecute - reason=failed to smart execute, error={}", e.getMessage(), e);
            return ApiResponse.error("智能执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取计划详情
     */
    @GetMapping("/{planId}")
    public ApiResponse<ExecutionPlanResponse> getPlan(@PathVariable String planId) {
        logger.debug("PlanController#getPlan - reason=getting plan, planId={}", planId);

        ExecutionPlan plan = planCache.get(planId);
        if (plan == null) {
            return ApiResponse.notFound("计划不存在: " + planId);
        }

        return ApiResponse.success(ExecutionPlanResponse.from(plan));
    }

    /**
     * 恢复执行计划
     */
    @PostMapping("/{planId}/resume")
    public ApiResponse<PlanExecuteResponse> resumePlan(@PathVariable String planId,
                                                       @RequestBody(required = false) Map<String, Object> additionalParams) {
        logger.info("PlanController#resumePlan - reason=resuming plan, planId={}", planId);

        try {
            ExecutionPlan plan = planCache.get(planId);
            if (plan == null) {
                return ApiResponse.notFound("计划不存在: " + planId);
            }

            // 合并额外参数
            Map<String, Object> userInput = additionalParams != null ? additionalParams : Collections.emptyMap();
            PlanExecutionResult result = planExecutor.resume(plan, userInput);

            // 更新缓存
            if (result.getPlan() != null) {
                planCache.put(plan.getPlanId(), result.getPlan());
            }

            logger.info("PlanController#resumePlan - reason=plan resumed, planId={}, success={}",
                    planId, result.isSuccess());

            return ApiResponse.success(PlanExecuteResponse.from(result));

        } catch (Exception e) {
            logger.error("PlanController#resumePlan - reason=failed to resume plan, error={}", e.getMessage(), e);
            return ApiResponse.error("恢复执行计划失败: " + e.getMessage());
        }
    }

    /**
     * 取消执行计划
     */
    @PostMapping("/{planId}/cancel")
    public ApiResponse<Void> cancelPlan(@PathVariable String planId) {
        logger.info("PlanController#cancelPlan - reason=canceling plan, planId={}", planId);

        try {
            ExecutionPlan plan = planCache.get(planId);
            if (plan == null) {
                return ApiResponse.notFound("计划不存在: " + planId);
            }

            planExecutor.cancel(planId);
            planCache.remove(planId);

            logger.info("PlanController#cancelPlan - reason=plan canceled, planId={}", planId);
            return ApiResponse.success("计划已取消", null);

        } catch (Exception e) {
            logger.error("PlanController#cancelPlan - reason=failed to cancel plan, error={}", e.getMessage(), e);
            return ApiResponse.error("取消计划失败: " + e.getMessage());
        }
    }

    /**
     * 验证执行计划
     */
    @PostMapping("/validate")
    public ApiResponse<ValidationResponse> validatePlan(@RequestBody PlanExecuteRequest request) {
        logger.debug("PlanController#validatePlan - reason=validating plan, actionId={}", request.getActionId());

        try {
            Optional<ActionDefinition> actionOpt = actionProvider.getAction(request.getActionId());
            if (actionOpt.isEmpty()) {
                return ApiResponse.notFound("动作不存在: " + request.getActionId());
            }

            ActionDefinition action = actionOpt.get();
            Map<String, Object> params = request.getParameters() != null ? request.getParameters() : Collections.emptyMap();

            PlanGenerator.PlanGenerationContext context = createGenerationContext(request.getUserInput(), request.getContext());

            ExecutionPlan plan = planGenerator.generate(action, params, context);
            PlanGenerator.ValidationResult validationResult = planGenerator.validate(plan);

            List<String> errors = new ArrayList<>();
            if (!validationResult.isValid() && validationResult.getMessage() != null) {
                errors.add(validationResult.getMessage());
            }
            if (validationResult.getFieldErrors() != null) {
                validationResult.getFieldErrors().forEach((field, error) ->
                    errors.add(field + ": " + error));
            }

            return ApiResponse.success(ValidationResponse.builder()
                    .valid(validationResult.isValid())
                    .errors(errors)
                    .warnings(Collections.emptyList())
                    .build());

        } catch (Exception e) {
            logger.error("PlanController#validatePlan - reason=failed to validate plan, error={}", e.getMessage(), e);
            return ApiResponse.error("验证计划失败: " + e.getMessage());
        }
    }

    /**
     * 创建 PlanGenerationContext
     */
    private PlanGenerator.PlanGenerationContext createGenerationContext(String userInput, Map<String, Object> sessionContext) {
        return new PlanGenerator.PlanGenerationContext() {
            @Override
            public String getSessionId() {
                return sessionContext != null ? (String) sessionContext.get("sessionId") : null;
            }

            @Override
            public String getUserId() {
                return sessionContext != null ? (String) sessionContext.get("userId") : null;
            }

            @Override
            public String getUserInput() {
                return userInput;
            }

            @Override
            public Map<String, Object> getContextVariables() {
                return sessionContext != null ? sessionContext : Collections.emptyMap();
            }

            @Override
            public Integer getTimeoutMinutes() {
                return sessionContext != null ? (Integer) sessionContext.get("timeoutMinutes") : null;
            }
        };
    }

    /**
     * 执行计划响应（包含计划详情）
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ExecutionPlanResponse {
        private String planId;
        private String actionId;
        private String actionName;
        private String userInput;
        private Map<String, Object> extractedParameters;
        private List<StepInfo> steps;
        private PlanStatus status;

        @lombok.Data
        @lombok.Builder
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class StepInfo {
            private String stepId;
            private String stepName;
            private String stepType;
            private int order;
            private String status;
        }

        public static ExecutionPlanResponse from(ExecutionPlan plan) {
            List<StepInfo> stepInfos = null;
            if (plan.getSteps() != null) {
                stepInfos = new ArrayList<>();
                for (ExecutionStep step : plan.getSteps()) {
                    stepInfos.add(StepInfo.builder()
                            .stepId(step.getStepId())
                            .stepName(step.getName())
                            .stepType(step.getType() != null ? step.getType().name() : null)
                            .order(step.getOrder() != null ? step.getOrder() : 0)
                            .status(step.getStatus() != null ? step.getStatus().name() : null)
                            .build());
                }
            }

            return ExecutionPlanResponse.builder()
                    .planId(plan.getPlanId())
                    .actionId(plan.getActionId())
                    .actionName(plan.getActionName())
                    .userInput(plan.getUserInput())
                    .extractedParameters(plan.getExtractedParameters())
                    .steps(stepInfos)
                    .status(plan.getStatus())
                    .build();
        }
    }

    /**
     * 智能执行响应
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SmartExecuteResponse {
        private String userInput;
        private boolean matched;
        private ActionMatchResponse.MatchResult matchedAction;
        private double confidence;
        private PlanExecuteResponse executionResult;
        private String message;
    }

    /**
     * 验证响应
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationResponse {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
    }
}
