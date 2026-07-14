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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.TRACE_THREAD_ID;

/**
 * 响应式流（Reactor Flux）工具类。
 * <p>
 * 提供节点动作（{@link NodeAction}）的流式响应构造、Flux 级联拼接、以及 Langfuse token 用量累计等能力， 是工作流节点输出 GraphResponse 流的统一入口。
 * </p>
 *
 * @author vlsmb
 * @since 2025/10/22
 */
public final class FluxUtil {

	private FluxUtil() {
	}

	/**
	 * 级联两个具有前后关系的 Flux，支持在前后插入额外的信息流。
	 * @param <T> 流中元素类型
	 * @param <R> 聚合结果类型
	 * @param originFlux 第一个 Flux
	 * @param nextFluxFunc 根据第一个 Flux 的聚合结果生成第二个 Flux 的函数
	 * @param aggregator 将第一个 Flux 聚合为单个结果的函数
	 * @param preFlux 在第一个 Flux 之前追加的信息流
	 * @param middleFlux 在两个 Flux 之间追加的信息流
	 * @param endFlux 在第二个 Flux 之后追加的信息流
	 * @return 拼接后的完整 Flux
	 */
	public static <T, R> Flux<T> cascadeFlux(Flux<T> originFlux, Function<R, Flux<T>> nextFluxFunc,
			Function<Flux<T>, Mono<R>> aggregator, Flux<T> preFlux, Flux<T> middleFlux, Flux<T> endFlux) {
		// 缓存原始流避免重复执行
		Flux<T> cachedOrigin = originFlux.cache();

		// 聚合结果，并缓存以供下游复用
		Mono<R> aggregatedResult = aggregator.apply(cachedOrigin).cache();

		// 基于聚合结果构建第二个 Flux
		Flux<T> secondFlux = aggregatedResult.flatMapMany(nextFluxFunc);

		// 依次拼接：前缀流 + 原始流 + 中间流 + 第二个流 + 后缀流
		return preFlux.concatWith(cachedOrigin).concatWith(middleFlux).concatWith(secondFlux).concatWith(endFlux);
	}

	/**
	 * 级联两个具有前后关系的 Flux（不带额外信息流）。
	 * @param <T> 流中元素类型
	 * @param <R> 聚合结果类型
	 * @param originFlux 第一个 Flux
	 * @param nextFluxFunc 根据第一个 Flux 的聚合结果生成第二个 Flux 的函数
	 * @param aggregator 将第一个 Flux 聚合为单个结果的函数
	 * @return 拼接后的完整 Flux
	 */
	public static <T, R> Flux<T> cascadeFlux(Flux<T> originFlux, Function<R, Flux<T>> nextFluxFunc,
			Function<Flux<T>, Mono<R>> aggregator) {
		return cascadeFlux(originFlux, nextFluxFunc, aggregator, Flux.empty(), Flux.empty(), Flux.empty());
	}

	/**
	 * 快速构造一个携带起止提示消息的流式响应生成器。
	 * @param nodeClass 节点类，用于获取节点名称
	 * @param state 全局状态
	 * @param startMessage 起始提示消息，为 null 时不输出
	 * @param completionMessage 完成提示消息，为 null 时不输出
	 * @param resultMapper 将收集到的完整文本映射为节点输出结果的函数
	 * @param sourceFlux 源数据流
	 * @return 流式响应 Flux
	 */
	public static Flux<GraphResponse<StreamingOutput>> createStreamingGeneratorWithMessages(
			Class<? extends NodeAction> nodeClass, OverAllState state, String startMessage, String completionMessage,
			Function<String, Map<String, Object>> resultMapper, Flux<ChatResponse> sourceFlux) {
		String nodeName = nodeClass.getSimpleName();

		// 用于收集实际处理结果文本
		final StringBuilder collectedResult = new StringBuilder();

		// 构造带起始消息的前缀流
		Flux<ChatResponse> startFlux = (startMessage == null ? Flux.empty()
				: Flux.just(ChatResponseUtil.createResponse(startMessage)));
		Flux<ChatResponse> wrapperFlux = startFlux.concatWith(sourceFlux.doOnNext(chatResponse -> {
			// 边接收边累积结果文本
			String text = ChatResponseUtil.getText(chatResponse);
			collectedResult.append(text);
		}));
		// 追加完成消息
		if (completionMessage != null) {
			wrapperFlux = wrapperFlux.concatWith(Flux.just(ChatResponseUtil.createResponse(completionMessage)));
		}
		return toStreamingResponseFlux(nodeName, state, wrapperFlux,
				() -> resultMapper.apply(collectedResult.toString()));
	}

	/**
	 * 构造一个不带起止提示消息的流式响应生成器。
	 * @param nodeClass 节点类，用于获取节点名称
	 * @param state 全局状态
	 * @param resultMapper 将收集到的完整文本映射为节点输出结果的函数
	 * @param sourceFlux 源数据流
	 * @return 流式响应 Flux
	 */
	public static Flux<GraphResponse<StreamingOutput>> createStreamingGeneratorWithMessages(
			Class<? extends NodeAction> nodeClass, OverAllState state,
			Function<String, Map<String, Object>> resultMapper, Flux<ChatResponse> sourceFlux) {
		return createStreamingGeneratorWithMessages(nodeClass, state, null, null, resultMapper, sourceFlux);
	}

	/**
	 * 构造一个带前后额外 Flux 的流式响应生成器。
	 * @param nodeClass 节点类，用于获取节点名称
	 * @param state 全局状态
	 * @param sourceFlux 源数据流
	 * @param preFlux 前置 Flux
	 * @param sufFlux 后置 Flux
	 * @param sourceMapper 将 <code>sourceFlux</code> 收集结果映射为节点输出的函数
	 * @return 流式响应 Flux
	 */
	public static Flux<GraphResponse<StreamingOutput>> createStreamingGenerator(Class<? extends NodeAction> nodeClass,
			OverAllState state, Flux<ChatResponse> sourceFlux, Flux<ChatResponse> preFlux, Flux<ChatResponse> sufFlux,
			Function<String, Map<String, Object>> sourceMapper) {
		String nodeName = nodeClass.getSimpleName();
		// 用于收集实际处理结果文本
		final StringBuilder collectedResult = new StringBuilder();
		sourceFlux = sourceFlux.doOnNext(r -> collectedResult.append(ChatResponseUtil.getText(r)));
		return toStreamingResponseFlux(nodeName, state, Flux.concat(preFlux, sourceFlux, sufFlux),
				() -> sourceMapper.apply(collectedResult.toString()));
	}

	/**
	 * 将 ChatResponse 流转换为 GraphResponse 流式输出。
	 * <p>
	 * 同时累计 token 用量到 Langfuse，并在流末尾追加 DONE 信号，异常时返回 ERROR 信号。
	 * </p>
	 * @param nodeName 节点名称
	 * @param state 全局状态
	 * @param sourceFlux 源数据流
	 * @param resultSupplier 节点输出结果的供应者
	 * @return 流式响应 Flux
	 */
	private static Flux<GraphResponse<StreamingOutput>> toStreamingResponseFlux(String nodeName, OverAllState state,
			Flux<ChatResponse> sourceFlux, Supplier<Map<String, Object>> resultSupplier) {
		Object threadId = state.value(TRACE_THREAD_ID).orElse(null);

		// 累计 token 用量，过滤无效响应，并将有效响应转换为 StreamingOutput
		Flux<GraphResponse<StreamingOutput>> streamingFlux = sourceFlux
			.doOnNext(response -> extractAndAccumulateTokens(threadId, response))
			.filter(response -> response != null && response.getResult() != null
					&& response.getResult().getOutput() != null)
			.map(response -> GraphResponse.of(new StreamingOutput<>(response.getResult().getOutput(), response,
					nodeName, "", state, OutputType.from(true, nodeName))));

		// 追加 DONE 信号；异常时降级为 ERROR 信号
		return streamingFlux.concatWith(Mono.fromSupplier(() -> GraphResponse.done(resultSupplier.get())))
			.onErrorResume(error -> Flux.just(GraphResponse.error(error)));
	}

	/**
	 * 从 {@link ChatResponse} 中提取 token 用量并累计到 Langfuse Reporter。
	 * @param threadId 追踪线程 ID，为 null 时跳过
	 * @param response 聊天响应
	 */
	private static void extractAndAccumulateTokens(Object threadId, ChatResponse response) {
		// 缺少追踪上下文或元数据时直接跳过
		if (threadId == null || response.getMetadata() == null) {
			return;
		}
		Usage usage = response.getMetadata().getUsage();
		// 仅当存在有效 token 用量时累计
		if (usage != null && (usage.getPromptTokens() > 0 || usage.getCompletionTokens() > 0)) {
			LangfuseService.accumulateTokens(threadId, usage.getPromptTokens(), usage.getCompletionTokens());
		}
	}

}
