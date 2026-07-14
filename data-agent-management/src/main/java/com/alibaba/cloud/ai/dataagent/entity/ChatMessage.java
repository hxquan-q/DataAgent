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
 * 聊天消息实体类
 *
 * <p>
 * 记录用户与智能体之间的单条对话消息。每条消息归属一个会话（ChatSession）， 并区分角色（用户/助手/系统）与消息类型（文本/SQL/结果/错误）。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

	/** 主键ID */
	private Long id;

	/** 所属会话ID */
	private String sessionId;

	/** 消息角色：user-用户，assistant-助手，system-系统 */
	private String role;

	/** 消息内容 */
	private String content;

	/** 消息类型：text-文本，sql-SQL语句，result-查询结果，error-错误信息 */
	private String messageType;

	/** JSON 格式的元数据 */
	private String metadata;

	/** 创建时间 */
	private LocalDateTime createTime;

}
