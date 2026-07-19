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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.converter.ModelConfigConverter;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.mapper.ModelConfigMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.converter.ModelConfigConverter.toDTO;
import static com.alibaba.cloud.ai.dataagent.converter.ModelConfigConverter.toEntity;

/**
 * AI 模型配置数据服务实现类，实现模型配置的增删改查、激活状态切换和降级查询逻辑。
 */
@Slf4j
@Service
@AllArgsConstructor
public class ModelConfigDataServiceImpl implements ModelConfigDataService {

	/** 模型配置数据访问层 */
	private final ModelConfigMapper modelConfigMapper;

	/**
	 * 根据主键 ID 查询模型配置。
	 * @param id 配置主键 ID
	 * @return 模型配置实体，不存在时返回 null
	 */
	@Override
	public ModelConfig findById(Integer id) {
		return modelConfigMapper.findById(id);
	}

	/**
	 * 切换激活状态，禁用同类型的其他配置并激活指定配置。
	 * @param id 配置主键 ID
	 * @param type 模型类型
	 */
	@Transactional(rollbackFor = Exception.class)
	@Override
	public void switchActiveStatus(Integer id, ModelType type) {
		// 先禁用同类型的其他配置
		modelConfigMapper.deactivateOthers(type.getCode(), id);

		// 再激活当前配置
		ModelConfig entity = modelConfigMapper.findById(id);
		if (entity != null) {
			entity.setIsActive(true);
			entity.setUpdatedTime(LocalDateTime.now());
			modelConfigMapper.updateById(entity);
		}
	}

	/**
	 * 列出全部模型配置。
	 * @return 模型配置 DTO 列表
	 */
	@Override
	public List<ModelConfigDTO> listConfigs() {
		return modelConfigMapper.findAll().stream().map(ModelConfigConverter::toDTO).collect(Collectors.toList());
	}

	/**
	 * 新增模型配置，仅入库不切换激活状态。
	 * @param dto 待新增的模型配置 DTO
	 */
	@Override
	public void addConfig(ModelConfigDTO dto) {
		clean(dto);
		// 只存库，不切换
		modelConfigMapper.insert(toEntity(dto));
	}

	/**
	 * 清理 DTO 中的字段，去除首尾空格。
	 * @param dto 模型配置 DTO
	 */
	private void clean(ModelConfigDTO dto) {
		dto.setModelName(dto.getModelName().trim());
		dto.setBaseUrl(dto.getBaseUrl().trim());
		dto.setApiKey(dto.getApiKey().trim());
		if (dto.getCompletionsPath() != null) {
			dto.setCompletionsPath(dto.getCompletionsPath().trim());
		}
		if (dto.getEmbeddingsPath() != null) {
			dto.setEmbeddingsPath(dto.getEmbeddingsPath().trim());
		}
	}

	/**
	 * 更新数据库中的模型配置（不处理热切换），返回更新后的实体供上层判断是否需要刷新内存。
	 * @param dto 待更新的模型配置 DTO
	 * @return 更新后的实体
	 */
	@Transactional(rollbackFor = Exception.class)
	@Override
	public ModelConfig updateConfigInDb(ModelConfigDTO dto) {
		clean(dto);
		// 查询旧数据
		ModelConfig entity = modelConfigMapper.findById(dto.getId());
		if (entity == null) {
			throw new RuntimeException("配置不存在");
		}

		// 模型类型不允许修改
		if (!entity.getModelType().getCode().equals(dto.getModelType()))
			throw new RuntimeException("模型类型不允许修改");

		// 合并字段并更新数据库
		mergeDtoToEntity(dto, entity);
		entity.setUpdatedTime(LocalDateTime.now());

		modelConfigMapper.updateById(entity);

		return entity;
	}

	/**
	 * 将 DTO 字段合并到实体中，API Key 中含 "****" 时保留原值。
	 * @param dto 模型配置 DTO
	 * @param oldEntity 旧的实体对象
	 */
	private static void mergeDtoToEntity(ModelConfigDTO dto, ModelConfig oldEntity) {
		oldEntity.setProvider(dto.getProvider());
		oldEntity.setBaseUrl(dto.getBaseUrl());
		oldEntity.setModelName(dto.getModelName());
		oldEntity.setTemperature(dto.getTemperature());
		oldEntity.setMaxTokens(dto.getMaxTokens());
		oldEntity.setCompletionsPath(dto.getCompletionsPath());
		oldEntity.setEmbeddingsPath(dto.getEmbeddingsPath());
		oldEntity.setUpdatedTime(LocalDateTime.now());
		oldEntity.setProxyEnabled(dto.getProxyEnabled());
		oldEntity.setProxyHost(dto.getProxyHost());
		oldEntity.setProxyPort(dto.getProxyPort());
		oldEntity.setProxyUsername(dto.getProxyUsername());
		oldEntity.setProxyPassword(dto.getProxyPassword());

		// 只有前端传来的 Key 不包含 "****" 时，才说明用户真的改了 Key，否则保持原样
		if (dto.getApiKey() != null && !dto.getApiKey().contains("****")) {
			oldEntity.setApiKey(dto.getApiKey());
		}
	}

	/**
	 * 删除模型配置（软删除），激活状态的配置不允许删除。
	 * @param id 配置主键 ID
	 */
	@Override
	public void deleteConfig(Integer id) {
		// 先查询是否存在
		ModelConfig entity = modelConfigMapper.findById(id);
		if (entity == null) {
			throw new RuntimeException("配置不存在");
		}

		// 激活状态的配置禁止删除
		if (Boolean.TRUE.equals(entity.getIsActive())) {
			throw new RuntimeException("该配置当前正在使用中，无法删除！请先激活其他配置，再进行删除操作。");
		}

		// 执行软删除
		entity.setIsDeleted(1);
		entity.setUpdatedTime(LocalDateTime.now());
		int updated = modelConfigMapper.updateById(entity);
		if (updated == 0) {
			throw new RuntimeException("删除失败");
		}
	}

	/**
	 * 根据模型类型获取当前激活的配置。
	 * @param modelType 模型类型
	 * @return 激活的模型配置 DTO，不存在时返回 null
	 */
	@Override
	public ModelConfigDTO getActiveConfigByType(ModelType modelType) {
		ModelConfig entity = modelConfigMapper.selectActiveByType(modelType.getCode());
		if (entity == null) {
			// R192: 中文可操作日志
			log.warn("未找到已激活的 {} 模型配置。请到「模型服务」添加并激活（CHAT 为问答硬依赖，EMBEDDING 影响召回）。",
					modelType);
			return null;
		}
		return toDTO(entity);
	}

}
