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

import com.alibaba.cloud.ai.dataagent.entity.MetricVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 指标口径版本 Mapper，操作 {@code metric_version} 表。
 *
 * @author dataagent
 */
@Mapper
public interface MetricVersionMapper {

	@Select("SELECT * FROM metric_version WHERE id = #{id}")
	MetricVersion selectById(@Param("id") Long id);

	/**
	 * 按指标ID查询全部口径版本（SemanticLayerLoader 加载用）。
	 * @param metricId 指标ID
	 * @return 口径版本列表
	 */
	@Select("SELECT * FROM metric_version WHERE metric_id = #{metricId} AND status = 1 ORDER BY is_default DESC")
	List<MetricVersion> selectByMetricId(@Param("metricId") Long metricId);

	/**
	 * 查询指标的默认口径版本。
	 * @param metricId 指标ID
	 * @return 默认口径版本；不存在返回 {@code null}
	 */
	@Select("SELECT * FROM metric_version WHERE metric_id = #{metricId} AND is_default = 1 AND status = 1 LIMIT 1")
	MetricVersion selectDefaultByMetricId(@Param("metricId") Long metricId);

	@Insert("""
			INSERT INTO metric_version (metric_id, ver_code, time_field, filter_condition, is_default,
			  description, status, created_time, updated_time)
			VALUES (#{metricId}, #{verCode}, #{timeField}, #{filterCondition}, #{isDefault},
			  #{description}, #{status}, NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(MetricVersion version);

	@Update("""
			<script>
			UPDATE metric_version
			<set>
				<if test="verCode != null">ver_code = #{verCode},</if>
				<if test="timeField != null">time_field = #{timeField},</if>
				<if test="filterCondition != null">filter_condition = #{filterCondition},</if>
				<if test="isDefault != null">is_default = #{isDefault},</if>
				<if test="description != null">description = #{description},</if>
				<if test="status != null">status = #{status},</if>
				updated_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(MetricVersion version);

	@Delete("DELETE FROM metric_version WHERE id = #{id}")
	int deleteById(@Param("id") Long id);

}
