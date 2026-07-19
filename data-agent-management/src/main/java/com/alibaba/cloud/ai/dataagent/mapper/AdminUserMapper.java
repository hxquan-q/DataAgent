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
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.AdminUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 平台管理员 Mapper。
 */
@Mapper
public interface AdminUserMapper {

	@Select("SELECT COUNT(1) FROM admin_user")
	long count();

	@Select("SELECT * FROM admin_user WHERE username = #{username} LIMIT 1")
	AdminUser findByUsername(@Param("username") String username);

	@Select("SELECT * FROM admin_user WHERE id = #{id} LIMIT 1")
	AdminUser findById(@Param("id") Long id);

	@Insert("""
			INSERT INTO admin_user (username, password_hash, display_name, status, create_time, update_time)
			VALUES (#{username}, #{passwordHash}, #{displayName}, #{status}, NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(AdminUser user);

	@Update("""
			UPDATE admin_user
			SET password_hash = #{passwordHash}, update_time = NOW()
			WHERE id = #{id}
			""")
	int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

	@Update("""
			UPDATE admin_user
			SET last_login_at = NOW(), update_time = NOW()
			WHERE id = #{id}
			""")
	int touchLastLogin(@Param("id") Long id);

}
