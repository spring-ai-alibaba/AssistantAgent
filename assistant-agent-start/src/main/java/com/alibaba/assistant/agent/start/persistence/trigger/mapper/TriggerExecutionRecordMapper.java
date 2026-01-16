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

import com.alibaba.assistant.agent.extension.trigger.model.ExecutionStatus;
import com.alibaba.assistant.agent.start.persistence.trigger.entity.TriggerExecutionRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

/**
 * 触发器执行记录 Mapper
 * 提供触发器执行记录的数据库操作
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Mapper
public interface TriggerExecutionRecordMapper extends BaseMapper<TriggerExecutionRecordEntity> {

    /**
     * 根据执行ID查询
     * @param executionId 执行ID
     * @return 执行记录实体
     */
    @Select("SELECT * FROM trigger_execution_logs WHERE execution_id = #{executionId}")
    TriggerExecutionRecordEntity selectByExecutionId(@Param("executionId") String executionId);

    /**
     * 更新执行状态
     * @param executionId 执行ID
     * @param status 执行状态
     * @param errorMessage 错误信息
     * @param endTime 结束时间
     * @return 更新行数
     */
    @Update("UPDATE trigger_execution_logs SET status = #{status}, " +
            "error_message = #{errorMessage}, end_time = #{endTime}, updated_at = NOW() " +
            "WHERE execution_id = #{executionId}")
    int updateStatusByExecutionId(@Param("executionId") String executionId,
                                   @Param("status") ExecutionStatus status,
                                   @Param("errorMessage") String errorMessage,
                                   @Param("endTime") Instant endTime);

    /**
     * 查询指定触发器的执行记录（按时间倒序，限制数量）
     * @param triggerId 触发器ID
     * @param limit 最大返回数量
     * @return 执行记录实体列表
     */
    @Select("SELECT * FROM trigger_execution_logs WHERE trigger_id = #{triggerId} " +
            "ORDER BY start_time DESC LIMIT #{limit}")
    List<TriggerExecutionRecordEntity> selectByTriggerIdWithLimit(@Param("triggerId") String triggerId,
                                                                   @Param("limit") int limit);

    /**
     * 查询指定触发器的所有执行记录（按时间倒序）
     * @param triggerId 触发器ID
     * @return 执行记录实体列表
     */
    @Select("SELECT * FROM trigger_execution_logs WHERE trigger_id = #{triggerId} " +
            "ORDER BY start_time DESC")
    List<TriggerExecutionRecordEntity> selectByTriggerId(@Param("triggerId") String triggerId);

    /**
     * 根据执行ID删除
     * @param executionId 执行ID
     * @return 删除行数
     */
    @Delete("DELETE FROM trigger_execution_logs WHERE execution_id = #{executionId}")
    int deleteByExecutionId(@Param("executionId") String executionId);
}
