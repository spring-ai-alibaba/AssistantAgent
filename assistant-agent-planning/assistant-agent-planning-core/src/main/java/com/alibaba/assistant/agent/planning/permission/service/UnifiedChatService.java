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
package com.alibaba.assistant.agent.planning.permission.service;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.permission.model.ExternalIdentity;
import com.alibaba.assistant.agent.planning.permission.model.StandardPermission;
import com.alibaba.assistant.agent.planning.permission.spi.IdentityMappingService;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionCheckResult;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Unified chat service with permission integration.
 * <p>
 * This service orchestrates the chat flow with permission checking:
 * <ol>
 *   <li>Resolve target system from message or explicit parameter</li>
 *   <li>Get user's external identity in the target system</li>
 *   <li>Adapt permissions using the appropriate adapter</li>
 *   <li>Check action permission before execution</li>
 *   <li>Inject data permission into action parameters</li>
 * </ol>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Service
public class UnifiedChatService {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedChatService.class);

    private final IdentityMappingService identityMappingService;
    private final PermissionService permissionService;

    public UnifiedChatService(IdentityMappingService identityMappingService,
                              PermissionService permissionService) {
        this.identityMappingService = identityMappingService;
        this.permissionService = permissionService;
    }

    /**
     * Process a chat request with permission checking.
     *
     * @param request the chat request
     * @return the chat response
     */
    public ChatResponse processChat(ChatRequest request) {
        logger.info("UnifiedChatService#processChat - processing request: platformUser={}, targetSystem={}",
                request.getPlatformUserId(), request.getTargetSystem());

        // Step 1: Validate request
        if (request.getPlatformUserId() == null || request.getPlatformUserId().isEmpty()) {
            return ChatResponse.error("用户ID不能为空", "INVALID_USER");
        }

        String targetSystem = request.getTargetSystem();
        if (targetSystem == null || targetSystem.isEmpty()) {
            // TODO: Auto-detect target system from message content
            return ChatResponse.error("请指定目标系统", "SYSTEM_NOT_SPECIFIED");
        }

        // Step 2: Get external identity
        Optional<ExternalIdentity> identityOpt = identityMappingService.getExternalIdentity(
                request.getPlatformUserId(), targetSystem);

        if (identityOpt.isEmpty()) {
            logger.warn("UnifiedChatService#processChat - user not bound to system: platformUser={}, system={}",
                    request.getPlatformUserId(), targetSystem);
            return ChatResponse.error(
                    "您尚未绑定" + targetSystem + "系统，请先完成绑定",
                    "NOT_BOUND");
        }

        ExternalIdentity identity = identityOpt.get();

        // Step 3: Build context and get permission
        Map<String, Object> context = new HashMap<>();
        context.put("userId", identity.getUserId());
        context.put("username", identity.getUsername());
        if (identity.getExtraInfo() != null) {
            context.putAll(identity.getExtraInfo());
        }
        if (request.getContext() != null) {
            context.putAll(request.getContext());
        }

        StandardPermission permission = permissionService.adaptPermission(targetSystem, context);

        // Step 4: Build response with permission info
        ChatResponse response = ChatResponse.success();
        response.setSystemId(targetSystem);
        response.setIdentity(IdentityInfo.from(identity));
        response.setPermission(PermissionInfo.from(permission));

        // Note: The actual action execution would happen here
        // For now, we just return the permission info
        response.setMessage("权限验证成功，可以执行操作");

        logger.info("UnifiedChatService#processChat - permission validated: user={}, system={}, allowedActions={}",
                request.getPlatformUserId(), targetSystem, permission.getAllowedActions());

        return response;
    }

    /**
     * Check if user can execute the specified action.
     *
     * @param platformUserId the platform user ID
     * @param systemId the target system ID
     * @param actionId the action ID
     * @param context additional context
     * @return the permission check result
     */
    public PermissionCheckResult checkPermission(String platformUserId, String systemId,
                                                  String actionId, Map<String, Object> context) {
        StandardPermission permission = permissionService.getPermission(platformUserId, systemId, context);
        return permissionService.checkActionPermission(permission, actionId);
    }

    /**
     * Get parameters with data permission injected.
     *
     * @param platformUserId the platform user ID
     * @param systemId the target system ID
     * @param action the action definition
     * @param userParams user-provided parameters
     * @param context additional context
     * @return parameters with data permission filters
     */
    public Map<String, Object> getParametersWithPermission(String platformUserId, String systemId,
                                                            ActionDefinition action,
                                                            Map<String, Object> userParams,
                                                            Map<String, Object> context) {
        StandardPermission permission = permissionService.getPermission(platformUserId, systemId, context);
        return permissionService.injectDataPermission(permission, action, userParams);
    }

    /**
     * Chat request model.
     */
    public static class ChatRequest {
        private String platformUserId;
        private String targetSystem;
        private String message;
        private Map<String, Object> context;

        public String getPlatformUserId() {
            return platformUserId;
        }

        public void setPlatformUserId(String platformUserId) {
            this.platformUserId = platformUserId;
        }

        public String getTargetSystem() {
            return targetSystem;
        }

        public void setTargetSystem(String targetSystem) {
            this.targetSystem = targetSystem;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Map<String, Object> getContext() {
            return context;
        }

        public void setContext(Map<String, Object> context) {
            this.context = context;
        }
    }

    /**
     * Chat response model.
     */
    public static class ChatResponse {
        private boolean success;
        private String message;
        private String errorCode;
        private String systemId;
        private String actionId;
        private IdentityInfo identity;
        private PermissionInfo permission;
        private Object data;

        public static ChatResponse success() {
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            return response;
        }

        public static ChatResponse error(String message, String errorCode) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(false);
            response.setMessage(message);
            response.setErrorCode(errorCode);
            return response;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
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

        public IdentityInfo getIdentity() {
            return identity;
        }

        public void setIdentity(IdentityInfo identity) {
            this.identity = identity;
        }

        public PermissionInfo getPermission() {
            return permission;
        }

        public void setPermission(PermissionInfo permission) {
            this.permission = permission;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    /**
     * Identity info for response.
     */
    public static class IdentityInfo {
        private String externalUserId;
        private String externalUsername;
        private Map<String, Object> extraInfo;

        public static IdentityInfo from(ExternalIdentity identity) {
            IdentityInfo info = new IdentityInfo();
            info.setExternalUserId(identity.getUserId());
            info.setExternalUsername(identity.getUsername());
            info.setExtraInfo(identity.getExtraInfo());
            return info;
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
     * Permission info for response.
     */
    public static class PermissionInfo {
        private java.util.List<String> allowedActions;
        private String dataScope;
        private Map<String, Object> filters;

        public static PermissionInfo from(StandardPermission permission) {
            PermissionInfo info = new PermissionInfo();
            info.setAllowedActions(permission.getAllowedActions());
            info.setDataScope(permission.getDataScope() != null ? permission.getDataScope().name() : null);
            Map<String, Object> filterMap = new HashMap<>();
            if (permission.getFilters() != null) {
                permission.getFilters().forEach((k, v) -> filterMap.put(k, v.getValue()));
            }
            info.setFilters(filterMap);
            return info;
        }

        public java.util.List<String> getAllowedActions() {
            return allowedActions;
        }

        public void setAllowedActions(java.util.List<String> allowedActions) {
            this.allowedActions = allowedActions;
        }

        public String getDataScope() {
            return dataScope;
        }

        public void setDataScope(String dataScope) {
            this.dataScope = dataScope;
        }

        public Map<String, Object> getFilters() {
            return filters;
        }

        public void setFilters(Map<String, Object> filters) {
            this.filters = filters;
        }
    }
}
