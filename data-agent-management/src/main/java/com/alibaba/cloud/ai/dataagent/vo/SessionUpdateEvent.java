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

import lombok.Builder;
import lombok.Value;

/**
 * 会话更新事件 VO
 *
 * <p>
 * 表示会话状态变更的事件通知，目前支持标题更新事件。采用不可变值对象（@Value）定义。 通过 SSE 等机制推送给前端。
 * </p>
 */
@Value
@Builder
public class SessionUpdateEvent {

	/** 事件类型常量：标题已更新 */
	public static final String TYPE_TITLE_UPDATED = "title-updated";

	/** 事件类型 */
	String type;

	/** 会话ID */
	String sessionId;

	/** 会话标题 */
	String title;

	public static SessionUpdateEvent titleUpdated(String sessionId, String title) {
		return SessionUpdateEvent.builder().type(TYPE_TITLE_UPDATED).sessionId(sessionId).title(title).build();
	}

}
