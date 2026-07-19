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

import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.util.EncryptedStringTypeHandler;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 数据源 Mapper 接口，操作 {@code datasource} 表。
 * <p>
 * 管理数据源连接信息（类型、主机、端口、凭证、连接串、状态）及其统计查询、增删改。
 * </p>
 * <p>
 * {@code password} 列经 {@link EncryptedStringTypeHandler} 透明加解密：落库为 {@code enc:v1:} 密文，实体字段始终
 * 持有明文。其余列依赖默认 PARTIAL 自动映射。
 * </p>
 *
 * @author Alibaba Cloud AI
 */
@Mapper
public interface DatasourceMapper {

	/**
	 * 根据主键查询数据源。
	 * @param id 数据源 ID
	 * @return 数据源；不存在返回 {@code null}
	 */
	@ResultMap("dsResults")
	@Select("SELECT * FROM datasource WHERE id = #{id}")
	Datasource selectById(@Param("id") Integer id);

	/**
	 * 查询全部数据源，按创建时间倒序返回。
	 * @return 数据源列表
	 */
	@Results(id = "dsResults", value = { @Result(column = "password", property = "password", typeHandler = EncryptedStringTypeHandler.class) })
	@Select("SELECT * FROM datasource ORDER BY create_time DESC")
	List<Datasource> selectAll();

	/**
	 * 新增数据源，并将自增主键回填到入参对象的 {@code id} 字段。password 落库前加密。
	 * @param datasource 数据源实体
	 * @return 受影响行数
	 */
	@Insert("""
			INSERT INTO datasource
			    (name, type, host, port, database_name, username, password, connection_url, status, test_status, description, creator_id, create_time, update_time)
			VALUES (#{name}, #{type}, #{host}, #{port}, #{databaseName}, #{username}, #{password, typeHandler=com.alibaba.cloud.ai.dataagent.util.EncryptedStringTypeHandler}, #{connectionUrl}, #{status}, #{testStatus}, #{description}, #{creatorId}, NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Datasource datasource);

	/**
	 * 根据主键动态更新数据源（仅更新非空字段），并刷新 {@code update_time}。password 更新前加密。
	 * @param datasource 数据源实体（需携带 {@code id}）
	 * @return 受影响行数
	 */
	@Update("""
			<script>
			UPDATE datasource
			<set>
			    <if test="name != null">name = #{name},</if>
			    <if test="type != null">type = #{type},</if>
			    <if test="host != null">host = #{host},</if>
			    <if test="port != null">port = #{port},</if>
			    <if test="databaseName != null">database_name = #{databaseName},</if>
			    <if test="username != null">username = #{username},</if>
			    <if test="password != null">password = #{password, typeHandler=com.alibaba.cloud.ai.dataagent.util.EncryptedStringTypeHandler},</if>
			    <if test="connectionUrl != null">connection_url = #{connectionUrl},</if>
			    <if test="status != null">status = #{status},</if>
			    <if test="testStatus != null">test_status = #{testStatus},</if>
			    <if test="description != null">description = #{description},</if>
			    <if test="creatorId != null">creator_id = #{creatorId},</if>
			    update_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(Datasource datasource);

	/**
	 * 更新指定数据源的连通性测试状态。
	 * @param id 数据源 ID
	 * @param testStatus 测试状态
	 * @return 受影响行数
	 */
	@Update("UPDATE datasource SET test_status = #{testStatus} WHERE id = #{id}")
	int updateTestStatusById(@Param("id") Integer id, @Param("testStatus") String testStatus);

	/**
	 * 根据状态查询数据源列表，按创建时间倒序返回。
	 * @param status 数据源状态
	 * @return 数据源列表
	 */
	@ResultMap("dsResults")
	@Select("SELECT * FROM datasource WHERE status = #{status} ORDER BY create_time DESC")
	List<Datasource> selectByStatus(@Param("status") String status);

	/**
	 * 根据类型查询数据源列表，按创建时间倒序返回。
	 * @param type 数据源类型
	 * @return 数据源列表
	 */
	@ResultMap("dsResults")
	@Select("SELECT * FROM datasource WHERE type = #{type} ORDER BY create_time DESC")
	List<Datasource> selectByType(@Param("type") String type);

	/**
	 * 按状态分组统计数据源数量。
	 * @return 每组的状态与数量（key 为 status、count）
	 */
	@Select("SELECT status, COUNT(*) as count FROM datasource GROUP BY status")
	List<Map<String, Object>> selectStatusStats();

	/**
	 * 按类型分组统计数据源数量。
	 * @return 每组的类型与数量（key 为 type、count）
	 */
	@Select("SELECT type, COUNT(*) as count FROM datasource GROUP BY type")
	List<Map<String, Object>> selectTypeStats();

	/**
	 * 按连通性测试状态分组统计数据源数量。
	 * @return 每组的测试状态与数量（key 为 test_status、count）
	 */
	@Select("SELECT test_status, COUNT(*) as count FROM datasource GROUP BY test_status")
	List<Map<String, Object>> selectTestStatusStats();

	/**
	 * 查询数据源总数。
	 * @return 数据源总数
	 */
	@Select("SELECT COUNT(*) FROM datasource")
	Long selectCount();

	/**
	 * 根据主键物理删除数据源。
	 * @param id 数据源 ID
	 * @return 受影响行数
	 */
	@Delete("DELETE FROM datasource WHERE id = #{id}")
	int deleteById(Integer id);

}
