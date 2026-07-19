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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject;
import com.alibaba.cloud.ai.dataagent.entity.Metric;
import com.alibaba.cloud.ai.dataagent.prompt.PromptLoader;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticLayerLoader;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.DATASOURCE_ID;
import static com.alibaba.cloud.ai.dataagent.util.FluxUtil.createStreamingGeneratorWithMessages;

/**
 * 语义解析节点（NL2Semantic2SQL 核心，η₁ 收敛选择空间）。
 * <p>
 * LLM 只从 {@link SemanticLayerLoader#getCandidateMetrics}
 * 收敛的候选指标中**选择**（metric/dim/filter/time）， 输出结构化 {@link SemanticObject}，不生成 SQL。下游
 * {@code BuildSQLNode} 据此受控拼装。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
@Component
@AllArgsConstructor
public class SemanticParseNode implements NodeAction {

	/** 状态键：语义对象（SemanticParse 产出，BuildSQL/Validate 消费） */
	public static final String SEMANTIC_OBJECT = "SEMANTIC_OBJECT";

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	private final SemanticLayerLoader semanticLayerLoader;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		String query = StateUtil.getCanonicalQuery(state);
		Integer agentId = state.value(AGENT_ID, 0);
		Integer datasourceId = state.value(DATASOURCE_ID, 0);
		List<Metric> candidates = semanticLayerLoader.getCandidateMetrics(agentId, datasourceId);
		log.info("语义解析：候选 {} 指标，问题={}", candidates.size(), query);

		String prompt = buildPrompt(query, candidates);
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

		Flux<GraphResponse<StreamingOutput>> generator = createStreamingGeneratorWithMessages(this.getClass(), state,
				"正在进行语义解析...", "语义解析完成！", result -> {
					SemanticObject so = jsonParseUtil.tryConvertToObject(result, SemanticObject.class);
					return Map.of(SEMANTIC_OBJECT, so);
				}, responseFlux);
		return Map.of(SEMANTIC_OBJECT, generator);
	}

	/**
	 * 构建语义解析提示词（候选指标列表 + 用户问题 + JSON 格式约束）。
	 */
	private String buildPrompt(String query, List<Metric> candidates) {
		Map<String, Object> params = new java.util.HashMap<>();
		params.put("latest_query", query != null ? query : "");
		params.put("candidates_info", candidates.stream().map(Metric::getPromptInfo).collect(Collectors.joining("\n")));
		BeanOutputConverter<SemanticObject> converter = new BeanOutputConverter<>(SemanticObject.class);
		params.put("format", converter.getFormat());
		return new PromptTemplate(PromptLoader.loadPrompt("semantic-parse")).render(params);
	}

}
