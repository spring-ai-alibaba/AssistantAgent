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

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionMatch;
import com.alibaba.assistant.agent.planning.spi.ActionProvider;
import com.alibaba.assistant.agent.planning.spi.ActionRepository;
import com.alibaba.assistant.agent.planning.vector.ActionVectorizationService;
import com.alibaba.assistant.agent.planning.intent.PlanningIntentHook;
import com.alibaba.assistant.agent.planning.intent.UnifiedIntentRecognitionHook;
import com.alibaba.assistant.agent.planning.web.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 动作管理 Controller
 *
 * <p>提供动作的 CRUD 和匹配接口。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/actions")
public class ActionController {

    private static final Logger logger = LoggerFactory.getLogger(ActionController.class);

    private final ActionRepository actionRepository;
    private final ActionProvider actionProvider;
    private final ActionVectorizationService vectorizationService;  // 可选，ES 启用时注入
    private final PlanningIntentHook planningIntentHook;  // 可选，用于刷新关键词索引（原始版本）
    private final UnifiedIntentRecognitionHook unifiedIntentHook;  // 可选，用于刷新关键词索引（统一版本）

    public ActionController(ActionRepository actionRepository,
                            ActionProvider actionProvider,
                            @Autowired(required = false) ActionVectorizationService vectorizationService,
                            @Autowired(required = false) PlanningIntentHook planningIntentHook,
                            @Autowired(required = false) UnifiedIntentRecognitionHook unifiedIntentHook) {
        this.actionRepository = actionRepository;
        this.actionProvider = actionProvider;
        this.vectorizationService = vectorizationService;
        this.planningIntentHook = planningIntentHook;
        this.unifiedIntentHook = unifiedIntentHook;
    }

    /**
     * 创建动作
     */
    @PostMapping
    public ApiResponse<ActionResponse> createAction(@RequestBody ActionRequest request) {
        logger.info("ActionController#createAction - reason=creating action, actionName={}", request.getActionName());

        try {
            // 生成 actionId
            String actionId = request.getActionId();
            if (actionId == null || actionId.isBlank()) {
                actionId = "action_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            }

            // 检查是否已存在
            if (actionRepository.existsById(actionId)) {
                return ApiResponse.badRequest("动作ID已存在: " + actionId);
            }

            ActionDefinition definition = buildDefinition(actionId, request);
            ActionDefinition saved = actionRepository.save(definition);

            // 向量化索引（ES 启用时）
            if (vectorizationService != null) {
                try {
                    vectorizationService.indexAction(saved);
                    logger.info("ActionController#createAction - reason=action vectorized, actionId={}", saved.getActionId());
                } catch (Exception e) {
                    logger.warn("ActionController#createAction - reason=vectorization failed, actionId={}, error={}",
                            saved.getActionId(), e.getMessage());
                }
            }

            // 刷新关键词索引（支持两种Hook）
            if (planningIntentHook != null) {
                planningIntentHook.registerAction(saved);
            }
            if (unifiedIntentHook != null) {
                unifiedIntentHook.registerAction(saved);
            }

            logger.info("ActionController#createAction - reason=action created, actionId={}", saved.getActionId());
            return ApiResponse.success("动作创建成功", ActionResponse.from(saved));

        } catch (Exception e) {
            logger.error("ActionController#createAction - reason=failed to create action, error={}", e.getMessage(), e);
            return ApiResponse.error("创建动作失败: " + e.getMessage());
        }
    }

    /**
     * 更新动作
     */
    @PutMapping("/{actionId}")
    public ApiResponse<ActionResponse> updateAction(@PathVariable String actionId,
                                                    @RequestBody ActionRequest request) {
        logger.info("ActionController#updateAction - reason=updating action, actionId={}", actionId);

        try {
            Optional<ActionDefinition> existing = actionRepository.findById(actionId);
            if (existing.isEmpty()) {
                return ApiResponse.notFound("动作不存在: " + actionId);
            }

            ActionDefinition definition = buildDefinition(actionId, request);
            ActionDefinition saved = actionRepository.save(definition);

            // 更新向量化索引（ES 启用时）
            if (vectorizationService != null) {
                try {
                    vectorizationService.indexAction(saved);
                    logger.info("ActionController#updateAction - reason=action re-vectorized, actionId={}", actionId);
                } catch (Exception e) {
                    logger.warn("ActionController#updateAction - reason=vectorization failed, actionId={}, error={}",
                            actionId, e.getMessage());
                }
            }

            // 刷新关键词索引（支持两种Hook）
            if (planningIntentHook != null) {
                planningIntentHook.removeAction(actionId);
                planningIntentHook.registerAction(saved);
            }
            if (unifiedIntentHook != null) {
                unifiedIntentHook.removeAction(actionId);
                unifiedIntentHook.registerAction(saved);
            }

            logger.info("ActionController#updateAction - reason=action updated, actionId={}", actionId);
            return ApiResponse.success("动作更新成功", ActionResponse.from(saved));

        } catch (Exception e) {
            logger.error("ActionController#updateAction - reason=failed to update action, error={}", e.getMessage(), e);
            return ApiResponse.error("更新动作失败: " + e.getMessage());
        }
    }

    /**
     * 获取动作详情
     */
    @GetMapping("/{actionId}")
    public ApiResponse<ActionResponse> getAction(@PathVariable String actionId) {
        logger.debug("ActionController#getAction - reason=getting action, actionId={}", actionId);

        Optional<ActionDefinition> action = actionRepository.findById(actionId);
        if (action.isEmpty()) {
            return ApiResponse.notFound("动作不存在: " + actionId);
        }

        return ApiResponse.success(ActionResponse.from(action.get()));
    }

    /**
     * 获取所有动作
     */
    @GetMapping
    public ApiResponse<List<ActionResponse>> listActions(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "tags", required = false) List<String> tags,
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {

        logger.debug("ActionController#listActions - reason=listing actions, category={}, tags={}", category, tags);

        try {
            List<ActionDefinition> actions;

            if (category != null && !category.isBlank()) {
                actions = actionProvider.getActionsByCategory(category);
            } else if (tags != null && !tags.isEmpty()) {
                actions = actionProvider.getActionsByTags(tags);
            } else if (enabled != null) {
                actions = actionRepository.findByEnabled(enabled);
            } else {
                actions = actionProvider.getAllActions();
            }

            List<ActionResponse> responses = actions.stream()
                    .limit(limit)
                    .map(ActionResponse::from)
                    .collect(Collectors.toList());

            return ApiResponse.success(responses);

        } catch (Exception e) {
            logger.error("ActionController#listActions - reason=failed to list actions, error={}", e.getMessage(), e);
            return ApiResponse.error("获取动作列表失败: " + e.getMessage());
        }
    }

    /**
     * 删除动作
     */
    @DeleteMapping("/{actionId}")
    public ApiResponse<Void> deleteAction(@PathVariable String actionId) {
        logger.info("ActionController#deleteAction - reason=deleting action, actionId={}", actionId);

        try {
            if (!actionRepository.existsById(actionId)) {
                return ApiResponse.notFound("动作不存在: " + actionId);
            }

            boolean deleted = actionRepository.deleteById(actionId);
            if (deleted) {
                // 删除向量索引
                if (vectorizationService != null) {
                    try {
                        vectorizationService.deleteAction(actionId);
                    } catch (Exception e) {
                        logger.warn("ActionController#deleteAction - reason=failed to delete from ES, actionId={}", actionId);
                    }
                }

                // 从关键词索引移除
                if (planningIntentHook != null) {
                    planningIntentHook.removeAction(actionId);
                }

                logger.info("ActionController#deleteAction - reason=action deleted, actionId={}", actionId);
                return ApiResponse.success("动作删除成功", null);
            } else {
                return ApiResponse.error("删除动作失败");
            }

        } catch (Exception e) {
            logger.error("ActionController#deleteAction - reason=failed to delete action, error={}", e.getMessage(), e);
            return ApiResponse.error("删除动作失败: " + e.getMessage());
        }
    }

    /**
     * 匹配动作
     */
    @PostMapping("/match")
    public ApiResponse<ActionMatchResponse> matchActions(@RequestBody ActionMatchRequest request) {
        logger.info("ActionController#matchActions - reason=matching actions, userInput={}", request.getUserInput());

        try {
            if (request.getUserInput() == null || request.getUserInput().isBlank()) {
                return ApiResponse.badRequest("用户输入不能为空");
            }

            Map<String, Object> context = request.getContext() != null ? request.getContext() : Collections.emptyMap();
            List<ActionMatch> matches = actionProvider.matchActions(request.getUserInput(), context);

            // 限制返回数量
            int maxMatches = request.getMaxMatches() != null ? request.getMaxMatches() : 5;
            if (matches.size() > maxMatches) {
                matches = matches.subList(0, maxMatches);
            }

            ActionMatchResponse response = ActionMatchResponse.from(matches, request.getUserInput());

            logger.info("ActionController#matchActions - reason=matching completed, matchCount={}", response.getMatchCount());
            return ApiResponse.success(response);

        } catch (Exception e) {
            logger.error("ActionController#matchActions - reason=failed to match actions, error={}", e.getMessage(), e);
            return ApiResponse.error("匹配动作失败: " + e.getMessage());
        }
    }

    /**
     * 搜索动作
     */
    @GetMapping("/search")
    public ApiResponse<List<ActionResponse>> searchActions(
            @RequestParam(name = "keyword") String keyword,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {

        logger.debug("ActionController#searchActions - reason=searching actions, keyword={}", keyword);

        try {
            List<ActionDefinition> actions = actionProvider.searchActions(keyword, limit);

            List<ActionResponse> responses = actions.stream()
                    .map(ActionResponse::from)
                    .collect(Collectors.toList());

            return ApiResponse.success(responses);

        } catch (Exception e) {
            logger.error("ActionController#searchActions - reason=failed to search actions, error={}", e.getMessage(), e);
            return ApiResponse.error("搜索动作失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有分类
     */
    @GetMapping("/categories")
    public ApiResponse<List<String>> getCategories() {
        try {
            List<String> categories = actionProvider.getAllCategories();
            return ApiResponse.success(categories);
        } catch (Exception e) {
            logger.error("ActionController#getCategories - reason=failed to get categories, error={}", e.getMessage(), e);
            return ApiResponse.error("获取分类失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有标签
     */
    @GetMapping("/tags")
    public ApiResponse<List<String>> getTags() {
        try {
            List<String> tags = actionProvider.getAllTags();
            return ApiResponse.success(tags);
        } catch (Exception e) {
            logger.error("ActionController#getTags - reason=failed to get tags, error={}", e.getMessage(), e);
            return ApiResponse.error("获取标签失败: " + e.getMessage());
        }
    }

    /**
     * 启用动作
     */
    @PostMapping("/{actionId}/enable")
    public ApiResponse<ActionResponse> enableAction(@PathVariable String actionId) {
        return toggleActionEnabled(actionId, true);
    }

    /**
     * 禁用动作
     */
    @PostMapping("/{actionId}/disable")
    public ApiResponse<ActionResponse> disableAction(@PathVariable String actionId) {
        return toggleActionEnabled(actionId, false);
    }

    private ApiResponse<ActionResponse> toggleActionEnabled(String actionId, boolean enabled) {
        logger.info("ActionController#toggleActionEnabled - reason=toggling action enabled, actionId={}, enabled={}",
                actionId, enabled);

        try {
            Optional<ActionDefinition> existing = actionRepository.findById(actionId);
            if (existing.isEmpty()) {
                return ApiResponse.notFound("动作不存在: " + actionId);
            }

            ActionDefinition definition = existing.get();
            ActionDefinition updated = ActionDefinition.builder()
                    .actionId(definition.getActionId())
                    .actionName(definition.getActionName())
                    .description(definition.getDescription())
                    .actionType(definition.getActionType())
                    .category(definition.getCategory())
                    .tags(definition.getTags())
                    .triggerKeywords(definition.getTriggerKeywords())
                    .synonyms(definition.getSynonyms())
                    .exampleInputs(definition.getExampleInputs())
                    .parameters(definition.getParameters())
                    .steps(definition.getSteps())
                    .stateSchema(definition.getStateSchema())
                    .handler(definition.getHandler())
                    .interfaceBinding(definition.getInterfaceBinding())
                    .priority(definition.getPriority())
                    .timeoutMinutes(definition.getTimeoutMinutes())
                    .enabled(enabled)
                    .requiredPermissions(definition.getRequiredPermissions())
                    .metadata(definition.getMetadata())
                    .build();

            ActionDefinition saved = actionRepository.save(updated);

            String message = enabled ? "动作已启用" : "动作已禁用";
            return ApiResponse.success(message, ActionResponse.from(saved));

        } catch (Exception e) {
            logger.error("ActionController#toggleActionEnabled - reason=failed to toggle action, error={}", e.getMessage(), e);
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }

    private ActionDefinition buildDefinition(String actionId, ActionRequest request) {
        return ActionDefinition.builder()
                .actionId(actionId)
                .actionName(request.getActionName())
                .description(request.getDescription())
                .actionType(request.getActionType())
                .category(request.getCategory())
                .tags(request.getTags())
                .triggerKeywords(request.getTriggerKeywords())
                .synonyms(request.getSynonyms())
                .exampleInputs(request.getExampleInputs())
                .parameters(request.getParameters())
                .steps(request.getSteps())
                .stateSchema(request.getStateSchema())
                .handler(request.getHandler())
                .interfaceBinding(request.getInterfaceBinding())
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .timeoutMinutes(request.getTimeoutMinutes() != null ? request.getTimeoutMinutes() : 30)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .requiredPermissions(request.getRequiredPermissions())
                .metadata(request.getMetadata())
                .build();
    }
}
