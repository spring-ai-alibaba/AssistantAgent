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

import com.alibaba.assistant.agent.planning.persistence.entity.ExternalSystemConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 外部系统配置 Mapper 接口
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Mapper
public interface ExternalSystemConfigMapper extends BaseMapper<ExternalSystemConfigEntity> {

    /**
     * 根据 systemId 查询
     */
    @Select("SELECT * FROM external_system_config WHERE system_id = #{systemId}")
    ExternalSystemConfigEntity selectBySystemId(@Param("systemId") String systemId);

    /**
     * 查询所有启用的系统
     */
    @Select("SELECT * FROM external_system_config WHERE enabled = 1")
    List<ExternalSystemConfigEntity> selectAllEnabled();

    /**
     * 根据系统类型查询
     */
    @Select("SELECT * FROM external_system_config WHERE system_type = #{systemType} AND enabled = 1")
    List<ExternalSystemConfigEntity> selectBySystemType(@Param("systemType") String systemType);
}
