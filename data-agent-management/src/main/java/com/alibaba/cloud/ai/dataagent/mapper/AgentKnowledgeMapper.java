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

import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.AgentKnowledgeQueryDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体知识库 Mapper，操作 {@code agent_knowledge} 表。
 * <p>
 * 管理智能体挂载的知识文档（标题、正文、文件元信息、向量化状态等），支持条件分页查询、 软删除与待清理“僵尸”记录检索。
 * </p>
 */
@Mapper
public interface AgentKnowledgeMapper {

	/**
	 * 根据主键查询未删除的知识记录。
	 * @param id 知识记录 ID
	 * @return 知识记录；不存在返回 {@code null}
	 */
	@Select("""
			SELECT * FROM agent_knowledge WHERE id = #{id} AND is_deleted = 0
			""")
	AgentKnowledge selectById(@Param("id") Integer id);

	/**
	 * 根据主键查询知识记录（包含已软删除的记录）。
	 * @param id 知识记录 ID
	 * @return 知识记录；不存在返回 {@code null}
	 */
	@Select("""
			    SELECT * FROM agent_knowledge WHERE id = #{id}
			""")
	AgentKnowledge selectByIdIncludeDeleted(@Param("id") Integer id);

	/**
	 * 插入一条知识记录，并将自增主键回填到入参对象的 {@code id} 字段。
	 * @param knowledge 知识记录实体
	 * @return 受影响行数
	 */
	@Insert("""

			INSERT INTO agent_knowledge (agent_id, title, content, type, question, is_recall, embedding_status, source_filename, file_path, file_size, file_type, splitter_type, is_deleted, is_resource_cleaned, created_time, updated_time)
			VALUES (#{agentId}, #{title}, #{content}, #{type}, #{question}, #{isRecall}, #{embeddingStatus}, #{sourceFilename}, #{filePath}, #{fileSize}, #{fileType}, #{splitterType}, #{isDeleted}, #{isResourceCleaned}, #{createdTime}, #{updatedTime})

			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(AgentKnowledge knowledge);

	/**
	 * 根据主键动态更新知识记录（仅更新非空字段），并刷新 {@code updated_time}。
	 * @param knowledge 知识记录实体（需携带 {@code id}）
	 * @return 受影响行数
	 */
	@Update("""
			<script>
			UPDATE agent_knowledge
			<set>
				<if test="title != null">title = #{title},</if>
				<if test="content != null">content = #{content},</if>
				<if test="type != null">type = #{type},</if>
				<if test="question != null">question = #{question},</if>
				<if test="isRecall != null">is_recall = #{isRecall},</if>
				<if test="embeddingStatus != null">embedding_status = #{embeddingStatus},</if>
				<if test="errorMsg != null">error_msg = #{errorMsg},</if>
				<if test="sourceFilename != null">source_filename = #{sourceFilename},</if>
				<if test="filePath != null">file_path = #{filePath},</if>
				<if test="fileSize != null">file_size = #{fileSize},</if>
				<if test="fileType != null">file_type = #{fileType},</if>
				<if test="splitterType != null">splitter_type = #{splitterType},</if>
				<if test="isDeleted != null">is_deleted = #{isDeleted},</if>
				<if test="isResourceCleaned != null">is_resource_cleaned = #{isResourceCleaned},</if>
				updated_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int update(AgentKnowledge knowledge);

	/**
	 * 按条件分页查询未删除的知识记录。
	 * <p>
	 * 支持按标题模糊匹配、类型与向量化状态过滤。
	 * </p>
	 * @param queryDTO 查询条件
	 * @param offset 偏移量（已计算好的分页起始位置）
	 * @return 当前页的知识记录列表
	 */
	@Select("""
			<script>
			SELECT * FROM agent_knowledge
			WHERE agent_id = #{queryDTO.agentId}
			<if test="queryDTO.title != null and queryDTO.title != ''">
				AND title LIKE CONCAT('%', #{queryDTO.title}, '%')
			</if>
			<if test="queryDTO.type != null and queryDTO.type != ''">
				AND type = #{queryDTO.type}
			</if>
			<if test="queryDTO.embeddingStatus != null and queryDTO.embeddingStatus != ''">
				AND embedding_status = #{queryDTO.embeddingStatus}
			</if>
			AND is_deleted = 0
			LIMIT #{offset}, #{queryDTO.pageSize}
			</script>
			""")
	List<AgentKnowledge> selectByConditionsWithPage(@Param("queryDTO") AgentKnowledgeQueryDTO queryDTO,
			@Param("offset") Integer offset);

	/**
	 * 按条件统计未删除的知识记录总数（用于分页计算）。
	 * @param queryDTO 查询条件
	 * @return 满足条件的记录总数
	 */
	@Select("""
			<script>
			SELECT COUNT(*) FROM agent_knowledge
			WHERE agent_id = #{queryDTO.agentId}
			<if test="queryDTO.title != null and queryDTO.title != ''">
				AND title LIKE CONCAT('%', #{queryDTO.title}, '%')
			</if>
			<if test="queryDTO.type != null and queryDTO.type != ''">
				AND type = #{queryDTO.type}
			</if>
			<if test="queryDTO.embeddingStatus != null and queryDTO.embeddingStatus != ''">
				AND embedding_status = #{queryDTO.embeddingStatus}
			</if>
			AND is_deleted = 0
			</script>
			""")
	Long countByConditions(@Param("queryDTO") AgentKnowledgeQueryDTO queryDTO);

	/**
	 * 查询某智能体下需要参与召回且未删除的知识记录 ID 列表。
	 * @param agentId 智能体 ID
	 * @return 需召回的知识记录 ID 列表
	 */
	@Select("""
			SELECT id FROM agent_knowledge WHERE agent_id = #{agentId} AND is_recall = 1 AND is_deleted = 0
			""")
	List<Integer> selectRecalledKnowledgeIds(@Param("agentId") Integer agentId);

	/**
	 * 查询待清理的“僵尸”记录。
	 * <p>
	 * 条件：{@code is_deleted = 1} 且 {@code is_resource_cleaned = 0} 且 {@code updated_time}
	 * 早于指定时间。
	 * </p>
	 * @param beforeTime 时间下限，仅返回在该时间之前更新的记录
	 * @param limit 单次查询的最大记录数
	 * @return 待清理记录列表
	 */
	@Select("""
			    SELECT * FROM agent_knowledge
			    WHERE is_deleted = 1
			      AND is_resource_cleaned = 0
			      AND updated_time < #{beforeTime}
			    LIMIT #{limit}
			""")
	List<AgentKnowledge> selectDirtyRecords(@Param("beforeTime") LocalDateTime beforeTime, @Param("limit") int limit);

}
