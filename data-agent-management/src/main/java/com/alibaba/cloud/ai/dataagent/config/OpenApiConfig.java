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

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置。
 * <p>
 * 定义 API 文档元信息并按包路径分组，启动后访问 {@code http://localhost:8065/swagger-ui.html} 查看接口文档。
 * </p>
 */
@Configuration
public class OpenApiConfig {

	/**
	 * 定义 OpenAPI 文档基本信息（标题、描述、版本）。
	 * @return OpenAPI 元信息
	 */
	@Bean
	public OpenAPI dataAgentOpenApi() {
		return new OpenAPI()
			.info(new Info().title("DataAgent Backend API").description("DataAgent 后端接口文档").version("v1"));
	}

	/**
	 * 定义 API 分组：扫描 controller 包下 {@code /api/**} 路径的接口。
	 * @return 分组配置
	 */
	@Bean
	public GroupedOpenApi dataAgentApiGroup() {
		return GroupedOpenApi.builder()
			.group("data-agent")
			.packagesToScan("com.alibaba.cloud.ai.dataagent.controller")
			.pathsToMatch("/api/**")
			.build();
	}

}
