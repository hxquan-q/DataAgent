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

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文本块类型枚举。
 * <p>
 * 定义大模型输出文本中可解析的各类型标记块（JSON、Python、SQL 等）， 每种类型通过起始标记和结束标记来界定内容范围。
 */
@AllArgsConstructor
@Getter
public enum TextType {

	/** JSON 代码块 */
	JSON("$$$json", "$$$"),

	/** Python 代码块 */
	PYTHON("$$$python", "$$$"),

	// LLM 模型倾向于输出 ```sql 标记，因此使用自定义标记替代
	/** SQL 代码块 */
	SQL("$$$sql", "$$$"),

	/** Markdown 报告块 */
	MARK_DOWN("$$$markdown-report", "$$$/markdown-report"),

	/** 结果集块 */
	RESULT_SET("$$$result_set", "$$$"),

	/** 普通文本（无特殊标记） */
	TEXT(null, null);

	/** 起始标记 */
	private final String startSign;

	/** 结束标记 */
	private final String endSign;

	/**
	 * 根据当前文本块类型和读取到的片段推断新的文本块类型。
	 * <p>
	 * 当当前处于普通文本（TEXT）状态时，检查是否匹配某个类型的起始标记； 当当前处于特定类型时，检查是否匹配该类型的结束标记。
	 * @param origin 当前文本块类型
	 * @param chuck 当前读取到的标记片段
	 * @return 推断后的文本块类型
	 */
	public static TextType getType(TextType origin, String chuck) {
		if (origin == TEXT) {
			// 当前为普通文本，检查是否进入某个特殊类型
			for (TextType type : TextType.values()) {
				if (chuck.equals(type.startSign)) {
					return type;
				}
			}
		}
		else {
			// 当前为特殊类型，检查是否匹配结束标记回到普通文本
			if (chuck.equals(origin.endSign)) {
				return TextType.TEXT;
			}
		}
		return origin;
	}

	/**
	 * 根据起始标记获取对应的文本块类型。
	 * @param startSign 起始标记字符串
	 * @return 匹配的文本块类型，无匹配时返回 {@link #TEXT}
	 */
	public static TextType getTypeByStratSign(String startSign) {
		for (TextType type : TextType.values()) {
			if (startSign.equals(type.startSign)) {
				return type;
			}
		}
		return TextType.TEXT;
	}

}
