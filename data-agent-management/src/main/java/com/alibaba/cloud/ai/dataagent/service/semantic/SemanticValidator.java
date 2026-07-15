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
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 语义对象校验器（NL2Semantic2SQL 完整性 + 口径歧义校验，η₂ 第二道防线）。
 * <p>
 * 校验 SemanticParse 产出的 {@link SemanticObject}：指标存在性、口径版本歧义（多口径未指定且无默认 → 触发反问）。不完整/歧义返回
 * {@code needsClarification}，由反问机制（interruptBefore）向用户澄清。
 * </p>
 *
 * @author dataagent
 */
@Component
public class SemanticValidator {

	private final SemanticLayerLoader loader;

	public SemanticValidator(SemanticLayerLoader loader) {
		this.loader = loader;
	}

	/**
	 * 校验语义对象。
	 * @param so 语义对象
	 * @return 校验结果（valid 或 needsClarification + 反问消息）
	 */
	public ValidationResult validate(SemanticObject so) {
		if (so == null || so.getMetrics() == null || so.getMetrics().isEmpty()) {
			return ValidationResult.clarification("未选择任何指标，请说明您想查询的指标（如订单金额、订单数量）");
		}
		MetricRef ref = so.getMetrics().get(0);
		Metric metric = loader.getMetric(ref.getMetricCode());
		if (metric == null) {
			return ValidationResult.clarification("未知指标：" + ref.getMetricCode());
		}
		return validateMetricVersion(metric, ref);
	}

	private ValidationResult validateMetricVersion(Metric metric, MetricRef ref) {
		List<MetricVersion> versions = loader.getVersions(metric.getId());
		String verCode = ref.getVerCode();
		if (verCode == null || verCode.isBlank()) {
			// 未指定口径：多版本且无默认 → 歧义反问
			if (versions.size() > 1 && loader.getDefaultVersion(metric.getId()) == null) {
				return ValidationResult
					.clarification("指标「" + metric.getMetricName() + "」有多种口径，请指定：" + describeVersions(versions));
			}
			return ValidationResult.ok();
		}
		// 指定了口径：校验存在
		boolean exists = versions.stream().anyMatch(v -> verCode.equals(v.getVerCode()));
		if (!exists) {
			return ValidationResult.clarification(
					"指标「" + metric.getMetricName() + "」无口径版本：" + verCode + "，可选：" + describeVersions(versions));
		}
		return ValidationResult.ok();
	}

	private String describeVersions(List<MetricVersion> versions) {
		StringBuilder sb = new StringBuilder();
		for (MetricVersion v : versions) {
			if (sb.length() > 0) {
				sb.append(" / ");
			}
			sb.append(v.getVerCode());
			if (v.getDescription() != null && !v.getDescription().isBlank()) {
				sb.append("(").append(v.getDescription()).append(")");
			}
		}
		return sb.toString();
	}

	/**
	 * 校验结果。
	 *
	 * @param valid 是否通过（可进 BuildSQL）
	 * @param needsClarification 是否需反问
	 * @param message 反问消息（valid=true 时 null）
	 */
	public record ValidationResult(boolean valid, boolean needsClarification, String message) {

		public static ValidationResult ok() {
			return new ValidationResult(true, false, null);
		}

		public static ValidationResult clarification(String message) {
			return new ValidationResult(false, true, message);
		}

	}

}
