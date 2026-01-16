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

package com.alibaba.assistant.agent.start.persistence.learning.mapper;

import com.alibaba.assistant.agent.start.persistence.learning.entity.LearningRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 学习记录 Mapper
 * 提供学习记录的数据库操作
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Mapper
public interface LearningRecordMapper extends BaseMapper<LearningRecordEntity> {

    /**
     * 根据命名空间和键查询记录
     * @param namespace 命名空间
     * @param recordKey 记录键
     * @return 学习记录实体
     */
    @Select("SELECT * FROM learning_records WHERE namespace = #{namespace} AND record_key = #{recordKey}")
    LearningRecordEntity selectByNamespaceAndKey(@Param("namespace") String namespace,
                                                  @Param("recordKey") String recordKey);

    /**
     * 根据命名空间查询所有记录
     * @param namespace 命名空间
     * @return 学习记录实体列表
     */
    @Select("SELECT * FROM learning_records WHERE namespace = #{namespace}")
    List<LearningRecordEntity> selectByNamespace(@Param("namespace") String namespace);

    /**
     * 根据命名空间查询记录（支持分页）
     * @param namespace 命名空间
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 学习记录实体列表
     */
    @Select("SELECT * FROM learning_records WHERE namespace = #{namespace} " +
            "ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<LearningRecordEntity> selectByNamespaceWithPaging(@Param("namespace") String namespace,
                                                            @Param("offset") int offset,
                                                            @Param("limit") int limit);

    /**
     * 根据命名空间和学习类型查询记录（支持分页）
     * @param namespace 命名空间
     * @param learningType 学习类型
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 学习记录实体列表
     */
    @Select("SELECT * FROM learning_records WHERE namespace = #{namespace} " +
            "AND learning_type = #{learningType} " +
            "ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<LearningRecordEntity> selectByNamespaceAndTypeWithPaging(@Param("namespace") String namespace,
                                                                   @Param("learningType") String learningType,
                                                                   @Param("offset") int offset,
                                                                   @Param("limit") int limit);

    /**
     * 根据命名空间和键删除记录
     * @param namespace 命名空间
     * @param recordKey 记录键
     * @return 删除行数
     */
    @Delete("DELETE FROM learning_records WHERE namespace = #{namespace} AND record_key = #{recordKey}")
    int deleteByNamespaceAndKey(@Param("namespace") String namespace, @Param("recordKey") String recordKey);

    /**
     * 删除命名空间下的所有记录
     * @param namespace 命名空间
     * @return 删除行数
     */
    @Delete("DELETE FROM learning_records WHERE namespace = #{namespace}")
    int deleteByNamespace(@Param("namespace") String namespace);

    /**
     * 统计命名空间下的记录数量
     * @param namespace 命名空间
     * @return 记录数量
     */
    @Select("SELECT COUNT(*) FROM learning_records WHERE namespace = #{namespace}")
    int countByNamespace(@Param("namespace") String namespace);
}
