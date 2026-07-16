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

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.dto.prompt.EvidenceQueryRewriteDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.semantic.SqlExampleRecallHelper;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.util.*;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 证据召回节点，位于意图识别之后、查询增强之前。
 *
 * <p>
 * 该节点从向量数据库中召回与用户问题相关的业务术语和智能体知识（FAQ/QA/文档等）， 作为后续 SQL 生成和结果分析的上下文证据。 主要流程：
 * <ol>
 * <li>调用大模型对用户问题进行查询重写（消除多轮上下文歧义）</li>
 * <li>基于重写后的查询在向量库中检索业务术语文档与智能体知识文档</li>
 * <li>将召回的文档格式化为证据内容并写入全局状态</li>
 * </ol>
 * </p>
 *
 * @see IntentRecognitionNode
 * @see QueryEnhanceNode
 */
@Slf4j
@Component
@AllArgsConstructor
public class EvidenceRecallNode implements NodeAction {

	private final LlmService llmService;

	private final AgentVectorStoreService vectorStoreService;

	private final JsonParseUtil jsonParseUtil;

	private final AgentKnowledgeMapper agentKnowledgeMapper;

	private final SqlExampleRecallHelper sqlExampleRecallHelper;

	/**
	 * 执行证据召回逻辑。
	 * <p>
	 * 先通过大模型重写查询，再从向量库召回业务知识和智能体知识， 最终将格式化后的证据内容写入状态。
	 * </p>
	 * @param state 工作流全局状态，包含用户输入与智能体 ID
	 * @return 包含证据内容的 Map，key 为 {@value EVIDENCE}，value 为流式生成器
	 * @throws Exception 调用大模型或向量库时可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 从状态中提取用户问题与智能体 ID
		String question = StateUtil.getStringValue(state, INPUT_KEY);
		String agentId = StateUtil.getStringValue(state, AGENT_ID);
		Assert.hasText(agentId, "智能体 ID 不能为空。");

		log.info("证据召回前对问题进行查询重写，原始问题: {}", question);
		log.debug("智能体 ID: {}", agentId);

		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");
		Sinks.Many<String> evidenceDisplaySink = Sinks.many().multicast().onBackpressureBuffer();
		final Map<String, Object> resultMap = new HashMap<>();

		// 单轮无上下文：跳过 LLM 查询重写，直接用原问向量召回（省 1 次完整 LLM，η₂/η₄）
		if (isSingleTurnContext(multiTurn)) {
			log.info("单轮查询，跳过证据查询重写 LLM，直接召回: {}", question);
			Flux<GraphResponse<StreamingOutput>> skipFlux = FluxUtil.createStreamingGeneratorWithMessages(
					this.getClass(), state, "单轮查询，跳过重写，直接召回证据...", "证据召回完成！", ignored -> {
						resultMap.putAll(recallEvidencesByQuery(question, agentId, evidenceDisplaySink));
						return resultMap;
					}, Flux.empty());
			Flux<GraphResponse<StreamingOutput>> evidenceFlux = FluxUtil.createStreamingGenerator(this.getClass(),
					state, evidenceDisplaySink.asFlux().map(ChatResponseUtil::createPureResponse), Flux.empty(),
					Flux.empty(), result -> resultMap);
			return Map.of(EVIDENCE, skipFlux.concatWith(evidenceFlux));
		}

		// 构建查询重写提示词
		// 不扩展为多个子查询，因为此时大模型无法理解不同公司的个性化业务知识（如 PV、KMV 等专业名词），扩展反而会引入噪音
		String prompt = PromptHelper.buildEvidenceQueryRewritePrompt(multiTurn, question);
		log.debug("构建的证据查询重写提示词如下 \n {} \n", prompt);

		// 调用大模型进行查询重写
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

		// 第一段流：查询重写过程
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(this.getClass(), state,
				responseFlux,
				Flux.just(ChatResponseUtil.createResponse("正在查询重写以更好召回evidence..."),
						ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
						ChatResponseUtil.createResponse("\n查询重写完成！")),
				result -> {
					// 基于重写后的查询召回证据，并写入结果 Map
					resultMap.putAll(getEvidences(result, agentId, evidenceDisplaySink));
					return resultMap;
				});

		// 第二段流：证据召回过程的展示输出
		Flux<GraphResponse<StreamingOutput>> evidenceFlux = FluxUtil.createStreamingGenerator(this.getClass(), state,
				evidenceDisplaySink.asFlux().map(ChatResponseUtil::createPureResponse), Flux.empty(), Flux.empty(),
				result -> resultMap);
		return Map.of(EVIDENCE, generator.concatWith(evidenceFlux));
	}

	/** 多轮上下文为空或占位时视为单轮。 */
	public static boolean isSingleTurnContext(String multiTurn) {
		if (multiTurn == null) {
			return true;
		}
		String t = multiTurn.trim();
		return t.isEmpty() || "(无)".equals(t) || "无".equals(t) || "null".equalsIgnoreCase(t);
	}

	/**
	 * 跳过 LLM 重写时的直接召回（与 getEvidences 后半段一致）。
	 */
	private Map<String, Object> recallEvidencesByQuery(String query, String agentId, Sinks.Many<String> sink) {
		try {
			if (query == null || query.isBlank()) {
				sink.tryEmitNext("查询为空，跳过证据召回\n");
				return Map.of(EVIDENCE, "无");
			}
			outputRewrittenQuery(query, sink);
			DocumentRetrievalResult retrievalResult = retrieveDocuments(agentId, query);
			if (retrievalResult.allDocuments().isEmpty()) {
				sink.tryEmitNext("未找到证据！\n");
				return Map.of(EVIDENCE, "无");
			}
			String evidence = buildFormattedEvidenceContent(retrievalResult.businessTermDocuments(),
					retrievalResult.agentKnowledgeDocuments(), agentId, query);
			outputEvidenceContent(retrievalResult.allDocuments(), sink);
			return Map.of(EVIDENCE, evidence);
		}
		catch (Exception e) {
			log.error("直接召回证据失败", e);
			sink.tryEmitError(e);
			return Map.of(EVIDENCE, "");
		}
		finally {
			sink.tryEmitComplete();
		}
	}


	/**
	 * 根据大模型重写后的查询，从向量库中召回证据文档。
	 * @param llmOutput 大模型重写后的查询输出
	 * @param agentId 智能体 ID
	 * @param sink 用于向用户推送召回进度的响应式 Sink
	 * @return 包含证据内容的 Map，key 为 {@value EVIDENCE}
	 */
	private Map<String, Object> getEvidences(String llmOutput, String agentId, Sinks.Many<String> sink) {
		try {
			String standaloneQuery = extractStandaloneQuery(llmOutput);

			// 查询重写失败，直接返回无证据
			if (null == standaloneQuery || standaloneQuery.isEmpty()) {
				log.debug("大模型输出中未提取到独立查询");
				sink.tryEmitNext("未能进行查询重写！\n");
				return Map.of(EVIDENCE, "无");
			}

			// 向用户输出重写后的查询
			outputRewrittenQuery(standaloneQuery, sink);

			// 从向量库检索业务知识与智能体知识文档
			DocumentRetrievalResult retrievalResult = retrieveDocuments(agentId, standaloneQuery);

			// 未检索到任何证据文档
			if (retrievalResult.allDocuments().isEmpty()) {
				log.debug("未找到证据文档，智能体: {}，查询: {}", agentId, standaloneQuery);
				sink.tryEmitNext("未找到证据！\n");
				return Map.of(EVIDENCE, "无");
			}

			// 将召回的文档格式化为证据内容（传入 standaloneQuery 增强 SQL 样例关键词召回）
			String evidence = buildFormattedEvidenceContent(retrievalResult.businessTermDocuments(),
					retrievalResult.agentKnowledgeDocuments(), agentId, standaloneQuery);
			log.info("构建的证据内容如下 \n {} \n", evidence);
			// 向用户输出证据召回结果
			outputEvidenceContent(retrievalResult.allDocuments(), sink);

			return Map.of(EVIDENCE, evidence);
		}
		catch (Exception e) {
			log.error("召回证据时发生异常", e);
			sink.tryEmitError(e);
			return Map.of(EVIDENCE, "");
		}
		finally {
			sink.tryEmitComplete();
		}
	}

	/**
	 * 向用户输出重写后的查询信息。
	 * @param standaloneQuery 重写后的独立查询
	 * @param sink 响应式 Sink
	 */
	private void outputRewrittenQuery(String standaloneQuery, Sinks.Many<String> sink) {
		sink.tryEmitNext("重写后查询：\n");
		sink.tryEmitNext(standaloneQuery + "\n");
		log.debug("使用独立查询进行证据召回: {}", standaloneQuery);
		sink.tryEmitNext("正在获取证据...");
	}

	/**
	 * 从向量库中检索业务术语文档与智能体知识文档。
	 * @param agentId 智能体 ID
	 * @param standaloneQuery 重写后的独立查询
	 * @return 文档检索结果，包含业务术语、智能体知识以及合并后的全部文档
	 */
	private DocumentRetrievalResult retrieveDocuments(String agentId, String standaloneQuery) {
		// 检索业务知识（业务术语）文档
		List<Document> businessTermDocuments = vectorStoreService
			.getDocumentsForAgent(agentId, standaloneQuery, DocumentMetadataConstant.BUSINESS_TERM)
			.stream()
			.toList();

		// 检索智能体知识文档
		List<Document> agentKnowledgeDocuments = vectorStoreService
			.getDocumentsForAgent(agentId, standaloneQuery, DocumentMetadataConstant.AGENT_KNOWLEDGE)
			.stream()
			.toList();

		// 合并所有证据文档
		List<Document> allDocuments = new ArrayList<>();
		if (!businessTermDocuments.isEmpty())
			allDocuments.addAll(businessTermDocuments);
		if (!agentKnowledgeDocuments.isEmpty())
			allDocuments.addAll(agentKnowledgeDocuments);

		// 输出文档检索日志
		log.info("智能体 {} 召回文档: {} 篇业务术语, {} 篇智能体知识, 共 {} 篇", agentId, businessTermDocuments.size(),
				agentKnowledgeDocuments.size(), allDocuments.size());

		return new DocumentRetrievalResult(businessTermDocuments, agentKnowledgeDocuments, allDocuments);
	}

	// 构建证据内容，输出格式示例：
	// 1. [来源: 2025Q3报告-销售数据.md] ...华东地区的增长主要来自于核心用户...
	// 2. [来源: 客服FAQ] Q: 退款怎么算? A: 只统计已入库退货...
	/**
	 * 将召回的业务术语与智能体知识文档格式化为证据内容字符串。
	 * <p>
	 * 末尾额外追加 sql_example 训练库的 few-shot 样例（vanna get_similar_question_sql 的 Java 等价），
	 * 作为第三路证据增强后续 NL2SQL 生成。few-shot 召回失败不阻断主流程。
	 * </p>
	 * @param businessTermDocuments 业务术语文档列表
	 * @param agentKnowledgeDocuments 智能体知识文档列表
	 * @param agentId 智能体ID，用于召回已审核 SQL 样例
	 * @param userQuestion 用户当前独立查询（重写后），用于 SQL 样例关键词相似度召回；可为空（降级全量）
	 * @return 格式化后的证据内容字符串；若三路均为空则返回 "无"
	 */
	private String buildFormattedEvidenceContent(List<Document> businessTermDocuments,
			List<Document> agentKnowledgeDocuments, String agentId, String userQuestion) {
		// 构建业务知识内容
		String businessKnowledgeContent = buildBusinessKnowledgeContent(businessTermDocuments);

		// 构建智能体知识内容
		String agentKnowledgeContent = buildAgentKnowledgeContent(agentKnowledgeDocuments);

		// 使用 PromptHelper 模板方法渲染业务知识与智能体知识
		String businessPrompt = PromptHelper.buildBusinessKnowledgePrompt(businessKnowledgeContent);
		String agentPrompt = PromptHelper.buildAgentKnowledgePrompt(agentKnowledgeContent);

		// 第三路：召回 sql_example 已审核样例，拼成 few-shot（有 question 时关键词过滤，失败降级为空串）
		Integer agentIdInt = parseAgentId(agentId);
		String sqlFewShot = agentIdInt == null ? "" : sqlExampleRecallHelper.recallFewShot(agentIdInt, userQuestion);

		// 输出证据构建日志
		log.info("构建证据内容: 业务知识长度 {}, 智能体知识长度 {}, SQL样例few-shot长度 {}", businessKnowledgeContent.length(),
				agentKnowledgeContent.length(), sqlFewShot.length());

		// 拼接业务知识、智能体知识与 SQL few-shot 作为最终证据
		StringBuilder evidence = new StringBuilder();
		if (!businessKnowledgeContent.isEmpty()) {
			evidence.append(businessPrompt);
		}
		if (!agentKnowledgeContent.isEmpty()) {
			if (evidence.length() > 0) {
				evidence.append("\n\n");
			}
			evidence.append(agentPrompt);
		}
		if (!sqlFewShot.isEmpty()) {
			if (evidence.length() > 0) {
				evidence.append("\n\n");
			}
			evidence.append(sqlFewShot);
		}
		return evidence.length() == 0 ? "无" : evidence.toString();
	}

	/**
	 * 将字符串形式的 agentId 解析为 Integer，非法值返回 {@code null}（不阻断召回）。
	 * @param agentId 原始字符串
	 * @return 解析后的整数；空或非数字返回 {@code null}
	 */
	private Integer parseAgentId(String agentId) {
		if (agentId == null || agentId.isBlank()) {
			return null;
		}
		try {
			return Integer.valueOf(agentId.trim());
		}
		catch (NumberFormatException e) {
			log.warn("agentId 非数字格式，跳过 SQL 样例召回: {}", agentId);
			return null;
		}
	}

	/**
	 * 构建业务知识内容字符串。
	 * @param businessTermDocuments 业务术语文档列表
	 * @return 业务知识内容；若列表为空则返回空字符串
	 */
	private String buildBusinessKnowledgeContent(List<Document> businessTermDocuments) {
		if (businessTermDocuments.isEmpty()) {
			return "";
		}

		StringBuilder result = new StringBuilder();

		// 直接使用 Document 的完整文本内容，每行一个文档
		for (Document doc : businessTermDocuments) {
			result.append(doc.getText()).append("\n");
		}

		return result.toString();
	}

	/**
	 * 构建智能体知识内容字符串，根据知识类型（FAQ/QA/文档）分别处理。
	 * @param agentKnowledgeDocuments 智能体知识文档列表
	 * @return 智能体知识内容；若列表为空则返回空字符串
	 */
	private String buildAgentKnowledgeContent(List<Document> agentKnowledgeDocuments) {
		if (agentKnowledgeDocuments.isEmpty()) {
			return "";
		}

		StringBuilder result = new StringBuilder();

		for (int i = 0; i < agentKnowledgeDocuments.size(); i++) {
			Document doc = agentKnowledgeDocuments.get(i);
			Map<String, Object> metadata = doc.getMetadata();
			String knowledgeType = (String) metadata.get(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE);

			// 根据知识类型调用不同的处理方法：FAQ/QA 类型或普通文档类型
			if (KnowledgeType.FAQ.getCode().equals(knowledgeType) || KnowledgeType.QA.getCode().equals(knowledgeType)) {
				processFaqOrQaKnowledge(doc, i, result);
			}
			else {
				processDocumentKnowledge(doc, i, result);
			}
		}

		return result.toString();
	}

	/**
	 * 处理 FAQ 或 QA 类型的知识，格式为 "[来源: xxx] Q: xxx A: xxx"。
	 * @param doc 知识文档
	 * @param index 文档索引（用于编号）
	 * @param result 用于拼接内容的 StringBuilder
	 */
	private void processFaqOrQaKnowledge(Document doc, int index, StringBuilder result) {
		Map<String, Object> metadata = doc.getMetadata();
		String content = doc.getText();
		Integer knowledgeId = ((Number) metadata.get(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID)).intValue();
		String knowledgeType = (String) metadata.get(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE);

		log.debug("Processing {} type knowledge with id: {}", knowledgeType, knowledgeId);

		if (knowledgeId != null) {
			try {
				AgentKnowledge knowledge = agentKnowledgeMapper.selectById(knowledgeId);
				if (knowledge != null) {
					String title = knowledge.getTitle();
					// 格式：[来源: xxx] Q: xxx A: xxx
					result.append(index + 1).append(". [来源: ");
					result.append(title.isEmpty() ? "知识库" : title);
					result.append("] Q: ").append(content).append(" A: ").append(knowledge.getContent()).append("\n");

					log.debug("Successfully processed {} knowledge with title: {}", knowledgeType, title);
				}
				else {
					log.warn("Knowledge not found for id: {}", knowledgeId);
				}
			}
			catch (Exception e) {
				log.error("Error getting knowledge by id: {}", knowledgeId, e);
				// 如果获取失败，使用原始内容
				result.append(index + 1).append(". [来源: 知识库] ").append(content).append("\n");
			}
		}
		else {
			// 如果没有知识ID，使用原始内容
			log.error("No knowledge id found for agent knowledge document: {}", doc.getId());
			result.append(index + 1).append(". [来源: 知识库] ").append(content).append("\n");
		}
	}

	/**
	 * 处理文档（DOCUMENT）类型的知识，格式为 "[来源: 标题-文件名] 内容"。
	 * @param doc 知识文档
	 * @param index 文档索引（用于编号）
	 * @param result 用于拼接内容的 StringBuilder
	 */
	private void processDocumentKnowledge(Document doc, int index, StringBuilder result) {
		Map<String, Object> metadata = doc.getMetadata();
		String content = doc.getText();
		Integer knowledgeId = ((Number) metadata.get(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID)).intValue();
		String knowledgeType = (String) metadata.get(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE);
		String title = "";
		String sourceFilename = "";

		log.debug("Processing {} type knowledge with id: {}", knowledgeType, knowledgeId);

		if (knowledgeId != null) {
			try {
				AgentKnowledge knowledge = agentKnowledgeMapper.selectById(knowledgeId);
				if (knowledge != null) {
					title = knowledge.getTitle();
					sourceFilename = knowledge.getSourceFilename();

					log.debug("Successfully processed {} knowledge with title: {}, source file: {}", knowledgeType,
							title, sourceFilename);
				}
				else {
					log.warn("Knowledge not found for id: {}", knowledgeId);
				}
			}
			catch (Exception e) {
				log.error("Error getting knowledge by id: {}", knowledgeId, e);
			}
		}

		// 构建来源信息，格式为"标题-文件名"
		String sourceInfo = title.isEmpty() ? "文档" : title;
		if (!sourceFilename.isEmpty()) {
			sourceInfo += "-" + sourceFilename;
		}

		result.append(index + 1).append(". [来源: ");
		result.append(sourceInfo);
		result.append("] ").append(content).append("\n");
	}

	private void outputEvidenceContent(List<Document> allDocuments, Sinks.Many<String> sink) {
		if (allDocuments.isEmpty()) {
			return;
		}

		log.info("Outputting evidence content for {} documents", allDocuments.size());
		sink.tryEmitNext("已找到 " + allDocuments.size() + " 条相关证据文档，如下是文档的部分信息\n");

		// 只输出文档的摘要信息，而不是完整内容
		for (int i = 0; i < allDocuments.size(); i++) {
			Document doc = allDocuments.get(i);
			String content = doc.getText();

			// 限制每个文档摘要的长度，最多显示100个字符
			String summary = content.length() > 100 ? content.substring(0, 100) + "..." : content;

			sink.tryEmitNext(String.format("证据%d: %s\n", i + 1, summary));
		}
	}

	private record DocumentRetrievalResult(List<Document> businessTermDocuments, List<Document> agentKnowledgeDocuments,
			List<Document> allDocuments) {
	}

	private String extractStandaloneQuery(String llmOutput) {
		EvidenceQueryRewriteDTO evidenceQueryRewriteDTO;
		try {
			String content = MarkdownParserUtil.extractText(llmOutput.trim());
			evidenceQueryRewriteDTO = jsonParseUtil.tryConvertToObject(content, EvidenceQueryRewriteDTO.class);
			log.info("For getting evidence, successfully parsed EvidenceQueryRewriteDTO from LLM response: {}",
					evidenceQueryRewriteDTO);
			return evidenceQueryRewriteDTO.getStandaloneQuery();
		}
		catch (Exception e) {
			log.error("Failed to parse EvidenceQueryRewriteDTO from LLM response", e);
		}
		return null;
	}

}
