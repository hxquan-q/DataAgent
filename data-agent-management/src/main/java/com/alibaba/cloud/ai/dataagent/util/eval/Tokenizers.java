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
package com.alibaba.cloud.ai.dataagent.util.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * 评测分词器。替代 Tencent/WeKnora 的 jieba+正则方案——WeKnora 面向 CJK 自然语言，DataAgent 评测对象是
 * SQL / schema 标识符，故采用面向 SQL 的归一化分词。
 * <p>
 * 分词与指标（{@link Bleu}/{@link Rouge}）解耦：指标只消费 token 列表，分词策略可替换。
 * </p>
 */
public final class Tokenizers {

	private Tokenizers() {
	}

	/**
	 * SQL 归一化分词：小写，按"非标识符字符"（非字母/数字/下划线）切分——保留 {@code order_count}、{@code t1.id}
	 * 中的标识符段，丢弃操作符/括号/逗号/分号。适配 NL2SQL 评测（比较关键字与标识符 token 袋）。
	 * @param sql 原始 SQL
	 * @return token 列表（已小写）
	 */
	public static List<String> sql(String sql) {
		if (sql == null || sql.isBlank()) {
			return List.of();
		}
		String lower = sql.toLowerCase();
		List<String> tokens = new ArrayList<>();
		for (String t : lower.split("[^a-z0-9_]+")) {
			if (!t.isEmpty()) {
				tokens.add(t);
			}
		}
		return tokens;
	}

	/** 通用英文分词：小写、按空白切。 */
	public static List<String> words(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		String lower = text.toLowerCase().trim();
		List<String> tokens = new ArrayList<>();
		for (String t : lower.split("\\s+")) {
			if (!t.isEmpty()) {
				tokens.add(t);
			}
		}
		return tokens;
	}

}
