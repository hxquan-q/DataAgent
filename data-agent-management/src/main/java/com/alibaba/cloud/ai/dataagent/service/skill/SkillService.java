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
import com.alibaba.cloud.ai.dataagent.vo.PageResult;
import java.util.List;

/** 技能管理服务。 */
public interface SkillService {

	/** 分页查询技能。 */
	PageResult<Skill> page(String scope, String keyword, int pageNum, int pageSize);

	/** 按主键查询技能。 */
	Skill getById(Long id);

	/** 创建技能。 */
	Skill create(Skill skill);

	/** 更新技能。 */
	Skill update(Long id, Skill skill);

	/** 删除技能及其绑定。 */
	boolean delete(Long id);

	/** 查询智能体绑定的技能。 */
	List<Skill> listByAgentId(Long agentId);

	/** 查询智能体指定作用域下启用的技能。 */
	List<Skill> listEnabled(String scope, Long agentId);

	/** 新增或更新智能体技能绑定。 */
	AgentSkill bind(Long agentId, Long skillId, Boolean enabled);

	/** 解除智能体技能绑定。 */
	boolean unbind(Long agentId, Long skillId);

}
