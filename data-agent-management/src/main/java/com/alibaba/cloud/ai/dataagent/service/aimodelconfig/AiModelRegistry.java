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

import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 模型注册中心，以懒加载 + 缓存的方式管理全局的 ChatClient 和 EmbeddingModel 实例。
 *
 * <p>
 * 支持通过 {@link #refreshChat()} 和 {@link #refreshEmbedding()} 方法清除缓存以实现模型热切换。 当未配置
 * Embedding 模型时，使用 {@link DummyEmbeddingModel} 作为兜底，避免向量库初始化崩溃。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelRegistry {

	/** 动态模型工厂 */
	private final DynamicModelFactory modelFactory;

	/** 模型配置数据服务 */
	private final ModelConfigDataService modelConfigDataService;

	/** 当前缓存的 ChatClient（volatile 保证可见性） */
	private volatile ChatClient currentChatClient;

	/** 当前缓存的 EmbeddingModel（volatile 保证可见性） */
	private volatile EmbeddingModel currentEmbeddingModel;

	/**
	 * 获取全局 ChatClient，使用双重检查锁实现懒加载。未配置时抛出异常提示用户配置。
	 * @return 全局 ChatClient
	 * @throws RuntimeException 当没有激活的 CHAT 模型配置时抛出
	 */
	public ChatClient getChatClient() {
		if (currentChatClient == null) {
			synchronized (this) {
				if (currentChatClient == null) {
					log.info("Initializing global ChatClient...");
					try {
						ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.CHAT);
						if (config != null) {
							ChatModel chatModel = modelFactory.createChatModel(config);
							// 核心：基于新 Model 创建新 Client，彻底消除旧参数缓存
							currentChatClient = ChatClient.builder(chatModel).build();
						}
					}
					catch (Exception e) {
						log.error("Failed to initialize ChatClient: {}", e.getMessage(), e);
					}

					// 兜底：如果还没初始化成功，抛出运行时异常，提示用户配置
					if (currentChatClient == null) {
						throw new RuntimeException(
								"No active CHAT model configured. Please configure it in the dashboard.");
					}
				}
			}
		}
		return currentChatClient;
	}

	/**
	 * 获取全局 EmbeddingModel，使用双重检查锁实现懒加载。未配置时使用 DummyEmbeddingModel 兜底。
	 * @return 全局 EmbeddingModel
	 */
	public EmbeddingModel getEmbeddingModel() {
		if (currentEmbeddingModel == null) {
			synchronized (this) {
				if (currentEmbeddingModel == null) {
					log.info("Initializing global EmbeddingModel...");
					try {
						ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING);
						if (config != null) {
							currentEmbeddingModel = modelFactory.createEmbeddingModel(config);
						}
					}
					catch (Exception e) {
						log.error("Failed to initialize EmbeddingModel: {}", e.getMessage());
					}

					// 兜底：为了防止 VectorStore Starter 启动时调用 dimensions() 报错
					// 我们必须返回一个"哑巴"模型，而不是 null 或 抛异常
					if (currentEmbeddingModel == null) {
						log.warn("Using DummyEmbeddingModel for fallback.");
						currentEmbeddingModel = new DummyEmbeddingModel();
					}
				}
			}
		}
		return currentEmbeddingModel;
	}

	// =========================================================
	// 缓存刷新方法（用于热切换）
	// =========================================================

	/**
	 * 清除 ChatClient 缓存，下次获取时重新初始化。
	 */
	public void refreshChat() {
		this.currentChatClient = null;
		log.info("Chat cache cleared.");
	}

	/**
	 * 清除 EmbeddingModel 缓存，下次获取时重新初始化。
	 */
	public void refreshEmbedding() {
		this.currentEmbeddingModel = null;
		log.info("Embedding cache cleared.");
	}

	/**
	 * 哑巴嵌入模型，仅在未配置真实 Embedding 模型时用于启动防崩，返回常用维度 1536 骗过向量库初始化检查。
	 */
	private static class DummyEmbeddingModel implements EmbeddingModel {

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			throw new RuntimeException("No active EMBEDDING model. Please configure it first!");
		}

		@Override
		public float[] embed(Document document) {
			return new float[0];
		}

		@Override
		public float[] embed(String text) {
			return new float[0];
		}

		@Override
		public List<float[]> embed(List<String> texts) {
			return List.of();
		}

		@Override
		public EmbeddingResponse embedForResponse(List<String> texts) {
			return null;
		}

		// 返回常用维度 1536（OpenAI 标准），骗过向量库的初始化检查
		@Override
		public int dimensions() {
			return 1536;
		}

	}

}
