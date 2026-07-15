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

import com.alibaba.cloud.ai.dataagent.util.SqlGuard.GuardResult;
import org.junit.jupiter.api.Test;

/**
 * {@link SqlGuard} 单测（#17 只读护栏）。
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
		GuardResult result = SqlGuard.check("INSERT INTO users (id) VALUES (1)");
		assertThat(result.allowed()).isFalse();
		assertThat(result.reason()).contains("拦截");
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

	@Test
	void unparseableIsFailOpen() {
		// 无法解析的语句 fail-open 放行，不阻断主流程
		assertThat(SqlGuard.check("not a real sql @@@ !!!").allowed()).isTrue();
	}

}
