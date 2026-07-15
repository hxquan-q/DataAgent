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

import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.MetricRef;
import com.alibaba.cloud.ai.dataagent.entity.Metric;
import com.alibaba.cloud.ai.dataagent.entity.MetricVersion;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticValidator.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * {@link SemanticValidator} 单测：完整性校验 + 口径歧义反问。
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
class SemanticValidatorTest {

	@Mock
	private SemanticLayerLoader loader;

	@InjectMocks
	private SemanticValidator validator;

	@Test
	void validate_emptyMetrics_needsClarification() {
		assertThat(validator.validate(null).needsClarification()).isTrue();
		SemanticObject so = new SemanticObject();
		assertThat(validator.validate(so).needsClarification()).isTrue();
	}

	@Test
	void validate_unknownMetric_needsClarification() {
		given(loader.getMetric("unknown")).willReturn(null);
		SemanticObject so = soWithMetric("unknown", null);
		ValidationResult r = validator.validate(so);
		assertThat(r.needsClarification()).isTrue();
		assertThat(r.message()).contains("未知指标");
	}

	@Test
	void validate_singleVersion_valid() {
		given(loader.getMetric("order_count")).willReturn(metric(1L, "订单数量"));
		given(loader.getVersions(1L)).willReturn(List.of());
		assertThat(validator.validate(soWithMetric("order_count", null)).valid()).isTrue();
	}

	@Test
	void validate_multiVersionsNoDefaultUnspecified_needsClarification() {
		given(loader.getMetric("amount")).willReturn(metric(2L, "金额"));
		given(loader.getVersions(2L)).willReturn(List.of(version("signed"), version("batch")));
		given(loader.getDefaultVersion(2L)).willReturn(null);
		ValidationResult r = validator.validate(soWithMetric("amount", null));
		assertThat(r.needsClarification()).isTrue();
		assertThat(r.message()).contains("多种口径", "signed", "batch");
	}

	@Test
	void validate_multiVersionsHasDefaultUnspecified_valid() {
		given(loader.getMetric("amount")).willReturn(metric(2L, "金额"));
		given(loader.getVersions(2L)).willReturn(List.of(version("signed"), version("batch")));
		given(loader.getDefaultVersion(2L)).willReturn(version("signed"));
		assertThat(validator.validate(soWithMetric("amount", null)).valid()).isTrue();
	}

	@Test
	void validate_specifiedVersionExists_valid() {
		given(loader.getMetric("amount")).willReturn(metric(2L, "金额"));
		given(loader.getVersions(2L)).willReturn(List.of(version("signed"), version("batch")));
		assertThat(validator.validate(soWithMetric("amount", "batch")).valid()).isTrue();
	}

	@Test
	void validate_specifiedVersionNotExists_needsClarification() {
		given(loader.getMetric("amount")).willReturn(metric(2L, "金额"));
		given(loader.getVersions(2L)).willReturn(List.of(version("signed")));
		ValidationResult r = validator.validate(soWithMetric("amount", "nonexistent"));
		assertThat(r.needsClarification()).isTrue();
		assertThat(r.message()).contains("无口径版本");
	}

	private SemanticObject soWithMetric(String code, String ver) {
		MetricRef ref = new MetricRef();
		ref.setMetricCode(code);
		ref.setVerCode(ver);
		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(ref));
		return so;
	}

	private Metric metric(Long id, String name) {
		return Metric.builder().id(id).metricCode(name).metricName(name).build();
	}

	private MetricVersion version(String code) {
		return MetricVersion.builder().verCode(code).build();
	}

}
