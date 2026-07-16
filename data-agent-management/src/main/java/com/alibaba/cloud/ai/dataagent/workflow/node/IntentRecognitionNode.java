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

import com.alibaba.cloud.ai.dataagent.dto.prompt.IntentRecognitionOutputDTO;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 意图识别节点，位于工作流的起始位置。
 *
 * <p>
 * 该节点负责识别用户输入的意图类型，判断当前请求是闲聊或无关指令， 还是数据分析请求。识别结果将作为后续工作流路由的依据：
 * <ul>
 * <li>闲聊或无关指令：直接结束流程</li>
 * <li>数据分析请求：进入证据召回节点（{@code EvidenceRecallNode}）</li>
 * </ul>
 * 该节点通过调用大模型（LLM）完成意图识别，并以流式方式向用户输出处理进度。
 * </p>
 *
 * @see EvidenceRecallNode
 */
@Slf4j
@Component
@AllArgsConstructor
public class IntentRecognitionNode implements NodeAction {

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	/**
	 * 执行意图识别逻辑。
	 * <p>
	 * 主要流程：从全局状态中获取用户输入与多轮对话上下文， 构建意图识别提示词并调用大模型，最终将识别结果写入状态。
	 * </p>
	 * @param state 工作流全局状态，包含用户输入与多轮对话上下文
	 * @return 包含意图识别结果的 Map，key 为 {@value INTENT_RECOGNITION_NODE_OUTPUT}， value 为流式生成器
	 * @throws Exception 调用大模型或解析结果时可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 获取用户输入与多轮对话上下文
		String userInput = StateUtil.getStringValue(state, INPUT_KEY);
		log.info("意图识别节点接收到的用户输入: {}", userInput);

		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		// 构建意图识别提示词
		String prompt = PromptHelper.buildIntentRecognitionPrompt(PromptHelper.boundMultiTurn(multiTurn), PromptHelper.boundQuery(userInput));
		log.debug("构建的意图识别提示词如下 \n {} \n", prompt);

		// 调用大模型进行意图识别
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

		// 创建流式生成器，前置/后置提示信息 + 结果解析回调
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(this.getClass(), state,
				responseFlux,
				Flux.just(ChatResponseUtil.createResponse("正在进行意图识别..."),
						ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
						ChatResponseUtil.createResponse("\n意图识别完成！")),
				result -> {
					// 解析大模型返回的 JSON，并转换为意图识别输出对象
					IntentRecognitionOutputDTO intentRecognitionOutput = jsonParseUtil.tryConvertToObject(result,
							IntentRecognitionOutputDTO.class);
					return Map.of(INTENT_RECOGNITION_NODE_OUTPUT, intentRecognitionOutput);
				});
		return Map.of(INTENT_RECOGNITION_NODE_OUTPUT, generator);
	}

}
