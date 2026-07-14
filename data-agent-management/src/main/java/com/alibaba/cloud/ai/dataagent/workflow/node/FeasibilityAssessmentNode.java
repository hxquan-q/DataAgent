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

import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
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
 * 可行性评估节点，位于表关系推断之后、计划生成之前。
 *
 * <p>
 * 该节点基于召回的 Schema 和证据信息，通过大模型评估当前需求是否可执行数据分析。 评估结果决定后续路由：
 * <ul>
 * <li>需求类型为"数据分析"：进入计划生成节点（{@code PlannerNode}）</li>
 * <li>需要澄清或为自由闲聊：结束流程</li>
 * </ul>
 * </p>
 *
 * @see TableRelationNode
 * @see PlannerNode
 */
@Slf4j
@Component
@AllArgsConstructor
public class FeasibilityAssessmentNode implements NodeAction {

	private final LlmService llmService;

	/**
	 * 执行可行性评估逻辑。
	 * <p>
	 * 获取规范化查询、召回的 Schema 和证据信息，构建可行性评估提示词并调用大模型， 最终将评估结果写入状态。
	 * </p>
	 * @param state 工作流全局状态
	 * @return 包含可行性评估结果的 Map，key 为 {@value FEASIBILITY_ASSESSMENT_NODE_OUTPUT}
	 * @throws Exception 调用大模型时可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		// 获取规范化查询
		String canonicalQuery = StateUtil.getCanonicalQuery(state);

		// 获取召回的 Schema
		SchemaDTO recalledSchema = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);

		// 获取证据信息
		String evidence = StateUtil.getStringValue(state, EVIDENCE);

		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		// 构建可行性评估提示词
		String prompt = PromptHelper.buildFeasibilityAssessmentPrompt(canonicalQuery, recalledSchema, evidence,
				multiTurn);
		log.debug("构建的可行性评估提示词如下 \n {} \n", prompt);

		// 调用大模型进行可行性评估
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

		// 创建流式生成器，前置/后置提示信息 + 结果解析回调
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "正在进行可行性评估...", "可行性评估完成！", llmOutput -> {
					// 获取评估结果
					String assessmentResult = llmOutput.trim();
					log.info("可行性评估结果: {}", assessmentResult);
					// 返回评估结果
					return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, assessmentResult);
				}, responseFlux);
		return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, generator);
	}

}
