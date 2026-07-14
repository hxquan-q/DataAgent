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
package com.alibaba.cloud.ai.dataagent.connector.ddl;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.util.SqlUtil;

import java.sql.Connection;
import java.util.List;

/**
 * JDBC DDL 执行器抽象类。
 * <p>
 * 定义了数据库元数据查询（数据库、schema、表、列、外键）和表数据操作（扫描、采样）的抽象方法。
 * 各数据库类型需要实现这些方法以提供特定方言的 SQL 查询逻辑。
 * </p>
 */
public abstract class AbstractJdbcDdl implements Ddl {

	/**
	 * 查询所有数据库列表。
	 * @param connection 数据库连接
	 * @return 数据库信息列表
	 * @deprecated 该方法已弃用，不同数据库对"数据库"概念定义不一，建议使用 {@link #showSchemas(Connection)}
	 */
	@Deprecated
	public abstract List<DatabaseInfoBO> showDatabases(Connection connection);

	/**
	 * 查询所有 schema 列表。
	 * @param connection 数据库连接
	 * @return schema 信息列表
	 */
	public abstract List<SchemaInfoBO> showSchemas(Connection connection);

	/**
	 * 查询指定 schema 下的表列表。
	 * @param connection 数据库连接
	 * @param schema schema 名称
	 * @param tablePattern 表名匹配模式（用于模糊查询，可为空）
	 * @return 表信息列表
	 */
	public abstract List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern);

	/**
	 * 获取指定表名的详细信息。
	 * @param connection 数据库连接
	 * @param schema schema 名称
	 * @param tables 表名列表
	 * @return 表信息列表
	 */
	public abstract List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables);

	/**
	 * 查询指定表的列信息。
	 * @param connection 数据库连接
	 * @param schema schema 名称
	 * @param table 表名
	 * @return 列信息列表
	 */
	public abstract List<ColumnInfoBO> showColumns(Connection connection, String schema, String table);

	/**
	 * 查询指定表的外键信息。
	 * @param connection 数据库连接
	 * @param schema schema 名称
	 * @param tables 表名列表
	 * @return 外键信息列表
	 */
	public abstract List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables);

	/**
	 * 采样指定列的数据值。
	 * @param connection 数据库连接
	 * @param schema schema 名称
	 * @param table 表名
	 * @param column 列名
	 * @return 采样值列表
	 */
	public abstract List<String> sampleColumn(Connection connection, String schema, String table, String column);

	/**
	 * 扫描表数据（预览前 20 行）。
	 * @param connection 数据库连接
	 * @param schema schema 名称
	 * @param table 表名
	 * @return 结构化结果集
	 */
	public abstract ResultSetBO scanTable(Connection connection, String schema, String table);

	/**
	 * 构建 SELECT 查询 SQL 语句。
	 * @param typeName 数据库类型名称
	 * @param tableName 表名
	 * @param columnNames 列名（逗号分隔），"*" 表示所有列
	 * @param limit 结果行数限制
	 * @return 构建好的 SELECT SQL 语句
	 */
	public String getSelectSql(String typeName, String tableName, String columnNames, int limit) {
		return SqlUtil.buildSelectSql(typeName, tableName, columnNames, limit);
	}

}
