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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import com.alibaba.cloud.ai.dataagent.util.SqlGuard.GuardResult;
import org.junit.jupiter.api.Test;

/**
 * {@link SqlGuard} 单测（v0.2 M1：三层只读护栏 + 表名白名单）。
 *
 * @author xquan
 */
class SqlGuardTest {

	@Test
	void selectQueriesAreAllowed() {
		assertThat(SqlGuard.check("SELECT * FROM users")).isEqualTo(GuardResult.pass());
		assertThat(SqlGuard.check("SELECT id, name FROM users WHERE age > 18 ORDER BY id"))
			.isEqualTo(GuardResult.pass());
		assertThat(SqlGuard.check("  select 1  ")).isEqualTo(GuardResult.pass());
	}

	@Test
	void cteSelectIsAllowed() {
		assertThat(SqlGuard.check("WITH cte AS (SELECT id FROM orders) SELECT * FROM cte"))
			.isEqualTo(GuardResult.pass());
	}

	@Test
	void blankOrNullAreAllowed() {
		assertThat(SqlGuard.check(null)).isEqualTo(GuardResult.pass());
		assertThat(SqlGuard.check("")).isEqualTo(GuardResult.pass());
		assertThat(SqlGuard.check("   ")).isEqualTo(GuardResult.pass());
	}

	@Test
	void insertIsBlocked() {
		assertThat(SqlGuard.check("INSERT INTO users (id) VALUES (1)").allowed()).isFalse();
	}

	@Test
	void updateIsBlocked() {
		assertThat(SqlGuard.check("UPDATE users SET name='x' WHERE id=1").allowed()).isFalse();
	}

	@Test
	void deleteIsBlocked() {
		assertThat(SqlGuard.check("DELETE FROM users WHERE id=1").allowed()).isFalse();
	}

	@Test
	void dropIsBlocked() {
		assertThat(SqlGuard.check("DROP TABLE users").allowed()).isFalse();
	}

	@Test
	void alterIsBlocked() {
		assertThat(SqlGuard.check("ALTER TABLE users ADD COLUMN age INT").allowed()).isFalse();
	}

	@Test
	void truncateIsBlocked() {
		assertThat(SqlGuard.check("TRUNCATE TABLE users").allowed()).isFalse();
	}

	@Test
	void createTableIsBlocked() {
		assertThat(SqlGuard.check("CREATE TABLE t (id INT)").allowed()).isFalse();
	}

	// ===== v0.2 M1 新增：预处理 + 首词白名单 + 危险模式 =====

	@Test
	void nonSelectFirstWordIsBlocked() {
		// 首词非 SELECT/WITH 直接拦截（fail-safe）
		assertThat(SqlGuard.check("not a real sql @@@ !!!").allowed()).isFalse();
		assertThat(SqlGuard.check("SHOW TABLES").allowed()).isFalse();
		assertThat(SqlGuard.check("EXPLAIN SELECT * FROM t").allowed()).isFalse();
	}

	@Test
	void multipleStatementsAreBlocked() {
		assertThat(SqlGuard.check("SELECT 1; DROP TABLE users").allowed()).isFalse();
		assertThat(SqlGuard.check("SELECT 1; SELECT 2").allowed()).isFalse();
	}

	@Test
	void dangerousPatternsAreBlocked() {
		assertThat(SqlGuard.check("SELECT * FROM t INTO OUTFILE '/tmp/x'").allowed()).isFalse();
		assertThat(SqlGuard.check("SELECT pg_sleep(5)").allowed()).isFalse();
		assertThat(SqlGuard.check("SELECT xp_cmdshell('dir')").allowed()).isFalse();
		assertThat(SqlGuard.check("SELECT LOAD_FILE('/etc/passwd')").allowed()).isFalse();
	}

	@Test
	void commentObfuscationIsHandled() {
		// 注释内 DROP 不影响（注释被剥离），首词 SELECT 放行
		assertThat(SqlGuard.check("-- DROP TABLE x\nSELECT 1").allowed()).isTrue();
		// 剥注释后首词为 DROP → 拦截
		assertThat(SqlGuard.check("-- comment\nDROP TABLE users").allowed()).isFalse();
	}

	@Test
	void unparseableSelectIsFailOpen() {
		// SELECT 开头但 AST 解析失败 → fail-open 放行（方言兜底）
		assertThat(SqlGuard.check("SELECT FROM WHERE").allowed()).isTrue();
	}

	// ===== 表名白名单 =====

	@Test
	void checkTablesAllowsWhitelistedTable() {
		assertThat(SqlGuard.checkTables("SELECT * FROM users WHERE id = 1", Set.of("users")))
			.isEqualTo(GuardResult.pass());
	}

	@Test
	void checkTablesBlocksUnauthorizedTable() {
		GuardResult result = SqlGuard.checkTables("SELECT * FROM secret_table", Set.of("users"));
		assertThat(result.allowed()).isFalse();
		assertThat(result.reason()).contains("越权表");
	}

	@Test
	void checkTablesBlocksWhenParseFails() {
		// 表名校验 fail-safe：解析失败当安全事件拒绝
		assertThat(SqlGuard.checkTables("not sql", Set.of("users")).allowed()).isFalse();
	}

	@Test
	void validateCombinesReadonlyAndTableCheck() {
		// 合法只读 + 合法表 → 过
		assertThat(SqlGuard.validate("SELECT * FROM users", Set.of("users")).allowed()).isTrue();
		// 越权表 → 拦
		assertThat(SqlGuard.validate("SELECT * FROM secret", Set.of("users")).allowed()).isFalse();
		// 非只读（DDL）→ check 先拦
		assertThat(SqlGuard.validate("DROP TABLE users", Set.of("users")).allowed()).isFalse();
		// allowedTables 为空 → 跳过表名校验，仅只读校验
		assertThat(SqlGuard.validate("SELECT * FROM users", Set.of()).allowed()).isTrue();
	}

}
