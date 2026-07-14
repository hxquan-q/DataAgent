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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.service.chat.SessionEventPublisher;
import com.alibaba.cloud.ai.dataagent.vo.SessionUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.springframework.http.server.reactive.ServerHttpResponse;

/**
 * 会话事件 SSE 推送控制器。
 * <p>
 * 以 Server-Sent Events 方式实时推送会话更新事件（如新会话创建、会话删除等），
 * 供前端实时刷新会话列表。
 * </p>
 */
@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
@RequiredArgsConstructor
public class SessionEventController {

	private final SessionEventPublisher sessionEventPublisher;

	/**
	 * 订阅指定 Agent 的会话更新事件流。
	 * @param agentId  Agent ID
	 * @param response HTTP 响应（用于设置 SSE 头）
	 * @return SSE 事件流
	 */
	@GetMapping(value = "/agent/{agentId}/sessions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<SessionUpdateEvent>> streamSessionUpdates(@PathVariable Integer agentId,
			ServerHttpResponse response) {
		response.getHeaders().add("Cache-Control", "no-cache");
		response.getHeaders().add("Connection", "keep-alive");
		response.getHeaders().add("Access-Control-Allow-Origin", "*");

		log.debug("Client subscribed to session update stream for agent {}", agentId);
		return sessionEventPublisher.register(agentId)
			.doFinally(
					signal -> log.debug("Session update stream finished for agent {} with signal {}", agentId, signal));
	}

}
