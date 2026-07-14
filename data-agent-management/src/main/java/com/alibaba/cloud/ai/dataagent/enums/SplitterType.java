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

import lombok.Getter;

/**
 * 文本分块器类型枚举。
 * <p>
 * 定义知识库文档分块（Chunking）时使用的不同策略类型。
 */
@Getter
public enum SplitterType {

	/** 基于 Token 数量的分块 */
	TOKEN("token"),
	/** 基于递归字符的分块 */
	RECURSIVE("recursive"),
	/** 基于句子的分块 */
	SENTENCE("sentence"),
	/** 基于段落的分块 */
	PARAGRAPH("paragraph"),
	/** 基于语义的分块 */
	SEMANTIC("semantic");

	private final String value;

	/**
	 * 构造分块器类型枚举。
	 * @param value 分块策略标识
	 */
	SplitterType(String value) {
		this.value = value;
	}

}
