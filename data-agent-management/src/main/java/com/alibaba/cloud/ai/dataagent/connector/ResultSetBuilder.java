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
package com.alibaba.cloud.ai.dataagent.connector;

import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 结果集构建器，负责将 JDBC ResultSet 转换为结构化的 ResultSetBO 对象。
 * <p>
 * 主要职责：提取列名、读取数据行、清理列名中的特殊字符（反引号、双引号），并限制结果集行数。
 * </p>
 */
public class ResultSetBuilder {

	/**
	 * 从 JDBC ResultSet 构建结构化结果对象。
	 * @param rs JDBC 结果集
	 * @param schema schema 名称
	 * @return 结构化结果对象，包含列名列表和数据行
	 * @throws SQLException 结果集读取异常
	 */
	public static ResultSetBO buildFrom(ResultSet rs, String schema) throws SQLException {
		ResultSetMetaData data = rs.getMetaData();
		int columnsCount = data.getColumnCount();
		ResultSetBO resultSetBO = new ResultSetBO();
		String[] rowHead = new String[columnsCount];

		// 提取列名作为表头
		for (int i = 1; i <= columnsCount; i++) {
			rowHead[i - 1] = data.getColumnLabel(i);
		}

		List<Map<String, String>> resultSetData = Lists.newArrayList();
		int count = 0;

		// 遍历结果集，限制最大行数
		while (rs.next() && count < SqlExecutor.RESULT_SET_LIMIT) {
			Map<String, String> kv = new HashMap<>();
			for (String h : rowHead) {
				kv.put(h, rs.getString(h) == null ? "" : rs.getString(h));
			}
			resultSetData.add(kv);
			count++;
		}

		// 清理列名和数据中的特殊字符
		List<String> cleanedHead = cleanColumnNames(Arrays.asList(rowHead));
		List<Map<String, String>> cleanedData = cleanResultSet(resultSetData);

		resultSetBO.setColumn(cleanedHead);
		resultSetBO.setData(cleanedData);

		return resultSetBO;
	}

	/**
	 * 清理列名中的特殊字符（反引号和双引号）。
	 * @param columnNames 原始列名列表
	 * @return 清理后的列名列表
	 */
	private static List<String> cleanColumnNames(List<String> columnNames) {
		return columnNames.stream().map(name -> StringUtils.remove(StringUtils.remove(name, "`"), "\"")).toList();
	}

	/**
	 * 清理结果集数据中的列名特殊字符（反引号和双引号）。
	 * @param data 原始数据行列表
	 * @return 清理后的数据行列表
	 */
	private static List<Map<String, String>> cleanResultSet(List<Map<String, String>> data) {
		return data.stream().map(row -> {
			Map<String, String> cleanedRow = new HashMap<>();
			row.forEach((k, v) -> {
				String cleanedKey = StringUtils.remove(k, "`");
				cleanedKey = StringUtils.remove(cleanedKey, "\"");
				cleanedRow.put(cleanedKey, v);
			});
			return cleanedRow;
		}).toList();
	}

}
