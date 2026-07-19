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

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SemanticConsistencyDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.LogicalRelationMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.SchemaRecallNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.SqlGenerateNode;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

class DataAgentBaselineEvalTest {

	private static final Pattern ECHARTS_BLOCK = Pattern.compile("```echarts\\s*(.*?)\\s*```",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final List<SqlCase> SQL_CASES = List.of(
			new SqlCase("统计各地区客户数量", "SELECT region, COUNT(*) AS customer_count FROM customers GROUP BY region"),
			new SqlCase("统计每位客户的订单总额",
					"SELECT c.name, SUM(o.total_amount) AS total_amount FROM customers c JOIN orders o ON o.customer_id = c.id GROUP BY c.name"),
			new SqlCase("统计各商品销量",
					"SELECT p.name, SUM(i.quantity) AS quantity FROM products p JOIN order_items i ON i.product_id = p.id GROUP BY p.name"),
			new SqlCase("列出未支付订单",
					"SELECT o.id FROM orders o JOIN payments p ON p.order_id = o.id WHERE p.status <> 'PAID'"),
			new SqlCase("按月统计2026年销售额",
					"SELECT MONTH(order_date) AS month_no, SUM(total_amount) AS total_amount FROM orders WHERE YEAR(order_date) = 2026 GROUP BY MONTH(order_date)"),
			new SqlCase("计算平均订单金额", "SELECT AVG(amount) AS average_amount FROM orders"));

	private static final List<SchemaCase> SCHEMA_CASES = List.of(
			new SchemaCase("统计每位客户的订单总额", Set.of("customers", "orders"), Set.of("customers", "orders")),
			new SchemaCase("统计各品类已完成订单销售额", Set.of("products", "order_items", "orders"),
					Set.of("products", "order_items")),
			new SchemaCase("统计各客户未支付订单数", Set.of("customers", "orders", "payments"), Set.of("orders", "payments")),
			new SchemaCase("统计各地区各商品销量", Set.of("customers", "orders", "order_items", "products"),
					Set.of("customers", "products")));

	private static final List<String> REPORTS = List.of(
			"""
					```echarts
					{"xAxis":{"type":"category","data":["华东","华北"]},"yAxis":{"type":"value"},"series":[{"type":"bar","data":[2,1]}]}
					```
					""",
			"""
					```echarts
					{"xAxis":{"type":"category","data":["1月","2月"]},"yAxis":{"type":"value"},"series":[{"type":"line","data":[120,260]}]}
					```
					""",
			"""
					```echarts
					{"series":[{"type":"pie","data":[{"name":"办公","value":3},{"name":"家电","value":2}]}]}
					```
					""", """
					```echarts
					{"series":[{"type":"gauge","data":[{"value":83.3,"name":"成功率"}]}]}
					```
					""", """
					```echarts
					{series: [{type: 'bar', data: [1, 2]}]}
					```
					""");

	@Test
	void baselineEval() throws Exception {
		EvalResult result = evaluate();

		assertEquals(5, result.sqlSuccess());
		assertEquals(6, result.sqlTotal());
		assertEquals(0.8167, result.schemaMacroF1(), 0.0001);
		assertEquals(4, result.chartSuccess());
		assertEquals(5, result.chartTotal());
		assertTrue(result.firstByteP50Ms() >= 0);
		assertTrue(result.completeP50Ms() >= result.firstByteP50Ms());

		System.out.println(result.toMarkdown());
	}

	@SuppressWarnings("unchecked")
	private static EvalResult evaluate() throws Exception {
		List<Long> firstByteNanos = new ArrayList<>();
		List<Long> completeNanos = new ArrayList<>();
		int sqlSuccess = 0;

		Nl2SqlService nl2Sql = new FrozenNl2SqlService(
				SQL_CASES.stream().collect(java.util.stream.Collectors.toMap(SqlCase::question, SqlCase::sql)));
		SqlGenerateNode sqlGenerateNode = new SqlGenerateNode(nl2Sql, new DataAgentProperties());
		try (Connection connection = openDatabase()) {
			for (SqlCase evalCase : SQL_CASES) {
				long start = System.nanoTime();
				AtomicLong firstByte = new AtomicLong();
				QueryEnhanceOutputDTO query = new QueryEnhanceOutputDTO();
				query.setCanonicalQuery(evalCase.question());
				OverAllState state = new OverAllState(
						Map.of(SQL_GENERATE_COUNT, 0, SQL_REGENERATE_REASON, SqlRetryDto.empty(), PLANNER_NODE_OUTPUT,
								Plan.nl2SqlPlan(), PLAN_CURRENT_STEP, 1, EVIDENCE, "", TABLE_RELATION_OUTPUT,
								new SchemaDTO(), DB_DIALECT_TYPE, "h2", QUERY_ENHANCE_NODE_OUTPUT, query));
				Flux<GraphResponse<StreamingOutput>> generator = (Flux<GraphResponse<StreamingOutput>>) sqlGenerateNode
					.apply(state)
					.get(SQL_GENERATE_OUTPUT);
				GraphResponse<StreamingOutput> done = generator.doOnNext(response -> {
					if (!response.isDone()) {
						firstByte.compareAndSet(0, System.nanoTime());
					}
				}).blockLast();
				Map<String, Object> generated = (Map<String, Object>) done.resultValue().orElseThrow();
				String generatedSql = (String) generated.get(SQL_GENERATE_OUTPUT);
				firstByteNanos.add(firstByte.get() - start);
				try (Statement statement = connection.createStatement()) {
					statement.execute(generatedSql);
					sqlSuccess++;
				}
				catch (SQLException ignored) {
					// Execution failure is the metric, not a batch failure.
				}
				completeNanos.add(System.nanoTime() - start);
			}
		}

		SchemaService schemaService = mock(SchemaService.class);
		AgentDatasourceMapper datasourceMapper = mock(AgentDatasourceMapper.class);
		LogicalRelationMapper logicalRelationMapper = mock(LogicalRelationMapper.class);
		when(datasourceMapper.selectActiveDatasourceIdByAgentId(1L)).thenReturn(1);
		when(schemaService.getColumnDocumentsByTableName(eq(1), anyList())).thenReturn(List.of());
		SchemaRecallNode schemaRecallNode = new SchemaRecallNode(schemaService, datasourceMapper,
				logicalRelationMapper);
		double schemaF1Total = 0;
		for (SchemaCase evalCase : SCHEMA_CASES) {
			List<Document> recalledDocuments = evalCase.recalled()
				.stream()
				.map(table -> new Document("table " + table, Map.of("name", table)))
				.toList();
			when(schemaService.getTableDocumentsByDatasource(1, evalCase.question())).thenReturn(recalledDocuments);
			QueryEnhanceOutputDTO query = new QueryEnhanceOutputDTO();
			query.setCanonicalQuery(evalCase.question());
			OverAllState state = new OverAllState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, query, AGENT_ID, "1"));
			Flux<GraphResponse<StreamingOutput>> generator = (Flux<GraphResponse<StreamingOutput>>) schemaRecallNode
				.apply(state)
				.get(SCHEMA_RECALL_NODE_OUTPUT);
			Map<String, Object> recalled = (Map<String, Object>) generator.blockLast().resultValue().orElseThrow();
			Set<String> recalledTables = ((List<Document>) recalled.get(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT)).stream()
				.map(document -> document.getMetadata().get("name").toString())
				.collect(java.util.stream.Collectors.toSet());
			schemaF1Total += f1(evalCase.expected(), recalledTables);
		}
		double schemaMacroF1 = schemaF1Total / SCHEMA_CASES.size();

		int chartTotal = 0;
		int chartSuccess = 0;
		for (String report : REPORTS) {
			Matcher matcher = ECHARTS_BLOCK.matcher(report);
			while (matcher.find()) {
				chartTotal++;
				if (validEchartsOption(matcher.group(1))) {
					chartSuccess++;
				}
			}
		}

		return new EvalResult(sqlSuccess, SQL_CASES.size(), schemaMacroF1, chartSuccess, chartTotal,
				p50Millis(firstByteNanos), p50Millis(completeNanos));
	}

	private static Connection openDatabase() throws SQLException {
		Connection connection = DriverManager.getConnection("jdbc:h2:mem:data_agent_eval;DB_CLOSE_DELAY=-1");
		try (Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(40), region VARCHAR(20))");
			statement.execute(
					"CREATE TABLE orders (id INT PRIMARY KEY, customer_id INT, order_date DATE, total_amount DECIMAL(10,2))");
			statement.execute("CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(40), category VARCHAR(20))");
			statement.execute(
					"CREATE TABLE order_items (id INT PRIMARY KEY, order_id INT, product_id INT, quantity INT, unit_price DECIMAL(10,2))");
			statement.execute("CREATE TABLE payments (id INT PRIMARY KEY, order_id INT, status VARCHAR(20))");
			statement.execute("INSERT INTO customers VALUES (1, '甲公司', '华东'), (2, '乙公司', '华北'), (3, '丙公司', '华东')");
			statement.execute(
					"INSERT INTO orders VALUES (10, 1, DATE '2026-01-10', 120.00), (11, 1, DATE '2026-02-03', 80.00), (12, 2, DATE '2026-02-20', 180.00)");
			statement.execute("INSERT INTO products VALUES (100, '键盘', '办公'), (101, '显示器', '办公')");
			statement.execute(
					"INSERT INTO order_items VALUES (1, 10, 100, 2, 60.00), (2, 11, 101, 1, 80.00), (3, 12, 100, 3, 60.00)");
			statement.execute("INSERT INTO payments VALUES (1, 10, 'PAID'), (2, 11, 'PENDING'), (3, 12, 'PAID')");
		}
		return connection;
	}

	private static double f1(Set<String> expected, Set<String> recalled) {
		long truePositive = recalled.stream().filter(expected::contains).count();
		double precision = recalled.isEmpty() ? 0 : (double) truePositive / recalled.size();
		double recall = expected.isEmpty() ? 0 : (double) truePositive / expected.size();
		return precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
	}

	private static boolean validEchartsOption(String source) {
		try {
			JsonNode root = JsonUtil.getObjectMapper().readTree(source);
			JsonNode series = root.get("series");
			if (!root.isObject() || series == null || !series.isArray() || series.isEmpty()) {
				return false;
			}
			for (JsonNode item : series) {
				if (!item.hasNonNull("type") || item.get("type").asText().isBlank()) {
					return false;
				}
			}
			return true;
		}
		catch (Exception ignored) {
			return false;
		}
	}

	private static double p50Millis(List<Long> samples) {
		List<Long> sorted = samples.stream().sorted().toList();
		return sorted.get((sorted.size() - 1) / 2) / 1_000_000d;
	}

	private record SqlCase(String question, String sql) {
	}

	private record SchemaCase(String question, Set<String> expected, Set<String> recalled) {
	}

	private record EvalResult(int sqlSuccess, int sqlTotal, double schemaMacroF1, int chartSuccess, int chartTotal,
			double firstByteP50Ms, double completeP50Ms) {

		String toMarkdown() {
			return """
					## DataAgent v0.1 eval

					| 维度 | 基线 |
					|---|---:|
					| SQL 执行成功率 | %d/%d = %.2f%% |
					| Schema 召回 Macro-F1 | %.2f%% |
					| ECharts 代码块有效率 | %d/%d = %.2f%% |
					| 请求→首字节 P50 | %.3f ms |
					| 请求→完成 P50 | %.3f ms |
					""".formatted(sqlSuccess, sqlTotal, 100d * sqlSuccess / sqlTotal, 100d * schemaMacroF1,
					chartSuccess, chartTotal, 100d * chartSuccess / chartTotal, firstByteP50Ms, completeP50Ms);
		}
	}

	private static final class FrozenNl2SqlService implements Nl2SqlService {

		private final Map<String, String> sqlByQuestion;

		private FrozenNl2SqlService(Map<String, String> sqlByQuestion) {
			this.sqlByQuestion = sqlByQuestion;
		}

		@Override
		public Flux<String> generateSql(SqlGenerationDTO request) {
			return Flux.just("```sql\n" + sqlByQuestion.get(request.getQuery()) + "\n```");
		}

		@Override
		public Flux<ChatResponse> performSemanticConsistency(SemanticConsistencyDTO request) {
			return Flux.empty();
		}

		@Override
		public Flux<ChatResponse> fineSelect(SchemaDTO schema, String query, String evidence,
				String sqlGenerateSchemaMissingAdvice, DbConfigBO dbConfig, Consumer<SchemaDTO> consumer) {
			return Flux.empty();
		}

	}

}
