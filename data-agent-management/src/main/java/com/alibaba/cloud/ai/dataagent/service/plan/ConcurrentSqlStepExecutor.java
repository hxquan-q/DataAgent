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
package com.alibaba.cloud.ai.dataagent.service.plan;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.util.SqlGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 并发 SQL 步骤执行器（#10 执行接线）。
 *
 * <p>
 * 基于 {@link com.alibaba.cloud.ai.dataagent.util.PlanDependencyAnalyzer} 划分的独立波次， 用
 * {@code dbOperationExecutor} 线程池并发执行多个无依赖 SQL 步骤的「生成→护栏→执行」。 每步
 * fail-safe：单步失败只记录，不影响同波其它步骤。
 * </p>
 *
 * <p>
 * <b>并发通道取舍（运行时待完善）</b>：当前并发路径仅做 生成+只读护栏+执行， 暂不含语义一致性校验与图表渲染（避免每步额外 LLM
 * 调用）；启用并发（enableConcurrentSteps）时接受该取舍， 默认关闭即退回逐步串行（含全部既有特性）。
 * </p>
 *
 * @author xquan
 */
@Slf4j
@Service
public class ConcurrentSqlStepExecutor {

	private static final Duration GENERATE_TIMEOUT = Duration.ofSeconds(30);

	private final Nl2SqlService nl2SqlService;

	private final DatabaseUtil databaseUtil;

	private final ExecutorService dbOperationExecutor;

	public ConcurrentSqlStepExecutor(Nl2SqlService nl2SqlService, DatabaseUtil databaseUtil,
			@Qualifier("dbOperationExecutor") ExecutorService dbOperationExecutor) {
		this.nl2SqlService = nl2SqlService;
		this.databaseUtil = databaseUtil;
		this.dbOperationExecutor = dbOperationExecutor;
	}

	/**
	 * 并发执行一个独立 SQL 步骤波次，结果写入 results（key=step_N）。
	 * @param steps 同波次的无依赖 SQL 步骤
	 * @param agentId 智能体 ID（取 accessor）
	 * @param dbConfig 数据库配置
	 * @param schemaDTO schema 信息（生成 SQL 用）
	 * @param evidence 证据
	 * @param dialect 方言
	 * @param canonicalQuery 规范化查询
	 * @param results 累积执行结果（会被并发写入）
	 */
	public void executeWave(List<ExecutionStep> steps, Long agentId, DbConfigBO dbConfig, SchemaDTO schemaDTO,
			String evidence, String dialect, String canonicalQuery, Map<String, String> results) {
		if (steps == null || steps.isEmpty()) {
			return;
		}
		Accessor accessor = databaseUtil.getAgentAccessor(agentId);
		CompletableFuture<?>[] futures = steps.stream()
			.map(step -> CompletableFuture.runAsync(
					() -> executeStep(step, accessor, dbConfig, schemaDTO, evidence, dialect, canonicalQuery, results),
					dbOperationExecutor))
			.toArray(CompletableFuture[]::new);
		CompletableFuture.allOf(futures).join();
		log.info("并发执行波次完成，步骤数：{}", steps.size());
	}

	private void executeStep(ExecutionStep step, Accessor accessor, DbConfigBO dbConfig, SchemaDTO schemaDTO,
			String evidence, String dialect, String canonicalQuery, Map<String, String> results) {
		int stepNo = step.getStep();
		try {
			String instruction = step.getToolParameters() != null ? step.getToolParameters().getInstruction() : null;
			String sql = generateSqlBlocking(schemaDTO, evidence, dialect, canonicalQuery, instruction);
			if (sql == null || sql.isBlank()) {
				log.warn("并发步骤 {} 未生成 SQL，跳过", stepNo);
				return;
			}
			sql = nl2SqlService.sqlTrim(sql);
			// #17 只读护栏
			SqlGuard.GuardResult guard = SqlGuard.check(sql);
			if (!guard.allowed()) {
				log.warn("并发步骤 {} SQL 被只读护栏拦截：{}", stepNo, guard.reason());
				return;
			}
			DbQueryParameter param = new DbQueryParameter();
			param.setSql(sql);
			param.setSchema(dbConfig.getSchema());
			ResultSetBO resultSetBO = accessor.executeSqlAndReturnObject(dbConfig, param);
			String json = JsonUtil.getObjectMapper().writeValueAsString(resultSetBO);
			results.put("step_" + stepNo, json);
			if (step.getToolParameters() != null) {
				step.getToolParameters().setSqlQuery(sql);
			}
			log.info("并发步骤 {} 执行成功，行数：{}", stepNo, resultSetBO.getData() != null ? resultSetBO.getData().size() : 0);
		}
		catch (Exception e) {
			log.warn("并发执行步骤 {} 失败，跳过：{}", stepNo, e.getMessage());
		}
	}

	private String generateSqlBlocking(SchemaDTO schemaDTO, String evidence, String dialect, String canonicalQuery,
			String instruction) {
		SqlGenerationDTO dto = SqlGenerationDTO.builder()
			.evidence(evidence)
			.query(canonicalQuery)
			.schemaDTO(schemaDTO)
			.executionDescription(instruction)
			.dialect(dialect)
			.build();
		return nl2SqlService.generateSql(dto)
			.collect(StringBuilder::new, StringBuilder::append)
			.map(StringBuilder::toString)
			.block(GENERATE_TIMEOUT);
	}

}
