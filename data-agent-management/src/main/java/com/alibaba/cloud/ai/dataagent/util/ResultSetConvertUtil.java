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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC {@link ResultSet} 转换工具类。
 * <p>
 * 将数据库查询结果集转换为以字符串数组表示的二维表格（首行为列名，其余为数据行）， 便于后续渲染或写入 Markdown 表格等场景。
 * </p>
 */
public class ResultSetConvertUtil {

	/**
	 * 将 {@link ResultSet} 转换为字符串数组的列表。
	 * <p>
	 * 第一项为列名数组，后续每一项对应一行数据；列值为 null 时转换为空字符串。
	 * </p>
	 * @param rs JDBC 结果集
	 * @return 字符串数组列表，首元素为表头
	 * @throws SQLException 当访问结果集元数据或取值出现数据库异常时抛出
	 */
	public static List<String[]> convert(ResultSet rs) throws SQLException {
		ResultSetMetaData data = rs.getMetaData();
		int columnsCount = data.getColumnCount();
		List<String[]> list = new ArrayList<>();
		String[] rowHead = new String[columnsCount];

		// 构造表头：使用列标签作为表头名
		for (int i = 1; i <= columnsCount; i++) {
			rowHead[i - 1] = data.getColumnLabel(i);
		}

		list.add(rowHead);

		// 逐行读取数据，null 值转换为空字符串以避免显示问题
		while (rs.next()) {
			String[] rowData = new String[columnsCount];
			int idx = 0;
			for (String head : rowHead) {
				rowData[idx++] = rs.getString(head) == null ? "" : rs.getString(head);
			}
			list.add(rowData);
		}

		return list;
	}

}
