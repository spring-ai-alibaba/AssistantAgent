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

import com.alibaba.assistant.agent.planning.permission.model.AccessibleSystem;
import com.alibaba.assistant.agent.planning.permission.model.StandardPermission;
import com.alibaba.assistant.agent.planning.permission.service.UnifiedChatService;
import com.alibaba.assistant.agent.planning.permission.spi.IdentityMappingService;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionCheckResult;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Permission-related REST API controller.
 * <p>
 * Provides APIs for:
 * <ul>
 *   <li>Getting user's accessible systems</li>
 *   <li>Binding/unbinding external systems</li>
 *   <li>Checking permissions</li>
 *   <li>Permission-aware chat</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/permission")
public class PermissionController {

    private static final Logger logger = LoggerFactory.getLogger(PermissionController.class);

    private final IdentityMappingService identityMappingService;
    private final PermissionService permissionService;
    private final UnifiedChatService unifiedChatService;

    public PermissionController(IdentityMappingService identityMappingService,
                                PermissionService permissionService,
                                UnifiedChatService unifiedChatService) {
        this.identityMappingService = identityMappingService;
        this.permissionService = permissionService;
        this.unifiedChatService = unifiedChatService;
    }

    /**
     * Get all systems accessible to a user.
     *
     * @param userId the platform user ID
     * @return list of accessible systems with binding status
     */
    @GetMapping("/systems")
    public ResponseEntity<Map<String, Object>> getAccessibleSystems(
            @RequestParam("userId") String userId) {
        logger.info("PermissionController#getAccessibleSystems - userId={}", userId);

        List<AccessibleSystem> systems = identityMappingService.getAccessibleSystems(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", systems);

        return ResponseEntity.ok(response);
    }

    /**
     * Bind an external system to a user.
     *
     * @param request the bind request
     * @return success or error response
     */
    @PostMapping("/bind")
    public ResponseEntity<Map<String, Object>> bindSystem(@RequestBody BindRequest request) {
        logger.info("PermissionController#bindSystem - userId={}, systemId={}",
                request.getUserId(), request.getSystemId());

        try {
            identityMappingService.bindIdentity(
                    request.getUserId(),
                    request.getSystemId(),
                    request.getExternalUserId(),
                    request.getExternalUsername(),
                    request.getExtraInfo()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "绑定成功");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("PermissionController#bindSystem - error", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "绑定失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Unbind an external system from a user.
     *
     * @param userId the platform user ID
     * @param systemId the system ID to unbind
     * @return success or error response
     */
    @DeleteMapping("/unbind")
    public ResponseEntity<Map<String, Object>> unbindSystem(
            @RequestParam("userId") String userId,
            @RequestParam("systemId") String systemId) {
        logger.info("PermissionController#unbindSystem - userId={}, systemId={}", userId, systemId);

        identityMappingService.unbindIdentity(userId, systemId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "解绑成功");

        return ResponseEntity.ok(response);
    }

    /**
     * Get user's permission in a specific system.
     *
     * @param userId the platform user ID
     * @param systemId the system ID
     * @return the user's permission
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getPermission(
            @RequestParam("userId") String userId,
            @RequestParam("systemId") String systemId) {
        logger.info("PermissionController#getPermission - userId={}, systemId={}", userId, systemId);

        StandardPermission permission = permissionService.getPermission(userId, systemId, null);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "systemId", systemId,
                "userId", permission.getUserId(),
                "allowedActions", permission.getAllowedActions(),
                "dataScope", permission.getDataScope() != null ? permission.getDataScope().name() : null,
                "filters", permission.getFilters()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Check if user can execute a specific action.
     *
     * @param request the check request
     * @return the check result
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> checkPermission(@RequestBody CheckRequest request) {
        logger.info("PermissionController#checkPermission - userId={}, systemId={}, actionId={}",
                request.getUserId(), request.getSystemId(), request.getActionId());

        PermissionCheckResult result = unifiedChatService.checkPermission(
                request.getUserId(),
                request.getSystemId(),
                request.getActionId(),
                request.getContext()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        Map<String, Object> data = new HashMap<>();
        data.put("allowed", result.isAllowed());
        data.put("message", result.getMessage() != null ? result.getMessage() : "");
        data.put("errorCode", result.getErrorCode() != null ? result.getErrorCode() : "");
        response.put("data", data);

        return ResponseEntity.ok(response);
    }

    /**
     * Chat with permission integration.
     *
     * @param request the chat request
     * @return the chat response
     */
    @PostMapping("/chat")
    public ResponseEntity<UnifiedChatService.ChatResponse> chat(
            @RequestBody UnifiedChatService.ChatRequest request) {
        logger.info("PermissionController#chat - userId={}, systemId={}, message={}",
                request.getPlatformUserId(), request.getTargetSystem(), request.getMessage());

        UnifiedChatService.ChatResponse response = unifiedChatService.processChat(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Bind request model.
     */
    public static class BindRequest {
        private String userId;
        private String systemId;
        private String externalUserId;
        private String externalUsername;
        private Map<String, Object> extraInfo;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getSystemId() {
            return systemId;
        }

        public void setSystemId(String systemId) {
            this.systemId = systemId;
        }

        public String getExternalUserId() {
            return externalUserId;
        }

        public void setExternalUserId(String externalUserId) {
            this.externalUserId = externalUserId;
        }

        public String getExternalUsername() {
            return externalUsername;
        }

        public void setExternalUsername(String externalUsername) {
            this.externalUsername = externalUsername;
        }

        public Map<String, Object> getExtraInfo() {
            return extraInfo;
        }

        public void setExtraInfo(Map<String, Object> extraInfo) {
            this.extraInfo = extraInfo;
        }
    }

    /**
     * Check request model.
     */
    public static class CheckRequest {
        private String userId;
        private String systemId;
        private String actionId;
        private Map<String, Object> context;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getSystemId() {
            return systemId;
        }

        public void setSystemId(String systemId) {
            this.systemId = systemId;
        }

        public String getActionId() {
            return actionId;
        }

        public void setActionId(String actionId) {
            this.actionId = actionId;
        }

        public Map<String, Object> getContext() {
            return context;
        }

        public void setContext(Map<String, Object> context) {
            this.context = context;
        }
    }
}
