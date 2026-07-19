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

import java.util.Optional;

/**
 * 数据库 SQL 方言枚举。
 * <p>
 * 定义系统支持的各类数据库方言标识，用于 SQL 语法适配和方言路由。
 */
public enum DatabaseDialectEnum {

	/** MySQL 方言 */
	MYSQL("MySQL"),

	/** SQLite 方言 */
	SQLite("SQLite"),

	/** PostgreSQL 方言 */
	POSTGRESQL("PostgreSQL"),

	/** H2 方言 */
	H2("H2"),

	/** 达梦（Dameng）方言 */
	DAMENG("Dameng"),

	/** SQL Server 方言 */
	SQL_SERVER("SqlServer"),

	/** Oracle 方言 */
	ORACLE("Oracle"),

	/** Hive 方言 */
	HIVE("Hive");

	/** 方言编码标识 */
	public final String code;

	/**
	 * 构造数据库方言枚举。
	 * @param code 方言编码标识
	 */
	DatabaseDialectEnum(String code) {
		this.code = code;
	}

	/**
	 * 获取方言编码标识。
	 * @return 方言编码
	 */
	public String getCode() {
		return code;
	}

	/**
	 * 根据编码获取对应的方言枚举。
	 * @param code 方言编码标识
	 * @return 匹配的方言枚举（可能为空）
	 */
	public static Optional<DatabaseDialectEnum> getByCode(String code) {
		for (DatabaseDialectEnum value : values()) {
			if (value.code.equals(code)) {
				return Optional.of(value);
			}
		}
		return Optional.empty();
	}

}
