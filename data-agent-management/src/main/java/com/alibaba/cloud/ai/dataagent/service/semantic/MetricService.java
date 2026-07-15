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

import com.alibaba.cloud.ai.dataagent.entity.Metric;
import com.alibaba.cloud.ai.dataagent.mapper.MetricMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 指标定义服务（NL2Semantic2SQL 指标层）。
 * <p>
 * 负责指标 CRUD 与候选过滤。写操作（新增/修改/删除）完成后会调用 {@link SemanticLayerLoader#clearCache()} 刷新内存缓存，保证
 * {@code SemanticParse} 节点能立即看到最新指标定义。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
@Service
@AllArgsConstructor
public class MetricService {

	private final MetricMapper metricMapper;

	private final SemanticLayerLoader semanticLayerLoader;

	/**
	 * 查询全部指标（按创建时间倒序）。
	 * @return 指标列表
	 */
	public List<Metric> list() {
		return metricMapper.selectAll();
	}

	/**
	 * 按主键ID查指标。
	 * @param id 主键ID
	 * @return 指标定义；不存在返回 {@code null}
	 */
	public Metric getById(Long id) {
		return metricMapper.selectById(id);
	}

	/**
	 * 按指标编码查指标。
	 * @param code 指标编码（全局唯一）
	 * @return 指标定义；不存在返回 {@code null}
	 */
	public Metric getByCode(String code) {
		return metricMapper.selectByCode(code);
	}

	/**
	 * 新建指标。
	 * <p>
	 * 未显式指定状态时默认置为 1（启用），保证新建指标能立即进入候选空间。入库后刷新内存缓存。
	 * </p>
	 * @param metric 指标定义（不带主键）
	 * @return 新建主键ID
	 */
	public Long create(Metric metric) {
		if (metric.getStatus() == null) {
			metric.setStatus(1);
		}
		metricMapper.insert(metric);
		semanticLayerLoader.clearCache();
		return metric.getId();
	}

	/**
	 * 更新指标。更新后刷新内存缓存。
	 * @param metric 指标定义（必须带主键）
	 */
	public void update(Metric metric) {
		metricMapper.updateById(metric);
		semanticLayerLoader.clearCache();
	}

	/**
	 * 按主键ID删除指标。删除后刷新内存缓存。
	 * @param id 主键ID
	 */
	public void delete(Long id) {
		metricMapper.deleteById(id);
		semanticLayerLoader.clearCache();
	}

	/**
	 * 按 agent+datasource 过滤候选指标（走内存缓存，仅启用项）。
	 * @param agentId 智能体ID
	 * @param datasourceId 数据源ID
	 * @return 候选指标列表
	 */
	public List<Metric> candidates(Integer agentId, Integer datasourceId) {
		return semanticLayerLoader.getCandidateMetrics(agentId, datasourceId);
	}

}
