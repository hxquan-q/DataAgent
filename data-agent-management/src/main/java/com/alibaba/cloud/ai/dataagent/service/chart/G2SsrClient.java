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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * g2-ssr 服务端渲染客户端（#6）。
 *
 * <p>
 * 对接独立 Node 微服务 {@code POST /render}，把图表 spec 渲染成 PNG 并返回可访问 URL。 全部调用
 * fail-safe：服务不可达或超时返回 null，不阻断主流程。
 * </p>
 *
 * @author xquan
 */
@Slf4j
@Component
public class G2SsrClient {

	/** 默认 15s 超时，与 g2-ssr 服务端一致 */
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(12);

	private final String baseUrl;

	private final WebClient webClient;

	public G2SsrClient(@Value("${spring.ai.alibaba.data-agent.chart.g2ssr-url:http://localhost:3000}") String baseUrl) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.webClient = WebClient.builder().baseUrl(this.baseUrl).build();
		log.info("G2SsrClient 初始化，baseUrl={}", this.baseUrl);
	}

	/**
	 * 健康检查（服务是否可用）。
	 * @return 可用返回 true
	 */
	public boolean health() {
		try {
			webClient.get().uri("/health").retrieve().bodyToMono(String.class).block(DEFAULT_TIMEOUT);
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}

	/**
	 * 渲染图表为 PNG。
	 * @param type 图表类型（column/bar/line/pie 等，table 不渲染）
	 * @param axis 坐标映射 {x, y, [color]}
	 * @param data 数据行
	 * @return 可访问的图片 URL；失败返回 null
	 */
	@SuppressWarnings("unchecked")
	public String render(String type, Map<String, Object> axis, List<Map<String, Object>> data) {
		if (type == null || type.isBlank() || "table".equalsIgnoreCase(type) || data == null || data.isEmpty()) {
			return null;
		}
		try {
			String path = "chart-" + UUID.randomUUID() + ".png";
			Map<String, Object> body = Map.of("type", type, "axis", axis, "data", data, "path", path);
			Map<String, Object> resp = webClient.post()
				.uri("/render")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(Map.class)
				.block(DEFAULT_TIMEOUT);
			if (resp != null && resp.get("url") instanceof String url) {
				return baseUrl + url;
			}
			return null;
		}
		catch (Exception e) {
			log.warn("g2-ssr 渲染失败（type={}）：{}", type, e.getMessage());
			return null;
		}
	}

}
