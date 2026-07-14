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
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * Schema 召回节点，位于查询增强之后、表关系推断之前。
 *
 * <p>
 * 该节点负责根据查询增强结果召回与用户输入相关的数据库 Schema 信息，包括：
 * <ul>
 * <li>根据用户输入检索相关的数据表文档</li>
 * <li>根据已召回的表名检索对应的列文档</li>
 * <li>将 Schema 信息组织后供后续节点使用</li>
 * <li>在召回过程中向用户提供流式反馈</li>
 * </ul>
 * </p>
 *
 * @author zhangshenghang
 * @see QueryEnhanceNode
 * @see TableRelationNode
 */
@Slf4j
@Component
@AllArgsConstructor
public class SchemaRecallNode implements NodeAction {

	private final SchemaService schemaService;

	private final AgentDatasourceMapper agentDatasourceMapper;

	/**
	 * 执行 Schema 召回逻辑。
	 * <p>
	 * 从查询增强结果中获取规范化查询和智能体 ID，查询智能体的激活数据源， 然后召回相关的表文档和列文档并写入状态。若无激活数据源或未检索到表，则返回提示并终止流程。
	 * </p>
	 * @param state 工作流全局状态，包含查询增强结果与智能体 ID
	 * @return 包含 Schema 召回结果的 Map，key 为 {@value SCHEMA_RECALL_NODE_OUTPUT}， value 为流式生成器
	 * @throws Exception 召回 Schema 时可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 获取查询增强节点的输出
		QueryEnhanceOutputDTO queryEnhanceOutputDTO = StateUtil.getObjectValue(state, QUERY_ENHANCE_NODE_OUTPUT,
				QueryEnhanceOutputDTO.class);
		String input = queryEnhanceOutputDTO.getCanonicalQuery();
		String agentId = StateUtil.getStringValue(state, AGENT_ID);

		// 查询智能体的激活数据源
		Integer datasourceId = agentDatasourceMapper.selectActiveDatasourceIdByAgentId(Long.valueOf(agentId));

		// 无激活数据源时，返回提示信息并终止流程
		if (datasourceId == null) {
			log.warn("智能体 {} 没有激活的数据源", agentId);
			String noDataSourceMessage = """
					\n 该智能体没有激活的数据源

					这可能是因为：
					1. 数据源尚未配置或关联。
					2. 所有数据源都已被禁用。
					3. 请先配置并激活数据源。
					流程已终止。
					""";

			Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
				emitter.next(ChatResponseUtil.createResponse(noDataSourceMessage));
				emitter.complete();
			});

			Flux<GraphResponse<StreamingOutput>> generator = FluxUtil
				.createStreamingGeneratorWithMessages(this.getClass(), state, currentState -> {
					return Map.of(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, Collections.emptyList(),
							COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, Collections.emptyList());
				}, displayFlux);

			return Map.of(SCHEMA_RECALL_NODE_OUTPUT, generator);
		}

		// 先执行业务逻辑，立即召回 Schema 信息
		List<Document> tableDocuments = new ArrayList<>(
				schemaService.getTableDocumentsByDatasource(datasourceId, input));
		// 提取召回的表名列表
		List<String> recalledTableNames = extractTableName(tableDocuments);
		// 根据召回的表名检索列文档
		List<Document> columnDocuments = schemaService.getColumnDocumentsByTableName(datasourceId, recalledTableNames);

		String failMessage = """
				\n 未检索到相关数据表

				这可能是因为：
				1. 数据源尚未初始化。
				2. 您的提问与当前数据库中的表结构无关。
				3. 请尝试点击“初始化数据源”或换一个与业务相关的问题。
				4. 如果你用A嵌入模型初始化数据源，却更换为B嵌入模型，请重新初始化数据源
				流程已终止。
				""";

		Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createResponse("开始初步召回Schema信息..."));
			emitter.next(ChatResponseUtil.createResponse(
					"初步表信息召回完成，数量: " + tableDocuments.size() + "，表名: " + String.join(", ", recalledTableNames)));
			if (tableDocuments.isEmpty()) {
				emitter.next(ChatResponseUtil.createResponse(failMessage));
			}
			emitter.next(ChatResponseUtil.createResponse("初步Schema信息召回完成."));
			emitter.complete();
		});

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, currentState -> {
					return Map.of(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, tableDocuments,
							COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, columnDocuments);
				}, displayFlux);

		// 返回 Schema 召回结果
		return Map.of(SCHEMA_RECALL_NODE_OUTPUT, generator);
	}

	/**
	 * 从表文档列表中提取表名。
	 * @param tableDocuments 表文档列表
	 * @return 表名列表
	 */
	private static List<String> extractTableName(List<Document> tableDocuments) {
		List<String> tableNames = new ArrayList<>();
		// 从 metadata 的 name 字段提取表名
		for (Document document : tableDocuments) {
			String name = (String) document.getMetadata().get("name");
			if (name != null && !name.isEmpty()) {
				tableNames.add(name);
			}
		}
		log.info("SchemaRecallNode 节点召回的表: {}", tableNames);
		return tableNames;

	}

}
