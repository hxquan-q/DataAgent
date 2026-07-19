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
package com.alibaba.cloud.ai.dataagent.service.semantic;

import com.alibaba.cloud.ai.dataagent.entity.SemanticAlias;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticAliasMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 语义别名服务（NL2Semantic2SQL 语义治理层）。
 * <p>
 * 负责业务黑话→结构化 code 的别名 CRUD 与消歧查询。写操作（新增/修改/删除）完成后会调用
 * {@link SemanticLayerLoader#clearCache()} 刷新内存缓存，保证后续别名解析立即生效。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
@Service
@AllArgsConstructor
public class SemanticAliasService {

	private final SemanticAliasMapper semanticAliasMapper;

	private final SemanticLayerLoader semanticLayerLoader;

	/**
	 * 按智能体ID查询全部启用别名。
	 * @param agentId 智能体ID
	 * @return 别名列表
	 */
	public List<SemanticAlias> listByAgentId(Integer agentId) {
		return semanticAliasMapper.selectByAgentId(agentId);
	}

	/**
	 * 别名消歧：按优先级返回最佳匹配（委托内存加载器）。
	 * @param agentId 智能体ID
	 * @param text 业务黑话/用户说法
	 * @return 最佳匹配别名；无匹配返回 {@code null}
	 */
	public SemanticAlias resolve(Integer agentId, String text) {
		return semanticLayerLoader.resolveAlias(agentId, text);
	}

	/**
	 * 新建别名。入库后刷新内存缓存。
	 * @param alias 别名（不带主键）
	 * @return 新建主键ID
	 */
	public Long create(SemanticAlias alias) {
		semanticAliasMapper.insert(alias);
		semanticLayerLoader.clearCache();
		return alias.getId();
	}

	/**
	 * 更新别名。更新后刷新内存缓存。
	 * @param alias 别名（必须带主键）
	 */
	public void update(SemanticAlias alias) {
		semanticAliasMapper.updateById(alias);
		semanticLayerLoader.clearCache();
	}

	/**
	 * 按主键ID删除别名。删除后刷新内存缓存。
	 * @param id 主键ID
	 */
	public void delete(Long id) {
		semanticAliasMapper.deleteById(id);
		semanticLayerLoader.clearCache();
	}

}
