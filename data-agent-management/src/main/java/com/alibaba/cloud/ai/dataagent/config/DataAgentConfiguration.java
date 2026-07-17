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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.properties.AdminAuthProperties;
import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.SimpleVectorStoreInitialization;
import com.alibaba.cloud.ai.dataagent.splitter.SentenceSplitter;
import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import com.alibaba.cloud.ai.dataagent.splitter.SemanticTextSplitter;
import com.alibaba.cloud.ai.dataagent.splitter.ParagraphTextSplitter;
import com.alibaba.cloud.ai.dataagent.util.McpServerToolUtil;
import com.alibaba.cloud.ai.dataagent.util.NodeBeanUtil;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.strategy.EnhancedTokenCountBatchingStrategy;
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.*;
import com.alibaba.cloud.ai.dataagent.workflow.node.*;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

/**
 * DataAgent 的自动配置类。
 * <p>
 * 集中装配 DataAgent 运行所需的全部 Bean：
 * </p>
 * <ul>
 * <li>HTTP 客户端（RestClient / WebClient 超时配置）</li>
 * <li>NL2SQL 工作流状态图（{@link StateGraph}），定义全部节点与边</li>
 * <li>向量存储、文本分块、Embedding 批处理等 AI 组件</li>
 * <li>MCP 工具、ChatClient 等 LLM 交互组件</li>
 * </ul>
 *
 * @author vlsmb
 * @since 2025/9/28
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties({ CodeExecutorProperties.class, DataAgentProperties.class, FileStorageProperties.class,
		AdminAuthProperties.class })
public class DataAgentConfiguration implements DisposableBean {

	/**
	 * 专用线程池，用于数据库操作的并行处理（如多表 Schema 召回）。
	 */
	private ExecutorService dbOperationExecutor;

	/**
	 * 自定义 RestClient 超时配置。
	 * @param connectTimeout 连接超时（秒），默认 600
	 * @param readTimeout 读取超时（秒），默认 600
	 * @return RestClientCustomizer
	 */
	@Bean
	@ConditionalOnMissingBean(RestClientCustomizer.class)
	public RestClientCustomizer restClientCustomizer(@Value("${rest.connect.timeout:600}") long connectTimeout,
			@Value("${rest.read.timeout:600}") long readTimeout) {
		return restClientBuilder -> restClientBuilder
			.requestFactory(ClientHttpRequestFactoryBuilder.reactor().withCustomizer(factory -> {
				factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
				factory.setReadTimeout(Duration.ofSeconds(readTimeout));
			}).build());
	}

	/**
	 * 自定义 WebClient 超时配置（用于流式 LLM 调用）。
	 * @param responseTimeout 响应超时（秒），默认 600
	 * @return WebClient.Builder
	 */
	@Bean
	@ConditionalOnMissingBean(WebClient.Builder.class)
	public WebClient.Builder webClientBuilder(@Value("${webclient.response.timeout:600}") long responseTimeout) {

		return WebClient.builder()
			.clientConnector(new ReactorClientHttpConnector(
					HttpClient.create().responseTimeout(Duration.ofSeconds(responseTimeout))));
	}

	/**
	 * 构建 NL2SQL 工作流状态图。
	 * <p>
	 * 定义 DataAgent 从用户输入到生成最终报告的完整流程， 包含意图识别、Schema 召回、SQL 生成/执行、Python 分析、报告生成等节点，
	 * 以及节点间的条件路由（Dispatcher）。
	 * </p>
	 * @param nodeBeanUtil 节点工具类（将节点实例转为 Graph 节点）
	 * @param codeExecutorProperties Python 执行器配置（用于重试控制）
	 * @return 工作流状态图
	 * @throws GraphStateException 图构建异常
	 */
	@Bean
	@org.springframework.context.annotation.Primary
	public StateGraph nl2sqlGraph(NodeBeanUtil nodeBeanUtil, CodeExecutorProperties codeExecutorProperties)
			throws GraphStateException {

		KeyStrategyFactory keyStrategyFactory = () -> {
			HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
			// User input
			keyStrategyHashMap.put(INPUT_KEY, KeyStrategy.REPLACE);
			// Agent ID
			keyStrategyHashMap.put(AGENT_ID, KeyStrategy.REPLACE);
			// Multi-turn context
			keyStrategyHashMap.put(MULTI_TURN_CONTEXT, KeyStrategy.REPLACE);
			// Intent recognition
			keyStrategyHashMap.put(INTENT_RECOGNITION_NODE_OUTPUT, KeyStrategy.REPLACE);
			// QUERY_ENHANCE_NODE节点输出
			keyStrategyHashMap.put(QUERY_ENHANCE_NODE_OUTPUT, KeyStrategy.REPLACE);
			// Semantic model
			keyStrategyHashMap.put(GENEGRATED_SEMANTIC_MODEL_PROMPT, KeyStrategy.REPLACE);
			// EVIDENCE节点输出
			keyStrategyHashMap.put(EVIDENCE, KeyStrategy.REPLACE);
			// schema recall节点输出
			keyStrategyHashMap.put(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, KeyStrategy.REPLACE);
			// table relation节点输出
			keyStrategyHashMap.put(TABLE_RELATION_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(TABLE_RELATION_EXCEPTION_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(TABLE_RELATION_RETRY_COUNT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(DB_DIALECT_TYPE, KeyStrategy.REPLACE);
			// Feasibility Assessment 节点输出
			keyStrategyHashMap.put(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, KeyStrategy.REPLACE);
			// sql generate节点输出
			keyStrategyHashMap.put(SQL_GENERATE_SCHEMA_MISSING_ADVICE, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_GENERATE_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_GENERATE_COUNT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_REGENERATE_REASON, KeyStrategy.REPLACE);
			// Semantic consistence节点输出
			keyStrategyHashMap.put(SEMANTIC_CONSISTENCY_NODE_OUTPUT, KeyStrategy.REPLACE);
			// Planner 节点输出
			keyStrategyHashMap.put(PLANNER_NODE_OUTPUT, KeyStrategy.REPLACE);
			// PlanExecutorNode
			keyStrategyHashMap.put(PLAN_CURRENT_STEP, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PLAN_NEXT_NODE, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PLAN_VALIDATION_STATUS, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PLAN_VALIDATION_ERROR, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PLAN_REPAIR_COUNT, KeyStrategy.REPLACE);
			// SQL Execute 节点输出
			keyStrategyHashMap.put(SQL_EXECUTE_NODE_OUTPUT, KeyStrategy.REPLACE);
			// Python代码运行相关
			keyStrategyHashMap.put(SQL_RESULT_LIST_MEMORY, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PYTHON_IS_SUCCESS, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PYTHON_TRIES_COUNT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PYTHON_FALLBACK_MODE, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PYTHON_EXECUTE_NODE_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PYTHON_GENERATE_NODE_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PYTHON_ANALYSIS_NODE_OUTPUT, KeyStrategy.REPLACE);
			// NL2SQL相关
			keyStrategyHashMap.put(IS_ONLY_NL2SQL, KeyStrategy.REPLACE);
			// Human Review keys
			keyStrategyHashMap.put(HUMAN_REVIEW_ENABLED, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(HUMAN_FEEDBACK_DATA, KeyStrategy.REPLACE);
			// Langfuse 追踪：threadId 透传
			keyStrategyHashMap.put(TRACE_THREAD_ID, KeyStrategy.REPLACE);
			// Final result
			keyStrategyHashMap.put(RESULT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_HEAL_ERRORS, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(CHART_IMAGE_MAP, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(RESULT_SANITY_RETRY, KeyStrategy.REPLACE);
			return keyStrategyHashMap;
		};

		StateGraph stateGraph = new StateGraph(NL2SQL_GRAPH_NAME, keyStrategyFactory)
			.addNode(INTENT_RECOGNITION_NODE, nodeBeanUtil.getNodeBeanAsync(IntentRecognitionNode.class))
			.addNode(EVIDENCE_RECALL_NODE, nodeBeanUtil.getNodeBeanAsync(EvidenceRecallNode.class))
			.addNode(QUERY_ENHANCE_NODE, nodeBeanUtil.getNodeBeanAsync(QueryEnhanceNode.class))
			.addNode(SCHEMA_RECALL_NODE, nodeBeanUtil.getNodeBeanAsync(SchemaRecallNode.class))
			.addNode(TABLE_RELATION_NODE, nodeBeanUtil.getNodeBeanAsync(TableRelationNode.class))
			.addNode(FEASIBILITY_ASSESSMENT_NODE, nodeBeanUtil.getNodeBeanAsync(FeasibilityAssessmentNode.class))
			.addNode(SQL_GENERATE_NODE, nodeBeanUtil.getNodeBeanAsync(SqlGenerateNode.class))
			.addNode(PLANNER_NODE, nodeBeanUtil.getNodeBeanAsync(PlannerNode.class))
			.addNode(PLAN_EXECUTOR_NODE, nodeBeanUtil.getNodeBeanAsync(PlanExecutorNode.class))
			.addNode(SQL_EXECUTE_NODE, nodeBeanUtil.getNodeBeanAsync(SqlExecuteNode.class))
			.addNode(PYTHON_GENERATE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonGenerateNode.class))
			.addNode(PYTHON_EXECUTE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonExecuteNode.class))
			.addNode(PYTHON_ANALYZE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonAnalyzeNode.class))
			.addNode(REPORT_GENERATOR_NODE, nodeBeanUtil.getNodeBeanAsync(ReportGeneratorNode.class))
			.addNode(SEMANTIC_CONSISTENCY_NODE, nodeBeanUtil.getNodeBeanAsync(SemanticConsistencyNode.class))
			.addNode(HUMAN_FEEDBACK_NODE, nodeBeanUtil.getNodeBeanAsync(HumanFeedbackNode.class));

		stateGraph.addEdge(START, INTENT_RECOGNITION_NODE)
			.addConditionalEdges(INTENT_RECOGNITION_NODE, edge_async(new IntentRecognitionDispatcher()),
					Map.of(EVIDENCE_RECALL_NODE, EVIDENCE_RECALL_NODE, END, END))
			.addEdge(EVIDENCE_RECALL_NODE, QUERY_ENHANCE_NODE)
			.addConditionalEdges(QUERY_ENHANCE_NODE, edge_async(new QueryEnhanceDispatcher()),
					Map.of(SCHEMA_RECALL_NODE, SCHEMA_RECALL_NODE, END, END))
			.addConditionalEdges(SCHEMA_RECALL_NODE, edge_async(new SchemaRecallDispatcher()),
					Map.of(TABLE_RELATION_NODE, TABLE_RELATION_NODE, END, END))

			.addConditionalEdges(TABLE_RELATION_NODE, edge_async(new TableRelationDispatcher()),
					Map.of(FEASIBILITY_ASSESSMENT_NODE, FEASIBILITY_ASSESSMENT_NODE, END, END, TABLE_RELATION_NODE,
							TABLE_RELATION_NODE)) // retry
			.addConditionalEdges(FEASIBILITY_ASSESSMENT_NODE, edge_async(new FeasibilityAssessmentDispatcher()),
					Map.of(PLANNER_NODE, PLANNER_NODE, END, END))

			// The edge from PlannerNode now goes to PlanExecutorNode for validation and
			// execution
			.addEdge(PLANNER_NODE, PLAN_EXECUTOR_NODE)
			// python nodes
			.addEdge(PYTHON_GENERATE_NODE, PYTHON_EXECUTE_NODE)
			.addConditionalEdges(PYTHON_EXECUTE_NODE, edge_async(new PythonExecutorDispatcher(codeExecutorProperties)),
					Map.of(PYTHON_ANALYZE_NODE, PYTHON_ANALYZE_NODE, END, END, PYTHON_GENERATE_NODE,
							PYTHON_GENERATE_NODE))
			.addEdge(PYTHON_ANALYZE_NODE, PLAN_EXECUTOR_NODE)
			// The dispatcher at PlanExecutorNode will decide the next step
			.addConditionalEdges(PLAN_EXECUTOR_NODE, edge_async(new PlanExecutorDispatcher()), Map.of(
					// If validation fails, go back to PlannerNode to repair
					PLANNER_NODE, PLANNER_NODE,
					// If validation passes, proceed to the correct execution node
					SQL_GENERATE_NODE, SQL_GENERATE_NODE, PYTHON_GENERATE_NODE, PYTHON_GENERATE_NODE,
					REPORT_GENERATOR_NODE, REPORT_GENERATOR_NODE,
					// If human review is enabled, go to human_feedback node
					HUMAN_FEEDBACK_NODE, HUMAN_FEEDBACK_NODE,
					// If max repair attempts are reached, end the process
					END, END))
			// Human feedback node routing
			.addConditionalEdges(HUMAN_FEEDBACK_NODE, edge_async(new HumanFeedbackDispatcher()), Map.of(
					// If plan is rejected, go back to PlannerNode
					PLANNER_NODE, PLANNER_NODE,
					// If plan is approved, continue with execution
					PLAN_EXECUTOR_NODE, PLAN_EXECUTOR_NODE,
					// If max repair attempts are reached, end the process
					END, END,
					// If human feedback data is null, go back to HumanFeedbackNode
					HUMAN_FEEDBACK_NODE, HUMAN_FEEDBACK_NODE))
			.addEdge(REPORT_GENERATOR_NODE, END)
			// sql generate and sql execute node
			.addConditionalEdges(SQL_GENERATE_NODE, nodeBeanUtil.getEdgeBeanAsync(SqlGenerateDispatcher.class),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, END, END, SEMANTIC_CONSISTENCY_NODE,
							SEMANTIC_CONSISTENCY_NODE))
			.addConditionalEdges(SEMANTIC_CONSISTENCY_NODE, edge_async(new SemanticConsistenceDispatcher()),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, SQL_EXECUTE_NODE, SQL_EXECUTE_NODE))
			.addConditionalEdges(SQL_EXECUTE_NODE, edge_async(new SQLExecutorDispatcher()),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, PLAN_EXECUTOR_NODE, PLAN_EXECUTOR_NODE));

		GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
				"workflow graph");

		log.info("workflow in PlantUML format as follows \n\n" + graphRepresentation.content() + "\n\n");

		return stateGraph;
	}

	/**
	 * 构建 v0.2 语义层工作流状态图（NL2Semantic2SQL，双 Graph opt-in）。
	 * <p>
	 * 与 {@link #nl2sqlGraph} 相互独立，不修改既有 NL2SQL 图。拓扑：
	 * </p>
	 * <pre>
	 *   START → SEMANTIC_PARSE_NODE
	 *         → (SemanticParseDispatcher, 返回 "build_sql_node") → BUILD_SQL_NODE
	 *         → (BuildSQLDispatcher, 返回 HUMAN_FEEDBACK_NODE / SQL_EXECUTE_NODE / END)
	 *              ├─ HUMAN_FEEDBACK_NODE（人工反馈/反问）
	 *              ├─ SQL_EXECUTE_NODE → REPORT_GENERATOR_NODE → END
	 *              └─ END
	 * </pre>
	 * <p>
	 * 复用既有 {@code SqlExecuteNode} / {@code ReportGeneratorNode} /
	 * {@code HumanFeedbackNode}， 新增 {@link SemanticParseNode} 与 {@link BuildSQLNode} 两个
	 * v0.2 节点。Dispatcher 返回值对齐： {@link SemanticParseDispatcher} 返回
	 * {@code BUILD_SQL_NODE}（{@code "build_sql_node"}）； {@link BuildSQLDispatcher} 返回
	 * {@code HUMAN_FEEDBACK_NODE} / {@code SQL_EXECUTE_NODE} / {@code StateGraph.END}。
	 * </p>
	 * <p>
	 * KeyStrategy 在既有键集合基础上补齐 v0.2 语义层状态键（全部 REPLACE 策略）：
	 * {@link SemanticParseNode#SEMANTIC_OBJECT}、{@link BuildSQLNode#CONTROLLED_SQL}、
	 * {@link BuildSQLNode#SQL_PARAMS}、{@link BuildSQLNode#NEEDS_CLARIFICATION}、
	 * {@link BuildSQLNode#CLARIFICATION_MESSAGE}。
	 * </p>
	 * @param nodeBeanUtil 节点工具类（将节点实例转为 Graph 节点）
	 * @return v0.2 语义层工作流状态图
	 * @throws GraphStateException 图构建异常
	 */
	@Bean(name = "nl2sqlSemanticGraph")
	public StateGraph nl2sqlSemanticGraph(NodeBeanUtil nodeBeanUtil) throws GraphStateException {

		KeyStrategyFactory semanticKeyStrategyFactory = () -> {
			HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
			// 基础与上下文（与 nl2sqlGraph 对齐）
			keyStrategyHashMap.put(INPUT_KEY, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(AGENT_ID, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(DATASOURCE_ID, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(MULTI_TURN_CONTEXT, KeyStrategy.REPLACE);
			// 数据库方言（SqlExecuteNode 需要）
			keyStrategyHashMap.put(DB_DIALECT_TYPE, KeyStrategy.REPLACE);
			// SQL 执行 / 最终结果 / 报告相关
			keyStrategyHashMap.put(SQL_EXECUTE_NODE_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_GENERATE_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_REGENERATE_REASON, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_GENERATE_COUNT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(RESULT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(CHART_IMAGE_MAP, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_RESULT_LIST_MEMORY, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(RESULT_SANITY_RETRY, KeyStrategy.REPLACE);
			// Human Review / 反馈
			keyStrategyHashMap.put(HUMAN_REVIEW_ENABLED, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(HUMAN_FEEDBACK_DATA, KeyStrategy.REPLACE);
			// Langfuse 追踪
			keyStrategyHashMap.put(TRACE_THREAD_ID, KeyStrategy.REPLACE);
			// v0.2 语义层状态键（η₁/η₂ 产物 + 反问控制）
			keyStrategyHashMap.put(SemanticParseNode.SEMANTIC_OBJECT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(BuildSQLNode.CONTROLLED_SQL, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(BuildSQLNode.SQL_PARAMS, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(BuildSQLNode.NEEDS_CLARIFICATION, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(BuildSQLNode.CLARIFICATION_MESSAGE, KeyStrategy.REPLACE);
			return keyStrategyHashMap;
		};

		StateGraph stateGraph = new StateGraph(NL2SQL_SEMANTIC_GRAPH_NAME, semanticKeyStrategyFactory)
			// v0.2 新增节点
			.addNode(SEMANTIC_PARSE_NODE, nodeBeanUtil.getNodeBeanAsync(SemanticParseNode.class))
			.addNode(BUILD_SQL_NODE, nodeBeanUtil.getNodeBeanAsync(BuildSQLNode.class))
			// 复用既有节点
			.addNode(SQL_EXECUTE_NODE, nodeBeanUtil.getNodeBeanAsync(SqlExecuteNode.class))
			.addNode(REPORT_GENERATOR_NODE, nodeBeanUtil.getNodeBeanAsync(ReportGeneratorNode.class))
			.addNode(HUMAN_FEEDBACK_NODE, nodeBeanUtil.getNodeBeanAsync(HumanFeedbackNode.class));

		stateGraph.addEdge(START, SEMANTIC_PARSE_NODE)
			// SemanticParseDispatcher 恒返回 BUILD_SQL_NODE（"build_sql_node"）
			.addConditionalEdges(SEMANTIC_PARSE_NODE, edge_async(new SemanticParseDispatcher()),
					Map.of(BUILD_SQL_NODE, BUILD_SQL_NODE))
			// BuildSQLDispatcher 返回 HUMAN_FEEDBACK_NODE / SQL_EXECUTE_NODE / END
			.addConditionalEdges(BUILD_SQL_NODE, edge_async(new BuildSQLDispatcher()),
					Map.of(HUMAN_FEEDBACK_NODE, HUMAN_FEEDBACK_NODE, SQL_EXECUTE_NODE, SQL_EXECUTE_NODE, END, END))
			// SQL 执行 → 报告 → 结束
			.addEdge(SQL_EXECUTE_NODE, REPORT_GENERATOR_NODE)
			.addEdge(REPORT_GENERATOR_NODE, END);

		GraphRepresentation semanticGraphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
				"semantic workflow graph");

		log.info("semantic workflow in PlantUML format as follows \n\n" + semanticGraphRepresentation.content()
				+ "\n\n");

		return stateGraph;
	}

	/**
	 * 为了不必要的重复手动配置，不要在此添加其他向量的手动配置，如果扩展其他向量，请阅读spring ai文档
	 * <a href="https://springdoc.cn/spring-ai/api/vectordbs.html">...</a>
	 * 根据自己想要的向量，在pom文件引入 Boot Starter 依赖即可。此处配置使用内存向量作为兜底配置
	 */
	@Primary
	@Bean
	@ConditionalOnMissingBean(VectorStore.class)
	@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "simple", matchIfMissing = true)
	public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
		return SimpleVectorStore.builder(embeddingModel).build();
	}

	@Bean
	@ConditionalOnBean(SimpleVectorStore.class)
	public SimpleVectorStoreInitialization simpleVectorStoreInitialization(SimpleVectorStore vectorStore,
			DataAgentProperties properties) {
		return new SimpleVectorStoreInitialization(vectorStore, properties);
	}

	@Bean
	@ConditionalOnMissingBean(BatchingStrategy.class)
	public BatchingStrategy customBatchingStrategy(DataAgentProperties properties) {
		// 使用增强的批处理策略，同时考虑token数量和文本数量限制
		EncodingType encodingType;
		try {
			Optional<EncodingType> encodingTypeOptional = EncodingType
				.fromName(properties.getEmbeddingBatch().getEncodingType());
			encodingType = encodingTypeOptional.orElse(EncodingType.CL100K_BASE);
		}
		catch (Exception e) {
			log.warn("Unknown encodingType '{}', falling back to CL100K_BASE",
					properties.getEmbeddingBatch().getEncodingType());
			encodingType = EncodingType.CL100K_BASE;
		}

		return new EnhancedTokenCountBatchingStrategy(encodingType, properties.getEmbeddingBatch().getMaxTokenCount(),
				properties.getEmbeddingBatch().getReservePercentage(),
				properties.getEmbeddingBatch().getMaxTextCount());
	}

	@Bean
	public ToolCallbackResolver toolCallbackResolver(GenericApplicationContext context) {
		List<ToolCallback> allFunctionAndToolCallbacks = new ArrayList<>(
				McpServerToolUtil.excludeMcpServerTool(context, ToolCallback.class));
		McpServerToolUtil.excludeMcpServerTool(context, ToolCallbackProvider.class)
			.stream()
			.map(pr -> List.of(pr.getToolCallbacks()))
			.forEach(allFunctionAndToolCallbacks::addAll);

		var staticToolCallbackResolver = new StaticToolCallbackResolver(allFunctionAndToolCallbacks);

		var springBeanToolCallbackResolver = SpringBeanToolCallbackResolver.builder()
			.applicationContext(context)
			.build();

		return new DelegatingToolCallbackResolver(List.of(staticToolCallbackResolver, springBeanToolCallbackResolver));
	}

	/**
	 * 动态生成 EmbeddingModel 的代理 Bean。 原理： 1. 这是一个 Bean，Milvus/PgVector Starter 能看到它，启动不会报错。
	 * 2. 它是动态代理，内部没有写死任何方法。 3. 每次被调用时，它会执行 getTarget() -> registry.getEmbeddingModel()。
	 */
	@Bean
	@Primary
	public EmbeddingModel embeddingModel(AiModelRegistry registry) {

		// 1. 定义目标源 (TargetSource)
		TargetSource targetSource = new TargetSource() {
			@Override
			public Class<?> getTargetClass() {
				return EmbeddingModel.class;
			}

			@Override
			public boolean isStatic() {
				// 关键：声明是动态的，每次都要重新获取目标
				return false;
			}

			@Override
			public Object getTarget() {
				// 每次方法调用，都去注册表拿最新的
				return registry.getEmbeddingModel();
			}

			@Override
			public void releaseTarget(Object target) {
				// 无需释放
			}
		};

		// 2. 创建代理工厂
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTargetSource(targetSource);
		// 代理接口
		proxyFactory.addInterface(EmbeddingModel.class);

		// 3. 返回动态生成的代理对象
		return (EmbeddingModel) proxyFactory.getProxy();
	}

	@Bean(name = "dbOperationExecutor")
	public ExecutorService dbOperationExecutor() {
		// 初始化专用线程池，用于数据库操作
		// 线程数量设置为CPU核心数的2倍，但不少于4个，不超过16个
		int corePoolSize = Math.max(4, Math.min(Runtime.getRuntime().availableProcessors() * 2, 16));
		log.info("Database operation executor initialized with {} threads", corePoolSize);

		// 自定义线程工厂
		ThreadFactory threadFactory = new ThreadFactory() {
			private final AtomicInteger threadNumber = new AtomicInteger(1);

			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "db-operation-" + threadNumber.getAndIncrement());
				t.setDaemon(false);
				if (t.getPriority() != Thread.NORM_PRIORITY) {
					t.setPriority(Thread.NORM_PRIORITY);
				}
				return t;
			}
		};

		// 创建原生线程池
		this.dbOperationExecutor = new ThreadPoolExecutor(corePoolSize, corePoolSize, 60L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(500), threadFactory, new ThreadPoolExecutor.CallerRunsPolicy());

		return dbOperationExecutor;
	}

	@Override
	public void destroy() {
		if (dbOperationExecutor != null && !dbOperationExecutor.isShutdown()) {
			log.info("Shutting down database operation executor...");

			// 记录关闭前的状态，便于排查问题
			if (dbOperationExecutor instanceof ThreadPoolExecutor tpe) {
				log.info("Executor Status before shutdown: [Queue Size: {}], [Active Count: {}], [Completed Tasks: {}]",
						tpe.getQueue().size(), tpe.getActiveCount(), tpe.getCompletedTaskCount());
			}

			// 1. 停止接收新任务
			dbOperationExecutor.shutdown();

			try {
				// 2. 等待现有任务完成（包括队列中的）
				if (!dbOperationExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
					log.warn("Executor did not terminate in 60s. Forcing shutdown...");

					// 3. 超时强行关闭
					dbOperationExecutor.shutdownNow();

					// 4. 再次确认是否关闭
					if (!dbOperationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
						log.error("Executor failed to terminate completely.");
					}
				}
				else {
					log.info("Database operation executor terminated gracefully.");
				}
			}
			catch (InterruptedException e) {
				log.warn("Interrupted during executor shutdown. Forcing immediate shutdown.");
				dbOperationExecutor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

	@Bean(name = "token")
	public TextSplitter textSplitter(DataAgentProperties properties) {
		DataAgentProperties.TextSplitter textSplitterProps = properties.getTextSplitter();
		DataAgentProperties.TextSplitter.TokenTextSplitterConfig config = textSplitterProps.getToken();
		return new TokenTextSplitter(textSplitterProps.getChunkSize(), config.getMinChunkSizeChars(),
				config.getMinChunkLengthToEmbed(), config.getMaxNumChunks(), config.isKeepSeparator());
	}

	/**
	 * 递归字符文本分块器
	 * @param properties 分块配置
	 * @return RecursiveCharacterTextSplitter实例
	 */
	@Bean(name = "recursive")
	public TextSplitter recursiveTextSplitter(DataAgentProperties properties) {
		DataAgentProperties.TextSplitter textSplitterProps = properties.getTextSplitter();
		DataAgentProperties.TextSplitter.RecursiveTextSplitterConfig config = textSplitterProps.getRecursive();
		// RecursiveCharacterTextSplitter
		String[] separators = config.getSeparators();
		if (separators != null && separators.length > 0) {
			return new RecursiveCharacterTextSplitter(textSplitterProps.getChunkSize(), separators);
		}
		else {
			return new RecursiveCharacterTextSplitter(textSplitterProps.getChunkSize());
		}
	}

	/**
	 * 句子分块器
	 * @param properties 分块配置
	 * @return SentenceSplitter实例
	 */
	@Bean(name = "sentence")
	public TextSplitter sentenceSplitter(DataAgentProperties properties) {
		DataAgentProperties.TextSplitter textSplitterConfig = properties.getTextSplitter();
		DataAgentProperties.TextSplitter.SentenceTextSplitterConfig sentenceConfig = textSplitterConfig.getSentence();

		return SentenceSplitter.builder()
			.withChunkSize(textSplitterConfig.getChunkSize())
			.withSentenceOverlap(sentenceConfig.getSentenceOverlap())
			.build();
	}

	/**
	 * 语义分块器
	 * @param properties 分块配置
	 * @param embeddingModel Embedding 模型
	 * @return SemanticTextSplitter实例
	 */
	@Bean(name = "semantic")
	public TextSplitter semanticSplitter(DataAgentProperties properties, EmbeddingModel embeddingModel) {
		DataAgentProperties.TextSplitter textSplitterProps = properties.getTextSplitter();
		DataAgentProperties.TextSplitter.SemanticTextSplitterConfig config = textSplitterProps.getSemantic();
		return SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(config.getMinChunkSize())
			.maxChunkSize(config.getMaxChunkSize())
			.similarityThreshold(config.getSimilarityThreshold())
			.build();
	}

	/**
	 * 段落分块器
	 * @param properties 分块配置
	 * @return ParagraphTextSplitter实例
	 */
	@Bean(name = "paragraph")
	public TextSplitter paragraphSplitter(DataAgentProperties properties) {
		DataAgentProperties.TextSplitter textSplitterProps = properties.getTextSplitter();
		DataAgentProperties.TextSplitter.ParagraphTextSplitterConfig config = textSplitterProps.getParagraph();
		return ParagraphTextSplitter.builder()
			.chunkSize(textSplitterProps.getChunkSize())
			.paragraphOverlapChars(config.getParagraphOverlapChars())
			.build();
	}

}
