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
package com.alibaba.cloud.ai.dataagent.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import com.alibaba.cloud.ai.dataagent.entity.Metric;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticLayerLoader;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * v0.2 Golden Query Set 数据加载验证测试（η₇ 评测基础设施）。
 * <p>
 * 使用 H2 内存库（{@code application-h2.yml} profile 自动加载 {@code data-h2.sql} 种子指标）， 校验
 * {@code golden-queries.json} 评测集数据的结构完整性与指标引用一致性，不执行实际 NL2SQL 链路。
 * <p>
 * 覆盖四类评测用例：
 * <ul>
 * <li>SIMPLE — 标准指标聚合，目标通过率 ≥95%</li>
 * <li>MEDIUM — 维度+过滤，目标通过率 ≥85%</li>
 * <li>AMBIGUOUS — 口径歧义/反问，目标通过率 ≥80%</li>
 * <li>BOUNDARY — 边界/护栏拦截</li>
 * </ul>
 *
 * @see SemanticLayerLoader#allMetrics()
 */
@SpringBootTest
@ActiveProfiles("h2")
class GoldenQueryDataTest {

	private static final String GOLDEN_QUERIES_RESOURCE = "golden-queries.json";

	private static final int MIN_TOTAL_CASES = 12;

	private static final int MIN_CASES_PER_CATEGORY = 3;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SemanticLayerLoader semanticLayerLoader;

	/**
	 * Golden Query 评测用例数据结构。
	 *
	 * @param id 用例唯一标识（如 g001）
	 * @param category 分类枚举字符串：SIMPLE / MEDIUM / AMBIGUOUS / BOUNDARY
	 * @param question 自然语言问题
	 * @param expectedMetricCode 期望命中的指标 code（边界/意图不明用例可为 null）
	 * @param expectedSqlPattern 期望 SQL 中包含的片段（如 SUM(total_amount)、COUNT；可为 null）
	 * @param expectedNeedsClarification 是否期望反问澄清
	 * @param description 用例描述
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record GoldenQuery(String id, String category, String question, String expectedMetricCode,
			String expectedSqlPattern, Boolean expectedNeedsClarification, String description) {
	}

	@Test
	void goldenQueriesLoadAndValidateAgainstSemanticLayer() throws IOException {
		List<GoldenQuery> queries = loadGoldenQueries();

		// 1) 数据可解析且总数达标（≥12）
		assertThat(queries).as("golden-queries.json 应可解析且用例数 >= %d", MIN_TOTAL_CASES)
			.hasSizeGreaterThanOrEqualTo(MIN_TOTAL_CASES);

		// 2) 每类 category 至少 3 条
		Map<String, Long> categoryCounts = queries.stream()
			.collect(Collectors.groupingBy(GoldenQuery::category, Collectors.counting()));
		Set<String> expectedCategories = Set.of("SIMPLE", "MEDIUM", "AMBIGUOUS", "BOUNDARY");
		assertThat(categoryCounts.keySet()).as("应覆盖全部四类 category").containsAll(expectedCategories);
		expectedCategories.forEach(category -> assertThat(categoryCounts.get(category))
			.as("category [%s] 用例数应 >= %d", category, MIN_CASES_PER_CATEGORY)
			.isGreaterThanOrEqualTo(MIN_CASES_PER_CATEGORY));

		// 3) expectedMetricCode 非空的用例，其 code 必须在 SemanticLayerLoader.allMetrics() 的
		// metricCode 集合中
		Set<String> registeredMetricCodes = semanticLayerLoader.allMetrics()
			.stream()
			.map(Metric::getMetricCode)
			.collect(Collectors.toSet());
		assertThat(registeredMetricCodes).as("H2 种子数据应加载至少一个指标").isNotEmpty();
		List<GoldenQuery> withMetricCode = queries.stream().filter(q -> q.expectedMetricCode() != null).toList();
		assertThat(withMetricCode).as("应存在至少一条带 expectedMetricCode 的用例").isNotEmpty();
		withMetricCode.forEach(q -> assertThat(registeredMetricCodes)
			.as("用例 [%s] 的 expectedMetricCode [%s] 未在语义层注册", q.id(), q.expectedMetricCode())
			.contains(q.expectedMetricCode()));

		// 4) expectedSqlPattern 非空的用例，其值不应为空白
		List<GoldenQuery> withSqlPattern = queries.stream().filter(q -> q.expectedSqlPattern() != null).toList();
		assertThat(withSqlPattern).as("应存在至少一条带 expectedSqlPattern 的用例").isNotEmpty();
		withSqlPattern.forEach(
				q -> assertThat(q.expectedSqlPattern()).as("用例 [%s] 的 expectedSqlPattern 不应为空白", q.id()).isNotBlank());

		// 打印分布摘要（CI 日志可见）
		System.out.printf("[GoldenQuery] 总用例=%d, 分布=%s, 已注册指标=%s%n", queries.size(), categoryCounts,
				registeredMetricCodes);
	}

	private List<GoldenQuery> loadGoldenQueries() throws IOException {
		try (InputStream in = new ClassPathResource(GOLDEN_QUERIES_RESOURCE).getInputStream()) {
			return objectMapper.readValue(in,
					objectMapper.getTypeFactory().constructCollectionType(List.class, GoldenQuery.class));
		}
	}

}
