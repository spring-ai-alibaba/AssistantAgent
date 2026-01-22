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
package com.alibaba.assistant.agent.planning.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 外部系统配置实体
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@TableName(value = "external_system_config", autoResultMap = true)
public class ExternalSystemConfigEntity {

    /**
     * 系统ID（主键）
     */
    @TableId(type = IdType.INPUT)
    private String systemId;

    /**
     * 系统名称
     */
    private String systemName;

    /**
     * 系统类型
     */
    private String systemType;

    /**
     * API基础URL
     */
    private String apiBaseUrl;

    /**
     * 认证类型
     */
    private String authType;

    /**
     * 认证配置（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> authConfig;

    /**
     * 适配器类名
     */
    private String adapterClass;

    /**
     * 图标URL
     */
    private String iconUrl;

    /**
     * 描述
     */
    private String description;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
