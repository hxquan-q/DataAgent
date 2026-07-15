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
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.DimensionRef;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.FilterRef;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.MetricRef;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.TimeRange;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.entity.Metric;
import com.alibaba.cloud.ai.dataagent.entity.MetricVersion;
import com.alibaba.cloud.ai.dataagent.service.semantic.BuildSQLEngine.BuildResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * {@link BuildSQLEngine} 单测：验证受控拼装（参数化、维度、时间、过滤、有界 LIMIT）。
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
class BuildSQLEngineTest {

	@Mock
	private SemanticLayerLoader loader;

	@InjectMocks
	private BuildSQLEngine engine;

	private Metric orderAmount;

	@BeforeEach
	void setUp() {
		orderAmount = Metric.builder()
			.id(1L)
			.metricCode("order_amount")
			.metricName("订单金额")
			.sourceTable("orders")
			.aggField("total_amount")
			.aggFunc("SUM")
			.defaultTimeField("order_date")
			.build();
		// 用真实 ObjectMapper（version filter_condition JSON 解析），覆盖 @InjectMocks 的 null 注入
		engine = new BuildSQLEngine(loader, new com.fasterxml.jackson.databind.ObjectMapper());
	}

	@Test
	void build_simpleAggregateWithoutDimsOrFilters() {
		given(loader.getMetric("order_amount")).willReturn(orderAmount);
		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef("order_amount", null)));

		BuildResult result = engine.build(so);

		assertThat(result.sql()).isEqualTo("SELECT SUM(total_amount) AS order_amount FROM orders LIMIT ?");
		assertThat(result.params()).containsExactly(1000);
	}

	@Test
	void build_withDimensionTimeRangeAndFilter() {
		given(loader.getMetric("order_amount")).willReturn(orderAmount);
		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef("order_amount", "signed")));
		so.setDimensions(List.of(dim("orders", "status")));
		so.setTimeRange(timeRange("order_date", "2026-06-01", "2026-06-30"));
		so.setFilters(List.of(filter("status", "=", "completed")));
		so.setLimit(100);

		BuildResult result = engine.build(so);

		assertThat(result.sql()).isEqualTo(
				"SELECT status, SUM(total_amount) AS order_amount FROM orders WHERE order_date BETWEEN ? AND ? AND status = ? GROUP BY status LIMIT ?");
		// 参数顺序：start, end, filterValue, limit
		assertThat(result.params()).containsExactly("2026-06-01", "2026-06-30", "completed", 100);
	}

	@Test
	void build_defaultLimitWhenNullOrZero() {
		given(loader.getMetric("order_amount")).willReturn(orderAmount);
		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef("order_amount", null)));
		so.setLimit(null);

		BuildResult result = engine.build(so);
		assertThat(result.params()).containsExactly(1000);
	}

	@Test
	void build_throwsWhenMetricMissing() {
		SemanticObject so = new SemanticObject();
		assertThatThrownBy(() -> engine.build(so)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void build_throwsWhenMetricCodeUnknown() {
		given(loader.getMetric("unknown")).willReturn(null);
		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef("unknown", null)));
		assertThatThrownBy(() -> engine.build(so)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("未知指标");
	}

	@Test
	void build_appliesVersionFilterCondition() {
		// 口径版本 completed 的 filter_condition {"status":"completed"} 应注入 WHERE
		given(loader.getMetric("order_amount")).willReturn(orderAmount);
		MetricVersion completed = MetricVersion.builder()
			.id(11L)
			.metricId(1L)
			.verCode("completed")
			.timeField("order_date")
			.filterCondition("{\"status\":\"completed\"}")
			.build();
		given(loader.getVersions(1L)).willReturn(List.of(completed));
		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef("order_amount", "completed")));

		BuildResult result = engine.build(so);

		assertThat(result.sql()).contains("status = ?");
		assertThat(result.params()).contains("completed");
	}

	@Test
	void build_crossTableDimensionInjectsDirectJoin() {
		// given：指标在 orders，维度在 users（直连 1 跳），传入逻辑关系后应拼 JOIN
		given(loader.getMetric("order_amount")).willReturn(orderAmount);
		List<LogicalRelation> relations = List.of(logicalRelation("orders", "user_id", "users", "id"));
		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef("order_amount", null)));
		so.setDimensions(List.of(dim("users", "region")));

		BuildResult result = engine.build(so, relations);

		// then：FROM orders JOIN users ON orders.user_id = users.id，维度列带表前缀
		assertThat(result.sql()).isEqualTo("SELECT users.region, SUM(total_amount) AS order_amount FROM orders"
				+ " JOIN users ON orders.user_id = users.id GROUP BY users.region LIMIT ?");
		assertThat(result.params()).containsExactly(1000);
	}

	@Test
	void build_crossTableDimensionInjectsBridgeJoin() {
		// given：指标在 orders，维度在 regions，需经 users 桥接（2 跳）
		given(loader.getMetric("order_amount")).willReturn(orderAmount);
		List<LogicalRelation> relations = List.of(logicalRelation("orders", "user_id", "users", "id"),
				logicalRelation("users", "region_id", "regions", "id"));
		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef("order_amount", null)));
		so.setDimensions(List.of(dim("regions", "region_name")));

		BuildResult result = engine.build(so, relations);

		// then：JOIN users ON orders.user_id=users.id JOIN regions ON
		// users.region_id=regions.id
		assertThat(result.sql()).contains("FROM orders JOIN users ON orders.user_id = users.id")
			.contains("JOIN regions ON users.region_id = regions.id");
		assertThat(result.sql()).contains("SELECT regions.region_name");
	}

	@Test
	void build_crossTableDimensionWithoutRelationSkipsJoin() {
		// given：跨表维度但无逻辑关系 → 回退单表，不拼 JOIN
		given(loader.getMetric("order_amount")).willReturn(orderAmount);
		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef("order_amount", null)));
		so.setDimensions(List.of(dim("regions", "region_name")));

		BuildResult result = engine.build(so, List.of());

		// then：无 JOIN 子句，单表 FROM orders
		assertThat(result.sql()).doesNotContain("JOIN");
		assertThat(result.sql()).contains("FROM orders");
	}

	@Test
	void build_sameTableDimensionHasNoJoin() {
		// given：维度与指标同表（orders），不应产生 JOIN
		given(loader.getMetric("order_amount")).willReturn(orderAmount);
		List<LogicalRelation> relations = List.of(logicalRelation("orders", "user_id", "users", "id"));
		SemanticObject so = new SemanticObject();
		so.setMetrics(List.of(metricRef("order_amount", null)));
		so.setDimensions(List.of(dim("orders", "status")));

		BuildResult result = engine.build(so, relations);

		assertThat(result.sql()).doesNotContain("JOIN");
		assertThat(result.sql()).contains("SELECT status, SUM(total_amount)");
	}

	private LogicalRelation logicalRelation(String sourceTable, String sourceColumn, String targetTable,
			String targetColumn) {
		return LogicalRelation.builder()
			.sourceTableName(sourceTable)
			.sourceColumnName(sourceColumn)
			.targetTableName(targetTable)
			.targetColumnName(targetColumn)
			.build();
	}

	private MetricRef metricRef(String code, String ver) {
		MetricRef ref = new MetricRef();
		ref.setMetricCode(code);
		ref.setVerCode(ver);
		return ref;
	}

	private DimensionRef dim(String table, String column) {
		DimensionRef d = new DimensionRef();
		d.setTable(table);
		d.setColumn(column);
		return d;
	}

	private TimeRange timeRange(String field, String start, String end) {
		TimeRange tr = new TimeRange();
		tr.setField(field);
		tr.setStart(start);
		tr.setEnd(end);
		return tr;
	}

	private FilterRef filter(String column, String op, String value) {
		FilterRef f = new FilterRef();
		f.setColumn(column);
		f.setOp(op);
		f.setValue(value);
		return f;
	}

}
