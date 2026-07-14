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
package com.alibaba.cloud.ai.dataagent.converter;

import com.alibaba.cloud.ai.dataagent.dto.knowledge.businessknowledge.CreateBusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.vo.BusinessKnowledgeVO;
import org.springframework.stereotype.Component;

/**
 * 业务知识对象转换器。
 * <p>
 * 负责在持久化实体（BusinessKnowledge）、数据传输对象（CreateBusinessKnowledgeDTO）
 * 和视图对象（BusinessKnowledgeVO）之间进行双向转换。
 */
@Component
public class BusinessKnowledgeConverter {

	/**
	 * 将持久化实体转换为视图对象。
	 * @param po 持久化实体
	 * @return 视图对象
	 */
	public BusinessKnowledgeVO toVo(BusinessKnowledge po) {
		return BusinessKnowledgeVO.builder()
			.id(po.getId())
			.businessTerm(po.getBusinessTerm())
			.description(po.getDescription())
			.synonyms(po.getSynonyms())
			.isRecall(po.getIsRecall() == 1)
			.agentId(po.getAgentId())
			.createdTime(po.getCreatedTime())
			.updatedTime(po.getUpdatedTime())
			.embeddingStatus(po.getEmbeddingStatus() != null ? po.getEmbeddingStatus().getValue() : null)
			.errorMsg(po.getErrorMsg())
			.build();
	}

	/**
	 * 根据创建 DTO 构建持久化实体。
	 * <p>
	 * 设置默认值（未删除、嵌入状态为处理中）。
	 * @param dto 业务知识创建 DTO
	 * @return 新建的持久化实体
	 */
	public BusinessKnowledge toEntityForCreate(CreateBusinessKnowledgeDTO dto) {
		return BusinessKnowledge.builder()
			.businessTerm(dto.getBusinessTerm())
			.description(dto.getDescription())
			.synonyms(dto.getSynonyms())
			.agentId(dto.getAgentId())
			.isRecall(dto.getIsRecall() ? 1 : 0)
			.isDeleted(0)
			.embeddingStatus(EmbeddingStatus.PROCESSING)
			.build();

	}

}
