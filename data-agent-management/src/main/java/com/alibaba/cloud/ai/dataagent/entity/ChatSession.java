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
package com.alibaba.cloud.ai.dataagent.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 聊天会话实体类
 *
 * <p>
 * 表示用户与某个智能体之间的一次完整对话会话。一个会话包含多条聊天消息（ChatMessage）， 并记录会话状态与是否置顶等信息。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

	/** 会话ID（UUID） */
	private String id;

	/** 关联的智能体ID */
	private Integer agentId;

	/** 会话标题 */
	private String title;

	/** 会话状态：active-活跃，archived-已归档，deleted-已删除 */
	private String status;

	/** 是否置顶 */
	@Builder.Default
	private Boolean isPinned = false;

	/** 用户ID */
	private Long userId;

	/** 创建时间 */
	private LocalDateTime createTime;

	/** 更新时间 */
	private LocalDateTime updateTime;

	public ChatSession(String id, Integer agentId, String title, String status, Long userId) {
		this.id = id;
		this.agentId = agentId;
		this.title = title;
		this.status = status;
		this.isPinned = false;
		this.userId = userId;
	}

}
