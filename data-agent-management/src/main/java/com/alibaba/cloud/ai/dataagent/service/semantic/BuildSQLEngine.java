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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.DimensionRef;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.FilterRef;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.MetricRef;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.TimeRange;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.entity.Metric;
import com.alibaba.cloud.ai.dataagent.entity.MetricVersion;
import com.alibaba.cloud.ai.dataagent.util.JoinRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 受控 SQL 拼装引擎（NL2Semantic2SQL 确定性构建核心，η₂）。
 * <p>
 * 输入 {@link SemanticObject}（LLM 选择产物），输出参数化 SQL + 值列表。标识符（表名/列名/聚合字段）来自
 * 语义层配置（可信源），值（时间/过滤）参数化（? 占位）防注入。口径版本（{@link MetricVersion}）的
 * filter_condition（JSON）作为可信配置注入 WHERE，time_field 按优先级覆盖。产物过 {@code SqlGuard.validate}
 * 二次校验。
 * </p>
 * <p>
 * 范围：单指标 + 跨表维度（经 {@code logical_relation} 路由桥接表）。多指标留后续。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
@Component
@AllArgsConstructor
public class BuildSQLEngine {

	/** 默认返回行数上限（η₆ 有界） */
	private static final int DEFAULT_LIMIT = 1000;

	private final SemanticLayerLoader loader;

	private final ObjectMapper objectMapper;

	/**
	 * 受控拼装参数化 SQL（无跨表 JOIN，等价于 {@link #build(SemanticObject, List)} 传空关系列表）。
	 * 保留此重载以兼容既有调用方（如 {@code BuildSQLNode}）。
	 * @param so 语义对象（需含至少一个指标）
	 * @return SQL 模板（? 占位）+ 参数值列表
	 * @throws IllegalArgumentException 指标缺失或未知指标编码
	 */
	public BuildResult build(SemanticObject so) {
		return build(so, Collections.emptyList());
	}

	/**
	 * 受控拼装参数化 SQL（含口径版本 filter/time_field + 跨表维度 JOIN 路由）。
	 * <p>
	 * 当维度 {@link DimensionRef#getTable()} 与指标 {@link Metric#getSourceTable()} 不同时， 调用
	 * {@link JoinRouter#findJoinPath} 基于 {@code logical_relation} 查找桥接表（≤2 跳）， 在 FROM 后追加
	 * {@code JOIN bridge ON ...} 子句（ON 条件来自逻辑外键列）。 无路径时回退单表查询并记日志。
	 * </p>
	 * @param so 语义对象（需含至少一个指标）
	 * @param logicalRelations 逻辑外键关系（用于桥接路由，可为空）
	 * @return SQL 模板（? 占位）+ 参数值列表
	 * @throws IllegalArgumentException 指标缺失或未知指标编码
	 */
	public BuildResult build(SemanticObject so, List<LogicalRelation> logicalRelations) {
		if (so == null || so.getMetrics() == null || so.getMetrics().isEmpty()) {
			throw new IllegalArgumentException("语义对象缺少指标，无法拼装 SQL");
		}
		MetricRef metricRef = so.getMetrics().get(0);
		Metric metric = loader.getMetric(metricRef.getMetricCode());
		if (metric == null) {
			throw new IllegalArgumentException("未知指标编码：" + metricRef.getMetricCode());
		}
		MetricVersion version = resolveVersion(metric, metricRef);

		List<Object> params = new ArrayList<>();
		StringBuilder sql = new StringBuilder("SELECT ");

		// 维度列（来自配置可信，可能跨表 → 需 JOIN 桥接）
		String sourceTable = metric.getSourceTable();
		List<String> dimCols = new ArrayList<>();
		List<DimensionRef> crossTableDims = new ArrayList<>();
		if (so.getDimensions() != null) {
			for (DimensionRef dim : so.getDimensions()) {
				if (dim.getColumn() == null || dim.getColumn().isBlank()) {
					continue;
				}
				// 带表名前缀以消歧（JOIN 后多表同名列需限定）
				dimCols.add(qualifyColumn(dim.getTable(), dim.getColumn(), sourceTable));
				if (dim.getTable() != null && !dim.getTable().equals(sourceTable)) {
					crossTableDims.add(dim);
				}
			}
		}
		if (!dimCols.isEmpty()) {
			sql.append(String.join(", ", dimCols)).append(", ");
		}

		// 聚合表达式（标识符来自 metric 配置）
		sql.append(metric.getAggFunc())
			.append("(")
			.append(metric.getAggField())
			.append(") AS ")
			.append(metric.getMetricCode());
		sql.append(" FROM ").append(sourceTable);

		// 跨表维度 JOIN 路由（η₂）：基于 logical_relation 找桥接表，拼 JOIN 子句
		appendJoinClauses(sql, sourceTable, crossTableDims, logicalRelations);

		// WHERE：时间范围 + 口径版本 filter_condition + 用户过滤（值参数化）
		List<String> conditions = new ArrayList<>();
		TimeRange tr = so.getTimeRange();
		String timeField = resolveTimeField(tr, version, metric);
		if (tr != null && tr.getStart() != null && tr.getEnd() != null && timeField != null) {
			conditions.add(timeField + " BETWEEN ? AND ?");
			params.add(tr.getStart());
			params.add(tr.getEnd());
		}
		if (version != null) {
			appendVersionFilters(version.getFilterCondition(), conditions, params);
		}
		if (so.getFilters() != null) {
			for (FilterRef filter : so.getFilters()) {
				if (filter.getColumn() == null || filter.getOp() == null) {
					continue;
				}
				conditions.add(filter.getColumn() + " " + filter.getOp() + " ?");
				params.add(filter.getValue());
			}
		}
		if (!conditions.isEmpty()) {
			sql.append(" WHERE ").append(String.join(" AND ", conditions));
		}

		// GROUP BY 维度
		if (!dimCols.isEmpty()) {
			sql.append(" GROUP BY ").append(String.join(", ", dimCols));
		}

		// LIMIT（有界）
		int limit = (so.getLimit() != null && so.getLimit() > 0) ? so.getLimit() : DEFAULT_LIMIT;
		sql.append(" LIMIT ?");
		params.add(limit);

		return new BuildResult(sql.toString(), params);
	}

	/**
	 * 维度列表限定为 {@code table.column}，若维度与指标同表则退化为裸列名。
	 */
	private String qualifyColumn(String dimTable, String dimColumn, String sourceTable) {
		if (dimTable == null || dimTable.isBlank() || dimTable.equals(sourceTable)) {
			return dimColumn;
		}
		return dimTable + "." + dimColumn;
	}

	/**
	 * 为每个跨表维度拼接桥接 JOIN 子句（最小侵入：仅在 FROM 后追加）。 对 fromTable→dimTable 调
	 * {@link JoinRouter#findJoinPath} 取桥接表序列，逐表 JOIN； ON 条件取首条匹配的逻辑关系列对。
	 * @param sql SQL 拼装缓冲
	 * @param fromTable 指标 source_table（JOIN 起点）
	 * @param crossTableDims 跨表维度列表（dim.table != fromTable）
	 * @param relations 逻辑外键关系
	 */
	private void appendJoinClauses(StringBuilder sql, String fromTable, List<DimensionRef> crossTableDims,
			List<LogicalRelation> relations) {
		if (crossTableDims.isEmpty() || relations == null || relations.isEmpty()) {
			return;
		}
		Set<String> joined = new HashSet<>();
		joined.add(fromTable);
		for (DimensionRef dim : crossTableDims) {
			String dimTable = dim.getTable();
			if (dimTable == null || joined.contains(dimTable)) {
				continue;
			}
			List<String> bridge = JoinRouter.findJoinPath(fromTable, dimTable, relations);
			if (bridge.isEmpty() && !hasDirectRelation(fromTable, dimTable, relations)) {
				// 无桥接路径且非直连：跳过该维度，保留单表查询（记日志）
				log.warn("BuildSQL：维度表 {} 与指标表 {} 无逻辑关系路径，跳过 JOIN", dimTable, fromTable);
				continue;
			}
			String prevTable = fromTable;
			// 直连（1 跳，bridge 为空）→ 直接 JOIN dimTable；否则逐桥接表 JOIN
			List<String> fullPath = new ArrayList<>(bridge);
			fullPath.add(dimTable);
			for (String step : fullPath) {
				if (joined.contains(step)) {
					prevTable = step;
					continue;
				}
				LogicalRelation rel = findRelation(prevTable, step, relations);
				if (rel == null) {
					log.warn("BuildSQL：缺失 {}→{} 的逻辑关系列，跳过该步 JOIN", prevTable, step);
					break;
				}
				sql.append(" JOIN ")
					.append(step)
					.append(" ON ")
					.append(rel.getSourceTableName())
					.append(".")
					.append(rel.getSourceColumnName())
					.append(" = ")
					.append(rel.getTargetTableName())
					.append(".")
					.append(rel.getTargetColumnName());
				joined.add(step);
				prevTable = step;
			}
		}
	}

	/** 是否存在 fromTable↔toTable 的直接逻辑关系（任一方向）。 */
	private boolean hasDirectRelation(String fromTable, String toTable, List<LogicalRelation> relations) {
		return findRelation(fromTable, toTable, relations) != null;
	}

	/** 查找 a↔b 的逻辑关系（无向，a/b 可在 source 或 target 任一侧）。 */
	private LogicalRelation findRelation(String a, String b, List<LogicalRelation> relations) {
		for (LogicalRelation rel : relations) {
			String s = rel.getSourceTableName();
			String t = rel.getTargetTableName();
			if (s == null || t == null) {
				continue;
			}
			if ((s.equals(a) && t.equals(b)) || (s.equals(b) && t.equals(a))) {
				return rel;
			}
		}
		return null;
	}

	/** 解析口径版本（verCode 非空时按 metricId 查版本列表匹配）。 */
	private MetricVersion resolveVersion(Metric metric, MetricRef ref) {
		if (ref.getVerCode() == null || ref.getVerCode().isBlank()) {
			return null;
		}
		return loader.getVersions(metric.getId())
			.stream()
			.filter(v -> ref.getVerCode().equals(v.getVerCode()))
			.findFirst()
			.orElse(null);
	}

	/** 时间字段优先级：timeRange.field > version.time_field > metric.defaultTimeField。 */
	private String resolveTimeField(TimeRange tr, MetricVersion version, Metric metric) {
		if (tr != null && tr.getField() != null && !tr.getField().isBlank()) {
			return tr.getField();
		}
		if (version != null && version.getTimeField() != null && !version.getTimeField().isBlank()) {
			return version.getTimeField();
		}
		return metric.getDefaultTimeField();
	}

	/** 口径版本 filter_condition（JSON 如 {"status":"completed"}）解析为参数化 WHERE 条件（可信配置）。 */
	private void appendVersionFilters(String filterCondition, List<String> conditions, List<Object> params) {
		if (filterCondition == null || filterCondition.isBlank()) {
			return;
		}
		try {
			JsonNode node = objectMapper.readTree(filterCondition);
			Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> entry = fields.next();
				conditions.add(entry.getKey() + " = ?");
				params.add(entry.getValue().asText());
			}
		}
		catch (Exception ex) {
			log.warn("口径 filter_condition 解析失败，跳过：{}", ex.getMessage());
		}
	}

	/**
	 * 拼装结果：SQL 模板（? 占位）+ 参数值列表（按 ? 顺序）。
	 *
	 * @param sql SQL 模板
	 * @param params 参数值列表
	 */
	public record BuildResult(String sql, List<Object> params) {

	}

}
