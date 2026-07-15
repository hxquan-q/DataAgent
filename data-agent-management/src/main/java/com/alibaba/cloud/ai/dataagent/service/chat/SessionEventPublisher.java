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

import com.alibaba.cloud.ai.dataagent.vo.SessionUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话更新事件发布器，管理向前端推送会话更新的 SSE 流。 一个 Agent 对应一个共享的 Sink，多个连接共享同一个 Sink。
 */
@Slf4j
@Service
public class SessionEventPublisher {

	/** 以 agentId 为键的共享 Sink 映射 */
	private final Map<Integer, AgentSessionSink> sinks = new ConcurrentHashMap<>();

	/**
	 * 注册订阅者，返回合并了心跳流和事件流的 SSE Flux。
	 * @param agentId Agent 主键 ID
	 * @return 合并了心跳和会话更新事件的 SSE Flux
	 */
	public Flux<ServerSentEvent<SessionUpdateEvent>> register(Integer agentId) {
		// 获取或创建该 Agent 的共享 Sink
		AgentSessionSink sink = sinks.computeIfAbsent(agentId, id -> new AgentSessionSink());
		// 构建每 2 秒一次的心跳流，保持连接活跃
		Flux<ServerSentEvent<SessionUpdateEvent>> heartbeat = Flux.interval(Duration.ofSeconds(2))
			.map(i -> ServerSentEvent.<SessionUpdateEvent>builder().comment("heartbeat").build());
		sink.increment();
		log.debug("Registered subscriber for agent {}, current count: {}", agentId, sink.subscribers.get());
		// 合并心跳流与事件流，并在连接断开时执行清理
		return Flux.merge(heartbeat, sink.sink.asFlux()).doFinally(signalType -> cleanup(agentId, sink, signalType));
	}

	/**
	 * 向订阅者推送会话标题更新事件。
	 * @param agentId Agent 主键 ID
	 * @param sessionId 会话唯一标识
	 * @param title 更新后的标题
	 */
	public void publishTitleUpdated(Integer agentId, String sessionId, String title) {
		if (agentId == null) {
			return;
		}
		SessionUpdateEvent event = SessionUpdateEvent.titleUpdated(sessionId, title);
		AgentSessionSink sink = sinks.get(agentId);
		if (sink == null) {
			log.debug("No active subscribers for agent {}, skip pushing session title update", agentId);
			return;
		}
		Sinks.EmitResult result = sink.sink.tryEmitNext(ServerSentEvent.builder(event).event(event.getType()).build());
		if (result.isFailure()) {
			log.warn("Failed to emit session title update for agent {}, session {}, reason {}", agentId, sessionId,
					result);
		}
	}

	/**
	 * 连接断开时的清理逻辑，减少订阅计数，当无订阅者时移除并关闭 Sink。
	 * @param agentId Agent 主键 ID
	 * @param sink 对应的共享 Sink
	 * @param signalType 断开信号类型
	 */
	private void cleanup(Integer agentId, AgentSessionSink sink, SignalType signalType) {
		int current = sink.decrement();
		log.debug("Cleanup called for agent {}, signal: {}, remaining subscribers: {}", agentId, signalType, current);
		if (current <= 0) {
			// 使用 remove(key, value) 确保只移除当前的 sink 实例，防止并发问题
			if (sinks.remove(agentId, sink)) {
				sink.sink.tryEmitComplete();
				log.debug("Removed session update sink for agent {}", agentId);
			}
		}
	}

	/**
	 * Agent 会话共享 Sink，维护订阅者计数和事件 Sink。
	 */
	private static class AgentSessionSink {

		/** 当前订阅者数量 */
		private final AtomicInteger subscribers = new AtomicInteger(0);

		/** 多播事件 Sink，带背压缓冲 */
		private final Sinks.Many<ServerSentEvent<SessionUpdateEvent>> sink = Sinks.many()
			.multicast()
			.onBackpressureBuffer();

		/** 增加订阅者计数 */
		private void increment() {
			subscribers.incrementAndGet();
		}

		/** 减少订阅者计数并返回当前值 */
		private int decrement() {
			return subscribers.decrementAndGet();
		}

	}

}
