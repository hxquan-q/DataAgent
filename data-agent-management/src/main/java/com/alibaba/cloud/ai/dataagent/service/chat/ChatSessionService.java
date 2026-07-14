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

import java.util.List;

/**
 * 聊天会话服务接口，提供会话的创建、查询、重命名、置顶、删除等管理能力。
 */
public interface ChatSessionService {

	/**
	 * 根据 Agent ID 获取会话列表。
	 * @param agentId Agent 主键 ID
	 * @return 该 Agent 下的会话列表
	 */
	List<ChatSession> findByAgentId(Integer agentId);

	/**
	 * 创建新的聊天会话。
	 * @param agentId 关联的 Agent 主键 ID
	 * @param title 会话标题，为空时使用默认标题
	 * @param userId 创建用户 ID
	 * @return 创建的会话对象
	 */
	ChatSession createSession(Integer agentId, String title, Long userId);

	/**
	 * 根据会话 ID 查询会话。
	 * @param sessionId 会话唯一标识
	 * @return 会话对象，不存在时返回 null
	 */
	ChatSession findBySessionId(String sessionId);

	/**
	 * 清除指定 Agent 的全部会话（软删除）。
	 * @param agentId Agent 主键 ID
	 */
	void clearSessionsByAgentId(Integer agentId);

	/**
	 * 更新会话的最后活动时间。
	 * @param sessionId 会话唯一标识
	 */
	void updateSessionTime(String sessionId);

	/**
	 * 置顶或取消置顶会话。
	 * @param sessionId 会话唯一标识
	 * @param isPinned 是否置顶
	 */
	void pinSession(String sessionId, boolean isPinned);

	/**
	 * 重命名会话标题。
	 * @param sessionId 会话唯一标识
	 * @param newTitle 新的会话标题
	 */
	void renameSession(String sessionId, String newTitle);

	/**
	 * 删除单个会话（软删除）。
	 * @param sessionId 会话唯一标识
	 */
	void deleteSession(String sessionId);

}
