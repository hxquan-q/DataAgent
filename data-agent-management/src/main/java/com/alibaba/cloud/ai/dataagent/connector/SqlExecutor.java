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
import com.alibaba.cloud.ai.dataagent.enums.DatabaseDialectEnum;
import com.alibaba.cloud.ai.dataagent.util.ResultSetConvertUtil;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * SQL 执行器，负责执行 SQL 语句并返回结构化结果。
 * <p>
 * 支持多种数据库方言（MySQL、PostgreSQL、Oracle、H2 等），会根据数据库类型自动处理 schema/数据库切换逻辑。
 * </p>
 */
public class SqlExecutor {

	/** 结果集最大行数限制 */
	public static final Integer RESULT_SET_LIMIT = 1000;

	/** SQL 语句执行超时时间（秒） */
	public static final Integer STATEMENT_TIMEOUT = 30;

	/**
	 * 执行 SQL 查询并返回包含列信息的结构化结果。
	 * @param connection 数据库连接
	 * @param schema schema 名称，用于切换数据库上下文（可为空）
	 * @param sql 待执行的 SQL 语句
	 * @return 结构化查询结果
	 * @throws SQLException SQL 执行异常
	 */
	public static ResultSetBO executeSqlAndReturnObject(Connection connection, String schema, String sql)
			throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.setMaxRows(RESULT_SET_LIMIT);
			statement.setQueryTimeout(STATEMENT_TIMEOUT);

			// 获取数据库方言，根据方言类型执行对应的 schema 切换逻辑
			DatabaseMetaData metaData = connection.getMetaData();
			String dialect = metaData.getDatabaseProductName();

			if (dialect.equals(DatabaseDialectEnum.POSTGRESQL.code)) {
				// PostgreSQL 通过设置 search_path 切换 schema
				if (StringUtils.isNotEmpty(schema)) {
					statement.execute("set search_path = '" + schema + "';");
				}
			}
			else if (dialect.equals(DatabaseDialectEnum.H2.code)) {
				// H2 数据库使用 USE 语句切换 schema
				if (StringUtils.isNotEmpty(schema)) {
					statement.execute("use " + schema + ";");
				}
			}
			else if (dialect.equals(DatabaseDialectEnum.ORACLE.code)) {
				// Oracle 通过修改会话级 CURRENT_SCHEMA 切换 schema
				if (StringUtils.isNotEmpty(schema)) {
					statement.execute("ALTER SESSION SET CURRENT_SCHEMA = " + schema);
				}
			}

			try (ResultSet rs = statement.executeQuery(sql)) {
				return ResultSetBuilder.buildFrom(rs, schema);
			}
		}
	}

	/**
	 * 执行 SQL 查询并以字符串二维数组格式返回结果。
	 * @param connection 数据库连接
	 * @param sql 待执行的 SQL 语句
	 * @return 二维数组格式的查询结果（首行为表头）
	 * @throws SQLException SQL 执行异常
	 */
	public static String[][] executeSqlAndReturnArr(Connection connection, String sql) throws SQLException {
		List<String[]> list = executeQuery(connection, sql);
		return list.toArray(new String[0][]);
	}

	/**
	 * 执行 SQL 查询并切换到指定数据库/schema 后以二维数组格式返回结果。
	 * @param connection 数据库连接
	 * @param databaseOrSchema 数据库名或 schema 名（用于切换上下文）
	 * @param sql 待执行的 SQL 语句
	 * @return 二维数组格式的查询结果（首行为表头）
	 * @throws SQLException SQL 执行异常
	 */
	public static String[][] executeSqlAndReturnArr(Connection connection, String databaseOrSchema, String sql)
			throws SQLException {
		List<String[]> list = executeQuery(connection, databaseOrSchema, sql);
		return list.toArray(new String[0][]);
	}

	/**
	 * 执行 SQL 查询并返回行列表结果（不切换数据库/schema）。
	 * @param connection 数据库连接
	 * @param sql 待执行的 SQL 语句
	 * @return 行列表，每行为字符串数组
	 * @throws SQLException SQL 执行异常
	 */
	private static List<String[]> executeQuery(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {

			return ResultSetConvertUtil.convert(rs);
		}
	}

	/**
	 * 执行 SQL 查询，在执行前切换到指定数据库/schema，执行完成后恢复原上下文。
	 * @param connection 数据库连接
	 * @param databaseOrSchema 数据库名或 schema 名
	 * @param sql 待执行的 SQL 语句
	 * @return 行列表，每行为字符串数组
	 * @throws SQLException SQL 执行异常
	 */
	private static List<String[]> executeQuery(Connection connection, String databaseOrSchema, String sql)
			throws SQLException {
		// 保存原始数据库名称，用于执行完成后恢复
		String originalDb = connection.getCatalog();
		DatabaseMetaData metaData = connection.getMetaData();
		String dialect = metaData.getDatabaseProductName();

		try (Statement statement = connection.createStatement()) {

			// 根据数据库方言切换到目标数据库/schema
			if (dialect.equals(DatabaseDialectEnum.MYSQL.code)) {
				if (StringUtils.isNotEmpty(databaseOrSchema)) {
					statement.execute("use `" + databaseOrSchema + "`;");
				}
			}
			else if (dialect.equals(DatabaseDialectEnum.POSTGRESQL.code)) {
				if (StringUtils.isNotEmpty(databaseOrSchema)) {
					statement.execute("set search_path = '" + databaseOrSchema + "';");
				}
			}
			else if (dialect.equals(DatabaseDialectEnum.ORACLE.code)) {
				if (StringUtils.isNotEmpty(databaseOrSchema)) {
					statement.execute("ALTER SESSION SET CURRENT_SCHEMA = " + databaseOrSchema);
				}
			}

			ResultSet rs = statement.executeQuery(sql);

			List<String[]> result = ResultSetConvertUtil.convert(rs);

			// MySQL 执行完成后恢复原始数据库上下文
			if (StringUtils.isNotEmpty(databaseOrSchema) && dialect.equals(DatabaseDialectEnum.MYSQL.code)) {
				statement.execute("use `" + originalDb + "`;");
			}

			return result;
		}
	}

}
