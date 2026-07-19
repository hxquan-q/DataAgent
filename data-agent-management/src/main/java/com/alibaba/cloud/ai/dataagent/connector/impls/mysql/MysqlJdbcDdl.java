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
package com.alibaba.cloud.ai.dataagent.connector.impls.mysql;

import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.util.ColumnTypeUtil.wrapType;

/**
 * MySQL JDBC DDL 执行器实现。
 * <p>
 * 通过 information_schema 和 SHOW 命令查询 MySQL 数据库的元数据信息， 支持数据库、表、列、外键查询以及表数据扫描和列值采样。
 * </p>
 */
@Service
public class MysqlJdbcDdl extends AbstractJdbcDdl {

	/**
	 * 查询所有数据库列表。
	 * @param connection 数据库连接
	 * @return 数据库信息列表
	 */
	@Override
	public List<DatabaseInfoBO> showDatabases(Connection connection) {
		String sql = "show databases;";
		List<DatabaseInfoBO> databaseInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, sql);
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}

			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				String database = resultArr[i][0];
				databaseInfoList.add(DatabaseInfoBO.builder().name(database).build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}

		return databaseInfoList;
	}

	/**
	 * MySQL 不支持 schema 概念，返回空列表。
	 * @param connection 数据库连接
	 * @return 空列表
	 */
	@Override
	public List<SchemaInfoBO> showSchemas(Connection connection) {
		return Collections.emptyList();
	}

	/**
	 * 查询指定数据库下的表列表（通过 information_schema）。
	 * @param connection 数据库连接
	 * @param schema 数据库名（MySQL 中 schema 等同于 database）
	 * @param tablePattern 表名匹配模式（用于模糊查询，可为空）
	 * @return 表信息列表（最多 2000 条）
	 */
	@Override
	public List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern) {
		String sql = "SELECT TABLE_NAME, TABLE_COMMENT \n" + "FROM INFORMATION_SCHEMA.TABLES \n"
				+ "WHERE TABLE_SCHEMA = '%s' \n";
		if (StringUtils.isNotBlank(tablePattern)) {
			sql += "AND TABLE_NAME LIKE CONCAT('%%','%s','%%') \n";
		}
		sql += "limit 2000;";
		List<TableInfoBO> tableInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection,
					String.format(sql, connection.getCatalog(), tablePattern));
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}

			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				String tableName = resultArr[i][0];
				String tableDesc = resultArr[i][1];
				tableInfoList.add(TableInfoBO.builder().name(tableName).description(tableDesc).build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}

		return tableInfoList;
	}

	/**
	 * 获取指定表名的详细信息。
	 * @param connection 数据库连接
	 * @param schema 数据库名
	 * @param tables 表名列表
	 * @return 表信息列表（最多 200 条）
	 */
	@Override
	public List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables) {
		String sql = "SELECT TABLE_NAME, TABLE_COMMENT \n" + "FROM INFORMATION_SCHEMA.TABLES \n"
				+ "WHERE TABLE_SCHEMA = '%s' \n" + "AND TABLE_NAME in(%s) \n" + "limit 200;";
		List<TableInfoBO> tableInfoList = Lists.newArrayList();
		String tableListStr = String.join(", ", tables.stream().map(x -> "'" + x + "'").collect(Collectors.toList()));
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection,
					String.format(sql, connection.getCatalog(), tableListStr));
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}

			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				String tableName = resultArr[i][0];
				String tableDesc = resultArr[i][1];
				tableInfoList.add(TableInfoBO.builder().name(tableName).description(tableDesc).build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}

		return tableInfoList;
	}

	/**
	 * 查询指定表的列信息（通过 information_schema.COLUMNS）。
	 * @param connection 数据库连接
	 * @param schema 数据库名
	 * @param table 表名
	 * @return 列信息列表，包含列名、注释、类型、是否主键、是否非空
	 */
	@Override
	public List<ColumnInfoBO> showColumns(Connection connection, String schema, String table) {
		String sql = "SELECT column_name, column_comment, data_type, "
				+ "IF(column_key='PRI','true','false') AS '主键唯一', \n" + "IF(IS_NULLABLE='NO','true','false') AS '非空' \n"
				+ "FROM information_schema.COLUMNS " + "WHERE table_schema='%s' " + "and table_name='%s';";
		List<ColumnInfoBO> columnInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, "INFORMATION_SCHEMA",
					String.format(sql, connection.getCatalog(), table));
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}

			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				columnInfoList.add(ColumnInfoBO.builder()
					.name(resultArr[i][0])
					.description(resultArr[i][1])
					.type(wrapType(resultArr[i][2]))
					.primary(BooleanUtils.toBoolean(resultArr[i][3]))
					.notnull(BooleanUtils.toBoolean(resultArr[i][4]))
					.build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}

		return columnInfoList;
	}

	/**
	 * 查询指定表的外键信息（通过 INFORMATION_SCHEMA.KEY_COLUMN_USAGE）。
	 * @param connection 数据库连接
	 * @param schema 数据库名
	 * @param tables 表名列表
	 * @return 外键信息列表，包含表名、列名、引用表名、引用列名
	 */
	@Override
	public List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables) {
		String sql = "SELECT \n" + "    TABLE_NAME AS '表名',\n" + "    COLUMN_NAME AS '列名',\n"
				+ "    CONSTRAINT_NAME AS '约束名',\n" + "    REFERENCED_TABLE_NAME AS '引用表名',\n"
				+ "    REFERENCED_COLUMN_NAME AS '引用列名'\n" + "FROM \n" + "    INFORMATION_SCHEMA.KEY_COLUMN_USAGE\n"
				+ "WHERE \n" + "    CONSTRAINT_SCHEMA = '%s' " + "    AND CONSTRAINT_NAME != 'PRIMARY'"
				+ "    AND TABLE_NAME in(%s)\n" + "    AND REFERENCED_TABLE_NAME in (%s);";
		List<ForeignKeyInfoBO> foreignKeyInfoList = Lists.newArrayList();
		String tableListStr = String.join(", ", tables.stream().map(x -> "'" + x + "'").collect(Collectors.toList()));

		try {
			sql = String.format(sql, connection.getCatalog(), tableListStr, tableListStr);
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, "INFORMATION_SCHEMA", sql);
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}

			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				foreignKeyInfoList.add(ForeignKeyInfoBO.builder()
					.table(resultArr[i][0])
					.column(resultArr[i][1])
					.referencedTable(resultArr[i][3])
					.referencedColumn(resultArr[i][4])
					.build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}

		return foreignKeyInfoList;
	}

	/**
	 * 采样指定列的数据值（取前 99 行，已去重）。
	 * @param connection 数据库连接
	 * @param schema 数据库名
	 * @param table 表名
	 * @param column 列名
	 * @return 去重后的采样值列表
	 */
	@Override
	public List<String> sampleColumn(Connection connection, String schema, String table, String column) {
		String sql = "SELECT \n" + "    `%s`\n" + "FROM \n" + "    `%s`\n" + "LIMIT 99;";
		List<String> sampleInfo = Lists.newArrayList();
		try {
			sql = String.format(sql, column, table);
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, null, sql);
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}

			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0 || column.equalsIgnoreCase(resultArr[i][0])) {
					continue;
				}
				sampleInfo.add(resultArr[i][0]);
			}
		}
		catch (SQLException e) {
			// throw new RuntimeException(e);
		}

		Set<String> siSet = sampleInfo.stream().collect(Collectors.toSet());
		sampleInfo = siSet.stream().collect(Collectors.toList());
		return sampleInfo;
	}

	/**
	 * 扫描表数据（预览前 20 行）。
	 * @param connection 数据库连接
	 * @param schema schema 名称
	 * @param table 表名
	 * @return 结构化结果集
	 */
	@Override
	public ResultSetBO scanTable(Connection connection, String schema, String table) {
		String sql = "SELECT *\n" + "FROM \n" + "    `%s`\n" + "LIMIT 20;";
		ResultSetBO resultSet = ResultSetBO.builder().build();
		try {
			resultSet = SqlExecutor.executeSqlAndReturnObject(connection, schema, String.format(sql, table));
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return resultSet;
	}

	/**
	 * 获取当前 DDL 执行器对应的数据源类型枚举。
	 * @return MySQL 数据源类型枚举
	 */
	@Override
	public BizDataSourceTypeEnum getDataSourceType() {
		return BizDataSourceTypeEnum.MYSQL;
	}

}
