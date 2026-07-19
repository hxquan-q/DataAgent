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
package com.alibaba.cloud.ai.dataagent.constant;

/**
 * 文档元数据（Metadata）键名常量。
 * <p>
 * 定义存储在向量库文档 Metadata 中的标准键名，用于标识列信息、表信息、 知识 ID、业务术语等元数据。
 */
public final class DocumentMetadataConstant {

	private DocumentMetadataConstant() {

	}

	/** 列名称 */
	public static final String COLUMN = "column";

	/** 表名称 */
	public static final String TABLE = "table";

	/** 通用名称字段 */
	public static final String NAME = "name";

	/** 表名（完整路径） */
	public static final String TABLE_NAME = "tableName";

	/** 向量化类型 */
	public static final String VECTOR_TYPE = "vectorType";

	/** 智能体知识 ID */
	public static final String DB_AGENT_KNOWLEDGE_ID = "agentKnowledgeId";

	/** 知识具体类型（FAQ / DOCUMENT / QA） */
	public static final String CONCRETE_AGENT_KNOWLEDGE_TYPE = "concreteAgentKnowledgeType";

	/** 标识该文档为智能体知识内容 */
	public static final String AGENT_KNOWLEDGE = "agentKnowledge";

	/** 业务术语 */
	public static final String BUSINESS_TERM = "businessTerm";

	/** 业务术语 ID */
	public static final String DB_BUSINESS_TERM_ID = "businessTermId";

}
