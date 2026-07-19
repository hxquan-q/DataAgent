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
package com.alibaba.cloud.ai.dataagent.service.cache;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** 按文本内容哈希缓存向量，并自动包装 Spring 中的 EmbeddingModel。 */
@Component
public class EmbeddingCache implements BeanPostProcessor {

	private final Duration ttl;

	private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

	public EmbeddingCache(@Value("${dataagent.cache.embedding-ttl:PT30M}") Duration ttl) {
		this.ttl = ttl;
	}

	public boolean contains(String content) {
		return get(content) != null;
	}

	public float[] get(String content) {
		String key = hash(content);
		Entry entry = cache.get(key);
		if (entry == null) {
			return null;
		}
		if (entry.expiresAt.isBefore(Instant.now())) {
			cache.remove(key, entry);
			return null;
		}
		return entry.vector.clone();
	}

	public void put(String content, float[] vector) {
		cache.put(hash(content), new Entry(vector.clone(), Instant.now().plus(ttl)));
	}

	public void invalidate(String content) {
		cache.remove(hash(content));
	}

	public void clear() {
		cache.clear();
	}

	/**
	 * 批量读取并计算缺失向量。全局锁保证并发请求不会重复调用模型；吞吐成为瓶颈时再改为分片锁。
	 */
	public List<float[]> getOrComputeAll(List<String> contents, Function<List<String>, List<float[]>> loader) {
		synchronized (cache) {
			Map<String, String> misses = new LinkedHashMap<>();
			for (String content : contents) {
				if (get(content) == null) {
					misses.putIfAbsent(hash(content), content);
				}
			}
			if (!misses.isEmpty()) {
				List<String> missingContents = new ArrayList<>(misses.values());
				List<float[]> loaded = loader.apply(missingContents);
				if (loaded.size() != missingContents.size()) {
					throw new IllegalStateException("Embedding 数量与输入数量不一致");
				}
				for (int i = 0; i < missingContents.size(); i++) {
					put(missingContents.get(i), loaded.get(i));
				}
			}
			return contents.stream().map(this::get).toList();
		}
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof EmbeddingModel model && !(bean instanceof CachedEmbeddingModel)) {
			return new CachedEmbeddingModel(model, this);
		}
		return bean;
	}

	private String hash(String content) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (Exception ex) {
			throw new IllegalStateException("无法计算 Embedding 缓存键", ex);
		}
	}

	private record Entry(float[] vector, Instant expiresAt) {
	}

	private static final class CachedEmbeddingModel implements EmbeddingModel {

		private final EmbeddingModel delegate;

		private final EmbeddingCache cache;

		private CachedEmbeddingModel(EmbeddingModel delegate, EmbeddingCache cache) {
			this.delegate = delegate;
			this.cache = cache;
		}

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			AtomicReference<EmbeddingResponseMetadata> metadata = new AtomicReference<>(
					new EmbeddingResponseMetadata());
			List<float[]> vectors = cache.getOrComputeAll(request.getInstructions(), missing -> {
				EmbeddingResponse response = delegate.call(new EmbeddingRequest(missing, request.getOptions()));
				metadata.set(response.getMetadata());
				return response.getResults().stream().map(Embedding::getOutput).toList();
			});
			List<Embedding> embeddings = new ArrayList<>(vectors.size());
			for (int i = 0; i < vectors.size(); i++) {
				embeddings.add(new Embedding(vectors.get(i), i));
			}
			return new EmbeddingResponse(embeddings, metadata.get());
		}

		@Override
		public float[] embed(Document document) {
			return cache.getOrComputeAll(List.of(document.getText()), missing -> delegate.embed(missing)).get(0);
		}

		@Override
		public int dimensions() {
			return delegate.dimensions();
		}

	}

}
