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
package com.alibaba.cloud.ai.dataagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图执行请求 DTO
 *
 * <p>
 * 封装提交给工作流图（Graph）执行的请求参数，包括智能体ID、会话ID、用户查询、 人工反馈以及执行模式控制等信息。
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GraphRequest {

	/** 智能体ID */
	private String agentId;

	/** 会话ID（线程ID） */
	private String threadId;

	/** 用户查询内容 */
	private String query;

	/** 是否包含人工反馈 */
	private boolean humanFeedback;

	/** 人工反馈内容 */
	private String humanFeedbackContent;

	/** 是否拒绝当前执行计划 */
	private boolean rejectedPlan;

	/** 是否仅执行 NL2SQL（不生成报告） */
	private boolean nl2sqlOnly;

}
