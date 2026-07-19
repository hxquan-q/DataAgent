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
package com.alibaba.cloud.ai.dataagent.service.chart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.bo.schema.DisplayStyleBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 图表渲染服务（#6）。
 *
 * <p>
 * 基于结果集数据与图表展示配置（DisplayStyleBO，复用既有 LLM 推荐的 type/x/y）， 调用 {@link G2SsrClient} 渲染 PNG
 * 并返回可嵌入报告的图片 URL。数值字符串会被转成数字以保证坐标轴正确。
 * </p>
 *
 * @author xquan
 */
@Slf4j
@Service
@AllArgsConstructor
public class ChartRenderService {

	private final G2SsrClient g2SsrClient;

	/**
	 * 渲染结果集图表。
	 * @param resultSetBO SQL 结果集（含 data）
	 * @param displayStyle 图表展示配置（type/title/x/y）
	 * @return 图片 URL；type 为 table、数据为空或渲染失败时返回 null
	 */
	public String render(ResultSetBO resultSetBO, DisplayStyleBO displayStyle) {
		if (resultSetBO == null || resultSetBO.getData() == null || resultSetBO.getData().isEmpty()) {
			return null;
		}
		if (displayStyle == null || StringUtils.isBlank(displayStyle.getType())
				|| "table".equalsIgnoreCase(displayStyle.getType())) {
			return null;
		}
		if (StringUtils.isBlank(displayStyle.getX()) || displayStyle.getY() == null || displayStyle.getY().isEmpty()) {
			return null;
		}
		try {
			List<Map<String, Object>> g2Data = convertData(resultSetBO.getData());
			Map<String, Object> axis = new LinkedHashMap<>();
			axis.put("x", displayStyle.getX());
			axis.put("y", displayStyle.getY());
			String url = g2SsrClient.render(displayStyle.getType(), axis, g2Data);
			if (url != null) {
				log.info("图表渲染成功：type={}, title={}", displayStyle.getType(), displayStyle.getTitle());
			}
			return url;
		}
		catch (Exception e) {
			log.warn("图表渲染失败：{}", e.getMessage());
			return null;
		}
	}

	/**
	 * 把结果集行（值为字符串）转成 g2-ssr 期望的行（数值列转数字，保证坐标轴类型正确）。
	 */
	private List<Map<String, Object>> convertData(List<Map<String, String>> rows) {
		List<Map<String, Object>> converted = new ArrayList<>(rows.size());
		for (Map<String, String> row : rows) {
			Map<String, Object> out = new HashMap<>(row.size());
			for (Map.Entry<String, String> entry : row.entrySet()) {
				out.put(entry.getKey(), coerceNumber(entry.getValue()));
			}
			converted.add(out);
		}
		return converted;
	}

	/** 数值字符串转 Number，否则保留原字符串。 */
	private Object coerceNumber(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return value;
		}
		try {
			return Long.parseLong(trimmed);
		}
		catch (NumberFormatException ignored) {
			// 不是整数，尝试小数
		}
		try {
			double d = Double.parseDouble(trimmed);
			if (Double.isFinite(d)) {
				return d;
			}
		}
		catch (NumberFormatException ignored) {
			// 不是数字，保留字符串
		}
		return value;
	}

}
