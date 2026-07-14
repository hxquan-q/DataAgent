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
package com.alibaba.cloud.ai.dataagent.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 将 SQL 结果集压缩为适合注入报告提示词的统计摘要。 */
public final class DataSummarizer {

	private static final int DEFAULT_TOP_N = 10;

	private static final int MAX_SAMPLE_ROWS = 5;

	private static final int MAX_SUMMARY_COLUMNS = 50;

	private DataSummarizer() {
	}

	/**
	 * 汇总 ResultSetBO JSON；同时兼容历史测试中的 JSON 数组格式。
	 * @param resultSetJson ResultSetBO 或行数组 JSON
	 * @return 统计摘要与最多 5 行样本
	 */
	public static String summarize(String resultSetJson) {
		try {
			JsonNode root = JsonUtil.getObjectMapper().readTree(resultSetJson);
			JsonNode data = root.isArray() ? root : root.path("data");
			if (!data.isArray()) {
				return "结果集格式无法识别，未注入原始数据。\n";
			}

			List<String> columns = columns(root, data);
			StringBuilder summary = new StringBuilder("**预聚合摘要**:\n");
			summary.append("- 行数: ").append(data.size()).append('\n');
			if (columns.size() > MAX_SUMMARY_COLUMNS) {
				summary.append("- 列数: ")
					.append(columns.size())
					.append("（仅展示前 ")
					.append(MAX_SUMMARY_COLUMNS)
					.append(" 列摘要）\n");
			}

			for (String column : columns.stream().limit(MAX_SUMMARY_COLUMNS).toList()) {
				List<String> values = values(data, column);
				ColumnType type = inferType(values);
				summary.append("- ").append(column).append(" [").append(type.label).append("]");
				if (type == ColumnType.NUMBER) {
					appendNumeric(summary, values);
				}
				else if (type == ColumnType.TIME) {
					summary.append(": 粒度=").append(timeGranularity(values));
				}
				else {
					appendCategory(summary, values);
				}
				summary.append('\n');
			}

			summary.append("**样本行**（最多 ").append(MAX_SAMPLE_ROWS).append(" 行）:\n```json\n");
			for (int i = 0; i < Math.min(data.size(), MAX_SAMPLE_ROWS); i++) {
				summary.append(JsonUtil.getObjectMapper().writeValueAsString(data.get(i))).append('\n');
			}
			return summary.append("```\n").toString();
		}
		catch (Exception ex) {
			return "结果集解析失败，未注入原始数据。\n";
		}
	}

	private static List<String> columns(JsonNode root, JsonNode data) {
		Set<String> columns = new LinkedHashSet<>();
		JsonNode declaredColumns = root.path("column");
		if (declaredColumns.isArray()) {
			declaredColumns.forEach(column -> columns.add(column.asText()));
		}
		data.forEach(row -> row.fieldNames().forEachRemaining(columns::add));
		return new ArrayList<>(columns);
	}

	private static List<String> values(JsonNode data, String column) {
		List<String> values = new ArrayList<>();
		data.forEach(row -> {
			JsonNode value = row.get(column);
			if (value != null && !value.isNull() && !value.asText().isBlank()) {
				values.add(value.asText());
			}
		});
		return values;
	}

	private static ColumnType inferType(List<String> values) {
		if (values.isEmpty()) {
			return ColumnType.TEXT;
		}
		if (values.stream().allMatch(DataSummarizer::isNumber)) {
			return ColumnType.NUMBER;
		}
		if (values.stream().allMatch(value -> parseTime(value) != null)) {
			return ColumnType.TIME;
		}
		if (values.stream().allMatch(value -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
			return ColumnType.BOOLEAN;
		}
		return ColumnType.TEXT;
	}

	private static void appendNumeric(StringBuilder summary, List<String> values) {
		List<BigDecimal> numbers = values.stream().map(BigDecimal::new).toList();
		BigDecimal sum = numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal avg = sum.divide(BigDecimal.valueOf(numbers.size()), MathContext.DECIMAL64);
		String top = numbers.stream()
			.sorted(Comparator.reverseOrder())
			.limit(DEFAULT_TOP_N)
			.map(DataSummarizer::format)
			.collect(Collectors.joining(", "));
		summary.append(": min=")
			.append(format(numbers.stream().min(Comparator.naturalOrder()).orElseThrow()))
			.append(", max=")
			.append(format(numbers.stream().max(Comparator.naturalOrder()).orElseThrow()))
			.append(", avg=")
			.append(format(avg))
			.append(", sum=")
			.append(format(sum))
			.append(", top-10=[")
			.append(top)
			.append(']');
	}

	private static void appendCategory(StringBuilder summary, List<String> values) {
		Map<String, Long> counts = values.stream()
			.collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));
		String top = counts.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
			.limit(DEFAULT_TOP_N)
			.map(entry -> entry.getKey() + "(" + entry.getValue() + ")")
			.collect(Collectors.joining(", "));
		summary.append(": 基数=").append(counts.size()).append(", top-10=").append(top.isEmpty() ? "无" : top);
	}

	private static String timeGranularity(List<String> values) {
		List<Instant> times = values.stream()
			.map(DataSummarizer::parseTime)
			.filter(value -> value != null)
			.sorted()
			.toList();
		Duration min = null;
		for (int i = 1; i < times.size(); i++) {
			Duration duration = Duration.between(times.get(i - 1), times.get(i));
			if (!duration.isZero() && !duration.isNegative() && (min == null || duration.compareTo(min) < 0)) {
				min = duration;
			}
		}
		if (min == null) {
			return formatGranularity(values.get(0));
		}
		long seconds = min.getSeconds();
		if (seconds < 60) {
			return "秒";
		}
		if (seconds < 3600) {
			return "分钟";
		}
		if (seconds < 86400) {
			return "小时";
		}
		if (seconds < 28L * 86400) {
			return "天";
		}
		if (seconds < 365L * 86400) {
			return "月";
		}
		return "年";
	}

	private static String formatGranularity(String value) {
		if (value.matches("\\d{4}")) {
			return "年";
		}
		if (value.matches("\\d{4}-\\d{2}")) {
			return "月";
		}
		if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
			return "天";
		}
		return value.matches(".*:\\d{2}:\\d{2}.*") ? "秒" : value.matches(".*:\\d{2}.*") ? "分钟" : "小时";
	}

	private static Instant parseTime(String value) {
		try {
			return Instant.parse(value);
		}
		catch (DateTimeParseException ignored) {
		}
		try {
			return OffsetDateTime.parse(value).toInstant();
		}
		catch (DateTimeParseException ignored) {
		}
		try {
			return LocalDateTime.parse(value.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
		}
		catch (DateTimeParseException ignored) {
		}
		try {
			return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
		}
		catch (DateTimeParseException ignored) {
		}
		try {
			return YearMonth.parse(value).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
		}
		catch (DateTimeParseException ignored) {
			return null;
		}
	}

	private static boolean isNumber(String value) {
		try {
			new BigDecimal(value);
			return true;
		}
		catch (NumberFormatException ex) {
			return false;
		}
	}

	private static String format(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	private enum ColumnType {

		NUMBER("数值"), TIME("时序"), BOOLEAN("布尔"), TEXT("分类");

		private final String label;

		ColumnType(String label) {
			this.label = label;
		}

	}

}
