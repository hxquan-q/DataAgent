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
package com.alibaba.cloud.ai.dataagent.connector.accessor;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;

import java.util.List;

/**
 * 数据访问接口定义。
 * <p>
 * 提供数据库、schema、表、列、外键等元数据查询以及 SQL 执行能力。
 * 各数据库类型（MySQL、PostgreSQL、Oracle、H2、Hive、达梦、SQL Server 等）提供各自的实现。
 * </p>
 *
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */

public interface Accessor {

	/**
	 * 获取当前访问器的类型标识。
	 * @return 访问器类型名称
	 */
	String getAccessorType();

	/**
	 * 判断当前访问器是否支持指定的数据源类型。
	 * @param type 数据源类型名称
	 * @return 是否支持该数据源类型
	 */
	boolean supportedDataSourceType(String type);

	/**
	 * 判断当前访问器是否支持指定的数据源类型枚举。
	 * @param typeEnum 数据源类型枚举
	 * @return 是否支持该数据源类型
	 */
	default boolean supportedDataSourceType(BizDataSourceTypeEnum typeEnum) {
		return supportedDataSourceType(typeEnum.getTypeName());
	}

	/**
	 * 访问数据库并执行指定的方法。
	 * @param dbConfig 数据库配置信息
	 * @param method 方法名称（如 showDatabases、showTables 等）
	 * @param param 查询参数
	 * @return 结果对象，可以是数据库信息列表、schema 信息列表、表信息列表等
	 * @throws Exception 数据库访问过程中发生异常
	 */
	<T> T accessDb(DbConfigBO dbConfig, String method, DbQueryParameter param) throws Exception;

	/**
	 * 查询所有数据库列表。
	 * @param dbConfig 数据库配置信息
	 * @return 数据库信息列表
	 * @throws Exception 查询异常
	 */
	List<DatabaseInfoBO> showDatabases(DbConfigBO dbConfig) throws Exception;

	/**
	 * 查询所有 schema 列表。
	 * @param dbConfig 数据库配置信息
	 * @return schema 信息列表
	 * @throws Exception 查询异常
	 */
	List<SchemaInfoBO> showSchemas(DbConfigBO dbConfig) throws Exception;

	/**
	 * 查询指定 schema 下的表列表。
	 * @param dbConfig 数据库配置信息
	 * @param param 查询参数（包含 schema 和 tablePattern）
	 * @return 表信息列表
	 * @throws Exception 查询异常
	 */
	List<TableInfoBO> showTables(DbConfigBO dbConfig, DbQueryParameter param) throws Exception;

	/**
	 * 获取指定表名的详细信息。
	 * @param dbConfig 数据库配置信息
	 * @param param 查询参数（包含 schema 和 tables 列表）
	 * @return 表信息列表
	 * @throws Exception 查询异常
	 */
	List<TableInfoBO> fetchTables(DbConfigBO dbConfig, DbQueryParameter param) throws Exception;

	/**
	 * 查询指定表的列信息。
	 * @param dbConfig 数据库配置信息
	 * @param param 查询参数（包含 schema 和 table）
	 * @return 列信息列表
	 * @throws Exception 查询异常
	 */
	List<ColumnInfoBO> showColumns(DbConfigBO dbConfig, DbQueryParameter param) throws Exception;

	/**
	 * 查询指定表的外键信息。
	 * @param dbConfig 数据库配置信息
	 * @param param 查询参数（包含 schema 和 tables 列表）
	 * @return 外键信息列表
	 * @throws Exception 查询异常
	 */
	List<ForeignKeyInfoBO> showForeignKeys(DbConfigBO dbConfig, DbQueryParameter param) throws Exception;

	/**
	 * 采样指定列的数据值。
	 * @param dbConfig 数据库配置信息
	 * @param param 查询参数（包含 schema、table 和 column）
	 * @return 采样值列表（已去重）
	 * @throws Exception 查询异常
	 */
	List<String> sampleColumn(DbConfigBO dbConfig, DbQueryParameter param) throws Exception;

	/**
	 * 扫描表数据（预览前 20 行）。
	 * @param dbConfig 数据库配置信息
	 * @param param 查询参数（包含 schema 和 table）
	 * @return 结构化结果集
	 * @throws Exception 查询异常
	 */
	ResultSetBO scanTable(DbConfigBO dbConfig, DbQueryParameter param) throws Exception;

	/**
	 * 执行 SQL 语句并返回结构化结果。
	 * @param dbConfig 数据库配置信息
	 * @param param 查询参数（包含 schema 和 sql）
	 * @return 结构化结果集
	 * @throws Exception 执行异常
	 */
	ResultSetBO executeSqlAndReturnObject(DbConfigBO dbConfig, DbQueryParameter param) throws Exception;

}
