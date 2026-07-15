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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.entity.QueryLog;
import com.alibaba.cloud.ai.dataagent.service.semantic.QueryLogService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 查询日志（证据链）回放控制器（datafoundry 式 Run Trace）。
 * <p>
 * 对外提供「按会话回放证据链」与「单条证据链详情」查询，并支持用户对单次查询记录反馈打标， 便于审计追溯与口径优化闭环。查询能力依赖 {@code query_log} 表中
 * NL2Semantic2SQL 链路写入的快照。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
@RestController
@RequestMapping("/api/query-log")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class QueryLogController {

	private final QueryLogService queryLogService;

	/**
	 * 按会话ID查询证据链列表（回放追溯）。
	 * @param sessionId 会话ID
	 * @return 该会话下的查询日志列表（按时间倒序）
	 */
	@GetMapping("/session/{sessionId}")
	public ApiResponse<List<QueryLog>> getBySessionId(@PathVariable(value = "sessionId") String sessionId) {
		List<QueryLog> result = queryLogService.getBySessionId(sessionId);
		return ApiResponse.success("success list query log by session", result);
	}

	/**
	 * 按主键 ID 查询单条证据链详情。
	 * @param id 日志主键
	 * @return 单条查询日志
	 */
	@GetMapping("/{id}")
	public ApiResponse<QueryLog> getById(@PathVariable(value = "id") Long id) {
		QueryLog queryLog = queryLogService.getById(id);
		return ApiResponse.success("success retrieve query log", queryLog);
	}

	/**
	 * 记录用户对某条查询日志的反馈。
	 * @param id 日志主键
	 * @param feedback 反馈值（0=中性，1=正面，2=负面）
	 * @return 操作结果
	 */
	@PutMapping("/{id}/feedback")
	public ApiResponse<Boolean> recordFeedback(@PathVariable(value = "id") Long id,
			@RequestParam("feedback") Integer feedback) {
		queryLogService.recordFeedback(id, feedback);
		return ApiResponse.success("feedback recorded successfully", true);
	}

}
