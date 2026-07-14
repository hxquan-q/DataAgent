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
package com.alibaba.cloud.ai.dataagent.service.chat;

import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 聊天会话服务实现类，实现会话的创建、查询、重命名、置顶、软删除等管理逻辑。
 */
@Service
@Slf4j
@AllArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

	/** 聊天会话数据访问层 */
	private final ChatSessionMapper chatSessionMapper;

	/**
	 * 根据 Agent ID 获取会话列表。
	 * @param agentId Agent 主键 ID
	 * @return 该 Agent 下的会话列表
	 */
	@Override
	public List<ChatSession> findByAgentId(Integer agentId) {
		return chatSessionMapper.selectByAgentId(agentId);
	}

	/**
	 * 根据会话 ID 查询会话。
	 * @param sessionId 会话唯一标识
	 * @return 会话对象，不存在时返回 null
	 */
	@Override
	public ChatSession findBySessionId(String sessionId) {
		return chatSessionMapper.selectBySessionId(sessionId);
	}

	/**
	 * 创建新的聊天会话，生成唯一 sessionId 并持久化。
	 * @param agentId 关联的 Agent 主键 ID
	 * @param title 会话标题，为空时使用默认标题 "新会话"
	 * @param userId 创建用户 ID
	 * @return 创建的会话对象
	 */
	@Override
	public ChatSession createSession(Integer agentId, String title, Long userId) {
		// 生成唯一会话 ID
		String sessionId = UUID.randomUUID().toString();

		ChatSession session = new ChatSession(sessionId, agentId, title != null ? title : "新会话", "active", userId);
		chatSessionMapper.insert(session);

		log.info("Created new chat session: {} for agent: {}", sessionId, agentId);
		return session;
	}

	/**
	 * 清除指定 Agent 的全部会话（软删除）。
	 * @param agentId Agent 主键 ID
	 */
	@Override
	public void clearSessionsByAgentId(Integer agentId) {
		LocalDateTime now = LocalDateTime.now();
		// 批量软删除该 Agent 下的所有会话
		int updated = chatSessionMapper.softDeleteByAgentId(agentId, now);
		log.info("Cleared {} sessions for agent: {}", updated, agentId);
	}

	/**
	 * 更新会话的最后活动时间。
	 * @param sessionId 会话唯一标识
	 */
	@Override
	public void updateSessionTime(String sessionId) {
		LocalDateTime now = LocalDateTime.now();
		chatSessionMapper.updateSessionTime(sessionId, now);
	}

	/**
	 * 置顶或取消置顶会话。
	 * @param sessionId 会话唯一标识
	 * @param isPinned 是否置顶
	 */
	@Override
	public void pinSession(String sessionId, boolean isPinned) {
		LocalDateTime now = LocalDateTime.now();
		chatSessionMapper.updatePinStatus(sessionId, isPinned, now);
		log.info("Updated pin status for session: {} to: {}", sessionId, isPinned);
	}

	/**
	 * 重命名会话标题。
	 * @param sessionId 会话唯一标识
	 * @param newTitle 新的会话标题
	 */
	@Override
	public void renameSession(String sessionId, String newTitle) {
		LocalDateTime now = LocalDateTime.now();
		chatSessionMapper.updateTitle(sessionId, newTitle, now);
		log.info("Renamed session: {} to: {}", sessionId, newTitle);
	}

	/**
	 * 删除单个会话（软删除）。
	 * @param sessionId 会话唯一标识
	 */
	@Override
	public void deleteSession(String sessionId) {
		LocalDateTime now = LocalDateTime.now();
		chatSessionMapper.softDeleteById(sessionId, now);
		log.info("Deleted session: {}", sessionId);
	}

}
