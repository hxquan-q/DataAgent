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

import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天会话 Mapper，操作 {@code chat_session} 表。
 * <p>
 * 管理智能体会话（标题、状态、置顶、用户）的生命周期，支持查询、更新、置顶切换与软删除。
 * </p>
 */
@Mapper
public interface ChatSessionMapper {

	/**
	 * 根据智能体 ID 查询未删除的会话列表，按置顶优先、更新时间倒序返回。
	 * @param agentId 智能体 ID
	 * @return 会话列表
	 */
	@Select("""
			SELECT * FROM chat_session
			WHERE agent_id = #{agentId} AND status != 'deleted'
			ORDER BY is_pinned DESC, update_time DESC
			""")
	List<ChatSession> selectByAgentId(@Param("agentId") Integer agentId);

	/**
	 * 根据会话 ID 查询未删除的会话详情。
	 * @param sessionId 会话 ID
	 * @return 会话；不存在或已删除返回 {@code null}
	 */
	@Select("""
			SELECT * FROM chat_session
			WHERE id = #{sessionId} AND status != 'deleted'
			""")
	ChatSession selectBySessionId(@Param("sessionId") String sessionId);

	/**
	 * 根据会话 ID 动态更新会话（仅更新非空字段），并刷新 {@code update_time}。
	 * @param session 会话实体（需携带 {@code sessionId}）
	 * @return 受影响行数
	 */
	@Update("""
			<script>
			UPDATE chat_session
			<set>
				<if test="title != null">title = #{title},</if>
				<if test="status != null">status = #{status},</if>
				<if test="isPinned != null">is_pinned = #{isPinned},</if>
				<if test="userId != null">user_id = #{userId},</if>
				update_time = NOW()
			</set>
			WHERE id = #{sessionId}
			</script>
			""")
	int updateById(ChatSession session);

	/**
	 * 软删除某智能体下的全部会话（将状态置为 {@code deleted}）。
	 * @param agentId 智能体 ID
	 * @param updateTime 更新时间
	 * @return 受影响行数
	 */
	@Update("""
			UPDATE chat_session
			SET status = 'deleted', update_time = #{updateTime}
			WHERE agent_id = #{agentId}
			""")
	int softDeleteByAgentId(@Param("agentId") Integer agentId, @Param("updateTime") LocalDateTime updateTime);

	/**
	 * 更新会话的最后活跃时间。
	 * @param sessionId 会话 ID
	 * @param updateTime 更新时间
	 * @return 受影响行数
	 */
	@Update("""
			UPDATE chat_session
			SET update_time = #{updateTime}
			WHERE id = #{sessionId}
			""")
	int updateSessionTime(@Param("sessionId") String sessionId, @Param("updateTime") LocalDateTime updateTime);

	/**
	 * 更新会话的置顶状态。
	 * @param sessionId 会话 ID
	 * @param isPinned 是否置顶
	 * @param updateTime 更新时间
	 * @return 受影响行数
	 */
	@Update("""
			UPDATE chat_session SET
				is_pinned = #{isPinned},
				update_time = #{updateTime}
			WHERE id = #{sessionId}
			""")
	int updatePinStatus(@Param("sessionId") String sessionId, @Param("isPinned") boolean isPinned,
			@Param("updateTime") LocalDateTime updateTime);

	/**
	 * 更新会话标题。
	 * @param sessionId 会话 ID
	 * @param title 新标题
	 * @param updateTime 更新时间
	 * @return 受影响行数
	 */
	@Update("""
			UPDATE chat_session SET
				title = #{title},
				update_time = #{updateTime}
			WHERE id = #{sessionId}
			""")
	int updateTitle(@Param("sessionId") String sessionId, @Param("title") String title,
			@Param("updateTime") LocalDateTime updateTime);

	/**
	 * 软删除单个会话（将状态置为 {@code deleted}）。
	 * @param sessionId 会话 ID
	 * @param updateTime 更新时间
	 * @return 受影响行数
	 */
	@Update("""
			UPDATE chat_session
			SET status = 'deleted', update_time = #{updateTime}
			WHERE id = #{sessionId}
			""")
	int softDeleteById(@Param("sessionId") String sessionId, @Param("updateTime") LocalDateTime updateTime);

	/**
	 * 新增会话记录。
	 * @param session 会话实体
	 * @return 受影响行数
	 */
	@Insert("""
			INSERT INTO chat_session (id, agent_id, title, status, is_pinned, user_id, create_time, update_time)
			VALUES (#{id}, #{agentId}, #{title}, #{status}, #{isPinned}, #{userId}, #{createTime}, #{updateTime})
			""")
	int insert(ChatSession session);

}
