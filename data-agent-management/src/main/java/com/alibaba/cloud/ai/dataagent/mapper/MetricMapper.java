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
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.Metric;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 指标定义 Mapper，操作 {@code metric} 表（NL2Semantic2SQL 指标层）。
 *
 * @author dataagent
 */
@Mapper
public interface MetricMapper {

	@Select("SELECT * FROM metric ORDER BY created_time DESC")
	List<Metric> selectAll();

	@Select("SELECT * FROM metric WHERE id = #{id}")
	Metric selectById(@Param("id") Long id);

	@Select("SELECT * FROM metric WHERE metric_code = #{metricCode}")
	Metric selectByCode(@Param("metricCode") String metricCode);

	/**
	 * 查询候选指标（SemanticLayerLoader 上下文过滤用，仅启用项）。
	 * @param agentId 智能体ID
	 * @param datasourceId 数据源ID
	 * @return 候选指标列表
	 */
	@Select("""
			SELECT * FROM metric
			WHERE agent_id = #{agentId} AND datasource_id = #{datasourceId} AND status = 1
			ORDER BY updated_time DESC
			""")
	List<Metric> selectCandidates(@Param("agentId") Integer agentId, @Param("datasourceId") Integer datasourceId);

	@Insert("""
			INSERT INTO metric (metric_code, metric_name, agent_id, datasource_id, source_table,
			  agg_field, agg_func, default_time_field, sql_template, description, status, created_time, updated_time)
			VALUES (#{metricCode}, #{metricName}, #{agentId}, #{datasourceId}, #{sourceTable},
			  #{aggField}, #{aggFunc}, #{defaultTimeField}, #{sqlTemplate}, #{description}, #{status}, NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Metric metric);

	@Update("""
			<script>
			UPDATE metric
			<set>
				<if test="metricCode != null">metric_code = #{metricCode},</if>
				<if test="metricName != null">metric_name = #{metricName},</if>
				<if test="agentId != null">agent_id = #{agentId},</if>
				<if test="datasourceId != null">datasource_id = #{datasourceId},</if>
				<if test="sourceTable != null">source_table = #{sourceTable},</if>
				<if test="aggField != null">agg_field = #{aggField},</if>
				<if test="aggFunc != null">agg_func = #{aggFunc},</if>
				<if test="defaultTimeField != null">default_time_field = #{defaultTimeField},</if>
				<if test="sqlTemplate != null">sql_template = #{sqlTemplate},</if>
				<if test="description != null">description = #{description},</if>
				<if test="status != null">status = #{status},</if>
				updated_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(Metric metric);

	@Delete("DELETE FROM metric WHERE id = #{id}")
	int deleteById(@Param("id") Long id);

}
