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
package com.alibaba.assistant.agent.planning.persistence.mapper;

import com.alibaba.assistant.agent.planning.persistence.entity.ActionRegistryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 动作注册表 Mapper 接口
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Mapper
public interface ActionRegistryMapper extends BaseMapper<ActionRegistryEntity> {

    /**
     * 根据 actionId 查询
     */
    @Select("SELECT * FROM action_registry WHERE action_id = #{actionId}")
    ActionRegistryEntity selectByActionId(@Param("actionId") String actionId);

    /**
     * 根据 actionName 查询
     */
    @Select("SELECT * FROM action_registry WHERE action_name = #{actionName}")
    ActionRegistryEntity selectByActionName(@Param("actionName") String actionName);

    /**
     * 根据分类查询
     */
    @Select("SELECT * FROM action_registry WHERE category = #{category} AND enabled = 1")
    List<ActionRegistryEntity> selectByCategory(@Param("category") String category);

    /**
     * 查询所有启用的动作
     */
    @Select("SELECT * FROM action_registry WHERE enabled = 1 ORDER BY priority DESC")
    List<ActionRegistryEntity> selectAllEnabled();

    /**
     * 获取所有分类
     */
    @Select("SELECT DISTINCT category FROM action_registry WHERE category IS NOT NULL AND enabled = 1")
    List<String> selectAllCategories();

    /**
     * 关键词搜索（简单模糊匹配）
     */
    @Select("SELECT * FROM action_registry WHERE enabled = 1 AND " +
            "(action_name LIKE CONCAT('%', #{keyword}, '%') OR " +
            "description LIKE CONCAT('%', #{keyword}, '%') OR " +
            "trigger_keywords LIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY priority DESC LIMIT #{limit}")
    List<ActionRegistryEntity> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 增加使用次数
     */
    @Select("UPDATE action_registry SET usage_count = usage_count + 1 WHERE action_id = #{actionId}")
    void incrementUsageCount(@Param("actionId") String actionId);
}
