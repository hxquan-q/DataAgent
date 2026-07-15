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
package com.alibaba.cloud.ai.dataagent.dto.semantic;

import lombok.Data;

import java.util.List;

/**
 * 语义对象（SemanticParseNode 的 LLM 选择产物，受控拼装的输入）。
 * <p>
 * LLM 只从候选指标/维度中选择并填值，不生成 SQL。后端 {@code BuildSQLEngine} 据此参数化拼装。
 * </p>
 *
 * @author dataagent
 */
@Data
public class SemanticObject {

	/** 选择的指标（至少一个） */
	private List<MetricRef> metrics;

	/** 分组维度 */
	private List<DimensionRef> dimensions;

	/** 时间范围 */
	private TimeRange timeRange;

	/** 过滤条件 */
	private List<FilterRef> filters;

	/** 分析类型 trend/compare/ranking/summary（影响图表，M2 暂透传） */
	private String analysisType;

	/** 返回行数上限（默认 1000） */
	private Integer limit;

	/** LLM 标记需反问（候选无匹配/口径歧义） */
	private boolean needsClarification;

	/** 反问消息 */
	private String clarificationMessage;

	/**
	 * 指标引用（编码 + 可选口径版本）。
	 */
	@Data
	public static class MetricRef {

		/** 指标编码 */
		private String metricCode;

		/** 口径版本编码（空则用默认口径） */
		private String verCode;

	}

	/**
	 * 维度引用（表 + 列）。
	 */
	@Data
	public static class DimensionRef {

		private String table;

		private String column;

	}

	/**
	 * 时间范围。
	 */
	@Data
	public static class TimeRange {

		private String field;

		private String start;

		private String end;

	}

	/**
	 * 过滤条件。
	 */
	@Data
	public static class FilterRef {

		private String column;

		/** 操作符 = / &gt; / &lt; / &lt;&gt; / IN / LIKE */
		private String op;

		private String value;

	}

}
