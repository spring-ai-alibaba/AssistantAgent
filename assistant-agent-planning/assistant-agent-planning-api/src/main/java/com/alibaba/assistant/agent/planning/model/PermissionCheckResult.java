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
package com.alibaba.assistant.agent.planning.model;

import java.util.Objects;

/**
 * 权限检查结果
 *
 * <p>表示权限检查的结果，包括是否授权和拒绝原因。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class PermissionCheckResult {

    /**
     * 是否授权
     */
    private boolean granted;

    /**
     * 拒绝原因（如果未授权）
     */
    private String denialReason;

    /**
     * 私有构造函数
     */
    private PermissionCheckResult(boolean granted, String denialReason) {
        this.granted = granted;
        this.denialReason = denialReason;
    }

    /**
     * 创建授权结果
     */
    public static PermissionCheckResult granted() {
        return new PermissionCheckResult(true, null);
    }

    /**
     * 创建拒绝结果
     */
    public static PermissionCheckResult denied(String reason) {
        return new PermissionCheckResult(false, reason);
    }

    public boolean isGranted() {
        return granted;
    }

    public String getDenialReason() {
        return denialReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PermissionCheckResult that = (PermissionCheckResult) o;
        return granted == that.granted && Objects.equals(denialReason, that.denialReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(granted, denialReason);
    }

    @Override
    public String toString() {
        return "PermissionCheckResult{" +
                "granted=" + granted +
                ", denialReason='" + denialReason + '\'' +
                '}';
    }
}
