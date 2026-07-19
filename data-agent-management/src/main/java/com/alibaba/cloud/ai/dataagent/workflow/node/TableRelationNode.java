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

import static com.alibaba.cloud.ai.dataagent.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.DB_DIALECT_TYPE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.EVIDENCE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.GENEGRATED_SEMANTIC_MODEL_PROMPT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_GENERATE_SCHEMA_MISSING_ADVICE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_RELATION_EXCEPTION_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_RELATION_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_RELATION_RETRY_COUNT;
import static com.alibaba.cloud.ai.dataagent.prompt.PromptHelper.buildSemanticModelPrompt;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticModelService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 表关系推断节点，位于 Schema 召回之后、可行性评估之前。
 *
 * <p>
 * 该节点负责自动推断表与字段之间的关系，完成诸如 JOIN 和外键等复杂结构的构建。 主要职责：
 * <ul>
 * <li>推断表与表、字段与字段之间的关系</li>
 * <li>根据召回的列文档和表文档构建初始 Schema</li>
 * <li>基于用户输入和证据信息进行 Schema 精细选择</li>
 * <li>处理缺失信息时的 Schema 补充建议</li>
 * </ul>
 * </p>
 *
 * @author zhangshenghang
 */
@Slf4j
@Component
@AllArgsConstructor
public class TableRelationNode implements NodeAction {

	private final SchemaService schemaService;

	private final Nl2SqlService nl2SqlService;

	private final SemanticModelService semanticModelService;

	private final DatabaseUtil databaseUtil;

	private final DatasourceService datasourceService;

	private final AgentDatasourceService agentDatasourceService;

	/**
	 * 执行表关系推断逻辑。
	 * <p>
	 * 获取必要的输入参数，构建初始 Schema，然后进行精细的 Schema 选择， 最终将表关系结果、数据库方言类型、语义模型提示等信息写入状态。
	 * </p>
	 * @param state 工作流全局状态
	 * @return 包含表关系推断结果的 Map
	 * @throws Exception 推断过程中可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 获取必要的输入参数
		String canonicalQuery = StateUtil.getCanonicalQuery(state);

		String evidence = StateUtil.getStringValue(state, EVIDENCE);
		List<Document> tableDocuments = StateUtil.getDocumentList(state, TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT);
		List<Document> columnDocuments = StateUtil.getDocumentList(state, COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT);
		String agentIdStr = StateUtil.getStringValue(state, AGENT_ID);

		// 先执行业务逻辑，立即获取最终结果
		DbConfigBO agentDbConfig = databaseUtil.getAgentDbConfig(Long.valueOf(agentIdStr));

		// 获取逻辑外键信息
		List<String> logicalForeignKeys = getLogicalForeignKeys(Long.valueOf(agentIdStr), tableDocuments);
		log.info("为智能体 {} 找到 {} 个逻辑外键", agentIdStr, logicalForeignKeys.size());

		// 构建初始 Schema
		SchemaDTO initialSchema = buildInitialSchema(agentIdStr, columnDocuments, tableDocuments, agentDbConfig,
				logicalForeignKeys);

		Map<String, Object> resultMap = new HashMap<>();
		// 将 DB_DIALECT_TYPE 添加到 resultMap，确保它在 generator 完成时被写入 state
		resultMap.put(DB_DIALECT_TYPE, agentDbConfig.getDialectType());
		resultMap.put(TABLE_RELATION_RETRY_COUNT, 0);
		resultMap.put(TABLE_RELATION_EXCEPTION_OUTPUT, "");

		// 进行精细的 Schema 选择
		Flux<ChatResponse> schemaFlux = processSchemaSelection(initialSchema, canonicalQuery, evidence, state,
				agentDbConfig, result -> {
					log.info("[{}] Schema 处理结果: {}", this.getClass().getSimpleName(), result);
					resultMap.put(TABLE_RELATION_OUTPUT, result);

					// 从最终的 SchemaDTO 中获取表名列表
					List<String> tableNames = result.getTable().stream().map(TableDTO::getName).toList();

					// 根据智能体 ID 和表名列表获取语义模型
					List<SemanticModel> semanticModels = semanticModelService
						.getByAgentIdAndTableNames(Long.valueOf(agentIdStr), tableNames);

					// 构建语义模型提示并存储到 resultMap 中
					String semanticModelPrompt = buildSemanticModelPrompt(semanticModels);
					resultMap.put(GENEGRATED_SEMANTIC_MODEL_PROMPT, semanticModelPrompt);
				});

		// 创建展示流，仅用于提升用户体验
		Flux<ChatResponse> preFlux = Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createResponse("开始构建初始Schema..."));
			emitter.next(ChatResponseUtil.createResponse("初始Schema构建完成."));
			emitter.complete();
		});
		Flux<ChatResponse> displayFlux = preFlux.concatWith(schemaFlux).concatWith(Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createResponse("开始处理Schema选择..."));
			emitter.next(ChatResponseUtil.createResponse("Schema选择处理完成."));
			emitter.complete();
		}));

		// 使用工具类创建生成器，直接返回预先计算好的业务结果
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, v -> resultMap, displayFlux);

		// 返回生成器及需要立即可用的状态值
		// DB_DIALECT_TYPE 必须直接返回，以便后续节点能从状态中获取
		return Map.of(TABLE_RELATION_OUTPUT, generator, DB_DIALECT_TYPE, agentDbConfig.getDialectType(),
				TABLE_RELATION_RETRY_COUNT, 0, TABLE_RELATION_EXCEPTION_OUTPUT, "");
	}

	/**
	 * 根据列文档和表文档构建初始 Schema。
	 * @param agentId 智能体 ID
	 * @param columnDocuments 列文档列表
	 * @param tableDocuments 表文档列表
	 * @param agentDbConfig 数据库配置
	 * @param logicalForeignKeys 逻辑外键列表
	 * @return 构建完成的初始 SchemaDTO
	 */
	private SchemaDTO buildInitialSchema(String agentId, List<Document> columnDocuments, List<Document> tableDocuments,
			DbConfigBO agentDbConfig, List<String> logicalForeignKeys) {
		SchemaDTO schemaDTO = new SchemaDTO();

		schemaService.extractDatabaseName(schemaDTO, agentDbConfig);
		schemaService.buildSchemaFromDocuments(agentId, columnDocuments, tableDocuments, schemaDTO);

		// 将逻辑外键信息合并到 schemaDTO 的 foreignKeys 字段
		if (logicalForeignKeys != null && !logicalForeignKeys.isEmpty()) {
			List<String> existingForeignKeys = schemaDTO.getForeignKeys();
			if (existingForeignKeys == null || existingForeignKeys.isEmpty()) {
				// 如果没有现有外键，直接设置
				schemaDTO.setForeignKeys(logicalForeignKeys);
			}
			else {
				// 合并现有外键和逻辑外键
				List<String> allForeignKeys = new ArrayList<>(existingForeignKeys);
				allForeignKeys.addAll(logicalForeignKeys);
				schemaDTO.setForeignKeys(allForeignKeys);
			}
			log.info("为智能体 {} 合并了 {} 个逻辑外键到 Schema 中", logicalForeignKeys.size(), agentId);
		}

		return schemaDTO;
	}

	/**
	 * 根据用户输入、证据信息和可选的补充建议进行精细的 Schema 选择。
	 * @param schemaDTO 初始 Schema
	 * @param input 用户输入
	 * @param evidence 证据信息
	 * @param state 工作流全局状态
	 * @param agentDbConfig 数据库配置
	 * @param dtoConsumer Schema 处理结果回调
	 * @return 展示用流式响应
	 */
	private Flux<ChatResponse> processSchemaSelection(SchemaDTO schemaDTO, String input, String evidence,
			OverAllState state, DbConfigBO agentDbConfig, Consumer<SchemaDTO> dtoConsumer) {
		// 获取 Schema 缺失信息的补充建议
		String schemaAdvice = StateUtil.getStringValue(state, SQL_GENERATE_SCHEMA_MISSING_ADVICE, null);
		// R31: 建议文本有界（nl2sql 侧亦 bound，双保险）
		if (schemaAdvice != null) {
			schemaAdvice = PromptHelper.boundQuery(schemaAdvice);
		}

		Flux<ChatResponse> schemaFlux;
		if (schemaAdvice != null) {
			// 带补充建议的精细选择
			log.info("[{}] 带补充建议处理 Schema: {}", this.getClass().getSimpleName(), schemaAdvice);
			schemaFlux = nl2SqlService.fineSelect(schemaDTO, input, evidence, schemaAdvice, agentDbConfig, dtoConsumer);
		}
		else {
			// 常规 Schema 选择
			log.info("[{}] 执行常规 Schema 选择", this.getClass().getSimpleName());
			schemaFlux = nl2SqlService.fineSelect(schemaDTO, input, evidence, null, agentDbConfig, dtoConsumer);
		}
		return Flux
			.just(ChatResponseUtil.createResponse("正在选择合适的数据表...\n"),
					ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()))
			.concatWith(schemaFlux)
			.concatWith(Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
					ChatResponseUtil.createResponse("\n\n选择数据表完成。")));
	}

	/**
	 * 获取逻辑外键信息，并过滤只保留与当前召回表相关的外键。
	 * @param agentId 智能体 ID
	 * @param tableDocuments 召回的表文档列表
	 * @return 格式化后的逻辑外键列表
	 */
	private List<String> getLogicalForeignKeys(Long agentId, List<Document> tableDocuments) {
		try {
			// 获取当前智能体激活的数据源
			AgentDatasource agentDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
			if (agentDatasource == null || agentDatasource.getDatasourceId() == null) {
				log.warn("未找到智能体 {} 的激活数据源", agentId);
				return Collections.emptyList();
			}

			Integer datasourceId = agentDatasource.getDatasourceId();

			// 从 tableDocuments 提取表名列表
			Set<String> recalledTableNames = tableDocuments.stream()
				.map(doc -> (String) doc.getMetadata().get("name"))
				.filter(name -> name != null && !name.isEmpty())
				.collect(Collectors.toSet());

			log.info("智能体 {} 召回的表名: {}", agentId, recalledTableNames);

			// 查询该数据源的所有逻辑外键
			List<LogicalRelation> allLogicalRelations = datasourceService.getLogicalRelations(datasourceId);
			log.info("数据源 {} 中共有 {} 个逻辑关系", datasourceId, allLogicalRelations.size());

			// 过滤只保留与召回表相关的外键（源表或目标表在召回列表中）
			List<String> formattedForeignKeys = allLogicalRelations.stream()
				.filter(lr -> recalledTableNames.contains(lr.getSourceTableName())
						|| recalledTableNames.contains(lr.getTargetTableName()))
				.map(lr -> String.format("%s.%s=%s.%s", lr.getSourceTableName(), lr.getSourceColumnName(),
						lr.getTargetTableName(), lr.getTargetColumnName()))
				.distinct()
				.collect(Collectors.toList());

			log.info("为召回表筛选出 {} 个相关逻辑关系", formattedForeignKeys.size());
			return formattedForeignKeys;
		}
		catch (Exception e) {
			log.error("获取智能体 {} 的逻辑外键时出错", agentId, e);
			return Collections.emptyList();
		}
	}

}
