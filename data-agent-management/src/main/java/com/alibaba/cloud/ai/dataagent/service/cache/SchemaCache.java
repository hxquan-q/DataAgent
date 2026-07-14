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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 按数据源和选中表集合缓存 Schema 文档。 */
@Component
public class SchemaCache {

	private final Duration ttl;

	private final ConcurrentHashMap<Key, Entry> tables = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<Key, Entry> columns = new ConcurrentHashMap<>();

	public SchemaCache(@Value("${dataagent.cache.schema-ttl:PT10M}") Duration ttl) {
		this.ttl = ttl;
	}

	public Optional<List<Document>> getTables(Integer datasourceId, List<String> tableNames) {
		return get(tables, key(datasourceId, tableNames));
	}

	public void putTables(Integer datasourceId, List<String> tableNames, List<Document> documents) {
		put(tables, key(datasourceId, tableNames), documents);
	}

	public Optional<List<Document>> getColumns(Integer datasourceId, List<String> tableNames) {
		return get(columns, key(datasourceId, tableNames));
	}

	public void putColumns(Integer datasourceId, List<String> tableNames, List<Document> documents) {
		put(columns, key(datasourceId, tableNames), documents);
	}

	/** Schema 变更或手动刷新时按数据源失效。 */
	public void invalidate(Integer datasourceId) {
		tables.keySet().removeIf(key -> key.datasourceId.equals(datasourceId));
		columns.keySet().removeIf(key -> key.datasourceId.equals(datasourceId));
	}

	public void clear() {
		tables.clear();
		columns.clear();
	}

	private Optional<List<Document>> get(ConcurrentHashMap<Key, Entry> cache, Key key) {
		Entry entry = cache.get(key);
		if (entry == null) {
			return Optional.empty();
		}
		if (entry.expiresAt.isBefore(Instant.now())) {
			cache.remove(key, entry);
			return Optional.empty();
		}
		return Optional.of(entry.documents);
	}

	private void put(ConcurrentHashMap<Key, Entry> cache, Key key, List<Document> documents) {
		cache.put(key, new Entry(List.copyOf(documents), Instant.now().plus(ttl)));
	}

	private Key key(Integer datasourceId, List<String> tableNames) {
		String normalized = String.join("\n", tableNames.stream().distinct().sorted().toList());
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
			return new Key(datasourceId, HexFormat.of().formatHex(digest));
		}
		catch (Exception ex) {
			throw new IllegalStateException("无法计算 Schema 缓存键", ex);
		}
	}

	private record Key(Integer datasourceId, String tableSetHash) {
	}

	private record Entry(List<Document> documents, Instant expiresAt) {
	}

}
