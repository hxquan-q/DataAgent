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
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SQL 样例 few-shot 召回助手（vanna get_similar_question_sql 的 Java 等价实现，域Ⅴ§16）。
 * <p>
 * 从 {@code sql_example} 训练库中查询当前智能体已审核的样例， 格式化为 {@code "问题: xxx\nSQL: xxx"} 的 few-shot
 * 字符串， 供 {@code EvidenceRecallNode} 追加到 evidence 上下文，增强后续 NL2SQL 的生成质量。
 * </p>
 * <p>
 * 当前实现为「全量已审核样例」策略（按 created_time 倒序）。 相似度检索（向量/全文）可作为后续增强点，接口签名保持不变。
 * </p>
 * <p>
 * 增强版 {@link #recallFewShot(Integer, String)} 支持传入用户问题：有 question 时用关键词匹配过滤样例（question
 * LIKE）， 比「全量取最新」更精准，比「向量召回」更简单（无需 Embedding 模型与向量库）；question 为空时降级为全量召回。
 * </p>
 *
 * @author dataagent
 * @see SqlExampleService#ingest 反哺入口（写入训练库）
 * @see SqlExampleMapper#selectReviewedByAgentId 召回查询
 */
@Slf4j
@Component
@AllArgsConstructor
public class SqlExampleRecallHelper {

	/** few-shot 样例上限，避免 evidence 过长挤占 LLM token。 */
	private static final int MAX_FEW_SHOT_EXAMPLES = 5;

	private final SqlExampleMapper sqlExampleMapper;

	/**
	 * 召回已审核 SQL 样例并格式化为 few-shot 字符串（全量策略，向后兼容）。
	 * <p>
	 * 等价于 {@link #recallFewShot(Integer, String) recallFewShot(agentId, null)}。
	 * </p>
	 * @param agentId 智能体ID（必填）
	 * @return few-shot 字符串；无样例或异常时返回空字符串（不阻断主流程）
	 */
	public String recallFewShot(Integer agentId) {
		return recallFewShot(agentId, null);
	}

	/**
	 * 召回已审核 SQL 样例并格式化为 few-shot 字符串（关键词相似度增强）。
	 * <p>
	 * 当 {@code question} 非空时，先全量取已审核样例，再在内存中按关键词匹配打分（提取 question 中的多字符 token，与 样例
	 * question 做子串匹配），命中的优先排在前面，再取前 {@value #MAX_FEW_SHOT_EXAMPLES} 条。
	 * 相比全量「最新优先」，关键词命中更贴近 vanna {@code get_similar_question_sql} 的语义召回目标； 相比向量召回，无需
	 * Embedding 模型与向量库，实现简单且在样例量不大时效果稳定。
	 * </p>
	 * <p>
	 * {@code question} 为空/空白时降级为全量召回（取最新）。
	 * </p>
	 * @param agentId 智能体ID（必填）
	 * @param question 用户当前问题（可选；非空时启用关键词过滤）
	 * @return few-shot 字符串；无样例或异常时返回空字符串（不阻断主流程）
	 */
	public String recallFewShot(Integer agentId, String question) {
		if (agentId == null) {
			return "";
		}
		try {
			List<SqlExample> examples = sqlExampleMapper.selectReviewedByAgentId(agentId);
			if (examples == null || examples.isEmpty()) {
				log.debug("智能体 {} 无已审核 SQL 样例可召回", agentId);
				return "";
			}

			List<SqlExample> picked = pickByQuestion(examples, question);

			StringBuilder sb = new StringBuilder();
			sb.append("[SQL 样例 few-shot]\n");
			for (SqlExample ex : picked) {
				sb.append("问题: ").append(safe(ex.getQuestion())).append("\n");
				sb.append("SQL: ").append(safe(ex.getSqlText())).append("\n\n");
			}
			log.info("智能体 {} 召回 {} 条 SQL 样例用于 few-shot（总库 {} 条，question={})", agentId, picked.size(), examples.size(),
					question == null ? "空(全量)" : "关键词过滤");
			return sb.toString().stripTrailing();
		}
		catch (Exception e) {
			// 召回失败不应中断证据召回主流程，降级为空 few-shot
			log.error("召回 SQL 样例 few-shot 失败: agentId={}", agentId, e);
			return "";
		}
	}

	/**
	 * 按 question 关键词相似度排序并取前 {@value #MAX_FEW_SHOT_EXAMPLES} 条。
	 * <p>
	 * 无 question 时直接取前 N（保持原有「最新优先」语义）。打分规则：提取 question 中长度 &gt;=2 的字符 token
	 * （中文按单字滑窗、英文按空白分词），样例 question 每命中一个 token 计 1 分，同分按原顺序（稳定排序）。 得分为 0
	 * 的样例仍有机会被选中（排在命中的后面），保证无关键词命中时也有 few-shot 可用。
	 * </p>
	 */
	private List<SqlExample> pickByQuestion(List<SqlExample> examples, String question) {
		int limit = Math.min(examples.size(), MAX_FEW_SHOT_EXAMPLES);
		if (question == null || question.isBlank()) {
			return examples.subList(0, limit);
		}

		// 提取关键词 token：英文词 + 中文双字滑窗
		List<String> tokens = extractTokens(question);
		if (tokens.isEmpty()) {
			return examples.subList(0, limit);
		}

		// 打分 + 稳定排序：命中分高的优先；命中数相同的保持原顺序（created_time DESC）
		List<SqlExample> sorted = new ArrayList<>(examples);
		sorted.sort((a, b) -> Integer.compare(score(b, tokens), score(a, tokens)));

		return sorted.subList(0, limit);
	}

	/** 计算样例 question 命中的 token 数。 */
	private int score(SqlExample ex, List<String> tokens) {
		String q = ex.getQuestion();
		if (q == null || q.isBlank()) {
			return 0;
		}
		String lower = q.toLowerCase();
		int s = 0;
		for (String t : tokens) {
			if (lower.contains(t)) {
				s++;
			}
		}
		return s;
	}

	/**
	 * 从用户问题中提取匹配 token：英文按空白拆分为长度 &gt;=2 的词（小写），中文按双字滑窗。 去除纯标点。
	 */
	private List<String> extractTokens(String question) {
		List<String> tokens = new ArrayList<>();
		String trimmed = question.trim().toLowerCase();
		if (trimmed.isEmpty()) {
			return tokens;
		}
		// 英文/数字词
		for (String w : trimmed.split("[\\s,，。.!！?？;；:：、()\\[\\]{}\"']+")) {
			if (w.length() >= 2) {
				tokens.add(w);
			}
		}
		// 中文字符段做双字滑窗
		StringBuilder cjk = new StringBuilder();
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			if (c >= 0x4E00 && c <= 0x9FFF) {
				cjk.append(c);
			}
			else if (cjk.length() > 0) {
				addCjkTokens(tokens, cjk.toString());
				cjk.setLength(0);
			}
		}
		if (cjk.length() > 0) {
			addCjkTokens(tokens, cjk.toString());
		}
		return tokens;
	}

	private void addCjkTokens(List<String> tokens, String s) {
		if (s.length() < 2) {
			return;
		}
		for (int i = 0; i < s.length() - 1; i++) {
			tokens.add(s.substring(i, i + 2));
		}
	}

	private String safe(String s) {
		return s == null ? "" : s.strip();
	}

}
