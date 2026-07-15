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

import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

/**
 * SQL 只读护栏（#17，抄 SQLBot check_sql_read 思路）。
 *
 * <p>
 * 用 jsqlparser 解析 SQL，拦截 DDL/DML（Insert/Update/Delete/Drop/Alter/Truncate/Merge/Create
 * 等）， 防止自愈重试把写操作打到业务库。策略：<b>fail-open</b>——解析失败时放行（避免误伤合法的复杂 SELECT）， 仅拦截能明确识别的写语句。
 * </p>
 *
 * @author xquan
 */
@Slf4j
public final class SqlGuard {

	/** 写操作语句类型（按类简单名判断，对 jsqlparser 包路径变更鲁棒） */
	private static final Set<String> WRITE_STATEMENT_TYPES = Set.of("Insert", "Update", "Delete", "Drop", "Alter",
			"Truncate", "Merge", "CreateTable", "CreateView", "Grant", "Revoke", "Replace");

	private SqlGuard() {
	}

	/**
	 * 校验 SQL 是否只读。
	 * @param sql 待校验 SQL
	 * @return 校验结果；空 SQL 放行
	 */
	public static GuardResult check(String sql) {
		if (sql == null || sql.isBlank()) {
			return GuardResult.pass();
		}
		try {
			Statement statement = CCJSqlParserUtil.parse(sql);
			if (isWriteStatement(statement)) {
				return GuardResult.fail("检测到写操作/DDL 语句，已拦截（只读护栏）：" + statement.getClass().getSimpleName());
			}
			return GuardResult.pass();
		}
		catch (Exception e) {
			// fail-open：解析失败不阻断（可能是方言特殊语法），仅记录
			log.warn("SQL 护栏解析失败，放行：{}", e.getMessage());
			return GuardResult.pass();
		}
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
