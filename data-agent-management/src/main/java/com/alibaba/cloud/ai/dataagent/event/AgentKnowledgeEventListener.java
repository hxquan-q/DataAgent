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
package com.alibaba.cloud.ai.dataagent.event;

import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeResourceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 智能体知识事件监听器。
 * <p>
 * 监听 {@link AgentKnowledgeEmbeddingEvent} 和 {@link AgentKnowledgeDeletionEvent}，
 * 在主事务提交后异步执行向量化和资源清理操作。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AgentKnowledgeEventListener {

	private final AgentKnowledgeMapper agentKnowledgeMapper;

	private final AgentKnowledgeResourceManager agentKnowledgeResourceManager;

	/**
	 * 处理知识向量化嵌入事件。
	 * <p>
	 * 使用 {@code phase = TransactionPhase.AFTER_COMMIT} 确保仅在主事务提交成功后才执行，
	 * 避免事务回滚后仍执行向量化。处理流程：更新为处理中 -> 执行向量化 -> 更新为已完成（或失败时更新为失败状态）。
	 * @param event 向量化嵌入事件
	 */
	@Async("dbOperationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleEmbeddingEvent(AgentKnowledgeEmbeddingEvent event) {
		log.info("Received AgentKnowledgeEmbeddingEvent. agentKnowledgeId: {}", event.getKnowledgeId());
		Integer id = event.getKnowledgeId();

		// 1. 查询知识数据
		AgentKnowledge knowledge = agentKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			log.error("Knowledge not found during async processing. Id: {}", id);
			return;
		}

		try {
			// 2. 更新状态为 PROCESSING
			updateStatus(knowledge, EmbeddingStatus.PROCESSING, null);

			// 3. 执行核心向量化逻辑
			agentKnowledgeResourceManager.doEmbedingToVectorStore(knowledge);

			// 4. 更新状态为 COMPLETED
			updateStatus(knowledge, EmbeddingStatus.COMPLETED, null);

			log.info("Successfully embedded knowledge. Id: {}", id);

		}
		catch (Exception e) {
			log.error("Failed to embed knowledge. Id: {}", id, e);
			// 5. 失败处理
			updateStatus(knowledge, EmbeddingStatus.FAILED, e.getMessage());
		}
		log.info("Finished processing AgentKnowledgeEmbeddingEvent. agentKnowledgeId: {}", event.getKnowledgeId());

	}

	/**
	 * 更新知识的嵌入状态和错误信息。
	 * @param knowledge 知识实体
	 * @param status 目标嵌入状态
	 * @param errorMsg 错误信息（可为 {@code null}）
	 */
	private void updateStatus(AgentKnowledge knowledge, EmbeddingStatus status, String errorMsg) {
		knowledge.setEmbeddingStatus(status);
		knowledge.setUpdatedTime(LocalDateTime.now());
		if (errorMsg != null) {
			// 截断错误信息防止数据库字段超长报错
			knowledge.setErrorMsg(errorMsg.length() > 250 ? errorMsg.substring(0, 250) : errorMsg);
		}
		agentKnowledgeMapper.update(knowledge);
	}

	/**
	 * 处理知识删除事件。
	 * <p>
	 * 在主事务提交后异步清理向量和文件资源，清理成功后标记资源已清理。 若清理不完整，则保留未清理标记，由定时任务兜底清理。
	 * @param event 知识删除事件
	 */
	@Async("dbOperationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleDeletionEvent(AgentKnowledgeDeletionEvent event) {
		Integer id = event.getKnowledgeId();
		log.info("Starting async resource cleanup for knowledgeId: {}", id);

		// 1. 重新查询（包含已软删除的记录）
		AgentKnowledge knowledge = agentKnowledgeMapper.selectByIdIncludeDeleted(id);
		if (knowledge == null) {
			log.warn("Knowledge record physically missing, skipping cleanup. ID: {}", id);
			return;
		}

		try {
			// 2. 删除向量数据
			boolean vectorDeleted = agentKnowledgeResourceManager.deleteFromVectorStore(knowledge.getAgentId(), id);

			// 3. 删除物理文件
			boolean fileDeleted = agentKnowledgeResourceManager.deleteKnowledgeFile(knowledge);

			// 4. 更新资源清理状态
			if (vectorDeleted && fileDeleted) {
				// 向量和文件都清理成功，才标记为资源已清理
				knowledge.setIsResourceCleaned(1);
				knowledge.setUpdatedTime(LocalDateTime.now());
				agentKnowledgeMapper.update(knowledge);
				log.info("Resources cleaned up successfully. AgentKnowledgeID: {}", id);
			}
			else {
				log.error("Cleanup incomplete. AgentKnowledgeID: {}, VectorDeleted: {}, FileDeleted: {}", id,
						vectorDeleted, fileDeleted);
				// isResourceCleaned=0，由定时任务兜底清理
			}

		}
		catch (Exception e) {
			log.error("Exception during async cleanup for agentKnowledgeId: {}", id, e);
		}
	}

}
