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

import com.alibaba.cloud.ai.dataagent.entity.Metric;
import com.alibaba.cloud.ai.dataagent.service.semantic.MetricService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 指标定义配置控制器（NL2Semantic2SQL 指标层）。
 * <p>
 * 管理指标定义的 CRUD，并对外提供按 agent+datasource 过滤的候选指标查询。 候选指标供 SemanticParse 节点收敛 LLM 选择空间。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/metric")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class MetricController {

	private final MetricService metricService;

	@GetMapping("/list")
	public ApiResponse<List<Metric>> list() {
		List<Metric> result = metricService.list();
		return ApiResponse.success("success list metric", result);
	}

	@GetMapping("/{id}")
	public ApiResponse<Metric> getById(@PathVariable(value = "id") Long id) {
		Metric metric = metricService.getById(id);
		return ApiResponse.success("success retrieve metric", metric);
	}

	@GetMapping("/code/{code}")
	public ApiResponse<Metric> getByCode(@PathVariable(value = "code") String code) {
		Metric metric = metricService.getByCode(code);
		return ApiResponse.success("success retrieve metric by code", metric);
	}

	@PostMapping
	public ApiResponse<Long> create(@RequestBody Metric metric) {
		Long id = metricService.create(metric);
		return ApiResponse.success("Metric created successfully", id);
	}

	@PutMapping
	public ApiResponse<Boolean> update(@RequestBody Metric metric) {
		metricService.update(metric);
		return ApiResponse.success("Metric updated successfully", true);
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Boolean> delete(@PathVariable(value = "id") Long id) {
		metricService.delete(id);
		return ApiResponse.success("Metric deleted successfully", true);
	}

	@GetMapping("/candidates")
	public ApiResponse<List<Metric>> candidates(@RequestParam("agentId") Integer agentId,
			@RequestParam("datasourceId") Integer datasourceId) {
		List<Metric> result = metricService.candidates(agentId, datasourceId);
		return ApiResponse.success("success list candidate metric", result);
	}

}
