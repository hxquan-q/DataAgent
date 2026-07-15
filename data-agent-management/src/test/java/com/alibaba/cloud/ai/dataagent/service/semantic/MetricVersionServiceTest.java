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
 * {@link MetricVersionService} 单元测试。 重点验证：写操作后触发 {@code clearCache}、查询正确委托 mapper。
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
class MetricVersionServiceTest {

	@Mock
	private MetricVersionMapper metricVersionMapper;

	@Mock
	private SemanticLayerLoader semanticLayerLoader;

	@InjectMocks
	private MetricVersionService metricVersionService;

	@Test
	void create_shouldInsertAndClearCache_andReturnId() {
		// given
		MetricVersion version = MetricVersion.builder().metricId(10L).verCode("v_incl_tax").description("含税口径").build();
		given(metricVersionMapper.insert(version)).willAnswer(invocation -> {
			((MetricVersion) invocation.getArgument(0)).setId(200L);
			return 1;
		});

		// when
		Long id = metricVersionService.create(version);

		// then
		assertThat(id).isEqualTo(200L);
		verify(metricVersionMapper).insert(version);
		verify(semanticLayerLoader).clearCache();
	}

	@Test
	void update_shouldCallUpdateByIdAndClearCache() {
		// given
		MetricVersion version = MetricVersion.builder().id(5L).description("不含税口径").build();

		// when
		metricVersionService.update(version);

		// then
		verify(metricVersionMapper).updateById(version);
		verify(semanticLayerLoader).clearCache();
	}

	@Test
	void delete_shouldCallDeleteByIdAndClearCache() {
		// given
		Long id = 9L;

		// when
		metricVersionService.delete(id);

		// then
		verify(metricVersionMapper).deleteById(id);
		verify(semanticLayerLoader).clearCache();
	}

	@Test
	void listByMetricId_shouldDelegateToMapper() {
		// given
		Long metricId = 10L;
		List<MetricVersion> expected = List.of(MetricVersion.builder().id(1L).metricId(metricId).verCode("v1").build());
		given(metricVersionMapper.selectByMetricId(metricId)).willReturn(expected);

		// when
		List<MetricVersion> result = metricVersionService.listByMetricId(metricId);

		// then
		assertThat(result).isSameAs(expected);
		verify(metricVersionMapper).selectByMetricId(metricId);
	}

	@Test
	void getById_shouldDelegateToMapper() {
		// given
		Long id = 7L;
		MetricVersion expected = MetricVersion.builder().id(id).verCode("v1").build();
		given(metricVersionMapper.selectById(id)).willReturn(expected);

		// when
		MetricVersion result = metricVersionService.getById(id);

		// then
		assertThat(result).isSameAs(expected);
		verify(metricVersionMapper).selectById(id);
	}

}
