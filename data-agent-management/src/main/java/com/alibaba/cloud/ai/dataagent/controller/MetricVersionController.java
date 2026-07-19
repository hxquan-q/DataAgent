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

import com.alibaba.cloud.ai.dataagent.entity.MetricVersion;
import com.alibaba.cloud.ai.dataagent.service.semantic.MetricVersionService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 指标口径版本配置控制器（NL2Semantic2SQL 指标层）。
 * <p>
 * 管理同一指标下多口径版本（如金额含税/不含税）的 CRUD，供管理端维护口径定义。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/metric-version")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class MetricVersionController {

	private final MetricVersionService metricVersionService;

	@GetMapping("/metric/{metricId}")
	public ApiResponse<List<MetricVersion>> listByMetricId(@PathVariable(value = "metricId") Long metricId) {
		List<MetricVersion> result = metricVersionService.listByMetricId(metricId);
		return ApiResponse.success("success list metric version", result);
	}

	@GetMapping("/{id}")
	public ApiResponse<MetricVersion> getById(@PathVariable(value = "id") Long id) {
		MetricVersion version = metricVersionService.getById(id);
		return ApiResponse.success("success retrieve metric version", version);
	}

	@PostMapping
	public ApiResponse<Long> create(@RequestBody MetricVersion version) {
		Long id = metricVersionService.create(version);
		return ApiResponse.success("Metric version created successfully", id);
	}

	@PutMapping
	public ApiResponse<Boolean> update(@RequestBody MetricVersion version) {
		metricVersionService.update(version);
		return ApiResponse.success("Metric version updated successfully", true);
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Boolean> delete(@PathVariable(value = "id") Long id) {
		metricVersionService.delete(id);
		return ApiResponse.success("Metric version deleted successfully", true);
	}

}
