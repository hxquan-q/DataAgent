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
package com.alibaba.cloud.ai.dataagent.service.graph;

import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.workflow.node.PlannerNode;
import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.MultiTurnContextManager;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.StreamContext;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 图执行服务实现类，负责驱动 DataAgent 工作流图的编译与执行。
 *
 * <p>
 * 主要职责包括：
 * <ul>
 * <li>同步执行自然语言转 SQL（NL2SQL）流程；</li>
 * <li>基于 Reactor Flux 以流式（SSE）方式执行完整 DataAgent 流程，并支持人工反馈（Human Feedback）中断与恢复；</li>
 * <li>管理每个会话线程（threadId）的流式上下文（{@link StreamContext}），保证线程安全地启动、停止和清理资源；</li>
 * <li>集成 Langfuse 进行 LLM 调用链路追踪，并在多轮对话场景下管理上下文。
 * </ul>
 * </p>
 *
 * @author vlsmb
 * @since 2025/10/30
 */
@Slf4j
@Service
public class GraphServiceImpl implements GraphService {

	/**
	 * 已编译的工作流图集合（按 graphName 索引）。 启动期编译两类图：默认 NL2SQL 图与 v0.2 语义层图，按 Agent 的
	 * {@code workflow_mode} 透明路由（η₁ 分类定方法：自由生成走 {@code nl2sql}，受控拼装走 {@code semantic}）。
	 */
	private final Map<String, CompiledGraph> compiledGraphs = new ConcurrentHashMap<>();

	/**
	 * 测试可见的图集合快照（只读），供路由单测按图名取实例做引用断言。非生产 API。
	 * @return 不可修改的图名 → 已编译图 映射
	 */
	Map<String, CompiledGraph> getCompiledGraphsView() {
		return java.util.Collections.unmodifiableMap(compiledGraphs);
	}

	/** Agent 数据访问，用于查询 Agent 的工作流模式以选图 */
	private final AgentMapper agentMapper;

	/** 异步执行线程池，用于在后台订阅 Flux 流 */
	private final ExecutorService executor;

	/** 以 threadId 为键的流式上下文映射，保存每个会话的运行状态 */
	private final ConcurrentHashMap<String, StreamContext> streamContextMap = new ConcurrentHashMap<>();

	/** 多轮对话上下文管理器，维护历史问答与计划 */
	private final MultiTurnContextManager multiTurnContextManager;

	/** Langfuse 链路追踪上报服务 */
	private final LangfuseService langfuseReporter;

	/**
	 * 构造方法，编译两个状态图并注入所需依赖。
	 * <p>
	 * 默认 {@code nl2sqlGraph}（{@code @Primary}）与 {@code nl2sqlSemanticGraph} 在启动期各编译一次， 均在
	 * {@code HUMAN_FEEDBACK_NODE} 前设置中断点。运行时按 Agent 的 {@code workflow_mode}
	 * 选择，缺省/fail-open 走默认图， 保证既有 NL2SQL 行为零回归。
	 * </p>
	 * @param defaultStateGraph 默认 NL2SQL 状态图（@Primary）
	 * @param semanticStateGraph v0.2 语义层状态图
	 * @param agentMapper Agent 数据访问（选图用）
	 * @param executorService 异步执行线程池
	 * @param multiTurnContextManager 多轮对话上下文管理器
	 * @param langfuseReporter Langfuse 追踪上报服务
	 * @throws GraphStateException 当状态图编译失败时抛出
	 */
	public GraphServiceImpl(StateGraph defaultStateGraph,
			@Qualifier("nl2sqlSemanticGraph") StateGraph semanticStateGraph, AgentMapper agentMapper,
			ExecutorService executorService, MultiTurnContextManager multiTurnContextManager,
			LangfuseService langfuseReporter) throws GraphStateException {
		// 两图均在人工反馈节点前中断，复用既有人工审核/反问机制
		CompileConfig compileConfig = CompileConfig.builder().interruptBefore(HUMAN_FEEDBACK_NODE).build();
		this.compiledGraphs.put(NL2SQL_GRAPH_NAME, defaultStateGraph.compile(compileConfig));
		this.compiledGraphs.put(NL2SQL_SEMANTIC_GRAPH_NAME, semanticStateGraph.compile(compileConfig));
		this.agentMapper = agentMapper;
		this.executor = executorService;
		this.multiTurnContextManager = multiTurnContextManager;
		this.langfuseReporter = langfuseReporter;
	}

	/**
	 * 按 Agent 的工作流模式选择已编译的图（η₂ 最少自由度 + η₅ fail-open）。
	 * <p>
	 * 查询 Agent 的 {@code workflow_mode}：为 {@code semantic} 时走语义层图，其余（含 null、未知、查询异常）一律走默认
	 * NL2SQL 图。Agent 查询异常只记日志不阻断，保证既有用户体验零回归。
	 * </p>
	 * @param agentId Agent ID（字符串）
	 * @return 选中的已编译图；agentId 非法或 Agent 不存在时返回默认图
	 */
	CompiledGraph resolveGraph(String agentId) {
		if (StringUtils.hasText(agentId)) {
			try {
				Agent agent = agentMapper.findById(Long.valueOf(agentId));
				if (agent != null && "semantic".equalsIgnoreCase(agent.getWorkflowMode())) {
					return compiledGraphs.get(NL2SQL_SEMANTIC_GRAPH_NAME);
				}
			}
			catch (NumberFormatException e) {
				log.debug("agentId 非数字，走默认 NL2SQL 图：{}", agentId);
			}
			catch (Exception e) {
				// fail-open：Agent 查询失败不应阻断请求，降级为默认图
				log.warn("查询 Agent workflow_mode 失败，fail-open 走默认 NL2SQL 图，agentId={}，error={}", agentId,
						e.getMessage());
			}
		}
		return compiledGraphs.get(NL2SQL_GRAPH_NAME);
	}

	/**
	 * 自然语言转 SQL，同步阻塞执行并返回生成的 SQL。
	 * @param naturalQuery 用户的自然语言问题
	 * @param agentId 目标 Agent 的唯一标识
	 * @return 工作流执行后生成的 SQL 字符串
	 * @throws GraphRunnerException 当图执行过程中发生异常时抛出
	 */
	@Override
	public String nl2sql(String naturalQuery, String agentId) throws GraphRunnerException {
		// 以仅生成 SQL 的模式同步调用工作流图（按 Agent 模式选图）
		OverAllState state = resolveGraph(agentId)
			.invoke(Map.of(IS_ONLY_NL2SQL, true, INPUT_KEY, naturalQuery, AGENT_ID, agentId),
					RunnableConfig.builder().build())
			.orElseThrow();
		// 从最终状态中取出 SQL 生成结果
		return state.value(SQL_GENERATE_OUTPUT, "");
	}

	/**
	 * 流式处理 NL2SQL 或 DataAgent 请求，根据请求内容路由到新流程或人工反馈恢复流程。
	 * @param sink SSE 输出 Sink，用于向前端推送节点输出
	 * @param graphRequest 图执行请求体
	 */
	@Override
	public void graphStreamProcess(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest graphRequest) {
		// 若未指定 threadId，则生成新的唯一会话标识
		if (!StringUtils.hasText(graphRequest.getThreadId())) {
			graphRequest.setThreadId(UUID.randomUUID().toString());
		}
		String threadId = graphRequest.getThreadId();
		// 创建或获取当前线程对应的流式上下文
		StreamContext context = streamContextMap.computeIfAbsent(threadId, k -> new StreamContext());
		context.setSink(sink);
		// 根据是否携带人工反馈内容，路由到不同的处理分支
		if (StringUtils.hasText(graphRequest.getHumanFeedbackContent())) {
			handleHumanFeedback(graphRequest);
		}
		else {
			handleNewProcess(graphRequest);
		}
	}

	/**
	 * 停止指定 threadId 的流式处理 线程安全：使用 remove 操作确保只有一个线程能获取到 context
	 * @param threadId 线程ID
	 */
	@Override
	public void stopStreamProcessing(String threadId) {
		if (!StringUtils.hasText(threadId)) {
			return;
		}
		log.info("Stopping stream processing for threadId: {}", threadId);
		multiTurnContextManager.discardPending(threadId);
		StreamContext context = streamContextMap.remove(threadId);
		if (context != null) {
			// 客户端断开，结束 Langfuse span
			if (context.getSpan() != null && context.getSpan().isRecording()) {
				langfuseReporter.endSpanSuccess(context.getSpan(), threadId, context.getCollectedOutput());
			}
			context.cleanup();
			log.info("Cleaned up stream context for threadId: {}", threadId);
		}
	}

	/**
	 * 处理新的流式请求，构建多轮上下文并启动工作流图的流式执行。
	 * @param graphRequest 图执行请求体
	 */
	private void handleNewProcess(GraphRequest graphRequest) {
		String query = PromptHelper.boundQuery(graphRequest.getQuery());
		String agentId = graphRequest.getAgentId();
		String threadId = graphRequest.getThreadId();
		// 仅当非纯 NL2SQL 模式时才允许人工审核
		boolean nl2sqlOnly = graphRequest.isNl2sqlOnly();
		boolean humanReviewEnabled = graphRequest.isHumanFeedback() & !(nl2sqlOnly);
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(agentId) || !StringUtils.hasText(query)) {
			throw new IllegalArgumentException("请求参数无效：threadId、agentId、query 均不能为空");
		}
		StreamContext context = streamContextMap.get(threadId);
		if (context == null || context.getSink() == null) {
			throw new IllegalStateException("流式会话不存在或已失效，threadId=" + threadId + "。请刷新页面后重试。");
		}
		// 检查是否已经清理，如果已清理则不再启动新的流
		if (context.isCleaned()) {
			log.warn("StreamContext already cleaned for threadId: {}, skipping stream start", threadId);
			return;
		}
		// 开始 Langfuse 追踪
		Span span = langfuseReporter.startLLMSpan("graph-stream", graphRequest);
		context.setSpan(span);

		String multiTurnContext = multiTurnContextManager.buildContext(threadId);
		multiTurnContextManager.beginTurn(threadId, query);
		Flux<NodeOutput> nodeOutputFlux = resolveGraph(agentId).stream(
				Map.of(IS_ONLY_NL2SQL, nl2sqlOnly, INPUT_KEY, query, AGENT_ID, agentId, HUMAN_REVIEW_ENABLED,
						humanReviewEnabled, MULTI_TURN_CONTEXT, multiTurnContext, TRACE_THREAD_ID, threadId),
				RunnableConfig.builder().threadId(threadId).build());
		subscribeToFlux(context, nodeOutputFlux, graphRequest, agentId, threadId);
	}

	/**
	 * 处理人工反馈，根据反馈内容更新图状态并恢复被中断的流式执行。
	 * @param graphRequest 携带人工反馈内容的图执行请求体
	 */
	private void handleHumanFeedback(GraphRequest graphRequest) {
		String agentId = graphRequest.getAgentId();
		String threadId = graphRequest.getThreadId();
		String feedbackContent = PromptHelper.boundQuery(graphRequest.getHumanFeedbackContent());
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(agentId) || !StringUtils.hasText(feedbackContent)) {
			throw new IllegalArgumentException("请求参数无效：必要字段不能为空");
		}
		StreamContext context = streamContextMap.get(threadId);
		if (context == null || context.getSink() == null) {
			throw new IllegalStateException("流式会话不存在或已失效，threadId=" + threadId + "。请刷新页面后重试。");
		}
		if (context.isCleaned()) {
			log.warn("StreamContext already cleaned for threadId: {}, skipping stream start", threadId);
			return;
		}
		// 开始 Langfuse 追踪
		Span span = langfuseReporter.startLLMSpan("graph-feedback", graphRequest);
		context.setSpan(span);

		Map<String, Object> feedbackData = Map.of("feedback", !graphRequest.isRejectedPlan(), "feedback_content",
				feedbackContent);
		if (graphRequest.isRejectedPlan()) {
			multiTurnContextManager.restartLastTurn(threadId);
		}
		Map<String, Object> stateUpdate = new HashMap<>();
		stateUpdate.put(HUMAN_FEEDBACK_DATA, feedbackData);
		stateUpdate.put(MULTI_TURN_CONTEXT, multiTurnContextManager.buildContext(threadId));

		RunnableConfig baseConfig = RunnableConfig.builder().threadId(threadId).build();
		// 反馈恢复须用与该轮次相同的工作流图（按 Agent 模式选图）
		CompiledGraph graph = resolveGraph(agentId);
		RunnableConfig updatedConfig;
		try {
			updatedConfig = graph.updateState(baseConfig, stateUpdate);
		}
		catch (Exception e) {
			throw new IllegalStateException("更新人工反馈状态失败，请重试", e);
		}
		RunnableConfig resumeConfig = RunnableConfig.builder(updatedConfig)
			.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, feedbackData)
			.build();

		Flux<NodeOutput> nodeOutputFlux = graph.stream(null, resumeConfig);
		subscribeToFlux(context, nodeOutputFlux, graphRequest, agentId, threadId);
	}

	/**
	 * 订阅 Flux 并原子性地设置 Disposable 线程安全：使用 synchronized 确保 Disposable 设置的原子性
	 * @param context 流式处理上下文
	 * @param nodeOutputFlux 节点输出流
	 * @param graphRequest 图请求
	 * @param agentId 代理ID
	 * @param threadId 线程ID
	 */
	private void subscribeToFlux(StreamContext context, Flux<NodeOutput> nodeOutputFlux, GraphRequest graphRequest,
			String agentId, String threadId) {
		CompletableFuture.runAsync(() -> {
			// 在订阅之前检查上下文是否仍然有效
			if (context.isCleaned()) {
				log.debug("StreamContext cleaned before subscription for threadId: {}", threadId);
				return;
			}
			Disposable disposable = nodeOutputFlux.subscribe(output -> handleNodeOutput(graphRequest, output),
					error -> handleStreamError(agentId, threadId, error),
					() -> handleStreamComplete(agentId, threadId));
			// 原子性地设置 Disposable，如果已经清理则立即释放
			synchronized (context) {
				if (context.isCleaned()) {
					// 如果已经清理，立即释放刚创建的 Disposable
					if (disposable != null && !disposable.isDisposed()) {
						disposable.dispose();
					}
				}
				else {
					// 只有在未清理的情况下才设置 Disposable
					context.setDisposable(disposable);
				}
			}
		}, executor);
	}

	/**
	 * 处理流式错误 线程安全：使用 remove 操作确保只有一个线程能获取到 context
	 */
	private void handleStreamError(String agentId, String threadId, Throwable error) {
		log.error("Error in stream processing for threadId: {}: ", threadId, error);
		StreamContext context = streamContextMap.remove(threadId);
		if (context != null && !context.isCleaned()) {
			// 结束 Langfuse span（失败）
			if (context.getSpan() != null) {
				langfuseReporter.endSpanError(context.getSpan(), threadId,
						error instanceof Exception ? (Exception) error : new RuntimeException(error));
			}
			if (context.getSink() != null && context.getSink().currentSubscriberCount() > 0) {
				context.getSink()
					.tryEmitNext(ServerSentEvent
						.builder(GraphNodeResponse.error(agentId, threadId,
								"Error in stream processing: " + error.getMessage()))
						.event(STREAM_EVENT_ERROR)
						.build());
				context.getSink().tryEmitComplete();
			}
			// 清理资源（cleanup 内部已经保证只执行一次）
			context.cleanup();
		}
	}

	/**
	 * 处理流式完成 线程安全：使用 remove 操作确保只有一个线程能获取到 context
	 */
	private void handleStreamComplete(String agentId, String threadId) {
		log.info("Stream processing completed successfully for threadId: {}", threadId);
		multiTurnContextManager.finishTurn(threadId);
		StreamContext context = streamContextMap.remove(threadId);
		if (context != null && !context.isCleaned()) {
			// 结束 Langfuse span（成功）
			if (context.getSpan() != null) {
				langfuseReporter.endSpanSuccess(context.getSpan(), threadId, context.getCollectedOutput());
			}
			if (context.getSink() != null && context.getSink().currentSubscriberCount() > 0) {
				context.getSink()
					.tryEmitNext(ServerSentEvent.builder(GraphNodeResponse.complete(agentId, threadId))
						.event(STREAM_EVENT_COMPLETE)
						.build());
				context.getSink().tryEmitComplete();
			}
			context.cleanup();
		}
	}

	/**
	 * 处理节点输出
	 */
	/**
	 * 处理节点输出，根据输出类型分发到对应的流式处理逻辑。
	 * @param request 图执行请求体
	 * @param output 工作流节点输出
	 */
	private void handleNodeOutput(GraphRequest request, NodeOutput output) {
		log.debug("Received output: {}", output.getClass().getSimpleName());
		if (output instanceof StreamingOutput streamingOutput) {
			handleStreamNodeOutput(request, streamingOutput);
		}
	}

	/**
	 * 处理流式节点输出，解析文本类型标记，收集输出内容并向前端推送 SSE 事件。
	 * @param request 图执行请求体
	 * @param output 流式输出数据块
	 */
	private void handleStreamNodeOutput(GraphRequest request, StreamingOutput output) {
		String threadId = request.getThreadId();
		StreamContext context = streamContextMap.get(threadId);
		// 检查是否已经停止处理
		if (context == null || context.getSink() == null) {
			log.debug("Stream processing already stopped for threadId: {}, skipping output", threadId);
			return;
		}
		String node = output.node();
		String chunk = output.chunk();
		log.debug("Received Stream output: {}", chunk);

		if (chunk == null || chunk.isEmpty()) {
			return;
		}

		// 如果是文本标记符号，则更新文本类型
		TextType originType = context.getTextType();
		TextType textType;
		boolean isTypeSign = false;
		if (originType == null) {
			textType = TextType.getTypeByStratSign(chunk);
			if (textType != TextType.TEXT) {
				isTypeSign = true;
			}
			context.setTextType(textType);
		}
		else {
			textType = TextType.getType(originType, chunk);
			if (textType != originType) {
				isTypeSign = true;
			}
			context.setTextType(textType);
		}
		// 文本标记符号不返回给前端
		if (!isTypeSign) {
			context.appendOutput(chunk);
			if (PlannerNode.class.getSimpleName().equals(node)) {
				multiTurnContextManager.appendPlannerChunk(threadId, chunk);
			}
			GraphNodeResponse response = GraphNodeResponse.builder()
				.agentId(request.getAgentId())
				.threadId(threadId)
				.nodeName(node)
				.text(chunk)
				.textType(textType)
				.build();
			// 检查发送是否成功，如果失败说明客户端已断开
			Sinks.EmitResult result = context.getSink().tryEmitNext(ServerSentEvent.builder(response).build());
			if (result.isFailure()) {
				log.warn("Failed to emit data to sink for threadId: {}, result: {}. Stopping stream processing.",
						threadId, result);
				// 如果发送失败，停止处理
				stopStreamProcessing(threadId);
			}
		}
	}

}
