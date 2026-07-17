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

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * R173: 启动时打印关键运行时开关，便于确认 local 配置（如 concurrent SQL）已加载。
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class DataAgentRuntimeFlagsLogger implements ApplicationRunner {

	private final DataAgentProperties properties;

	@Override
	public void run(ApplicationArguments args) {
		log.info(
				"DataAgent runtime flags: enableConcurrentSteps={}, maxSqlRetryCount={}, enableSqlResultChart={}, enrichSqlResultTimeout={}ms",
				properties.isEnableConcurrentSteps(), properties.getMaxSqlRetryCount(),
				properties.isEnableSqlResultChart(), properties.getEnrichSqlResultTimeout());
	}

}
