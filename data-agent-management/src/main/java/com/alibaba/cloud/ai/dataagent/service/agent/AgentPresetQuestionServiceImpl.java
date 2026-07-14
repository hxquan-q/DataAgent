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
package com.alibaba.cloud.ai.dataagent.service.agent;

import com.alibaba.cloud.ai.dataagent.entity.AgentPresetQuestion;
import com.alibaba.cloud.ai.dataagent.mapper.AgentPresetQuestionMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 预设问题服务实现类，实现预设问题的增删改查及批量保存逻辑。
 */
@Service
@AllArgsConstructor
public class AgentPresetQuestionServiceImpl implements AgentPresetQuestionService {

	/** 预设问题数据访问层 */
	private final AgentPresetQuestionMapper agentPresetQuestionMapper;

	/**
	 * 根据 Agent ID 获取激活的预设问题列表。
	 * @param agentId Agent 主键 ID
	 * @return 激活的预设问题列表
	 */
	@Override
	public List<AgentPresetQuestion> findByAgentId(Long agentId) {
		return agentPresetQuestionMapper.selectByAgentId(agentId);
	}

	/**
	 * 根据 Agent ID 获取全部预设问题列表（包含未激活的）。
	 * @param agentId Agent 主键 ID
	 * @return 全部预设问题列表
	 */
	@Override
	public List<AgentPresetQuestion> findAllByAgentId(Long agentId) {
		return agentPresetQuestionMapper.selectAllByAgentId(agentId);
	}

	/**
	 * 创建新的预设问题，设置默认排序值和激活状态后插入数据库。
	 * @param question 待创建的预设问题对象
	 * @return 创建后的预设问题对象（包含生成的 ID）
	 */
	@Override
	public AgentPresetQuestion create(AgentPresetQuestion question) {
		// 确保默认值
		if (question.getSortOrder() == null) {
			question.setSortOrder(0);
		}
		if (question.getIsActive() == null) {
			question.setIsActive(true);
		}

		agentPresetQuestionMapper.insert(question);
		// ID 由 MyBatis 自动回填
		return question;
	}

	/**
	 * 更新已存在的预设问题。
	 * @param id 预设问题主键 ID
	 * @param question 待更新的预设问题对象
	 */
	@Override
	public void update(Long id, AgentPresetQuestion question) {
		// 确保主键 ID 被正确设置
		question.setId(id);
		agentPresetQuestionMapper.update(question);
	}

	/**
	 * 根据主键 ID 删除预设问题。
	 * @param id 预设问题主键 ID
	 */
	@Override
	public void deleteById(Long id) {
		agentPresetQuestionMapper.deleteById(id);
	}

	/**
	 * 删除指定 Agent 的全部预设问题。
	 * @param agentId Agent 主键 ID
	 */
	@Override
	public void deleteByAgentId(Long agentId) {
		agentPresetQuestionMapper.deleteByAgentId(agentId);
	}

	/**
	 * 批量保存预设问题：先删除该 Agent 已有的全部预设问题，再按顺序插入新列表。
	 * @param agentId Agent 主键 ID
	 * @param questions 待批量保存的预设问题列表
	 */
	@Override
	public void batchSave(Long agentId, List<AgentPresetQuestion> questions) {
		// 第一步：删除该 Agent 已有的全部预设问题
		deleteByAgentId(agentId);

		// 第二步：按顺序插入新的预设问题，设置排序值和默认激活状态
		for (int i = 0; i < questions.size(); i++) {
			AgentPresetQuestion question = questions.get(i);
			question.setAgentId(agentId);
			question.setSortOrder(i);
			if (question.getIsActive() == null) {
				question.setIsActive(true);
			}
			// 复用 create 方法，它负责设置默认值并插入
			create(question);
		}
	}

}
