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

/**
 * 模型就绪状态检查 VO
 *
 * <p>
 * 表示对话模型和向量模型的连接检查结果，用于前端展示模型配置的健康状态。
 * </p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModelCheckVo {

	/** 对话模型是否就绪 */
	boolean chatModelReady;

	/** 向量模型是否就绪 */
	boolean embeddingModelReady;

	/** 整体是否就绪（对话模型和向量模型均就绪时为 true） */
	boolean ready;

}
