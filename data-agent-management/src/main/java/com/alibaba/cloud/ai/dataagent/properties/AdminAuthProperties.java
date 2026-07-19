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
package com.alibaba.cloud.ai.dataagent.properties;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理端登录鉴权配置，绑定 {@code spring.ai.alibaba.data-agent.admin.*}。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX + ".admin")
public class AdminAuthProperties {

	/** bootstrap 默认用户名 */
	private String username = "admin";

	/**
	 * bootstrap 明文密码（仅空表首次创建使用）。local 可默认 admin123；prod 必须经 env
	 * 注入。
	 */
	private String password = "";

	/** JWT HMAC 密钥（≥32 字符推荐）；prod 必填 */
	private String jwtSecret = "";

	/** JWT 有效期（秒），默认 8h */
	private long jwtTtlSeconds = 28800L;

	/** 是否允许空表 bootstrap 插入管理员 */
	private boolean bootstrapEnabled = true;

}
