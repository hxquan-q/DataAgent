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
package com.alibaba.cloud.ai.dataagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DataAgent Management 服务启动入口。
 * <p>
 * 基于 Spring Boot 3.x，整合 Spring AI、MyBatis、Druid、WebFlux 等组件，
 * 提供 NL2SQL、数据分析、报告生成等 Agent 能力。
 * </p>
 * <ul>
 *   <li>{@code @SpringBootApplication} —— 开启自动装配与组件扫描</li>
 *   <li>{@code @EnableScheduling} —— 开启定时任务（向量存储持久化、清理等）</li>
 * </ul>
 * <p>
 * 默认 Profile 读取 {@code application.yml}（MySQL）；本地开发可用
 * {@code --spring.profiles.active=h2} 切换为内存 H2 数据库。
 * </p>
 */
@EnableScheduling
@SpringBootApplication
public class DataAgentApplication {

	/**
	 * 应用主入口。
	 * @param args 启动参数，支持 {@code --spring.profiles.active=h2} 等 Spring 标准参数
	 */
	public static void main(String[] args) {
		SpringApplication.run(DataAgentApplication.class, args);
	}

}
