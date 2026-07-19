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
import com.alibaba.cloud.ai.dataagent.entity.MetricVersion;
import com.alibaba.cloud.ai.dataagent.entity.SemanticAlias;
import com.alibaba.cloud.ai.dataagent.mapper.MetricMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MetricVersionMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticAliasMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 语义层内存加载器（NL2Semantic2SQL 语义治理层）。
 * <p>
 * 启动时全量加载 metric + metric_version 到内存，按 agent+datasource 内存过滤候选指标（η₂ 最少自由度： 收敛 LLM
 * 选择空间）。别名按需查 DB（频率低）。clearCache 触发重载（管理端 CRUD 后调用）。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
@Component
public class SemanticLayerLoader {

	private final MetricMapper metricMapper;

	private final MetricVersionMapper metricVersionMapper;

	private final SemanticAliasMapper semanticAliasMapper;

	/** 指标编码 → 指标定义 */
	private final Map<String, Metric> metricByCode = new ConcurrentHashMap<>();

	/** 指标ID → 口径版本列表 */
	private final Map<Long, List<MetricVersion>> versionsByMetricId = new ConcurrentHashMap<>();

	public SemanticLayerLoader(MetricMapper metricMapper, MetricVersionMapper metricVersionMapper,
			SemanticAliasMapper semanticAliasMapper) {
		this.metricMapper = metricMapper;
		this.metricVersionMapper = metricVersionMapper;
		this.semanticAliasMapper = semanticAliasMapper;
	}

	/**
	 * 启动时全量加载语义层到内存。
	 */
	@PostConstruct
	public void init() {
		refreshAll();
	}

	/**
	 * 全量重载语义层缓存（管理端 CRUD 后调用，热更新）。
	 */
	public synchronized void refreshAll() {
		metricByCode.clear();
		versionsByMetricId.clear();
		List<Metric> metrics = metricMapper.selectAll();
		for (Metric metric : metrics) {
			metricByCode.put(metric.getMetricCode(), metric);
			versionsByMetricId.put(metric.getId(), metricVersionMapper.selectByMetricId(metric.getId()));
		}
		log.info("语义层加载完成: {} 指标", metricByCode.size());
	}

	/**
	 * 定时延迟刷新语义层缓存（架构设计：内存版本延迟 60s 刷新的演化形态，用 fixedDelay 周期兜底）。
	 * <p>
	 * 默认每 {@code 300000}ms（5 分钟）刷新一次，间隔可通过
	 * {@code spring.ai.alibaba.data-agent.semantic-layer.refresh-interval} 配置覆盖。 与
	 * {@link #refreshAll()} 共用 {@code synchronized} 保证并发安全；单次失败仅记日志不中断调度。
	 * </p>
	 * <p>
	 * 依赖 {@code DataAgentApplication} 上的 {@code @EnableScheduling}（已启用）。
	 * </p>
	 */
	@Scheduled(fixedDelayString = "${spring.ai.alibaba.data-agent.semantic-layer.refresh-interval:300000}")
	public void scheduledRefresh() {
		try {
			refreshAll();
		}
		catch (Exception e) {
			log.error("语义层定时刷新失败，等待下一周期重试", e);
		}
	}

	/**
	 * 清空缓存（等价于下次访问前重载，兼容 PromptLoader.clearCache 命名）。
	 */
	public void clearCache() {
		refreshAll();
	}

	/**
	 * 按指标编码查定义。
	 * @param metricCode 指标编码
	 * @return 指标定义；不存在返回 {@code null}
	 */
	public Metric getMetric(String metricCode) {
		return metricByCode.get(metricCode);
	}

	/**
	 * 按指标ID查口径版本列表。
	 * @param metricId 指标ID
	 * @return 口径版本列表；无则空列表
	 */
	public List<MetricVersion> getVersions(Long metricId) {
		return versionsByMetricId.getOrDefault(metricId, Collections.emptyList());
	}

	/**
	 * 取指标的默认口径版本（SemanticParse 无显式口径时用）。
	 * @param metricId 指标ID
	 * @return 默认口径；无则 {@code null}
	 */
	public MetricVersion getDefaultVersion(Long metricId) {
		return getVersions(metricId).stream()
			.filter(v -> v.getIsDefault() != null && v.getIsDefault() == 1)
			.findFirst()
			.orElse(null);
	}

	/**
	 * 按 agent+datasource 内存过滤候选指标（SemanticContextFilter 用，仅启用项）。
	 * @param agentId 智能体ID
	 * @param datasourceId 数据源ID
	 * @return 候选指标列表
	 */
	public List<Metric> getCandidateMetrics(Integer agentId, Integer datasourceId) {
		return metricByCode.values()
			.stream()
			.filter(m -> agentId.equals(m.getAgentId()) && datasourceId.equals(m.getDatasourceId())
					&& m.getStatus() != null && m.getStatus() == 1)
			.collect(Collectors.toList());
	}

	/**
	 * 别名消歧：按优先级返回最佳匹配（DB 查询，selectByAgentAndText 已按 priority DESC）。
	 * @param agentId 智能体ID
	 * @param aliasText 业务黑话/用户说法
	 * @return 最佳匹配别名；无匹配返回 {@code null}
	 */
	public SemanticAlias resolveAlias(Integer agentId, String aliasText) {
		List<SemanticAlias> matches = semanticAliasMapper.selectByAgentAndText(agentId, aliasText);
		return matches.isEmpty() ? null : matches.get(0);
	}

	/**
	 * 返回全部已加载指标（只读快照，供调试/管理端）。
	 * @return 指标列表
	 */
	public List<Metric> allMetrics() {
		return new ArrayList<>(metricByCode.values());
	}

}
