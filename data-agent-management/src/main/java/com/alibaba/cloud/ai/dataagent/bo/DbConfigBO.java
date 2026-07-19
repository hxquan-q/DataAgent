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
package com.alibaba.cloud.ai.dataagent.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库配置业务对象。
 * <p>
 * 封装数据库连接所需的核心配置信息，包括连接 URL、用户名、密码、 数据库 Schema、连接类型和方言类型等。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbConfigBO {

	/** 数据库 Schema 名称 */
	private String schema;

	/** 数据库 JDBC 连接 URL */
	private String url;

	/** 数据库用户名 */
	private String username;

	/** 数据库密码 */
	private String password;

	/** 数据库连接类型（如 JDBC、SDK 等） */
	private String connectionType;

	/** SQL 方言类型（如 MySQL、PostgreSQL 等） */
	private String dialectType;

}
