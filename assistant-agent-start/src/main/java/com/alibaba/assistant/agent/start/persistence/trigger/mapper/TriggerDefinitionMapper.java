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

package com.alibaba.assistant.agent.start.persistence.trigger.mapper;

import com.alibaba.assistant.agent.extension.trigger.model.SourceType;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerStatus;
import com.alibaba.assistant.agent.start.persistence.trigger.entity.TriggerDefinitionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 触发器定义 Mapper
 * 提供触发器定义的数据库操作
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Mapper
public interface TriggerDefinitionMapper extends BaseMapper<TriggerDefinitionEntity> {

    /**
     * 根据触发器ID查询
     * @param triggerId 触发器ID
     * @return 触发器定义实体
     */
    @Select("SELECT * FROM trigger_definitions WHERE trigger_id = #{triggerId}")
    TriggerDefinitionEntity selectByTriggerId(@Param("triggerId") String triggerId);

    /**
     * 根据来源查询触发器列表
     * @param sourceType 来源类型
     * @param sourceId 来源ID
     * @return 触发器定义实体列表
     */
    @Select("SELECT * FROM trigger_definitions WHERE source_type = #{sourceType} AND source_id = #{sourceId}")
    List<TriggerDefinitionEntity> selectBySource(@Param("sourceType") SourceType sourceType,
                                                  @Param("sourceId") String sourceId);

    /**
     * 根据状态查询触发器列表
     * @param status 触发器状态
     * @return 触发器定义实体列表
     */
    @Select("SELECT * FROM trigger_definitions WHERE status = #{status}")
    List<TriggerDefinitionEntity> selectByStatus(@Param("status") TriggerStatus status);

    /**
     * 更新触发器状态
     * @param triggerId 触发器ID
     * @param status 新状态
     * @return 更新行数
     */
    @Update("UPDATE trigger_definitions SET status = #{status}, updated_at = NOW() WHERE trigger_id = #{triggerId}")
    int updateStatusByTriggerId(@Param("triggerId") String triggerId, @Param("status") TriggerStatus status);

    /**
     * 根据触发器ID删除
     * @param triggerId 触发器ID
     * @return 删除行数
     */
    @Update("DELETE FROM trigger_definitions WHERE trigger_id = #{triggerId}")
    int deleteByTriggerId(@Param("triggerId") String triggerId);
}
