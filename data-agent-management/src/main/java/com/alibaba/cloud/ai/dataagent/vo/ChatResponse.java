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
package com.alibaba.cloud.ai.dataagent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应 VO
 *
 * <p>
 * 封装聊天接口的返回数据，包括会话ID、消息内容、消息类型、生成的 SQL、 查询结果和错误信息。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

	/** 会话ID */
	private String sessionId;

	/** 消息内容 */
	private String message;

	/** 消息类型：text-文本，sql-SQL语句，result-查询结果，error-错误信息 */
	private String messageType;

	/** 生成的 SQL 语句 */
	private String sql;

	/** 查询结果 */
	private Object result;

	/** 错误信息 */
	private String error;

	public ChatResponse(String sessionId, String message, String messageType) {
		this.sessionId = sessionId;
		this.message = message;
		this.messageType = messageType;
	}

}
