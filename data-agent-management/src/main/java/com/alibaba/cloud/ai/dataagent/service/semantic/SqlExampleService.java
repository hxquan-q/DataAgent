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
package com.alibaba.cloud.ai.dataagent.service.semantic;

import com.alibaba.cloud.ai.dataagent.entity.SqlExample;
import com.alibaba.cloud.ai.dataagent.mapper.SqlExampleMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SQL 样例反哺服务（vanna 三库反哺，域Ⅴ§16 在线辨识）。
 * <p>
 * 成功执行的 SQL 经 SHA-256 指纹去重后写入 {@code sql_example} 训练库，作为后续 NL2SQL 的 few-shot 召回样本。 指纹算法：对
 * SQL 文本去首尾空白并转小写后取 SHA-256 十六进制摘要，保证大小写/缩进差异不影响去重。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
@Component
@AllArgsConstructor
public class SqlExampleService {

	private final SqlExampleMapper sqlExampleMapper;

	/**
	 * 反哺一条成功 SQL 样例。按 (agentId, sqlHash) 去重，已存在则跳过。
	 * @param question 用户原始问题
	 * @param sql 成功执行的 SQL 文本
	 * @param agentId 智能体ID
	 * @param datasourceId 数据源ID
	 * @param dialect 数据库方言
	 * @return {@code true} 新增成功；{@code false} 已存在跳过
	 */
	public boolean ingest(String question, String sql, Integer agentId, Integer datasourceId, String dialect) {
		String sqlHash = sha256Hex(sql);
		if (sqlExampleMapper.selectByHash(agentId, sqlHash) != null) {
			log.info("SQL 样例已存在，跳过反哺: agentId={}, sqlHash={}", agentId, sqlHash);
			return false;
		}
		sqlExampleMapper.insert(SqlExample.builder()
			.question(question)
			.sqlText(sql)
			.agentId(agentId)
			.datasourceId(datasourceId)
			.dialect(dialect)
			.sqlHash(sqlHash)
			.source("AUTO")
			.reviewed(0)
			.build());
		log.info("SQL 样例反哺成功: agentId={}, sqlHash={}", agentId, sqlHash);
		return true;
	}

	/**
	 * 计算 SQL 文本的 SHA-256 指纹（去首尾空白 + 转小写后取十六进制摘要）。
	 * @param sql 原始 SQL 文本
	 * @return 64 位小写十六进制哈希串
	 */
	private String sha256Hex(String sql) {
		String normalized = sql == null ? "" : sql.strip().toLowerCase();
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 算法不可用", e);
		}
	}

}
