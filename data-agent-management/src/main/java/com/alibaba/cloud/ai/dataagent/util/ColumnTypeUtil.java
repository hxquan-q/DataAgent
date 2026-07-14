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
package com.alibaba.cloud.ai.dataagent.util;

/**
 * 数据库列类型映射工具类。
 * <p>
 * 将数据库原始列类型（如 decimal、int、varchar 等）归一化为前端易于识别的语义类型（number、text）， 用于 schema 展示与向量化等场景。
 * </p>
 */
public class ColumnTypeUtil {

	/**
	 * 将数据库列类型映射为语义化的前端类型。
	 * <ul>
	 * <li>数值类型（decimal/int/bigint/bool/bit/boolean/double）统一归为 "number"；</li>
	 * <li>字符串类型（varchar*/char*）统一归为 "text"；</li>
	 * <li>其它类型原样返回。</li>
	 * </ul>
	 * @param s 原始列类型名称，大小写不敏感
	 * @return 归一化后的语义类型字符串
	 */
	public static String wrapType(String s) {
		// 数值类型归一化为 number
		if (s.equalsIgnoreCase("decimal") || s.equalsIgnoreCase("int") || s.equalsIgnoreCase("bigint")
				|| s.equalsIgnoreCase("bool") || s.equalsIgnoreCase("bit") || s.equalsIgnoreCase("boolean")
				|| s.equalsIgnoreCase("double")) {
			return "number";
		}
		// 字符串类型归一化为 text
		else if (s.startsWith("varchar") || s.startsWith("char")) {
			return "text";
		}
		// 其它类型保持不变
		return s;
	}

}
