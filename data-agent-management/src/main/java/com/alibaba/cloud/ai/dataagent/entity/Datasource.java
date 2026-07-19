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
package com.alibaba.cloud.ai.dataagent.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 数据源实体类
 *
 * <p>
 * 描述一个外部数据库连接的完整信息，包括数据库类型、连接地址、账号密码等。 数据源可被多个智能体关联使用，用于执行 NL2SQL 等数据查询场景。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Datasource {

	/** 主键ID */
	private Integer id;

	/** 数据源名称 */
	private String name;

	/** 数据库类型（如 mysql、postgresql、clickhouse 等） */
	private String type;

	/** 数据库主机地址 */
	private String host;

	/** 数据库端口 */
	private Integer port;

	/** 数据库名称 */
	private String databaseName;

	/** 数据库用户名 */
	private String username;

	/** 数据库密码（仅写入，不序列化输出） */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String password;

	/** 完整的 JDBC 连接URL（仅写入，不序列化输出） */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String connectionUrl;

	/** 数据源状态 */
	private String status;

	/** 连接测试状态 */
	private String testStatus;

	/** 数据源描述 */
	private String description;

	/** 创建者ID */
	private Long creatorId;

	/** 创建时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime createTime;

	/** 更新时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime updateTime;

	@Override
	public String toString() {
		return "Datasource{" + "id=" + id + ", name='" + name + '\'' + ", type='" + type + '\'' + ", host='" + host
				+ '\'' + ", port=" + port + ", databaseName='" + databaseName + '\'' + ", status='" + status + '\''
				+ ", testStatus='" + testStatus + '\'' + ", createTime=" + createTime + ", updateTime=" + updateTime
				+ '}';
	}

}
