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
package com.alibaba.cloud.ai.dataagent.service.knowledge;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.util.DocumentConverterUtil;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 知识资源管理器，负责将知识内容（QA/FAQ/文档）向量化存储到向量库，
 * 以及从向量库和文件存储中清理知识关联的向量数据和文件资源。
 */
@Slf4j
@Component
public class AgentKnowledgeResourceManager {

	/** 文本分割器工厂 */
	private final TextSplitterFactory textSplitterFactory;

	/** 文件存储服务 */
	private final FileStorageService fileStorageService;

	/** 向量存储服务 */
	private final AgentVectorStoreService agentVectorStoreService;

	/**
	 * 构造方法。
	 * @param textSplitterFactory 文本分割器工厂
	 * @param fileStorageService 文件存储服务
	 * @param agentVectorStoreService 向量存储服务
	 */
	public AgentKnowledgeResourceManager(TextSplitterFactory textSplitterFactory, FileStorageService fileStorageService,
			AgentVectorStoreService agentVectorStoreService) {
		this.textSplitterFactory = textSplitterFactory;
		this.fileStorageService = fileStorageService;
		this.agentVectorStoreService = agentVectorStoreService;
	}

	/**
	 * 将知识内容向量化并存储到向量库，先删除旧数据再按类型处理。
	 * @param agentKnowledge 知识对象
	 * @throws Exception 向量化或存储过程中发生异常时抛出
	 */
	public void doEmbedingToVectorStore(AgentKnowledge agentKnowledge) throws Exception {
		// 先删除旧的向量数据
		this.deleteFromVectorStore(agentKnowledge.getAgentId(), agentKnowledge.getId());

		// 根据知识类型分发处理
		if (KnowledgeType.QA.equals(agentKnowledge.getType()) || KnowledgeType.FAQ.equals(agentKnowledge.getType())) {
			processQaKnowledge(agentKnowledge);
		}
		else if (KnowledgeType.DOCUMENT.equals(agentKnowledge.getType())) {
			processDocumentKnowledge(agentKnowledge);
		}
		else {
			throw new RuntimeException("Unsupported KnowledgeType: " + agentKnowledge.getType());
		}
	}

	/**
	 * 处理 QA/FAQ 类型知识，将其转换为文档后存入向量库。
	 * @param knowledge 知识对象
	 */
	private void processQaKnowledge(AgentKnowledge knowledge) {
		Document document = DocumentConverterUtil.convertQaFaqKnowledgeToDocument(knowledge);
		agentVectorStoreService.addDocuments(knowledge.getAgentId().toString(), List.of(document));
		log.info("Successfully vectorized AgentKnowledge: id={}, type={}", knowledge.getId(), knowledge.getType());
	}

	/**
	 * 处理文档类型知识，读取文件、分割文本、添加元数据后存入向量库。
	 * @param knowledge 知识对象
	 */
	private void processDocumentKnowledge(AgentKnowledge knowledge) {

		// 读取并分割文档
		List<Document> documents = getAndSplitDocument(knowledge.getFilePath(), knowledge.getSplitterType());
		if (documents == null || documents.isEmpty()) {
			log.error("No documents extracted from file: knowledgeId={}, filePath={}", knowledge.getId(),
					knowledge.getFilePath());
			throw new RuntimeException("No documents extracted from file");
		}

		// 使用工具类为文档添加元数据
		List<Document> documentsWithMetadata = DocumentConverterUtil
			.convertAgentKnowledgeDocumentsWithMetadata(documents, knowledge);

		// 添加到向量存储
		agentVectorStoreService.addDocuments(knowledge.getAgentId().toString(), documentsWithMetadata);
		log.info("Successfully vectorized DOCUMENT knowledge: id={}, filePath={}, documentCount={}, splitterType={}",
				knowledge.getId(), knowledge.getFilePath(), documentsWithMetadata.size(), knowledge.getSplitterType());

	}

	/**
	 * 读取文件并按指定分割方式切分文档。
	 * @param filePath 文件路径
	 * @param splitterType 分割器类型
	 * @return 分割后的文档列表
	 */
	private List<Document> getAndSplitDocument(String filePath, String splitterType) {
		// 使用FileStorageService获取文件资源对象
		Resource resource = fileStorageService.getFileResource(filePath);

		// 使用TikaDocumentReader读取文件
		TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
		List<Document> documents = tikaDocumentReader.read();

		// 根据splitterType获取对应的分块器
		TextSplitter splitter = textSplitterFactory.getSplitter(splitterType);
		log.info("Using splitter type: {} for document splitting", splitterType);

		return splitter.apply(documents);
	}

	/**
	 * 从向量存储中删除知识
	 * @param agentId 代理ID
	 * @param knowledgeId 知识ID
	 * @return 是否删除成功（如果资源不存在也视为成功，实现等幂操作）
	 */
	public boolean deleteFromVectorStore(Integer agentId, Integer knowledgeId) {
		try {

			Map<String, Object> metadata = new HashMap<>();
			metadata.put(Constant.AGENT_ID, agentId.toString());
			metadata.put(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, knowledgeId);

			agentVectorStoreService.deleteDocumentsByMetedata(agentId.toString(), metadata);
			log.info("Successfully deleted knowledge from vector store, knowledgeId: {}", knowledgeId);
			return true;

		}
		catch (Exception e) {
			// 检查是否是资源不存在的错误，如果是则视为删除成功（等幂操作）
			if (e.getMessage() != null && (e.getMessage().contains("not found")
					|| e.getMessage().contains("does not exist") || e.getMessage().contains("already deleted"))) {
				log.info("Vector data already deleted or not found for knowledgeId: {}, treating as success",
						knowledgeId);
				return true;
			}
			else {
				log.error("Failed to delete knowledge from vector store, knowledgeId: {}", knowledgeId, e);
				return false;
			}
		}
	}

	/**
	 * 删除知识文件
	 * @param knowledge 知识对象
	 * @return 是否删除成功（如果不是文档类型或文件不存在也视为成功）
	 */
	public boolean deleteKnowledgeFile(AgentKnowledge knowledge) {
		// 只有文档类型且有文件路径的知识才需要删除文件
		if (!KnowledgeType.DOCUMENT.equals(knowledge.getType()) || !StringUtils.hasText(knowledge.getFilePath())) {
			log.info("Not a document type or no file path, knowledgeId: {}, treating as success", knowledge.getId());
			return true;
		}

		try {
			boolean fileDeleted = fileStorageService.deleteFile(knowledge.getFilePath());
			if (fileDeleted) {
				log.info("Successfully deleted knowledge file, filePath: {}", knowledge.getFilePath());
				return true;
			}
			else {
				log.error("Failed to delete knowledge file, filePath: {}", knowledge.getFilePath());
				return false;
			}

		}
		catch (Exception e) {
			// 检查是否是文件不存在的错误，如果是则视为删除成功（等幂操作）
			if (e.getMessage() != null
					&& (e.getMessage().contains("not found") || e.getMessage().contains("does not exist")
							|| e.getMessage().contains("already deleted") || e.getMessage().contains("No such file"))) {
				log.info("File already deleted or not found, filePath: {}, treating as success",
						knowledge.getFilePath());
				return true;
			}
			else {
				log.error("Exception when deleting knowledge file, filePath: {}", knowledge.getFilePath(), e);
				return false;
			}
		}
	}

}
