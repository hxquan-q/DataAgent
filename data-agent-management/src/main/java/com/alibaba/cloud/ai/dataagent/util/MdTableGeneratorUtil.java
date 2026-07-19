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

import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;

import java.util.List;
import java.util.Map;

/**
 * Markdown 表格生成工具类。
 * <p>
 * 将二维数组或结构化的 {@link ResultSetBO} 转换为 Markdown 表格字符串，用于在聊天或报告场景中展示查询结果。
 * </p>
 */
public class MdTableGeneratorUtil {

	/**
	 * 将二维数组转换为 Markdown 表格字符串。
	 * <p>
	 * 第一行作为表头，其后跟随分隔行和数据行。
	 * </p>
	 * @param resultArr 二维数组，第一行为表头
	 * @return Markdown 表格字符串；入参为空时返回空字符串
	 */
	public static String generateTable(String[][] resultArr) {
		if (resultArr == null || resultArr.length == 0) {
			return "";
		}

		StringBuilder sb = new StringBuilder();

		// 表头行
		sb.append("| ");
		for (String col : resultArr[0]) {
			sb.append(col).append(" | ");
		}
		sb.append("\n");

		// 表头与数据之间的分隔行
		sb.append("|---".repeat(resultArr[0].length)).append("|\n");

		// 数据行
		for (int i = 1; i < resultArr.length; i++) {
			sb.append("| ");
			for (String cell : resultArr[i]) {
				sb.append(cell).append(" | ");
			}
			sb.append("\n");
		}

		return sb.toString();
	}

	/**
	 * 将结构化的 {@link ResultSetBO} 转换为 Markdown 表格字符串。
	 * @param resultSetBO 结构化数据，包含列名和数据行
	 * @return Markdown 表格字符串
	 */
	public static String generateTable(ResultSetBO resultSetBO) {
		List<String> column = resultSetBO.getColumn();
		List<Map<String, String>> data = resultSetBO.getData();

		// 构造二维数组：首行为表头，其余为数据行
		String[][] resultArr = new String[data.size() + 1][column.size()];
		int idxR = 0;

		// 第一行写入列名
		resultArr[idxR++] = column.toArray(new String[0]);

		// 依次填充每行数据
		for (Map<String, String> kv : data) {
			String[] row = new String[column.size()];
			int idxC = 0;
			for (String c : column) {
				row[idxC++] = kv.get(c);
			}
			resultArr[idxR++] = row;
		}

		return generateTable(resultArr);
	}

}
