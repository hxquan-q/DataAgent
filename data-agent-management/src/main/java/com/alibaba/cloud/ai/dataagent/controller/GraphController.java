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
import com.alibaba.cloud.ai.dataagent.service.graph.GraphService;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_COMPLETE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_ERROR;

/**
 * 工作流流式搜索控制器。
 * <p>
 * 核心接口：以 SSE（Server-Sent Events）方式流式返回 Agent 工作流每一步的执行结果， 包括意图识别、Schema 召回、SQL
 * 生成/执行、Python 分析、报告生成等节点输出。
 * </p>
 *
 * @author zhangshenghang
 * @author vlsmb
 */
@Slf4j
@RestController
@AllArgsConstructor
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class GraphController {

	private final GraphService graphService;

	/**
	 * 流式搜索接口（核心入口）。
	 * <p>
	 * 客户端通过 SSE 长连接实时接收工作流各节点的输出， 支持 humanFeedback 人工反馈和 nl2sqlOnly 仅生成 SQL 模式。
	 * </p>
	 * @param agentId Agent ID
	 * @param threadId 会话线程 ID（用于多轮对话上下文关联）
	 * @param query 用户查询
	 * @param humanFeedback 是否处于人工反馈阶段
	 * @param humanFeedbackContent 人工反馈内容
	 * @param rejectedPlan 是否拒绝当前计划（触发重新规划）
	 * @param nl2sqlOnly 是否仅生成 SQL（跳过 Python 分析与报告）
	 * @param response HTTP 响应对象（用于设置 SSE 头）
	 * @return SSE 事件流
	 */
	@GetMapping(value = "/stream/search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<GraphNodeResponse>> streamSearch(@RequestParam("agentId") String agentId,
			@RequestParam(value = "threadId", required = false) String threadId, @RequestParam("query") String query,
			@RequestParam(value = "humanFeedback", required = false) boolean humanFeedback,
			@RequestParam(value = "humanFeedbackContent", required = false) String humanFeedbackContent,
			@RequestParam(value = "rejectedPlan", required = false) boolean rejectedPlan,
			@RequestParam(value = "nl2sqlOnly", required = false) boolean nl2sqlOnly, ServerHttpResponse response) {
		// 设置 SSE 相关 HTTP 头，确保浏览器正确处理事件流
		response.getHeaders().add("Cache-Control", "no-cache");
		response.getHeaders().add("Connection", "keep-alive");
		response.getHeaders().add("Access-Control-Allow-Origin", "*");

		// 创建单播 Sink，用于在工作流执行过程中推送事件
		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();

		// 构建请求并启动工作流流式处理
		GraphRequest request = GraphRequest.builder()
			.agentId(agentId)
			.threadId(threadId)
			.query(query)
			.humanFeedback(humanFeedback)
			.humanFeedbackContent(humanFeedbackContent)
			.rejectedPlan(rejectedPlan)
			.nl2sqlOnly(nl2sqlOnly)
			.build();
		graphService.graphStreamProcess(sink, request);

		return sink.asFlux().filter(sse -> {
			// complete 和 error 事件直接放行，不受空数据过滤影响
			if (STREAM_EVENT_COMPLETE.equals(sse.event()) || STREAM_EVENT_ERROR.equals(sse.event())) {
				return true;
			}
			// 过滤掉数据为空的事件
			return sse.data() != null && sse.data().getText() != null && !sse.data().getText().isEmpty();
		})
			.doOnSubscribe(subscription -> log.info("Client subscribed to stream, threadId: {}", request.getThreadId()))
			.doOnCancel(() -> {
				// 客户端断开连接时，停止工作流执行
				log.info("Client disconnected from stream, threadId: {}", request.getThreadId());
				if (request.getThreadId() != null) {
					graphService.stopStreamProcessing(request.getThreadId());
				}
			})
			.doOnError(e -> {
				log.error("Error occurred during streaming, threadId: {}: ", request.getThreadId(), e);
				if (request.getThreadId() != null) {
					graphService.stopStreamProcessing(request.getThreadId());
				}
			})
			.doOnComplete(() -> log.info("Stream completed successfully, threadId: {}", request.getThreadId()));
	}

}
