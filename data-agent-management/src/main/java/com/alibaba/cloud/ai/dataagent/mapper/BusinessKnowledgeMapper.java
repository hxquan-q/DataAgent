/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 业务术语知识 Mapper，操作 {@code business_knowledge} 表。
 * <p>
 * 管理智能体业务知识库中的业务术语及其描述、同义词，支持按智能体查询、关键词检索、 向量化召回与软删除。
 * </p>
 */
@Mapper
public interface BusinessKnowledgeMapper {

	/**
	 * 根据智能体 ID 查询未删除的业务知识列表，按创建时间倒序返回。
	 * @param agentId 智能体 ID
	 * @return 业务知识列表
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE agent_id = #{agentId} AND is_deleted = 0
			ORDER BY created_time DESC
			""")
	List<BusinessKnowledge> selectByAgentId(@Param("agentId") Long agentId);

	/**
	 * 查询全部未删除的业务知识列表，按创建时间倒序返回。
	 * @return 业务知识列表
	 */
	@Select("SELECT * FROM business_knowledge WHERE is_deleted = 0 ORDER BY created_time DESC")
	List<BusinessKnowledge> selectAll();

	/**
	 * 在指定智能体范围内按关键词检索业务知识（匹配业务术语、描述、同义词）。
	 * @param agentId 智能体 ID
	 * @param keyword 关键词
	 * @return 匹配的业务知识列表
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE agent_id = #{agentId} AND is_deleted = 0
			  AND (business_term LIKE CONCAT('%', #{keyword}, '%')
			    OR description LIKE CONCAT('%', #{keyword}, '%')
			    OR synonyms LIKE CONCAT('%', #{keyword}, '%'))
			ORDER BY created_time DESC
			""")
	List<BusinessKnowledge> searchInAgent(@Param("agentId") Long agentId, @Param("keyword") String keyword);

	/**
	 * 新增业务知识记录，并将自增主键回填到入参对象的 {@code id} 字段。
	 * @param knowledge 业务知识实体
	 * @return 受影响行数
	 */
	@Insert("""
			INSERT INTO business_knowledge (business_term, description, synonyms, is_recall, agent_id, created_time, updated_time, embedding_status, is_deleted)
			VALUES (#{businessTerm}, #{description}, #{synonyms}, #{isRecall}, #{agentId}, NOW(), NOW(), #{embeddingStatus}, #{isDeleted})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(BusinessKnowledge knowledge);

	/**
	 * 根据主键动态更新业务知识（仅更新非空字段），并刷新 {@code updated_time}。
	 * @param knowledge 业务知识实体（需携带 {@code id}）
	 * @return 受影响行数
	 */
	@Update("""
			<script>
			UPDATE business_knowledge
			<set>
				<if test="businessTerm != null">business_term = #{businessTerm},</if>
				<if test="description != null">description = #{description},</if>
				<if test="synonyms != null">synonyms = #{synonyms},</if>
				<if test="isRecall != null">is_recall = #{isRecall},</if>
				<if test="agentId != null">agent_id = #{agentId},</if>
				<if test="embeddingStatus != null">embedding_status = #{embeddingStatus},</if>
				<if test="errorMsg != null">error_msg = #{errorMsg},</if>
				<if test="isDeleted != null">is_deleted = #{isDeleted},</if>
				updated_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(BusinessKnowledge knowledge);

	/**
	 * 根据主键物理删除业务知识。
	 * @param id 业务知识 ID
	 * @return 受影响行数
	 */
	@Delete("""
			DELETE FROM business_knowledge
			WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

	/**
	 * 根据主键查询未删除的业务知识。
	 * @param id 业务知识 ID
	 * @return 业务知识；不存在返回 {@code null}
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE id = #{id} AND is_deleted = 0
			""")
	BusinessKnowledge selectById(Long id);

	/**
	 * 查询某智能体下需要参与召回且未删除的业务知识 ID 列表。
	 * @param agentId 智能体 ID
	 * @return 需召回的业务知识 ID 列表
	 */
	@Select("""
			SELECT id FROM business_knowledge
			WHERE agent_id = #{agentId} AND is_recall = 1 AND is_deleted = 0
			""")
	List<Long> selectRecalledKnowledgeIds(@Param("agentId") Long agentId);

	/**
	 * 逻辑删除（或恢复）业务知识，通过更新 {@code is_deleted} 字段实现。
	 * @param id 业务知识 ID
	 * @param isDeleted 删除标记：1 已删除、0 未删除
	 * @return 受影响行数
	 */
	@Update("""
			UPDATE business_knowledge
			SET is_deleted = #{isDeleted}, updated_time = NOW()
			WHERE id = #{id}
			""")
	int logicalDelete(@Param("id") Long id, @Param("isDeleted") Integer isDeleted);

}
