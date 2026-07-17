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

import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.entity.AgentPresetQuestion;
import com.alibaba.cloud.ai.dataagent.exception.EmbedException;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentPresetQuestionService;
import com.alibaba.cloud.ai.dataagent.service.embed.EmbedPublicService;
import com.alibaba.cloud.ai.dataagent.service.embed.EmbedPublicService.ExchangeResult;
import com.alibaba.cloud.ai.dataagent.service.graph.GraphService;
import com.alibaba.cloud.ai.dataagent.vo.EmbedConfig;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;

/**
 * embed 公开接口控制器（免登录态，经会话令牌/发布令牌鉴权）。
 *
 * <p>
 * 对话 SSE 复用 {@link GraphService#graphStreamProcess}，与 {@code GraphController.streamSearch} 同一对话执行链路，
 * 仅鉴权方式不同（embed 会话令牌 vs 隐式登录态）。embed 页与后端同域，SSE 无跨域问题。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/embed/public")
@RequiredArgsConstructor
public class EmbedPublicController {

	private final EmbedPublicService embedService;

	private final GraphService graphService;

	private final AgentPresetQuestionService presetQuestionService;

	/** 读 embed UI 配置（不含令牌），供 widget 初始化。 */
	@GetMapping("/{agentId}/config")
	public EmbedConfig config(@PathVariable Long agentId) {
		return embedService.getEmbedConfig(agentId);
	}

	/**
	 * 公开只读预设问题（embed 启用校验）。避免管理端全局鉴权后
	 * {@code GET /api/agent/{id}/preset-questions} 对 embed 页 401。
	 */
	@GetMapping("/{agentId}/preset-questions")
	public List<AgentPresetQuestion> presetQuestions(@PathVariable Long agentId) {
		// 复用 getEmbedConfig 的 embed 启用校验（agent 不存在/未启用会抛 EmbedException）
		embedService.getEmbedConfig(agentId);
		return presetQuestionService.findByAgentId(agentId);
	}

	/** 发布令牌（X-Publish-Token = Agent.apiKey）换短期会话令牌。 */
	@PostMapping("/{agentId}/exchange")
	public ExchangeResult exchange(@PathVariable Long agentId,
			@RequestHeader("X-Publish-Token") String publishToken) {
		return embedService.exchange(agentId, publishToken);
	}

	/** 创建 embed 会话，返回 sessionId（兼作 Graph threadId）。 */
	@PostMapping("/{agentId}/sessions")
	public Map<String, String> createSession(@PathVariable Long agentId,
			@RequestHeader("X-Session-Token") String sessionToken) {
		String sessionId = embedService.createEmbedSession(agentId, sessionToken);
		return Map.of("sessionId", sessionId);
	}

	/**
	 * embed 对话 SSE。sessionToken 同时支持 header（fetch 流）与 query（EventSource）两种来源——
	 * EventSource 无法设置请求头，故 query 兜底。
	 * <p>
	 * 安全注意：query token 可能进 access log / Referer。优先 header；query 仅 EventSource 兼容。
	 * 部署层应对 access log 脱敏 {@code token=}；后续可升级为一次性 SSE ticket。
	 * 本方法<strong>绝不</strong>把 token 写入业务日志。
	 * </p>
	 */
	@GetMapping(value = "/{agentId}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<GraphNodeResponse>> chat(@PathVariable Long agentId, @RequestParam String query,
			@RequestParam(value = "threadId", required = false) String threadId,
			@RequestHeader(value = "X-Session-Token", required = false) String headerToken,
			@RequestParam(value = "token", required = false) String queryToken, ServerHttpResponse response) {

		// header 优先，降低 token 进 URL/日志面
		String sessionToken = headerToken != null && !headerToken.isBlank() ? headerToken : queryToken;
		embedService.verifySession(sessionToken, agentId);

		response.getHeaders().add("Cache-Control", "no-cache");
		response.getHeaders().add("Connection", "keep-alive");
		response.getHeaders().add("Referrer-Policy", "no-referrer");

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
		GraphRequest request = GraphRequest.builder()
			.agentId(String.valueOf(agentId))
			.threadId(threadId)
			.query(query)
			.build();
		graphService.graphStreamProcess(sink, request);

		final String effectiveThreadId = threadId;
		return sink.asFlux().doOnCancel(() -> {
			log.info("embed client disconnected, threadId: {}", effectiveThreadId);
			if (effectiveThreadId != null) {
				graphService.stopStreamProcessing(effectiveThreadId);
			}
		});
	}

	/** 停止指定会话的流式生成。 */
	@PostMapping("/{agentId}/chat/{threadId}/stop")
	public Map<String, Boolean> stop(@PathVariable Long agentId, @PathVariable String threadId,
			@RequestHeader("X-Session-Token") String sessionToken) {
		embedService.verifySession(sessionToken, agentId);
		graphService.stopStreamProcessing(threadId);
		return Map.of("ok", true);
	}

	@ExceptionHandler(EmbedException.class)
	public ResponseEntity<Map<String, String>> handleEmbedException(EmbedException e) {
		return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
	}

}
