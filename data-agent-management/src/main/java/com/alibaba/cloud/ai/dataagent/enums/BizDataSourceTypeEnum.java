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
package com.alibaba.cloud.ai.dataagent.enums;

/**
 * 业务数据源类型枚举。
 * <p>
 * 定义系统支持的各种数据库类型，包含类型编码、类型名称、方言标识和访问协议。 用于数据源配置、连接池管理和 SQL 方言路由。
 */
public enum BizDataSourceTypeEnum {

	/** MySQL 数据库 */
	MYSQL(1, "mysql", DatabaseDialectEnum.MYSQL.getCode(), DbAccessTypeEnum.JDBC.getCode()),

	/** PostgreSQL 数据库 */
	POSTGRESQL(2, "postgresql", DatabaseDialectEnum.POSTGRESQL.getCode(), DbAccessTypeEnum.JDBC.getCode()),

	/** SQLite 数据库 */
	SQLITE(3, "sqlite", DatabaseDialectEnum.MYSQL.getCode(), DbAccessTypeEnum.JDBC.getCode()),

	/** H2 内存数据库 */
	H2(4, "h2", DatabaseDialectEnum.H2.getCode(), DbAccessTypeEnum.JDBC.getCode()),

	/** 达梦（Dameng）数据库 */
	DAMENG(5, "dameng", DatabaseDialectEnum.DAMENG.getCode(), DbAccessTypeEnum.JDBC.getCode()),

	/** SQL Server 数据库 */
	SQL_SERVER(6, "sqlserver", DatabaseDialectEnum.SQL_SERVER.getCode(), DbAccessTypeEnum.JDBC.getCode()),

	/** Oracle 数据库 */
	ORACLE(7, "oracle", DatabaseDialectEnum.ORACLE.getCode(), DbAccessTypeEnum.JDBC.getCode()),

	/** Hive 数据仓库 */
	HIVE(8, "hive", DatabaseDialectEnum.HIVE.getCode(), DbAccessTypeEnum.JDBC.getCode()),

	/** Hologres 实时数仓（兼容 PostgreSQL 协议） */
	HOLOGRESS(10, "hologress", DatabaseDialectEnum.POSTGRESQL.getCode(), DbAccessTypeEnum.JDBC.getCode()),

	/** VPC 内网环境的 MySQL 数据库 */
	MYSQL_VPC(11, "mysql-vpc", DatabaseDialectEnum.MYSQL.getCode(), DbAccessTypeEnum.JDBC.getCode()),

	/** VPC 内网环境的 PostgreSQL 数据库 */
	POSTGRESQL_VPC(12, "postgresql-vpc", DatabaseDialectEnum.POSTGRESQL.getCode(), DbAccessTypeEnum.JDBC.getCode()),

	/** AnalyticDB PostgreSQL（通过数据 API 访问） */
	ADB_PG(21, "adg_pg", DatabaseDialectEnum.POSTGRESQL.getCode(), DbAccessTypeEnum.DATA_API.getCode()),

	/** MaxCompute 大数据计算服务 */
	MAX_COMPUTE(31, "max_compute", DatabaseDialectEnum.MYSQL.getCode(), DbAccessTypeEnum.JDBC.getCode()),

	/** 函数计算中的 SQLite 模拟数据库（通过 FC HTTP 访问） */
	FC_MEMORY_DB(41, "fc_memory_db", DatabaseDialectEnum.SQLite.getCode(), DbAccessTypeEnum.FC_HTTP.getCode()),

	/** 虚拟 MySQL 数据库（内存模式） */
	MYSQL_VIRTUAL(51, "mysql-virtual", DatabaseDialectEnum.MYSQL.getCode(), DbAccessTypeEnum.MEMORY.getCode()),

	/** 虚拟 PostgreSQL 数据库（内存模式） */
	POSTGRESQL_VIRTUAL(52, "postgresql-virtual", DatabaseDialectEnum.POSTGRESQL.getCode(),
			DbAccessTypeEnum.MEMORY.getCode());

	/** 数据源类型编码 */
	public final Integer code;

	/** 数据源类型名称 */
	public final String typeName;

	/** SQL 方言标识 */
	public final String dialect;

	/** 访问协议标识 */
	public final String protocol;

	/**
	 * 构造数据源类型枚举。
	 * @param code 类型编码
	 * @param typeName 类型名称
	 * @param dialect SQL 方言
	 * @param protocol 访问协议
	 */
	BizDataSourceTypeEnum(Integer code, String typeName, String dialect, String protocol) {
		this.code = code;
		this.typeName = typeName;
		this.dialect = dialect;
		this.protocol = protocol;
	}

	/**
	 * 获取数据源类型编码。
	 * @return 类型编码
	 */
	public Integer getCode() {
		return code;
	}

	/**
	 * 获取数据源类型名称。
	 * @return 类型名称
	 */
	public String getTypeName() {
		return typeName;
	}

	/**
	 * 获取访问协议。
	 * @return 访问协议标识
	 */
	public String getProtocol() {
		return protocol;
	}

	/**
	 * 获取 SQL 方言。
	 * @return SQL 方言标识
	 */
	public String getDialect() {
		return dialect;
	}

	/**
	 * 根据编码获取对应的数据源类型名称。
	 * @param code 类型编码
	 * @return 对应的类型名称，未找到时返回 {@code null}
	 */
	public static String getTypeNameByCode(Integer code) {
		for (BizDataSourceTypeEnum type : values()) {
			if (type.getCode().equals(code)) {
				return type.getTypeName();
			}
		}
		return null; // 未找到对应编码时返回 null
	}

	/**
	 * 根据编码获取对应的 SQL 方言。
	 * @param code 类型编码
	 * @return 对应的 SQL 方言，未找到时返回 {@code null}
	 */
	public static String getDialectByCode(Integer code) {
		for (BizDataSourceTypeEnum type : values()) {
			if (type.getCode().equals(code)) {
				return type.getDialect();
			}
		}
		return null; // 未找到对应编码时返回 null
	}

	/**
	 * 根据编码获取对应的访问协议。
	 * @param code 类型编码
	 * @return 对应的访问协议，未找到时返回 {@code null}
	 */
	public static String getProtocolByCode(Integer code) {
		for (BizDataSourceTypeEnum type : values()) {
			if (type.getCode().equals(code)) {
				return type.getProtocol();
			}
		}
		return null;
	}

	/**
	 * 根据编码获取对应的枚举实例。
	 * @param code 类型编码
	 * @return 对应的枚举实例，未找到时返回 {@code null}
	 */
	public static BizDataSourceTypeEnum fromCode(Integer code) {
		for (BizDataSourceTypeEnum type : values()) {
			if (type.getCode() == code) {
				return type;
			}
		}
		return null;
	}

	/**
	 * 根据类型名称获取对应的枚举实例。
	 * @param typeName 类型名称
	 * @return 对应的枚举实例，未找到时返回 {@code null}
	 */
	public static BizDataSourceTypeEnum fromTypeName(String typeName) {
		for (BizDataSourceTypeEnum type : values()) {
			if (type.getTypeName().equals(typeName)) {
				return type;
			}
		}
		return null;
	}

	/**
	 * 判断给定类型名称是否为 MySQL 方言。
	 * @param typeName 数据源类型名称
	 * @return 如果是 MySQL 方言返回 {@code true}，否则返回 {@code false}
	 */
	public static boolean isMysqlDialect(String typeName) {
		return isDialect(typeName, DatabaseDialectEnum.MYSQL.getCode());
	}

	/**
	 * 判断给定类型名称是否为 SQL Server 方言。
	 * @param typeName 数据源类型名称
	 * @return 如果是 SQL Server 方言返回 {@code true}，否则返回 {@code false}
	 */
	public static boolean isSqlServerDialect(String typeName) {
		return isDialect(typeName, DatabaseDialectEnum.SQL_SERVER.getCode());
	}

	/**
	 * 判断给定类型名称是否为 PostgreSQL 方言。
	 * @param typeName 数据源类型名称
	 * @return 如果是 PostgreSQL 方言返回 {@code true}，否则返回 {@code false}
	 */
	public static boolean isPgDialect(String typeName) {
		return isDialect(typeName, DatabaseDialectEnum.POSTGRESQL.getCode());
	}

	/**
	 * 判断给定类型名称是否为 Oracle 方言。
	 * @param typeName 数据源类型名称
	 * @return 如果是 Oracle 方言返回 {@code true}，否则返回 {@code false}
	 */
	public static boolean isOracleDialect(String typeName) {
		return isDialect(typeName, DatabaseDialectEnum.ORACLE.getCode());
	}

	/**
	 * 判断给定类型名称是否为 AnalyticDB PostgreSQL。
	 * <p>
	 * 需同时满足：方言为 PostgreSQL 且协议为 DATA_API。
	 * @param typeName 数据源类型名称
	 * @return 如果是 ADB PG 返回 {@code true}，否则返回 {@code false}
	 */
	public static boolean isAdbPg(String typeName) {
		BizDataSourceTypeEnum te = fromTypeName(typeName);
		if (te == null) {
			return false;
		}
		// 同时满足 PostgreSQL 方言和 DATA_API 协议才判定为 ADB PG
		if (DatabaseDialectEnum.POSTGRESQL.getCode().equalsIgnoreCase(te.getDialect())
				&& DbAccessTypeEnum.DATA_API.getCode().equalsIgnoreCase(te.getProtocol())) {
			return true;
		}
		return false;
	}

	/**
	 * 判断给定类型名称是否使用指定的 SQL 方言。
	 * @param typeName 数据源类型名称
	 * @param dialect 目标方言标识
	 * @return 方言匹配返回 {@code true}，否则返回 {@code false}
	 */
	public static boolean isDialect(String typeName, String dialect) {
		BizDataSourceTypeEnum te = fromTypeName(typeName);
		if (te == null) {
			return false;
		}
		if (dialect.equalsIgnoreCase(te.getDialect())) {
			return true;
		}
		return false;
	}

}
