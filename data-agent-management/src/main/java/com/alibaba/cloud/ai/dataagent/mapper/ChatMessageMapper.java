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

import com.alibaba.cloud.ai.dataagent.entity.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 聊天消息 Mapper，操作 {@code chat_message} 表。
 * <p>
 * 管理会话内的消息记录（角色、内容、类型、元数据），支持按会话/角色查询、计数、新增与删除。
 * </p>
 */
@Mapper
public interface ChatMessageMapper {

	/**
	 * 根据会话 ID 查询消息列表，按创建时间升序返回。
	 * @param sessionId 会话 ID
	 * @return 消息列表
	 */
	@Select("""
			SELECT * FROM chat_message
			WHERE session_id = #{sessionId}
			ORDER BY create_time ASC
			""")
	List<ChatMessage> selectBySessionId(@Param("sessionId") String sessionId);

	/**
	 * 根据主键查询消息。
	 * @param id 消息 ID
	 * @return 消息；不存在返回 {@code null}
	 */
	@Select("""
			SELECT * FROM chat_message
			WHERE id = #{id}
			""")
	ChatMessage selectById(@Param("id") Long id);

	/**
	 * 根据会话 ID 统计消息数量。
	 * @param sessionId 会话 ID
	 * @return 消息数量
	 */
	@Select("""
			SELECT COUNT(*) FROM chat_message
			WHERE session_id = #{sessionId}
			""")
	int countBySessionId(@Param("sessionId") String sessionId);

	/**
	 * 根据会话 ID 与角色查询消息列表，按创建时间升序返回。
	 * @param sessionId 会话 ID
	 * @param role 消息角色（如 user/assistant）
	 * @return 消息列表
	 */
	@Select("""
			SELECT * FROM chat_message
			WHERE session_id = #{sessionId}
			AND role = #{role}
			ORDER BY create_time ASC
			""")
	List<ChatMessage> selectBySessionIdAndRole(@Param("sessionId") String sessionId, @Param("role") String role);

	/**
	 * 新增消息记录，并将自增主键回填到入参对象的 {@code id} 字段。
	 * @param message 消息实体
	 * @return 受影响行数
	 */
	@Insert("""
			INSERT INTO chat_message (session_id, role, content, message_type, metadata, create_time)
			VALUES (#{sessionId}, #{role}, #{content}, #{messageType}, #{metadata}, NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(ChatMessage message);

	/**
	 * 根据主键删除消息。
	 * @param id 消息 ID
	 * @return 受影响行数
	 */
	@Delete("""
			DELETE FROM chat_message
			WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

}
