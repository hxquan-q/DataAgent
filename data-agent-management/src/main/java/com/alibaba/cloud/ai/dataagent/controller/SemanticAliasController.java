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

import com.alibaba.cloud.ai.dataagent.entity.SemanticAlias;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticAliasService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 语义别名配置控制器（NL2Semantic2SQL 语义治理层）。
 * <p>
 * 管理业务黑话→结构化 code 的别名 CRUD，并对外提供别名消歧查询，供管理端维护业务术语映射。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/semantic-alias")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class SemanticAliasController {

	private final SemanticAliasService semanticAliasService;

	@GetMapping("/agent/{agentId}")
	public ApiResponse<List<SemanticAlias>> listByAgentId(@PathVariable(value = "agentId") Integer agentId) {
		List<SemanticAlias> result = semanticAliasService.listByAgentId(agentId);
		return ApiResponse.success("success list semantic alias", result);
	}

	@GetMapping("/resolve")
	public ApiResponse<SemanticAlias> resolve(@RequestParam("agentId") Integer agentId,
			@RequestParam("text") String text) {
		SemanticAlias alias = semanticAliasService.resolve(agentId, text);
		return ApiResponse.success("success resolve semantic alias", alias);
	}

	@PostMapping
	public ApiResponse<Long> create(@RequestBody SemanticAlias alias) {
		Long id = semanticAliasService.create(alias);
		return ApiResponse.success("Semantic alias created successfully", id);
	}

	@PutMapping
	public ApiResponse<Boolean> update(@RequestBody SemanticAlias alias) {
		semanticAliasService.update(alias);
		return ApiResponse.success("Semantic alias updated successfully", true);
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Boolean> delete(@PathVariable(value = "id") Long id) {
		semanticAliasService.delete(id);
		return ApiResponse.success("Semantic alias deleted successfully", true);
	}

}
