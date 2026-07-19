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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.Objects;

/**
 * 数据库查询参数类，封装数据库访问所需的各类查询条件。
 * <p>
 * 包含阿里云 UID、工作空间 ID、区域、密钥 ARN、数据库实例 ID、数据库名、schema、表名、 表名匹配模式、列名、SQL 语句等参数，采用链式 setter
 * 设计。
 * </p>
 */
public class DbQueryParameter {

	/** 阿里云账号 UID */
	private String aliuid;

	/** 工作空间 ID */
	private String workspaceId;

	/** 区域标识 */
	private String region;

	/** 密钥 ARN（Amazon 资源名称） */
	private String secretArn;

	/** 数据库实例 ID */
	private String dbInstanceId;

	/** 数据库名称 */
	private String database;

	/** schema 名称 */
	private String schema;

	/** 表名 */
	private String table;

	/** 表名匹配模式（用于模糊查询） */
	private String tablePattern;

	/** 表名列表（用于批量查询） */
	private List<String> tables;

	/** 列名 */
	private String column;

	/** SQL 语句 */
	private String sql;

	/**
	 * 默认构造函数。
	 */
	public DbQueryParameter() {
	}

	/**
	 * 全参数构造函数。
	 * @param aliuid 阿里云账号 UID
	 * @param workspaceId 工作空间 ID
	 * @param region 区域标识
	 * @param secretArn 密钥 ARN
	 * @param dbInstanceId 数据库实例 ID
	 * @param database 数据库名称
	 * @param schema schema 名称
	 * @param table 表名
	 * @param tablePattern 表名匹配模式
	 * @param tables 表名列表
	 * @param column 列名
	 * @param sql SQL 语句
	 */
	public DbQueryParameter(String aliuid, String workspaceId, String region, String secretArn, String dbInstanceId,
			String database, String schema, String table, String tablePattern, List<String> tables, String column,
			String sql) {
		this.aliuid = aliuid;
		this.workspaceId = workspaceId;
		this.region = region;
		this.secretArn = secretArn;
		this.dbInstanceId = dbInstanceId;
		this.database = database;
		this.schema = schema;
		this.table = table;
		this.tablePattern = tablePattern;
		this.tables = tables;
		this.column = column;
		this.sql = sql;
	}

	/** 获取阿里云账号 UID */
	public String getAliuid() {
		return aliuid;
	}

	/** 设置阿里云账号 UID */
	public DbQueryParameter setAliuid(String aliuid) {
		this.aliuid = aliuid;
		return this;
	}

	/** 获取工作空间 ID */
	public String getWorkspaceId() {
		return workspaceId;
	}

	/** 设置工作空间 ID */
	public DbQueryParameter setWorkspaceId(String workspaceId) {
		this.workspaceId = workspaceId;
		return this;
	}

	/** 获取区域标识 */
	public String getRegion() {
		return region;
	}

	/** 设置区域标识 */
	public DbQueryParameter setRegion(String region) {
		this.region = region;
		return this;
	}

	/** 获取密钥 ARN */
	public String getSecretArn() {
		return secretArn;
	}

	/** 设置密钥 ARN */
	public DbQueryParameter setSecretArn(String secretArn) {
		this.secretArn = secretArn;
		return this;
	}

	/** 获取数据库实例 ID */
	public String getDbInstanceId() {
		return dbInstanceId;
	}

	/** 设置数据库实例 ID */
	public DbQueryParameter setDbInstanceId(String dbInstanceId) {
		this.dbInstanceId = dbInstanceId;
		return this;
	}

	/** 获取数据库名称 */
	public String getDatabase() {
		return database;
	}

	/** 设置数据库名称 */
	public DbQueryParameter setDatabase(String database) {
		this.database = database;
		return this;
	}

	/** 获取 schema 名称 */
	public String getSchema() {
		return schema;
	}

	/** 设置 schema 名称 */
	public DbQueryParameter setSchema(String schema) {
		this.schema = schema;
		return this;
	}

	/** 获取表名 */
	public String getTable() {
		return table;
	}

	/** 设置表名 */
	public DbQueryParameter setTable(String table) {
		this.table = table;
		return this;
	}

	/** 获取表名匹配模式 */
	public String getTablePattern() {
		return tablePattern;
	}

	/** 设置表名匹配模式 */
	public DbQueryParameter setTablePattern(String tablePattern) {
		this.tablePattern = tablePattern;
		return this;
	}

	/** 获取表名列表 */
	public List<String> getTables() {
		return tables;
	}

	/** 设置表名列表 */
	public DbQueryParameter setTables(List<String> tables) {
		this.tables = tables;
		return this;
	}

	/** 获取列名 */
	public String getColumn() {
		return column;
	}

	/** 设置列名 */
	public DbQueryParameter setColumn(String column) {
		this.column = column;
		return this;
	}

	/** 获取 SQL 语句 */
	public String getSql() {
		return sql;
	}

	/** 设置 SQL 语句 */
	public DbQueryParameter setSql(String sql) {
		this.sql = sql;
		return this;
	}

	/**
	 * 从数据库配置 BO 构建查询参数对象。
	 * @param config 数据库配置 BO
	 * @return 转换后的查询参数对象
	 */
	public static DbQueryParameter from(DbConfigBO config) {
		DbQueryParameter param = new DbQueryParameter();
		BeanUtils.copyProperties(config, param);
		return param;
	}

	@Override
	public String toString() {
		return "DbQueryParameter{" + "aliuid='" + aliuid + '\'' + ", workspaceId='" + workspaceId + '\'' + ", region='"
				+ region + '\'' + ", secretArn='" + secretArn + '\'' + ", dbInstanceId='" + dbInstanceId + '\''
				+ ", database='" + database + '\'' + ", schema='" + schema + '\'' + ", table='" + table + '\''
				+ ", tablePattern='" + tablePattern + '\'' + ", tables=" + tables + ", column='" + column + '\''
				+ ", sql='" + sql + '\'' + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		DbQueryParameter that = (DbQueryParameter) o;
		return Objects.equals(aliuid, that.aliuid) && Objects.equals(workspaceId, that.workspaceId)
				&& Objects.equals(region, that.region) && Objects.equals(secretArn, that.secretArn)
				&& Objects.equals(dbInstanceId, that.dbInstanceId) && Objects.equals(database, that.database)
				&& Objects.equals(schema, that.schema) && Objects.equals(table, that.table)
				&& Objects.equals(tablePattern, that.tablePattern) && Objects.equals(tables, that.tables)
				&& Objects.equals(column, that.column) && Objects.equals(sql, that.sql);
	}

	@Override
	public int hashCode() {
		return Objects.hash(aliuid, workspaceId, region, secretArn, dbInstanceId, database, schema, table, tablePattern,
				tables, column, sql);
	}

}
