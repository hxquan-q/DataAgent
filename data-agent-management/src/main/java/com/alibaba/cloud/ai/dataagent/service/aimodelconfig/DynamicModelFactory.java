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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

/**
 * 动态模型工厂，根据模型配置动态创建 ChatModel 和 EmbeddingModel 实例。
 *
 * <p>
 * 统一使用 OpenAI 兼容的模型类（{@link OpenAiChatModel}、{@link OpenAiEmbeddingModel}）， 通过 baseUrl
 * 实现对多家厂商（DeepSeek、通义千问等）的兼容。支持代理配置。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicModelFactory {

	/** 无代理时默认响应超时（与 webclient.response.timeout 默认 600s 同量级，避免裸 builder 无超时）。 */
	private static final java.time.Duration DEFAULT_RESPONSE_TIMEOUT = java.time.Duration.ofSeconds(600);

	/**
	 * 根据配置创建 ChatModel，统一使用 OpenAiChatModel 通过 baseUrl 实现多厂商兼容。
	 * @param config 模型配置 DTO
	 * @return 创建的 ChatModel 实例
	 */
	public ChatModel createChatModel(ModelConfigDTO config) {

		log.info("Creating NEW ChatModel instance. Provider: {}, Model: {}, BaseUrl: {}", config.getProvider(),
				config.getModelName(), config.getBaseUrl());
		// 1. 验证参数
		checkBasic(config);

		// 2. 构建 OpenAiApi (核心通讯对象)
		String apiKey = StringUtils.hasText(config.getApiKey()) ? config.getApiKey() : "";
		OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
			.apiKey(apiKey)
			.baseUrl(config.getBaseUrl())
			.restClientBuilder(getProxiedRestClientBuilder(config))
			.webClientBuilder(getProxiedWebClientBuilder(config));

		if (StringUtils.hasText(config.getCompletionsPath())) {
			apiBuilder.completionsPath(config.getCompletionsPath());
		}
		OpenAiApi openAiApi = apiBuilder.build();

		// 3. 构建运行时选项 (设置默认的模型名称，如 "deepseek-chat" 或 "gpt-4")
		OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
			.model(config.getModelName())
			.temperature(config.getTemperature())
			.maxTokens(config.getMaxTokens())
			.streamUsage(true)
			.build();
		// 4. 返回统一的 OpenAiChatModel
		return OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(openAiChatOptions).build();
	}

	/**
	 * 根据配置创建 EmbeddingModel，与 ChatModel 同理使用 OpenAI 兼容接口。
	 * @param config 模型配置 DTO
	 * @return 创建的 EmbeddingModel 实例
	 */
	public EmbeddingModel createEmbeddingModel(ModelConfigDTO config) {
		log.info("Creating NEW EmbeddingModel instance. Provider: {}, Model: {}, BaseUrl: {}", config.getProvider(),
				config.getModelName(), config.getBaseUrl());
		checkBasic(config);

		String apiKey = StringUtils.hasText(config.getApiKey()) ? config.getApiKey() : "";
		OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
			.apiKey(apiKey)
			.baseUrl(config.getBaseUrl())
			.restClientBuilder(getProxiedRestClientBuilder(config))
			.webClientBuilder(getProxiedWebClientBuilder(config));

		if (StringUtils.hasText(config.getEmbeddingsPath())) {
			apiBuilder.embeddingsPath(config.getEmbeddingsPath());
		}

		OpenAiApi openAiApi = apiBuilder.build();
		return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
				OpenAiEmbeddingOptions.builder().model(config.getModelName()).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	private static void checkBasic(ModelConfigDTO config) {
		// R176: 校验信息面向运维/前端展示
		Assert.notNull(config, "模型配置不能为空");
		Assert.hasText(config.getBaseUrl(), "模型 Base URL 不能为空");
		if (!"custom".equalsIgnoreCase(config.getProvider())) {
			Assert.hasText(config.getApiKey(), "模型 API Key 不能为空");
		}
		Assert.hasText(config.getModelName(), "模型名称不能为空");
	}

	private RestClient.Builder getProxiedRestClientBuilder(ModelConfigDTO config) {
		if (config.getProxyEnabled() == null || !config.getProxyEnabled()) {
			return RestClient.builder();
		}

		// 打印同步代理日志
		log.info("【Proxy-Init】Model [{}] is using SYNC proxy -> {}:{}", config.getModelName(), config.getProxyHost(),
				config.getProxyPort());

		BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
		if (StringUtils.hasText(config.getProxyUsername())) {
			log.info("【Proxy-Auth】Enabling Basic Auth for SYNC proxy, user: {}", config.getProxyUsername());
			credsProvider.setCredentials(new AuthScope(config.getProxyHost(), config.getProxyPort()),
					new UsernamePasswordCredentials(config.getProxyUsername(),
							config.getProxyPassword().toCharArray()));
		}

		CloseableHttpClient httpClient = HttpClients.custom()
			.setProxy(new HttpHost(config.getProxyHost(), config.getProxyPort()))
			.setDefaultCredentialsProvider(credsProvider)
			.build();

		return RestClient.builder().requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
	}

	private WebClient.Builder getProxiedWebClientBuilder(ModelConfigDTO config) {
		// 始终自建带 responseTimeout 的 Netty 客户端（不依赖注入 Builder，避免 JRebel 热更字段为 null）
		if (config.getProxyEnabled() == null || !config.getProxyEnabled()) {
			HttpClient nettyClient = HttpClient.create().responseTimeout(DEFAULT_RESPONSE_TIMEOUT);
			return WebClient.builder().clientConnector(new ReactorClientHttpConnector(nettyClient));
		}

		log.info("【Proxy-Init】Model [{}] is using ASYNC (Netty) proxy -> {}:{}", config.getModelName(),
				config.getProxyHost(), config.getProxyPort());

		HttpClient nettyClient = HttpClient.create().responseTimeout(java.time.Duration.ofMinutes(3)).proxy(p -> {
			ProxyProvider.Builder proxyBuilder = p.type(ProxyProvider.Proxy.HTTP)
				.host(config.getProxyHost())
				.port(config.getProxyPort());

			if (StringUtils.hasText(config.getProxyUsername())) {
				log.info("【Proxy-Auth】Enabling Basic Auth for ASYNC proxy, user: {}", config.getProxyUsername());
				proxyBuilder.username(config.getProxyUsername()).password(s -> config.getProxyPassword());
			}
		});

		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(nettyClient));
	}

}
