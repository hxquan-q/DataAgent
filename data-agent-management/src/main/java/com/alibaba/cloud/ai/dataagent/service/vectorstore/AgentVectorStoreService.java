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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import com.alibaba.cloud.ai.dataagent.dto.search.AgentSearchRequest;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;

/**
 * Agent 向量存储服务接口，提供文档的向量检索、添加、删除和存在性检查能力。
 */
public interface AgentVectorStoreService {

	/**
	 * 查询指定 Agent 的文档（总入口），支持混合检索和纯向量检索。
	 * @param searchRequest 搜索请求对象
	 * @return 匹配的文档列表
	 */
	List<Document> search(AgentSearchRequest searchRequest);

	/**
	 * 根据向量类型删除指定 Agent 的文档。
	 * @param agentId Agent 主键 ID
	 * @param vectorType 向量类型
	 * @return 删除是否成功
	 * @throws Exception 删除过程中发生异常时抛出
	 */
	Boolean deleteDocumentsByVectorType(String agentId, String vectorType) throws Exception;

	/**
	 * 根据元数据删除指定 Agent 的文档。
	 * @param agentId Agent 主键 ID
	 * @param metadata 元数据过滤条件
	 * @return 删除是否成功
	 */
	Boolean deleteDocumentsByMetedata(String agentId, Map<String, Object> metadata);

	/**
	 * 根据元数据删除文档。
	 * @param metadata 元数据过滤条件
	 * @return 删除是否成功
	 */
	Boolean deleteDocumentsByMetadata(Map<String, Object> metadata);

	/**
	 * 获取指定 Agent 的文档列表。
	 * @param agentId Agent 主键 ID
	 * @param query 查询文本
	 * @param vectorType 向量类型
	 * @return 匹配的文档列表
	 */
	List<Document> getDocumentsForAgent(String agentId, String query, String vectorType);

	/**
	 * 获取指定 Agent 的文档列表，支持自定义 topK 和相似度阈值。
	 * @param agentId Agent 主键 ID
	 * @param query 查询文本
	 * @param vectorType 向量类型
	 * @param topK 返回的最大文档数
	 * @param threshold 相似度阈值
	 * @return 匹配的文档列表
	 */
	List<Document> getDocumentsForAgent(String agentId, String query, String vectorType, int topK, double threshold);

	/**
	 * 通过元数据过滤条件精确查找文档。
	 * @param filterExpression 过滤表达式
	 * @param topK 返回的最大文档数
	 * @return 匹配的文档列表
	 */
	List<Document> getDocumentsOnlyByFilter(Filter.Expression filterExpression, Integer topK);

	/**
	 * 检查指定 Agent 是否已存在文档。
	 * @param agentId Agent 主键 ID
	 * @return 是否存在文档
	 */
	boolean hasDocuments(String agentId);

	/**
	 * 向指定 Agent 添加文档到向量存储。
	 * @param agentId Agent 主键 ID
	 * @param documents 待添加的文档列表
	 */
	void addDocuments(String agentId, List<Document> documents);

}
