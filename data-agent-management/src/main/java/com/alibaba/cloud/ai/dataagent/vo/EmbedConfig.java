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
package com.alibaba.cloud.ai.dataagent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 网页嵌入（embed）配置，对应 {@code agent.embed_config} 列的 JSON 反序列化形态。
 * <p>
 * 该对象仅承载 UI 与限流配置，<strong>不含任何令牌</strong>。公开 {@code /config} 端点直接返回本对象。
 * 生产环境 {@link #allowedOrigins} 不得为 {@code ["*"]}（由 {@code EmbedPublicService} 强制校验）。
 * </p>
 */
@Data
public class EmbedConfig {

	/** 允许嵌入的宿主来源白名单，如 {@code ["https://www.example.com", "https://*.example.com"]}。 */
	private List<String> allowedOrigins = new ArrayList<>();

	/** 欢迎语。 */
	private String welcomeMessage;

	/** 主题色（如 {@code #07C05F}）。 */
	private String primaryColor = "#07C05F";

	/** widget 位置：bottom-right / bottom-left / top-right / top-left。 */
	private String widgetPosition = "bottom-right";

	/** 是否展示建议问题。 */
	private Boolean showSuggestedQuestions = true;

	/** 默认语言（zh-CN / en-US）。 */
	private String defaultLocale = "zh-CN";

	/** 每 Agent 每分钟请求上限。 */
	private Integer rateLimitPerMinute = 30;

	/** 每 Agent 每天请求上限。 */
	private Integer rateLimitPerDay = 10000;

	/** 返回带默认值的空配置（DB 中 embed_config 为空时兜底）。 */
	public static EmbedConfig defaults() {
		return new EmbedConfig();
	}

}
