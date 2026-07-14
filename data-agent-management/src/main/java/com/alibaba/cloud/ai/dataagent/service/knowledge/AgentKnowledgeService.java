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

import com.alibaba.cloud.ai.dataagent.vo.PageResult;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.AgentKnowledgeQueryDTO;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.CreateKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.UpdateKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.vo.AgentKnowledgeVO;

/**
 * Agent 知识库服务接口，提供知识的增删改查、分页查询、召回状态切换和向量重试能力。
 */
public interface AgentKnowledgeService {

	/**
	 * 根据主键 ID 获取知识详情。
	 * @param id 知识主键 ID
	 * @return 知识 VO 对象，不存在时返回 null
	 */
	AgentKnowledgeVO getKnowledgeById(Integer id);

	/**
	 * 创建知识，支持文档、QA、FAQ 类型。
	 * @param createKnowledgeDto 创建知识 DTO
	 * @return 创建后的知识 VO 对象
	 */
	AgentKnowledgeVO createKnowledge(CreateKnowledgeDTO createKnowledgeDto);

	/**
	 * 更新知识的标题和内容。
	 * @param id 知识主键 ID
	 * @param updateKnowledgeDto 更新知识 DTO
	 * @return 更新后的知识 VO 对象
	 */
	AgentKnowledgeVO updateKnowledge(Integer id, UpdateKnowledgeDTO updateKnowledgeDto);

	/**
	 * 删除知识（软删除），并异步清理向量数据和文件。
	 * @param id 知识主键 ID
	 * @return 删除是否成功
	 */
	boolean deleteKnowledge(Integer id);

	/**
	 * 分页条件查询知识列表。
	 * @param queryDTO 查询条件 DTO
	 * @return 分页结果
	 */
	PageResult<AgentKnowledgeVO> queryByConditionsWithPage(AgentKnowledgeQueryDTO queryDTO);

	/**
	 * 更新知识的召回状态（是否参与向量检索）。
	 * @param id 知识主键 ID
	 * @param recalled 是否可召回
	 * @return 更新后的知识 VO 对象
	 */
	AgentKnowledgeVO updateKnowledgeRecallStatus(Integer id, Boolean recalled);

	/**
	 * 重试向量嵌入，重置嵌入状态并重新发布嵌入事件。
	 * @param id 知识主键 ID
	 */
	void retryEmbedding(Integer id);

}
