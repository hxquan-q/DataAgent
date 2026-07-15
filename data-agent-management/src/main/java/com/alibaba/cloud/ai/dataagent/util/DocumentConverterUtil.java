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

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 业务对象到 {@link Document} 的转换工具类。
 * <p>
 * 将表、列、业务名词以及 QA/FAQ 等知识对象转换为向量存储所需的 Document， 统一封装元数据（如数据源 ID、向量类型、Agent ID
 * 等），便于后续检索与过滤。
 * </p>
 */
@Slf4j
public class DocumentConverterUtil {

	/**
	 * 批量将多张表的列信息转换为 Document 列表。
	 * @param datasourceId 数据源 ID
	 * @param tables 表信息集合
	 * @return 所有列对应的 Document 列表
	 */
	public static List<Document> convertColumnsToDocuments(Integer datasourceId, List<TableInfoBO> tables) {
		List<Document> documents = new ArrayList<>();
		for (TableInfoBO table : tables) {
			// 使用已经处理过的列数据，避免重复查询
			List<ColumnInfoBO> columns = table.getColumns();
			if (columns != null) {
				for (ColumnInfoBO column : columns) {
					documents.add(DocumentConverterUtil.convertColumnToDocument(datasourceId, table, column));
				}
			}
		}
		return documents;
	}

	/**
	 * 将单个列信息转换为向量存储使用的 Document。
	 * @param datasourceId 数据源 ID
	 * @param tableInfoBO 列所属的表信息
	 * @param columnInfoBO 列信息
	 * @return 携带列元数据的 Document 对象
	 */
	public static Document convertColumnToDocument(Integer datasourceId, TableInfoBO tableInfoBO,
			ColumnInfoBO columnInfoBO) {
		// 优先使用描述作为向量化的文本，缺失时回退到列名
		String text = StringUtils.isBlank(columnInfoBO.getDescription()) ? columnInfoBO.getName()
				: columnInfoBO.getDescription();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("name", columnInfoBO.getName());
		metadata.put("tableName", tableInfoBO.getName());
		metadata.put("description", Optional.ofNullable(columnInfoBO.getDescription()).orElse(""));
		metadata.put("type", columnInfoBO.getType());
		metadata.put("primary", columnInfoBO.isPrimary());
		metadata.put("notnull", columnInfoBO.isNotnull());
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.COLUMN);
		metadata.put(Constant.DATASOURCE_ID, datasourceId.toString());

		// 仅当存在样本值时写入 samples 字段
		if (columnInfoBO.getSamples() != null) {
			metadata.put("samples", columnInfoBO.getSamples());
		}

		return new Document(text, metadata);
	}

	/**
	 * 将表信息转换为向量存储使用的 Document。
	 * @param datasourceId 数据源 ID
	 * @param tableInfoBO 表信息
	 * @return 携带表元数据的 Document 对象
	 */
	public static Document convertTableToDocument(Integer datasourceId, TableInfoBO tableInfoBO) {
		// 优先使用描述，缺失时回退到表名
		String text = StringUtils.isBlank(tableInfoBO.getDescription()) ? tableInfoBO.getName()
				: tableInfoBO.getDescription();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("schema", Optional.ofNullable(tableInfoBO.getSchema()).orElse(""));
		metadata.put("name", tableInfoBO.getName());
		metadata.put("description", Optional.ofNullable(tableInfoBO.getDescription()).orElse(""));
		metadata.put("foreignKey", Optional.ofNullable(tableInfoBO.getForeignKey()).orElse(""));
		metadata.put("primaryKey", Optional.ofNullable(tableInfoBO.getPrimaryKeys()).orElse(new ArrayList<>()));
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.TABLE);
		metadata.put(Constant.DATASOURCE_ID, datasourceId.toString());
		return new Document(text, metadata);
	}

	/**
	 * 批量将多张表的信息转换为 Document 列表。
	 * @param datasourceId 数据源 ID
	 * @param tables 表信息集合
	 * @return 所有表对应的 Document 列表
	 */
	public static List<Document> convertTablesToDocuments(Integer datasourceId, List<TableInfoBO> tables) {
		return tables.stream()
			.map(table -> DocumentConverterUtil.convertTableToDocument(datasourceId, table))
			.collect(Collectors.toList());
	}

	/**
	 * 将业务名词知识转换为向量存储使用的 Document。
	 * <p>
	 * 文本内容包含业务名词、说明与同义词；元数据中标记向量类型为业务名词。
	 * </p>
	 * @param businessKnowledge 业务名词知识对象
	 * @return 携带业务名词元数据的 Document 对象
	 */
	public static Document convertBusinessKnowledgeToDocument(BusinessKnowledge businessKnowledge) {

		// 构建文档内容，包含业务名词、说明和同义词
		String businessTerm = businessKnowledge.getBusinessTerm();
		String description = Optional.ofNullable(businessKnowledge.getDescription()).orElse("无");
		String synonyms = Optional.ofNullable(businessKnowledge.getSynonyms()).orElse("无");

		String content = String.format("业务名词: %s, 说明: %s, 同义词: %s", businessTerm, description, synonyms);

		// 构建元数据
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM);
		metadata.put(Constant.AGENT_ID, businessKnowledge.getAgentId().toString());
		metadata.put(DocumentMetadataConstant.DB_BUSINESS_TERM_ID, businessKnowledge.getId());

		return new Document(content, metadata);
	}

	/**
	 * 将 QA/FAQ 知识转换为向量存储使用的 Document。
	 * <p>
	 * 文本内容使用问题作为向量化的文本；答案等频繁变更字段保存在关系数据库中，不在 metadata 中冗余。
	 * </p>
	 * @param knowledge QA/FAQ 知识对象
	 * @return 携带知识元数据的 Document 对象
	 */
	public static Document convertQaFaqKnowledgeToDocument(AgentKnowledge knowledge) {
		// 使用 question 作为 Document 的 content 字段
		String content = knowledge.getQuestion();
		Map<String, Object> metadata = new HashMap<>();
		// answer 和 isRecall 经常变更，放到关系数据库保存
		metadata.put(Constant.AGENT_ID, knowledge.getAgentId().toString());
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.AGENT_KNOWLEDGE);
		metadata.put(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, knowledge.getId());
		metadata.put(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE, knowledge.getType().getCode());

		return new Document(content, metadata);
	}

	/**
	 * 为文档列表添加元数据，用于 DOCUMENT 类型知识处理。
	 * <p>
	 * 保留原始文档的文本与 ID，并补充 Agent ID、知识 ID、向量类型等元数据； isRecall 等频繁变更字段仍由关系数据库维护，不放入 metadata。
	 * </p>
	 * @param documents 原始文档列表
	 * @param knowledge 知识对象
	 * @return 添加了元数据的文档列表
	 */
	public static List<Document> convertAgentKnowledgeDocumentsWithMetadata(List<Document> documents,
			AgentKnowledge knowledge) {
		List<Document> documentsWithMetadata = new ArrayList<>();

		for (Document doc : documents) {
			// isRecall 经常变更，放在关系数据库中而不放入 metadata
			// 复制原始文档元数据并补充知识相关字段
			Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
			metadata.put(Constant.AGENT_ID, knowledge.getAgentId().toString());
			metadata.put(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, knowledge.getId());
			metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.AGENT_KNOWLEDGE);
			metadata.put(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE, knowledge.getType().getCode());

			// 创建带有完整元数据的新文档
			Document docWithMetadata = new Document(doc.getId(), doc.getText(), metadata);
			documentsWithMetadata.add(docWithMetadata);
		}
		return documentsWithMetadata;
	}

	/**
	 * 私有构造方法，禁止实例化工具类。
	 */
	private DocumentConverterUtil() {
		throw new AssertionError("Cannot instantiate utility class");
	}

}
