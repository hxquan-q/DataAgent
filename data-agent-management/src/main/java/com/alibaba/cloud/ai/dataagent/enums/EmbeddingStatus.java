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
 * 向量化嵌入状态枚举。
 * <p>
 * 表示知识库文档的向量化处理生命周期状态，用于跟踪异步嵌入任务的进度。
 */
@Getter
public enum EmbeddingStatus {

	/** 待处理：任务已创建，尚未开始执行 */
	PENDING("PENDING"),
	/** 处理中：正在执行向量化 */
	PROCESSING("PROCESSING"),
	/** 已完成：向量化成功结束 */
	COMPLETED("COMPLETED"),
	/** 已失败：向量化过程中发生错误 */
	FAILED("FAILED");

	private final String value;

	/**
	 * 构造嵌入状态枚举。
	 * @param value 状态值字符串
	 */
	EmbeddingStatus(String value) {
		this.value = value;
	}

	/**
	 * 根据字符串值获取对应的枚举实例。
	 * @param value 状态值字符串
	 * @return 匹配的嵌入状态枚举
	 * @throws IllegalArgumentException 如果传入的状态值无法匹配任何枚举
	 */
	public static EmbeddingStatus fromValue(String value) {
		for (EmbeddingStatus status : EmbeddingStatus.values()) {
			// 严格比对状态值
			if (status.value.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("未知的嵌入状态: " + value);
	}

}
