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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.util.AesGcmCrypto;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 安全状态 REST 接口：暴露凭据加密（AES-256-GCM）的启停状态，供运维/前端可见性使用。
 * <p>
 * 加密为 opt-in（未注入主密钥则 passthrough 不加密），运维需明确知道是否已保护——这是把 R12
 * 凭据加密能力暴露成可观测状态的控制论闭环（"可控需先可观测"）。
 * </p>
 */
@RestController
@RequestMapping("/api/security")
public class SecurityStatusController {

	/** 凭据加密状态。 */
	public record CryptoStatus(boolean enabled, String algorithm, String envelopePrefix, String keyConfigured,
			String coverage) {
	}

	/**
	 * 查询凭据加密状态。
	 * @return 加密是否启用、算法、信封前缀、密钥配置、覆盖范围说明
	 */
	@GetMapping("/crypto-status")
	public ApiResponse<CryptoStatus> cryptoStatus() {
		boolean on = AesGcmCrypto.enabled();
		return ApiResponse.success("ok",
				new CryptoStatus(on, "AES-256-GCM", AesGcmCrypto.ENVELOPE_PREFIX, on ? "configured" : "not-configured",
						"model_config.api_key / model_config.proxy_password / datasource.password / agent.api_key"));
	}

}
