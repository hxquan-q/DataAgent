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

import com.alibaba.cloud.ai.dataagent.entity.ChatMessage;

import java.util.List;

/**
 * 聊天消息服务接口，提供会话消息的查询和保存能力。
 */
public interface ChatMessageService {

	/**
	 * 根据会话 ID 获取消息列表。
	 * @param sessionId 会话唯一标识
	 * @return 该会话下的消息列表
	 */
	List<ChatMessage> findBySessionId(String sessionId);

	/**
	 * 保存聊天消息。
	 * @param message 待保存的消息对象
	 * @return 保存后的消息对象（包含生成的 ID）
	 */
	ChatMessage saveMessage(ChatMessage message);

}
