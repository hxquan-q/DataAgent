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

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * 数据库访问类型枚举。
 * <p>
 * 定义系统支持的数据库访问方式，包括 JDBC 连接、SDK 接入、数据 API、函数计算 HTTP 以及内存模式。
 */
public enum DbAccessTypeEnum {

	/** 通过 JDBC 驱动访问 */
	JDBC("jdbc"),

	/** 通过 SDK 访问 */
	SDK("sdk"),

	/** 通过数据 API 访问 */
	DATA_API("data-api"),

	/** 通过函数计算 HTTP 接口访问 */
	FC_HTTP("fc-http"),

	/** 内存模式访问（无真实数据库） */
	MEMORY("in-memory");

	private String code;

	/**
	 * 构造数据库访问类型枚举。
	 * @param code 访问类型编码标识
	 */
	DbAccessTypeEnum(String code) {
		this.code = code;
	}

	/**
	 * 根据编码获取对应的访问类型枚举。
	 * @param code 访问类型编码标识
	 * @return 匹配的枚举实例，编码为空或未匹配时返回 {@code null}
	 */
	public static DbAccessTypeEnum of(String code) {
		if (StringUtils.isBlank(code)) {
			return null;
		}

		Optional<DbAccessTypeEnum> any = Arrays.stream(values())
			.filter(typeEnum -> code.equals(typeEnum.getCode()))
			.findAny();

		return any.orElse(null);
	}

	/**
	 * 获取访问类型编码标识。
	 * @return 访问类型编码
	 */
	public String getCode() {
		return code;
	}

}
