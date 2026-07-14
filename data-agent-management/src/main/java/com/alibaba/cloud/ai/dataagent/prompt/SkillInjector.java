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
package com.alibaba.cloud.ai.dataagent.prompt;

import com.alibaba.cloud.ai.dataagent.entity.Skill;
import com.alibaba.cloud.ai.dataagent.mapper.SkillMapper;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 按作用域把智能体启用的技能指令注入提示词。 */
@Component
@RequiredArgsConstructor
public class SkillInjector {

	private static final String OPTIMIZATION_PLACEHOLDER = "{optimization_section}";

	private static final Set<String> SCOPES = Set.of("report", "sql", "python");

	private final SkillMapper skillMapper;

	/**
	 * 注入技能指令。存在旧优化占位符时原位替换，否则追加到提示词末尾。
	 * @param scope 作用域
	 * @param agentId 智能体 ID
	 * @param basePrompt 基础提示词
	 * @return 注入后的提示词
	 */
	public String inject(String scope, Long agentId, String basePrompt) {
		String prompt = basePrompt == null ? "" : basePrompt;
		String section = buildSection(scope, agentId);
		if (prompt.contains(OPTIMIZATION_PLACEHOLDER)) {
			return prompt.replace(OPTIMIZATION_PLACEHOLDER, section);
		}
		return section.isEmpty() ? prompt : prompt + "\n\n" + section;
	}

	/** 构建按优先级排序的技能注入段。 */
	public String buildSection(String scope, Long agentId) {
		String normalizedScope = normalizeScope(scope);
		if (agentId == null) {
			return "";
		}
		List<Skill> skills = skillMapper.selectEnabledByScopeAndAgentId(normalizedScope, agentId);
		if (skills == null || skills.isEmpty()) {
			return "";
		}
		String content = skills.stream()
			.filter(skill -> skill.getContent() != null && !skill.getContent().isBlank())
			.map(skill -> "### " + skill.getName() + "\n" + skill.getContent().trim())
			.collect(Collectors.joining("\n\n"));
		return content.isEmpty() ? "" : "## 技能指令\n" + content;
	}

	private String normalizeScope(String scope) {
		if (scope == null || !SCOPES.contains(scope.trim().toLowerCase(Locale.ROOT))) {
			throw new IllegalArgumentException("技能作用域仅支持 report、sql、python");
		}
		return scope.trim().toLowerCase(Locale.ROOT);
	}

}
