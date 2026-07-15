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
package com.alibaba.cloud.ai.dataagent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果 VO
 *
 * <p>
 * 表示批量导入操作的结果汇总，包含总数、成功数、失败数和错误信息列表。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportResult {

	/** 总记录数 */
	private int total;

	/** 成功记录数 */
	private int successCount;

	/** 失败记录数 */
	private int failCount;

	/** 错误信息列表 */
	@Builder.Default
	private List<String> errors = new ArrayList<>();

	public void addError(String error) {
		if (errors == null) {
			errors = new ArrayList<>();
		}
		errors.add(error);
	}

}
