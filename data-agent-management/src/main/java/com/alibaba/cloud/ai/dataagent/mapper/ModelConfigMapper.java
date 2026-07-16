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

import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.util.EncryptedStringTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 模型配置 Mapper，操作 {@code model_config} 表。
 * <p>
 * 管理大语言模型/嵌入模型的供应商、接入地址、凭证、参数、代理及启用状态， 支持条件查询、按类型激活、互斥停用与软删除。
 * </p>
 * <p>
 * {@code api_key} / {@code proxy_password} 两列经 {@link EncryptedStringTypeHandler} 透明加解密：
 * 落库为 {@code enc:v1:} 密文，实体字段始终持有明文。其余列依赖默认 PARTIAL 自动映射。
 * </p>
 */
@Mapper
public interface ModelConfigMapper {

	/**
	 * 查询全部未删除的模型配置，按创建时间倒序返回。
	 * @return 模型配置列表
	 */
	@Results(id = "modelConfigResults", value = {
			@Result(column = "api_key", property = "apiKey", typeHandler = EncryptedStringTypeHandler.class),
			@Result(column = "proxy_password", property = "proxyPassword", typeHandler = EncryptedStringTypeHandler.class) })
	@Select("""
			SELECT id, provider, base_url, api_key, model_name, temperature, is_active, max_tokens,
			       model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted,
			       proxy_enabled, proxy_host, proxy_port, proxy_username, proxy_password
			FROM model_config WHERE is_deleted = 0 ORDER BY created_time DESC
			""")
	List<ModelConfig> findAll();

	/**
	 * 根据主键查询未删除的模型配置。
	 * @param id 模型配置 ID
	 * @return 模型配置；不存在返回 {@code null}
	 */
	@ResultMap("modelConfigResults")
	@Select("""
			SELECT id, provider, base_url, api_key, model_name, temperature, is_active, max_tokens,
			       model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted,
			       proxy_enabled, proxy_host, proxy_port, proxy_username, proxy_password
			FROM model_config WHERE id = #{id} AND is_deleted = 0
			""")
	ModelConfig findById(Integer id);

	/**
	 * 根据模型类型查询当前启用的模型配置（至多一条）。
	 * @param modelType 模型类型
	 * @return 启用的模型配置；不存在返回 {@code null}
	 */
	@ResultMap("modelConfigResults")
	@Select("""
			SELECT id, provider, base_url, api_key, model_name, temperature, is_active, max_tokens,
			       model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted,
			       proxy_enabled, proxy_host, proxy_port, proxy_username, proxy_password
			FROM model_config WHERE model_type = #{modelType} AND is_active = 1 AND is_deleted = 0 LIMIT 1
			""")
	ModelConfig selectActiveByType(@Param("modelType") String modelType);

	/**
	 * 将同类型下除当前配置外的其他模型全部停用（用于实现“同类型仅一个启用”的互斥逻辑）。
	 * @param modelType 模型类型
	 * @param currentId 当前启用的模型配置 ID
	 */
	@Update("UPDATE model_config SET is_active = 0 WHERE model_type = #{modelType} AND id != #{currentId} AND is_deleted = 0")
	void deactivateOthers(@Param("modelType") String modelType, @Param("currentId") Integer currentId);

	/**
	 * 按多条件组合查询未删除的模型配置（供应商、关键词、启用状态、最大 token、类型均可选），按创建时间倒序返回。
	 * @param provider 供应商（可为 {@code null}）
	 * @param keyword 关键词（可为 {@code null}）
	 * @param isActive 启用状态（可为 {@code null}）
	 * @param maxTokens 最大 token 数（可为 {@code null}）
	 * @param modelType 模型类型（可为 {@code null}）
	 * @return 匹配的模型配置列表
	 */
	@ResultMap("modelConfigResults")
	@Select("""
			<script>
			   SELECT id, provider, base_url, api_key, model_name, temperature, is_active, max_tokens,
			          model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted,
			          proxy_enabled, proxy_host, proxy_port, proxy_username, proxy_password
			   FROM model_config
			   <where>
			      is_deleted = 0
			      <if test='provider != null and provider != ""'>
			         AND provider = #{provider}
			      </if>
			      <if test='keyword != null and keyword != ""'>
			         AND (provider LIKE CONCAT('%', #{keyword}, '%')
			             OR base_url LIKE CONCAT('%', #{keyword}, '%')
			             OR model_name LIKE CONCAT('%', #{keyword}, '%'))
			      </if>
			      <if test='isActive != null'>
			         AND is_active = #{isActive}
			      </if>
			      <if test='maxTokens != null'>
			         AND max_tokens = #{maxTokens}
			      </if>
			      <if test='modelType != null'>
			         AND model_type = #{modelType}
			      </if>
			   </where>
			   ORDER BY created_time DESC
			</script>
			""")
	List<ModelConfig> findByConditions(@Param("provider") String provider, @Param("keyword") String keyword,
			@Param("isActive") Boolean isActive, @Param("maxTokens") Integer maxTokens,
			@Param("modelType") String modelType);

	/**
	 * 新增模型配置，并将自增主键回填到入参对象的 {@code id} 字段。api_key / proxy_password 落库前加密。
	 * @param modelConfig 模型配置实体
	 * @return 受影响行数
	 */
	@Insert("""
			INSERT INTO model_config (provider, base_url, api_key, model_name, temperature, is_active, max_tokens,
			                         model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted,
			                         proxy_enabled, proxy_host, proxy_port, proxy_username, proxy_password)
			VALUES (#{provider}, #{baseUrl}, #{apiKey, typeHandler=com.alibaba.cloud.ai.dataagent.util.EncryptedStringTypeHandler},
			        #{modelName}, #{temperature}, #{isActive}, #{maxTokens},
			        #{modelType}, #{completionsPath}, #{embeddingsPath}, NOW(), NOW(), 0,
			        #{proxyEnabled}, #{proxyHost}, #{proxyPort}, #{proxyUsername},
			        #{proxyPassword, typeHandler=com.alibaba.cloud.ai.dataagent.util.EncryptedStringTypeHandler})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(ModelConfig modelConfig);

	/**
	 * 根据主键动态更新模型配置（仅更新非空字段），并刷新 {@code updated_time}。api_key / proxy_password 更新前加密。
	 * @param modelConfig 模型配置实体（需携带 {@code id}）
	 * @return 受影响行数
	 */
	@Update("""
			<script>
			          UPDATE model_config
			          <trim prefix="SET" suffixOverrides=",">
			            <if test='provider != null'>provider = #{provider},</if>
			            <if test='baseUrl != null'>base_url = #{baseUrl},</if>
			            <if test='apiKey != null'>api_key = #{apiKey, typeHandler=com.alibaba.cloud.ai.dataagent.util.EncryptedStringTypeHandler},</if>
			            <if test='modelName != null'>model_name = #{modelName},</if>
			            <if test='temperature != null'>temperature = #{temperature},</if>
			            <if test='isActive != null'>is_active = #{isActive},</if>
			            <if test='maxTokens != null'>max_tokens = #{maxTokens},</if>
			            <if test='modelType != null'>model_type = #{modelType},</if>
			            <if test='completionsPath != null'>completions_path = #{completionsPath},</if>
			            <if test='embeddingsPath != null'>embeddings_path = #{embeddingsPath},</if>
			            <if test='isDeleted != null'>is_deleted = #{isDeleted},</if>
			            <if test='proxyEnabled != null'>proxy_enabled = #{proxyEnabled},</if>
			            <if test='proxyHost != null'>proxy_host = #{proxyHost},</if>
			            <if test='proxyPort != null'>proxy_port = #{proxyPort},</if>
			            <if test='proxyUsername != null'>proxy_username = #{proxyUsername},</if>
			            <if test='proxyPassword != null'>proxy_password = #{proxyPassword, typeHandler=com.alibaba.cloud.ai.dataagent.util.EncryptedStringTypeHandler},</if>
			            updated_time = NOW()
			          </trim>
			          WHERE id = #{id}
			</script>
			""")
	int updateById(ModelConfig modelConfig);

	/**
	 * 根据主键软删除模型配置（将 {@code is_deleted} 置为 1）。
	 * @param id 模型配置 ID
	 * @return 受影响行数
	 */
	@Update("""
			UPDATE model_config SET is_deleted = 1 WHERE id = #{id}
			""")
	int deleteById(Integer id);

}
