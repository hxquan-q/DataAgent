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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * {@link SemanticLayerLoader} 单元测试：验证内存加载、候选过滤、口径版本与别名消歧。
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
class SemanticLayerLoaderTest {

	@Mock
	private MetricMapper metricMapper;

	@Mock
	private MetricVersionMapper metricVersionMapper;

	@Mock
	private SemanticAliasMapper semanticAliasMapper;

	@InjectMocks
	private SemanticLayerLoader loader;

	private Metric orderAmount;

	private Metric disabledMetric;

	@BeforeEach
	void setUp() {
		orderAmount = Metric.builder()
			.id(1L)
			.metricCode("order_amount")
			.metricName("订单金额")
			.agentId(1)
			.datasourceId(10)
			.status(1)
			.build();
		// 不同 agent 的指标，应被候选过滤排除
		Metric otherAgent = Metric.builder().id(2L).metricCode("other").agentId(2).datasourceId(10).status(1).build();
		// 同 agent 但停用，应被排除
		disabledMetric = Metric.builder().id(3L).metricCode("disabled").agentId(1).datasourceId(10).status(0).build();
		given(metricMapper.selectAll()).willReturn(List.of(orderAmount, otherAgent, disabledMetric));
		given(metricVersionMapper.selectByMetricId(1L)).willReturn(List.of(defaultVersion(), nonDefaultVersion()));
		given(metricVersionMapper.selectByMetricId(2L)).willReturn(List.of());
		given(metricVersionMapper.selectByMetricId(3L)).willReturn(List.of());
		loader.refreshAll();
	}

	@Test
	void getMetric_shouldReturnByCode() {
		assertThat(loader.getMetric("order_amount")).isEqualTo(orderAmount);
		assertThat(loader.getMetric("not_exist")).isNull();
	}

	@Test
	void getCandidateMetrics_shouldFilterByAgentDatasourceAndStatus() {
		List<Metric> candidates = loader.getCandidateMetrics(1, 10);
		// 仅 orderAmount 命中（otherAgent agent≠1，disabled status=0）
		assertThat(candidates).containsExactly(orderAmount);
		assertThat(candidates).doesNotContain(disabledMetric);
	}

	@Test
	void getCandidateMetrics_shouldReturnEmptyWhenNoMatch() {
		assertThat(loader.getCandidateMetrics(999, 999)).isEmpty();
	}

	@Test
	void getDefaultVersion_shouldReturnMarkedDefault() {
		MetricVersion def = loader.getDefaultVersion(1L);
		assertThat(def).isNotNull();
		assertThat(def.getIsDefault()).isEqualTo(1);
	}

	@Test
	void resolveAlias_shouldReturnHighestPriorityMatch() {
		SemanticAlias high = SemanticAlias.builder().targetCode("order_amount").priority(10).build();
		SemanticAlias low = SemanticAlias.builder().targetCode("other").priority(1).build();
		// selectByAgentAndText 按 priority DESC 返回，高位在前
		given(semanticAliasMapper.selectByAgentAndText(1, "金额")).willReturn(List.of(high, low));
		assertThat(loader.resolveAlias(1, "金额")).isEqualTo(high);
	}

	@Test
	void resolveAlias_shouldReturnNullWhenNoMatch() {
		given(semanticAliasMapper.selectByAgentAndText(1, "未知")).willReturn(List.of());
		assertThat(loader.resolveAlias(1, "未知")).isNull();
	}

	@Test
	void clearCache_shouldReload() {
		// 清空后重新加载，指标仍在
		loader.clearCache();
		assertThat(loader.getMetric("order_amount")).isEqualTo(orderAmount);
	}

	private MetricVersion defaultVersion() {
		return MetricVersion.builder().id(11L).metricId(1L).verCode("signed").isDefault(1).status(1).build();
	}

	private MetricVersion nonDefaultVersion() {
		return MetricVersion.builder().id(12L).metricId(1L).verCode("batch").isDefault(0).status(1).build();
	}

}
