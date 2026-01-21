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
package com.alibaba.assistant.agent.planning.permission.spi;

import java.io.Serializable;
import java.util.Objects;

/**
 * Result of permission check operation.
 * <p>
 * Represents whether a user is allowed to perform an action,
 * with optional message explaining the result.
 * <p>
 * Example usage:
 * <pre>
 * PermissionCheckResult result = permissionService.checkActionPermission(permission, actionId);
 * if (!result.isAllowed()) {
 *     throw new AccessDeniedException(result.getMessage());
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class PermissionCheckResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Pre-built allowed result.
     */
    private static final PermissionCheckResult ALLOWED = new PermissionCheckResult(true, null, null);

    /**
     * Whether the operation is allowed.
     */
    private final boolean allowed;

    /**
     * Message explaining the result (especially for denied cases).
     */
    private final String message;

    /**
     * Error code for denied cases.
     */
    private final String errorCode;

    /**
     * The action ID that was checked.
     */
    private String actionId;

    /**
     * Private constructor. Use static factory methods.
     */
    private PermissionCheckResult(boolean allowed, String message, String errorCode) {
        this.allowed = allowed;
        this.message = message;
        this.errorCode = errorCode;
    }

    /**
     * Create an allowed result.
     *
     * @return allowed permission result
     */
    public static PermissionCheckResult allowed() {
        return ALLOWED;
    }

    /**
     * Create an allowed result with message.
     *
     * @param message success message
     * @return allowed permission result
     */
    public static PermissionCheckResult allowed(String message) {
        return new PermissionCheckResult(true, message, null);
    }

    /**
     * Create a denied result.
     *
     * @param message denial reason
     * @return denied permission result
     */
    public static PermissionCheckResult denied(String message) {
        return new PermissionCheckResult(false, message, "ACCESS_DENIED");
    }

    /**
     * Create a denied result with error code.
     *
     * @param message denial reason
     * @param errorCode error code
     * @return denied permission result
     */
    public static PermissionCheckResult denied(String message, String errorCode) {
        return new PermissionCheckResult(false, message, errorCode);
    }

    /**
     * Create a result indicating user is not bound to the system.
     *
     * @param systemId the system ID
     * @return denied permission result
     */
    public static PermissionCheckResult notBound(String systemId) {
        return new PermissionCheckResult(false,
                "您尚未绑定系统: " + systemId + "，请先完成绑定",
                "NOT_BOUND");
    }

    /**
     * Create a result indicating action is not found.
     *
     * @param actionId the action ID
     * @return denied permission result
     */
    public static PermissionCheckResult actionNotFound(String actionId) {
        return new PermissionCheckResult(false,
                "未找到操作: " + actionId,
                "ACTION_NOT_FOUND");
    }

    /**
     * Create a result indicating no permission for action.
     *
     * @param actionId the action ID
     * @return denied permission result
     */
    public static PermissionCheckResult noPermission(String actionId) {
        return new PermissionCheckResult(false,
                "您没有执行此操作的权限: " + actionId,
                "NO_PERMISSION");
    }

    /**
     * Check if the operation is allowed.
     *
     * @return true if allowed
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Check if the operation is denied.
     *
     * @return true if denied
     */
    public boolean isDenied() {
        return !allowed;
    }

    /**
     * Get the result message.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Get the error code.
     *
     * @return the error code, or null if allowed
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Get the action ID that was checked.
     *
     * @return the action ID
     */
    public String getActionId() {
        return actionId;
    }

    /**
     * Set the action ID that was checked.
     *
     * @param actionId the action ID
     * @return this result for chaining
     */
    public PermissionCheckResult withActionId(String actionId) {
        this.actionId = actionId;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PermissionCheckResult that = (PermissionCheckResult) o;
        return allowed == that.allowed &&
                Objects.equals(message, that.message) &&
                Objects.equals(errorCode, that.errorCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowed, message, errorCode);
    }

    @Override
    public String toString() {
        return "PermissionCheckResult{" +
                "allowed=" + allowed +
                ", message='" + message + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", actionId='" + actionId + '\'' +
                '}';
    }
}
