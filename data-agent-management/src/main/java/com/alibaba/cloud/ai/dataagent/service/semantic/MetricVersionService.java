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

import com.alibaba.cloud.ai.dataagent.entity.MetricVersion;
import com.alibaba.cloud.ai.dataagent.mapper.MetricVersionMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 指标口径版本服务（NL2Semantic2SQL 指标层）。
 * <p>
 * 负责指标口径版本 CRUD。写操作（新增/修改/删除）完成后会调用 {@link SemanticLayerLoader#clearCache()} 刷新内存缓存，保证
 * {@code SemanticParse} 节点能立即看到最新口径定义。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
@Service
@AllArgsConstructor
public class MetricVersionService {

	private final MetricVersionMapper metricVersionMapper;

	private final SemanticLayerLoader semanticLayerLoader;

	/**
	 * 按指标ID查询全部启用口径版本。
	 * @param metricId 指标ID
	 * @return 口径版本列表
	 */
	public List<MetricVersion> listByMetricId(Long metricId) {
		return metricVersionMapper.selectByMetricId(metricId);
	}

	/**
	 * 按主键ID查口径版本。
	 * @param id 主键ID
	 * @return 口径版本；不存在返回 {@code null}
	 */
	public MetricVersion getById(Long id) {
		return metricVersionMapper.selectById(id);
	}

	/**
	 * 新建口径版本。入库后刷新内存缓存。
	 * @param version 口径版本（不带主键）
	 * @return 新建主键ID
	 */
	public Long create(MetricVersion version) {
		metricVersionMapper.insert(version);
		semanticLayerLoader.clearCache();
		return version.getId();
	}

	/**
	 * 更新口径版本。更新后刷新内存缓存。
	 * @param version 口径版本（必须带主键）
	 */
	public void update(MetricVersion version) {
		metricVersionMapper.updateById(version);
		semanticLayerLoader.clearCache();
	}

	/**
	 * 按主键ID删除口径版本。删除后刷新内存缓存。
	 * @param id 主键ID
	 */
	public void delete(Long id) {
		metricVersionMapper.deleteById(id);
		semanticLayerLoader.clearCache();
	}

}
