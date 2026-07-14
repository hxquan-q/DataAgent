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
package com.alibaba.cloud.ai.dataagent.bo.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SQL 查询结果集业务对象。
 * <p>
 * 封装 SQL 执行后的列名列表和数据行（每行为列名到值的映射），
 * 同时支持深拷贝以便在工作流中安全传递。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class ResultSetBO implements Cloneable {

	/** 列名列表 */
	private List<String> column;

	/** 数据行列表，每行为列名到值的映射 */
	private List<Map<String, String>> data;

	/** 错误信息（执行失败时设置） */
	private String errorMsg;

	/**
	 * 深拷贝当前结果集。
	 * @return 新的结果集实例，包含所有列和数据的副本
	 */
	@Override
	public ResultSetBO clone() {
		return ResultSetBO.builder()
			.column(new ArrayList<>(this.column))
			.data(this.data.stream().map(HashMap::new).collect(Collectors.toList()))
			.build();
	}

}
