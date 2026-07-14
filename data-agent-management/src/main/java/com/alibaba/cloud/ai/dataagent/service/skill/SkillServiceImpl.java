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
package com.alibaba.cloud.ai.dataagent.service.skill;

import com.alibaba.cloud.ai.dataagent.entity.AgentSkill;
import com.alibaba.cloud.ai.dataagent.entity.Skill;
import com.alibaba.cloud.ai.dataagent.mapper.AgentSkillMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SkillMapper;
import com.alibaba.cloud.ai.dataagent.vo.PageResult;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 技能管理服务实现。 */
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

	private static final Set<String> SCOPES = Set.of("report", "sql", "python");

	private final SkillMapper skillMapper;

	private final AgentSkillMapper agentSkillMapper;

	@Override
	public PageResult<Skill> page(String scope, String keyword, int pageNum, int pageSize) {
		String normalizedScope = scope == null || scope.isBlank() ? null : normalizeScope(scope);
		int safePageNum = Math.max(pageNum, 1);
		int safePageSize = Math.min(Math.max(pageSize, 1), 100);
		long total = skillMapper.count(normalizedScope, keyword);
		PageResult<Skill> result = new PageResult<>(
				skillMapper.selectPage(normalizedScope, keyword, (safePageNum - 1) * safePageSize, safePageSize), total,
				safePageNum, safePageSize, 0);
		result.calculateTotalPages();
		return result;
	}

	@Override
	public Skill getById(Long id) {
		return skillMapper.selectById(id);
	}

	@Override
	public Skill create(Skill skill) {
		normalize(skill);
		skillMapper.insert(skill);
		return skill;
	}

	@Override
	public Skill update(Long id, Skill skill) {
		if (skillMapper.selectById(id) == null) {
			return null;
		}
		skill.setId(id);
		normalize(skill);
		skillMapper.updateById(skill);
		return skill;
	}

	@Override
	@Transactional
	public boolean delete(Long id) {
		agentSkillMapper.deleteBySkillId(id);
		return skillMapper.deleteById(id) > 0;
	}

	@Override
	public List<Skill> listByAgentId(Long agentId) {
		return skillMapper.selectByAgentId(agentId);
	}

	@Override
	public List<Skill> listEnabled(String scope, Long agentId) {
		return skillMapper.selectEnabledByScopeAndAgentId(normalizeScope(scope), agentId);
	}

	@Override
	@Transactional
	public AgentSkill bind(Long agentId, Long skillId, Boolean enabled) {
		if (skillMapper.selectById(skillId) == null) {
			throw new IllegalArgumentException("技能不存在: " + skillId);
		}
		AgentSkill binding = agentSkillMapper.select(agentId, skillId);
		if (binding == null) {
			binding = AgentSkill.builder()
				.agentId(agentId)
				.skillId(skillId)
				.enabled(enabled == null || enabled)
				.build();
			agentSkillMapper.insert(binding);
		}
		else {
			binding.setEnabled(enabled == null || enabled);
			agentSkillMapper.updateEnabled(binding);
		}
		return binding;
	}

	@Override
	public boolean unbind(Long agentId, Long skillId) {
		return agentSkillMapper.delete(agentId, skillId) > 0;
	}

	private void normalize(Skill skill) {
		if (skill == null || skill.getName() == null || skill.getName().isBlank() || skill.getContent() == null
				|| skill.getContent().isBlank()) {
			throw new IllegalArgumentException("技能名称和内容不能为空");
		}
		skill.setName(skill.getName().trim());
		skill.setScope(normalizeScope(skill.getScope()));
		if (skill.getEnabled() == null) {
			skill.setEnabled(true);
		}
		if (skill.getPriority() == null) {
			skill.setPriority(0);
		}
		if (skill.getDisplayOrder() == null) {
			skill.setDisplayOrder(0);
		}
	}

	private String normalizeScope(String scope) {
		if (scope == null || !SCOPES.contains(scope.trim().toLowerCase(Locale.ROOT))) {
			throw new IllegalArgumentException("技能作用域仅支持 report、sql、python");
		}
		return scope.trim().toLowerCase(Locale.ROOT);
	}

}
