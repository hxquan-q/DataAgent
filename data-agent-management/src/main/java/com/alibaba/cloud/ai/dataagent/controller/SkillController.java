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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.entity.AgentSkill;
import com.alibaba.cloud.ai.dataagent.entity.Skill;
import com.alibaba.cloud.ai.dataagent.service.skill.SkillService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import com.alibaba.cloud.ai.dataagent.vo.PageResponse;
import com.alibaba.cloud.ai.dataagent.vo.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 技能 CRUD 与智能体绑定接口。 */
@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class SkillController {

	private final SkillService skillService;

	/** 分页查询技能。 */
	@GetMapping
	public PageResponse<List<Skill>> page(@RequestParam(value = "scope", required = false) String scope,
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
			@RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
		PageResult<Skill> result = skillService.page(scope, keyword, pageNum, pageSize);
		return PageResponse.success(result.getData(), result.getTotal(), result.getPageNum(), result.getPageSize(),
				result.getTotalPages());
	}

	/** 查询技能详情。 */
	@GetMapping("/{id}")
	public ApiResponse<Skill> get(@PathVariable Long id) {
		Skill skill = skillService.getById(id);
		return skill == null ? ApiResponse.error("技能不存在") : ApiResponse.success("查询成功", skill);
	}

	/** 创建技能。 */
	@PostMapping
	public ApiResponse<Skill> create(@Valid @RequestBody Skill skill) {
		return ApiResponse.success("创建成功", skillService.create(skill));
	}

	/** 更新技能。 */
	@PutMapping("/{id}")
	public ApiResponse<Skill> update(@PathVariable Long id, @Valid @RequestBody Skill skill) {
		Skill updated = skillService.update(id, skill);
		return updated == null ? ApiResponse.error("技能不存在") : ApiResponse.success("更新成功", updated);
	}

	/** 删除技能及其绑定。 */
	@DeleteMapping("/{id}")
	public ApiResponse<Boolean> delete(@PathVariable Long id) {
		return skillService.delete(id) ? ApiResponse.success("删除成功", true) : ApiResponse.error("技能不存在", false);
	}

	/** 查询智能体绑定的全部技能。 */
	@GetMapping("/agent/{agentId}")
	public ApiResponse<List<Skill>> listByAgent(@PathVariable Long agentId) {
		return ApiResponse.success("查询成功", skillService.listByAgentId(agentId));
	}

	/** 新增或更新智能体技能绑定。 */
	@PostMapping("/{skillId}/agents/{agentId}")
	public ApiResponse<AgentSkill> bind(@PathVariable Long skillId, @PathVariable Long agentId,
			@RequestParam(value = "enabled", defaultValue = "true") Boolean enabled) {
		return ApiResponse.success("绑定成功", skillService.bind(agentId, skillId, enabled));
	}

	/** 解除智能体技能绑定。 */
	@DeleteMapping("/{skillId}/agents/{agentId}")
	public ApiResponse<Boolean> unbind(@PathVariable Long skillId, @PathVariable Long agentId) {
		return skillService.unbind(agentId, skillId) ? ApiResponse.success("解绑成功", true)
				: ApiResponse.error("绑定不存在", false);
	}

}
