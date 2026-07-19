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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.DimensionRef;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.FilterRef;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.MetricRef;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.TimeRange;
import com.alibaba.cloud.ai.dataagent.entity.Metric;
import com.alibaba.cloud.ai.dataagent.entity.MetricVersion;
import com.alibaba.cloud.ai.dataagent.entity.SqlExample;
import com.alibaba.cloud.ai.dataagent.mapper.MetricMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SqlExampleMapper;
import com.alibaba.cloud.ai.dataagent.service.semantic.BuildSQLEngine.BuildResult;

/**
 * v0.2 语义层（NL2Semantic2SQL）端到端集成测试。
 * <p>
 * 使用 H2 内存库（{@code application-h2.yml} profile 自动加载 {@code schema-h2.sql} 建表 +
 * {@code data-h2.sql} 种子数据），不依赖外部 MySQL，也不使用 Mockito。覆盖：
 * <ul>
 * <li>{@link SemanticLayerLoader} 启动加载 + 候选过滤 + 口径版本 + 默认口径</li>
 * <li>{@link BuildSQLEngine} 受控拼装参数化 SQL（指标/维度/时间/过滤/LIMIT）</li>
 * <li>{@link SqlExampleService} 反哺去重（SHA-256 指纹）</li>
 * <li>Mapper 层真实 DB 读写</li>
 * </ul>
 * 种子数据：agent_id=2（销售数据分析智能体）、datasource_id=3（h2 product_db）、6 条指标、 order_amount
 * 双口径（completed 默认 / all）。
 * </p>
 *
 * @author dataagent
 */
@SpringBootTest
@ActiveProfiles("h2")
class SemanticLayerIntegrationTest {

	private static final Integer AGENT_ID = 2;

	private static final Integer DATASOURCE_ID = 3;

	@Autowired
	private SemanticLayerLoader semanticLayerLoader;

	@Autowired
	private BuildSQLEngine buildSQLEngine;

	@Autowired
	private SqlExampleService sqlExampleService;

	@Autowired
	private MetricMapper metricMapper;

	@Autowired
	private SqlExampleMapper sqlExampleMapper;

	@Test
	void loaderLoadsAllMetricsFromSeedData() {
		List<Metric> all = semanticLayerLoader.allMetrics();
		assertThat(all).isNotEmpty();
		// 种子至少含
		// order_count/order_amount/avg_order_amount/item_quantity/product_count/user_count
		assertThat(all).extracting(Metric::getMetricCode)
			.contains("order_count", "order_amount", "avg_order_amount", "item_quantity", "product_count",
					"user_count");
	}

	@Test
	void candidateMetricsFilteredByAgentAndDatasource() {
		List<Metric> candidates = semanticLayerLoader.getCandidateMetrics(AGENT_ID, DATASOURCE_ID);
		assertThat(candidates).hasSize(6);
		assertThat(candidates).allSatisfy(m -> {
			assertThat(m.getAgentId()).isEqualTo(AGENT_ID);
			assertThat(m.getDatasourceId()).isEqualTo(DATASOURCE_ID);
			assertThat(m.getStatus()).isEqualTo(1);
		});
		assertThat(candidates).extracting(Metric::getMetricCode)
			.containsExactlyInAnyOrder("order_count", "order_amount", "avg_order_amount", "item_quantity",
					"product_count", "user_count");
	}

	@Test
	void candidateMetricsEmptyForUnknownAgent() {
		// agent_id=999 无种子数据
		assertThat(semanticLayerLoader.getCandidateMetrics(999, DATASOURCE_ID)).isEmpty();
	}

	@Test
	void getMetricReturnsOrderAmountConfiguredAgainstOrdersTable() {
		Metric metric = semanticLayerLoader.getMetric("order_amount");
		assertThat(metric).isNotNull();
		assertThat(metric.getSourceTable()).isEqualTo("orders");
		assertThat(metric.getAggFunc()).isEqualTo("SUM");
		assertThat(metric.getAggField()).isEqualTo("total_amount");
	}

	@Test
	void versionsForOrderAmountContainCompletedAndAll() {
		Metric metric = semanticLayerLoader.getMetric("order_amount");
		assertThat(metric).isNotNull();
		List<MetricVersion> versions = semanticLayerLoader.getVersions(metric.getId());
		assertThat(versions).hasSize(2);
		assertThat(versions).extracting(MetricVersion::getVerCode).containsExactlyInAnyOrder("completed", "all");
	}

	@Test
	void defaultVersionIsCompleted() {
		Metric metric = semanticLayerLoader.getMetric("order_amount");
		assertThat(metric).isNotNull();
		MetricVersion defaultVersion = semanticLayerLoader.getDefaultVersion(metric.getId());
		assertThat(defaultVersion).isNotNull();
		assertThat(defaultVersion.getVerCode()).isEqualTo("completed");
		assertThat(defaultVersion.getIsDefault()).isEqualTo(1);
	}

	@Test
	void resolveAliasMapsToCorrectMetric() {
		// 种子：alias_text='销售额' → target_code='order_amount'
		assertThat(semanticLayerLoader.resolveAlias(AGENT_ID, "销售额")).isNotNull()
			.extracting(a -> a.getTargetCode())
			.isEqualTo("order_amount");
		assertThat(semanticLayerLoader.resolveAlias(AGENT_ID, "销量")).isNotNull()
			.extracting(a -> a.getTargetCode())
			.isEqualTo("item_quantity");
		// 未命中
		assertThat(semanticLayerLoader.resolveAlias(AGENT_ID, "不存在的说法")).isNull();
	}

	@Test
	void buildSqlProducesParameterizedSelectWithAggDimAndGroupBy() {
		MetricRef metricRef = new MetricRef();
		metricRef.setMetricCode("order_amount");
		DimensionRef dim = new DimensionRef();
		dim.setColumn("status");
		TimeRange timeRange = new TimeRange();
		timeRange.setField("order_date");
		timeRange.setStart("2026-06-01");
		timeRange.setEnd("2026-06-30");

		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef));
		so.setDimensions(List.of(dim));
		so.setTimeRange(timeRange);

		BuildResult result = buildSQLEngine.build(so);

		String sql = result.sql();
		assertThat(sql).contains("SUM(total_amount)");
		assertThat(sql).contains("AS order_amount");
		assertThat(sql).contains("FROM orders");
		assertThat(sql).contains("status, "); // 维度列在聚合列之前
		assertThat(sql).contains("GROUP BY status");
		assertThat(sql).contains("order_date BETWEEN ? AND ?");
		assertThat(sql).endsWith(" LIMIT ?");

		// 参数顺序：start, end, limit
		assertThat(result.params()).containsExactly("2026-06-01", "2026-06-30", 1000);
	}

	@Test
	void buildSqlAppliesFiltersAsParameters() {
		MetricRef metricRef = new MetricRef();
		metricRef.setMetricCode("order_count");
		FilterRef filter = new FilterRef();
		filter.setColumn("status");
		filter.setOp("=");
		filter.setValue("completed");

		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef));
		so.setFilters(List.of(filter));

		BuildResult result = buildSQLEngine.build(so);

		assertThat(result.sql()).contains("COUNT(id)");
		assertThat(result.sql()).contains("WHERE status = ?");
		// 参数顺序：filter 值, limit
		assertThat(result.params()).containsExactly("completed", 1000);
	}

	@Test
	void buildSqlRespectsExplicitLimit() {
		MetricRef metricRef = new MetricRef();
		metricRef.setMetricCode("user_count");

		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef));
		so.setLimit(50);

		BuildResult result = buildSQLEngine.build(so);

		assertThat(result.params()).last().isEqualTo(50);
	}

	@Test
	void buildSqlThrowsOnMissingMetric() {
		SemanticObject empty = new SemanticObject();
		empty.setMetrics(List.of());
		assertThatThrownBy(() -> buildSQLEngine.build(empty)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("缺少指标");
	}

	@Test
	void buildSqlThrowsOnUnknownMetricCode() {
		MetricRef metricRef = new MetricRef();
		metricRef.setMetricCode("nonexistent_metric");
		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef));
		assertThatThrownBy(() -> buildSQLEngine.build(so)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("未知指标编码");
	}

	@Test
	void sqlExampleIngestDedupedByHash() {
		String question = "按状态统计 6 月订单金额";
		String sql = "SELECT status, SUM(total_amount) AS order_amount FROM orders "
				+ "WHERE order_date BETWEEN '2026-06-01' AND '2026-06-30' GROUP BY status LIMIT 1000";

		// 首次反哺应成功插入
		boolean first = sqlExampleService.ingest(question, sql, AGENT_ID, DATASOURCE_ID, "mysql");
		assertThat(first).isTrue();

		// 通过 hash 能查到（反哺落地）
		String hash = sha256Hex(sql);
		SqlExample persisted = sqlExampleMapper.selectByHash(AGENT_ID, hash);
		assertThat(persisted).isNotNull();
		assertThat(persisted.getQuestion()).isEqualTo(question);
		assertThat(persisted.getSqlText()).isEqualTo(sql);
		assertThat(persisted.getDatasourceId()).isEqualTo(DATASOURCE_ID);
		assertThat(persisted.getSource()).isEqualTo("AUTO");
		assertThat(persisted.getReviewed()).isEqualTo(0);

		// 二次反哺相同 SQL 应跳过（去重）
		boolean second = sqlExampleService.ingest(question, sql, AGENT_ID, DATASOURCE_ID, "mysql");
		assertThat(second).isFalse();
	}

	@Test
	void sqlExampleIngestDedupIgnoresCaseAndWhitespace() {
		String sqlA = "  SELECT SUM(total_amount) FROM orders  ";
		String sqlB = "select sum(total_amount) from orders";
		// 去空白 + 转小写后哈希一致
		assertThat(sha256Hex(sqlA)).isEqualTo(sha256Hex(sqlB));

		assertThat(sqlExampleService.ingest("q1", sqlA, AGENT_ID, DATASOURCE_ID, "h2")).isTrue();
		// 仅大小写/空白差异 → 视为重复跳过
		assertThat(sqlExampleService.ingest("q2", sqlB, AGENT_ID, DATASOURCE_ID, "h2")).isFalse();
	}

	@Test
	void mapperRoundTripInsertAndSelect() {
		Metric inserted = Metric.builder()
			.metricCode("test_metric_" + System.nanoTime())
			.metricName("测试指标")
			.agentId(AGENT_ID)
			.datasourceId(DATASOURCE_ID)
			.sourceTable("orders")
			.aggField("id")
			.aggFunc("COUNT")
			.defaultTimeField("order_date")
			.description("集成测试临时指标")
			.status(1)
			.build();
		metricMapper.insert(inserted);
		assertThat(inserted.getId()).isNotNull();

		Metric fetched = metricMapper.selectByCode(inserted.getMetricCode());
		assertThat(fetched).isNotNull();
		assertThat(fetched.getMetricName()).isEqualTo("测试指标");
		assertThat(fetched.getSourceTable()).isEqualTo("orders");

		// 重载后 Loader 内存可见
		semanticLayerLoader.clearCache();
		assertThat(semanticLayerLoader.getMetric(inserted.getMetricCode())).isNotNull();

		// 清理
		metricMapper.deleteById(inserted.getId());
	}

	/**
	 * 复刻 SqlExampleService 的 SHA-256 归一化算法，断言反哺去重一致性。
	 */
	private static String sha256Hex(String sql) {
		String normalized = sql == null ? "" : sql.strip().toLowerCase();
		try {
			java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
