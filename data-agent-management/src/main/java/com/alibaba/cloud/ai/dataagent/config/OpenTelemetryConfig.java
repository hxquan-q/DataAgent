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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * OpenTelemetry / Langfuse 可观测性配置。
 * <p>
 * 绑定 {@code spring.ai.alibaba.data-agent.langfuse.*} 前缀， 通过 OTLP HTTP 协议将 Trace 数据上报到
 * Langfuse 平台， 实现 LLM 调用链路追踪、Prompt 监控与性能分析。
 * </p>
 * <p>
 * 当 {@code enabled=false} 时返回 Noop 实现，不产生任何上报开销。
 * </p>
 *
 * @author zihenzzz
 * @date 2026/2/16 13:55
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX + ".langfuse")
public class OpenTelemetryConfig {

	/** 服务名称标识（上报到 Langfuse 的 service.name 属性） */
	private static final String SERVICE_NAME = "data-agent";

	/** 是否启用 Langfuse 链路追踪 */
	private boolean enabled = true;

	/** Langfuse 服务地址（OTLP endpoint 基址） */
	private String host;

	/** Langfuse 公钥（用于 Basic 认证） */
	private String publicKey;

	/** Langfuse 私钥（用于 Basic 认证，勿提交到公开仓库） */
	private String secretKey;

	/** SDK Tracer 提供者，销毁时需关闭 */
	private SdkTracerProvider tracerProvider;

	/**
	 * 创建 OpenTelemetry 实例。
	 * <p>
	 * 启用时构建 OTLP HTTP Span Exporter，将 Trace 批量上报到 Langfuse； 禁用时返回 Noop 实例。
	 * </p>
	 * @return OpenTelemetry 实例
	 */
	@Bean
	public OpenTelemetry openTelemetry() {
		if (!enabled) {
			return OpenTelemetry.noop();
		}

		String auth = publicKey + ":" + secretKey;
		String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

		OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
			.setEndpoint(host + "/api/public/otel/v1/traces")
			.addHeader("Authorization", "Basic " + encodedAuth)
			.setTimeout(10, TimeUnit.SECONDS)
			.build();

		Resource resource = Resource.getDefault()
			.merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), SERVICE_NAME)));

		tracerProvider = SdkTracerProvider.builder()
			.addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
				.setScheduleDelay(1, TimeUnit.SECONDS)
				.setMaxExportBatchSize(100)
				.build())
			.setResource(resource)
			.build();

		OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

		log.info("OpenTelemetry initialized with Langfuse OTLP HTTP exporter");

		return openTelemetrySdk;
	}

	/**
	 * 创建 Langfuse Tracer，用于手动创建 Span 追踪业务链路。
	 * @param openTelemetry OpenTelemetry 实例
	 * @return 以 {@value #SERVICE_NAME} 为名的 Tracer
	 */
	@Bean
	public Tracer langfuseTracer(OpenTelemetry openTelemetry) {
		return openTelemetry.getTracer(SERVICE_NAME);
	}

	/**
	 * 容器销毁回调：关闭 TracerProvider，确保缓存的 Span 全部上报。
	 */
	@PreDestroy
	public void shutdown() {
		if (tracerProvider != null) {
			tracerProvider.close();
		}
	}

}
