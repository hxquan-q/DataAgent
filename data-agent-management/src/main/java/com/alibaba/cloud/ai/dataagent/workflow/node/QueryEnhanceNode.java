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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.util.*;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 查询增强节点，位于证据召回节点之后、Schema 召回节点之前。
 *
 * <p>
 * 该节点根据召回的 evidence 信息对用户原始查询进行业务术语翻译、改写与扩展， 输出规范化查询（canonicalQuery）和扩展查询列表。
 * 此节点不需要提取关键词，因为若使用混合检索（如 ES 等），检索库会自行分词并计算相关性。
 * </p>
 *
 * @see EvidenceRecallNode
 * @see SchemaRecallNode
 */
@Slf4j
@Component
@AllArgsConstructor
public class QueryEnhanceNode implements NodeAction {

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	/**
	 * 执行查询增强逻辑。
	 * <p>
	 * 从状态中获取用户输入与召回的证据信息，构建查询增强提示词并调用大模型， 最终将增强后的查询结果写入状态。
	 * </p>
	 * @param state 工作流全局状态，包含用户输入、证据和多轮对话上下文
	 * @return 包含查询增强结果的 Map，key 为 {@value QUERY_ENHANCE_NODE_OUTPUT}，value 为流式生成器
	 * @throws Exception 调用大模型或解析结果时可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 获取用户输入与证据信息
		String userInput = StateUtil.getStringValue(state, INPUT_KEY);
		log.info("查询增强节点接收到的用户输入: {}", userInput);

		String evidence = StateUtil.getStringValue(state, EVIDENCE);
		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		// 构建查询增强提示词
		String prompt = PromptHelper.buildQueryEnhancePrompt(multiTurn, userInput, evidence);
		log.debug("构建的查询增强提示词如下 \n {} \n", prompt);

		// 调用大模型进行查询增强
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

		// 创建流式生成器，前置/后置提示信息 + 结果解析回调
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(this.getClass(), state,
				responseFlux,
				Flux.just(ChatResponseUtil.createResponse("正在进行问题增强..."),
						ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
						ChatResponseUtil.createResponse("\n问题增强完成！")),
				this::handleQueryEnhance);

		return Map.of(QUERY_ENHANCE_NODE_OUTPUT, generator);
	}

	/**
	 * 处理大模型返回的查询增强结果，解析并转换为 {@link QueryEnhanceOutputDTO}。
	 * @param llmOutput 大模型返回的原始文本
	 * @return 包含查询增强结果的 Map；解析失败时返回空 Map
	 */
	private Map<String, Object> handleQueryEnhance(String llmOutput) {
		// 提取纯文本结果
		String enhanceResult = MarkdownParserUtil.extractRawText(llmOutput.trim());
		log.info("查询增强结果: {}", enhanceResult);

		// 解析结果并转换为 QueryEnhanceOutputDTO
		QueryEnhanceOutputDTO queryEnhanceOutputDTO = null;
		try {
			queryEnhanceOutputDTO = jsonParseUtil.tryConvertToObject(enhanceResult, QueryEnhanceOutputDTO.class);
			log.info("成功解析查询增强结果: {}", queryEnhanceOutputDTO);
		}
		catch (Exception e) {
			log.error("解析查询增强结果失败: {}", enhanceResult, e);
		}

		if (queryEnhanceOutputDTO == null)
			return Map.of();
		// 返回解析后的查询增强结果
		return Map.of(QUERY_ENHANCE_NODE_OUTPUT, queryEnhanceOutputDTO);
	}

}
