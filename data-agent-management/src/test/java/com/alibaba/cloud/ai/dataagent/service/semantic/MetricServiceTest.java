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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

/**
 * {@link MetricService} 单元测试。 重点验证：写操作后触发 {@code clearCache}、status 默认值、候选过滤委托。
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
class MetricServiceTest {

	@Mock
	private MetricMapper metricMapper;

	@Mock
	private SemanticLayerLoader semanticLayerLoader;

	@InjectMocks
	private MetricService metricService;

	@Test
	void create_shouldInsertAndClearCache_andDefaultStatusTo1_whenStatusNull() {
		// given：未显式指定 status
		Metric metric = Metric.builder()
			.metricCode("m_gmv")
			.metricName("GMV")
			.agentId(1)
			.datasourceId(2)
			.sourceTable("orders")
			.aggField("amount")
			.aggFunc("SUM")
			.build();
		given(metricMapper.insert(metric)).willReturn(1);
		// 模拟 MyBatis useGeneratedKeys 回填主键
		given(metricMapper.insert(metric)).willAnswer(invocation -> {
			((Metric) invocation.getArgument(0)).setId(100L);
			return 1;
		});

		// when
		Long id = metricService.create(metric);

		// then
		assertThat(id).isEqualTo(100L);
		assertThat(metric.getStatus()).isEqualTo(1); // 默认启用
		verify(metricMapper).insert(metric);
		verify(semanticLayerLoader).clearCache();
	}

	@Test
	void create_shouldKeepExplicitStatus_whenStatusProvided() {
		// given：显式停用
		Metric metric = Metric.builder().metricCode("m_off").status(0).build();
		given(metricMapper.insert(metric)).willAnswer(invocation -> {
			((Metric) invocation.getArgument(0)).setId(7L);
			return 1;
		});

		// when
		Long id = metricService.create(metric);

		// then
		assertThat(id).isEqualTo(7L);
		assertThat(metric.getStatus()).isEqualTo(0); // 不被覆盖
		verify(metricMapper).insert(metric);
		verify(semanticLayerLoader).clearCache();
	}

	@Test
	void update_shouldCallUpdateByIdAndClearCache() {
		// given
		Metric metric = Metric.builder().id(5L).metricName("GMV-新").build();

		// when
		metricService.update(metric);

		// then
		verify(metricMapper).updateById(metric);
		verify(semanticLayerLoader).clearCache();
	}

	@Test
	void delete_shouldCallDeleteByIdAndClearCache() {
		// given
		Long id = 9L;

		// when
		metricService.delete(id);

		// then
		verify(metricMapper).deleteById(id);
		verify(semanticLayerLoader).clearCache();
	}

	@Test
	void candidates_shouldDelegateToLoader() {
		// given
		Integer agentId = 1;
		Integer datasourceId = 2;
		List<Metric> expected = List
			.of(Metric.builder().id(1L).metricCode("m_gmv").agentId(agentId).datasourceId(datasourceId).build());
		given(semanticLayerLoader.getCandidateMetrics(agentId, datasourceId)).willReturn(expected);

		// when
		List<Metric> result = metricService.candidates(agentId, datasourceId);

		// then
		assertThat(result).isSameAs(expected);
		verify(semanticLayerLoader).getCandidateMetrics(agentId, datasourceId);
	}

}
