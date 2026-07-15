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
package com.alibaba.cloud.ai.dataagent.util;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * SQL 只读护栏（v0.2 M1，抄 SQLBot check_sql_read + datafoundry readonly-guard）。
 *
 * <p>
 * 三层防御 + 表名白名单：
 * <ol>
 * <li>预处理（datafoundry）：状态机剥 {@code --}/{@code /* *} 注释 + 引号感知多语句检测</li>
 * <li>首词白名单（SQLBot）：仅放行 {@code SELECT}/{@code WITH}</li>
 * <li>危险模式正则（SQLBot）：{@code INTO OUTFILE}/{@code EXEC}/{@code pg_sleep}/{@code LOAD_FILE}
 * 等</li>
 * <li>AST 节点类型（#17 既有）：Insert/Update/Delete/Drop/... 沿继承链匹配（fail-open 兜底方言）</li>
 * </ol>
 * 表名白名单（{@link #checkTables}）：{@link TablesNamesFinder} AST 重解析真实表名 ⊆
 * 召回表集（fail-safe，不信自报）。
 * </p>
 *
 * @author xquan
 */
@Slf4j
public final class SqlGuard {

	/** 允许的首词（仅 SELECT/WITH 放行） */
	private static final Set<String> ALLOWED_PREFIXES = Set.of("SELECT", "WITH");

	/** 写操作语句类型（按类简单名判断，对 jsqlparser 包路径变更鲁棒） */
	private static final Set<String> WRITE_STATEMENT_TYPES = Set.of("Insert", "Update", "Delete", "Drop", "Alter",
			"Truncate", "Merge", "CreateTable", "CreateView", "Grant", "Revoke", "Replace");

	/** 危险模式正则（含危险函数：pg_sleep/xp_cmdshell/LOAD_FILE/pg_read_file/lo_import/UTL_FILE） */
	private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
			Pattern.compile("INTO\\s+OUTFILE", Pattern.CASE_INSENSITIVE),
			Pattern.compile("INTO\\s+DUMPFILE", Pattern.CASE_INSENSITIVE),
			Pattern.compile("EXEC(UTE)?\\s*\\(", Pattern.CASE_INSENSITIVE),
			Pattern.compile("COPY\\b.*?TO\\s+PROGRAM", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
			Pattern.compile("\\bpg_sleep\\s*\\(", Pattern.CASE_INSENSITIVE),
			Pattern.compile("\\bxp_cmdshell\\b", Pattern.CASE_INSENSITIVE),
			Pattern.compile("\\bLOAD_FILE\\s*\\(", Pattern.CASE_INSENSITIVE),
			Pattern.compile("\\bpg_read_file\\b", Pattern.CASE_INSENSITIVE),
			Pattern.compile("\\blo_import\\b", Pattern.CASE_INSENSITIVE),
			Pattern.compile("\\bUTL_FILE\\b", Pattern.CASE_INSENSITIVE));

	private SqlGuard() {
	}

	/**
	 * 只读校验（三层：预处理 + 首词 + 危险模式 + AST 节点类型）。向后兼容既有调用。
	 * <p>
	 * AST 解析失败 fail-open（方言兜底）；首词非 SELECT/WITH 与危险模式 fail-safe。
	 * </p>
	 * @param sql 待校验 SQL
	 * @return 校验结果；空 SQL 放行
	 */
	public static GuardResult check(String sql) {
		if (sql == null || sql.isBlank()) {
			return GuardResult.pass();
		}
		String stripped = stripComments(sql);
		if (hasMultipleStatements(stripped)) {
			return GuardResult.fail("多语句 SQL 已拦截（只读护栏）");
		}
		// 第一层：首词白名单（剥引号内容后取首词，防 'SELECT' DELETE 伪装）
		String firstWord = firstWord(stripQuoted(stripped).toUpperCase());
		if (!ALLOWED_PREFIXES.contains(firstWord)) {
			return GuardResult.fail("非只读语句（首词 " + firstWord + "）已拦截（只读护栏）");
		}
		// 第二层：危险模式正则
		for (Pattern pattern : DANGEROUS_PATTERNS) {
			if (pattern.matcher(stripped).find()) {
				return GuardResult.fail("危险模式已拦截（只读护栏）：" + pattern);
			}
		}
		// 第三层：AST 节点类型（fail-open 兜底方言）
		try {
			Statement statement = CCJSqlParserUtil.parse(stripped);
			if (isWriteStatement(statement)) {
				return GuardResult.fail("检测到写操作/DDL 语句，已拦截（只读护栏）：" + statement.getClass().getSimpleName());
			}
			return GuardResult.pass();
		}
		catch (Exception e) {
			log.warn("SQL 护栏 AST 解析失败，放行：{}", e.getMessage());
			return GuardResult.pass();
		}
	}

	/**
	 * 表名白名单校验（SQLBot extract_tables_from_sql）：AST 重解析真实表名 ⊆ 允许表集。
	 * <p>
	 * <b>fail-safe</b>：解析失败当安全事件拒绝（防 LLM/拼装自报表名撒谎）。
	 * </p>
	 * @param sql 待校验 SQL
	 * @param allowedTables 允许的表名集合（大小写不敏感）
	 * @return 校验结果；sql 为空或 allowedTables 为 null 放行
	 */
	public static GuardResult checkTables(String sql, Set<String> allowedTables) {
		if (sql == null || sql.isBlank() || allowedTables == null) {
			return GuardResult.pass();
		}
		Set<String> normalized = new java.util.HashSet<>();
		for (String t : allowedTables) {
			normalized.add(t == null ? "" : t.toLowerCase());
		}
		try {
			Statement statement = CCJSqlParserUtil.parse(stripComments(sql));
			List<String> realTables = new TablesNamesFinder().getTableList(statement);
			List<String> unauthorized = realTables.stream().filter(t -> !normalized.contains(t.toLowerCase())).toList();
			if (!unauthorized.isEmpty()) {
				return GuardResult.fail("越权表已拦截（只读护栏）：" + unauthorized);
			}
			return GuardResult.pass();
		}
		catch (Exception e) {
			return GuardResult.fail("表名校验解析失败，已拦截（只读护栏）：" + e.getMessage());
		}
	}

	/**
	 * 组合校验（只读 + 表名白名单），SqlExecuteNode 调用入口。
	 * @param sql 待校验 SQL
	 * @param allowedTables 允许的表名集合；null/空 跳过表名校验
	 * @return 校验结果
	 */
	public static GuardResult validate(String sql, Set<String> allowedTables) {
		GuardResult readonly = check(sql);
		if (!readonly.allowed()) {
			return readonly;
		}
		if (allowedTables != null && !allowedTables.isEmpty()) {
			return checkTables(sql, allowedTables);
		}
		return GuardResult.pass();
	}

	/** 沿继承链匹配写语句类型（兼容子类/包装类）。 */
	private static boolean isWriteStatement(Statement statement) {
		Class<?> clazz = statement.getClass();
		while (clazz != null && Statement.class.isAssignableFrom(clazz)) {
			if (WRITE_STATEMENT_TYPES.contains(clazz.getSimpleName())) {
				return true;
			}
			clazz = clazz.getSuperclass();
		}
		return false;
	}

	/** 状态机剥注释（引号感知，处理 {@code --} 行 + {@code /* *} 块）。 */
	private static String stripComments(String sql) {
		StringBuilder sb = new StringBuilder(sql.length());
		int n = sql.length();
		boolean inSingle = false;
		boolean inDouble = false;
		for (int i = 0; i < n; i++) {
			char c = sql.charAt(i);
			char next = i + 1 < n ? sql.charAt(i + 1) : '\0';
			if (inSingle) {
				sb.append(c);
				if (c == '\'' && next == '\'') {
					sb.append(next);
					i++;
				}
				else if (c == '\'') {
					inSingle = false;
				}
				continue;
			}
			if (inDouble) {
				sb.append(c);
				if (c == '"' && next == '"') {
					sb.append(next);
					i++;
				}
				else if (c == '"') {
					inDouble = false;
				}
				continue;
			}
			if (c == '\'') {
				inSingle = true;
				sb.append(c);
			}
			else if (c == '"') {
				inDouble = true;
				sb.append(c);
			}
			else if (c == '-' && next == '-') {
				i++;
				while (i + 1 < n && sql.charAt(i + 1) != '\n' && sql.charAt(i + 1) != '\r') {
					i++;
				}
				sb.append(' ');
			}
			else if (c == '/' && next == '*') {
				i++;
				while (i + 1 < n && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
					i++;
				}
				i++;
				sb.append(' ');
			}
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/** 引号外的分号后仍有内容则判定多语句。 */
	private static boolean hasMultipleStatements(String sql) {
		boolean inSingle = false;
		boolean inDouble = false;
		for (int i = 0; i < sql.length(); i++) {
			char c = sql.charAt(i);
			if (c == '\'' && !inDouble) {
				inSingle = !inSingle;
			}
			else if (c == '"' && !inSingle) {
				inDouble = !inDouble;
			}
			else if (c == ';' && !inSingle && !inDouble && sql.substring(i + 1).trim().length() > 0) {
				return true;
			}
		}
		return false;
	}

	/** 剥引号内容（首词判断用，防引号内关键字干扰）。 */
	private static String stripQuoted(String sql) {
		return sql.replaceAll("'([^']|'')*'", "''").replaceAll("\"([^\"]|\"\")*\"", "\"\"");
	}

	/** 取首词（到首个空白或左括号）。 */
	private static String firstWord(String upperSql) {
		String trimmed = upperSql.trim();
		int i = 0;
		while (i < trimmed.length() && Character.isWhitespace(trimmed.charAt(i))) {
			i++;
		}
		int start = i;
		while (i < trimmed.length() && !Character.isWhitespace(trimmed.charAt(i)) && trimmed.charAt(i) != '(') {
			i++;
		}
		return start >= i ? "" : trimmed.substring(start, i);
	}

	/** 校验结果。 */
	public record GuardResult(boolean allowed, String reason) {

		public static GuardResult pass() {
			return new GuardResult(true, null);
		}

		public static GuardResult fail(String reason) {
			return new GuardResult(false, reason);
		}

	}

}
