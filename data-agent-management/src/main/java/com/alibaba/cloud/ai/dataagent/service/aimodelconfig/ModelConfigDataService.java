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
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;

import java.util.List;

/**
 * AI 模型配置数据服务接口，提供模型配置的增删改查和激活状态切换能力。
 */
public interface ModelConfigDataService {

	/**
	 * 根据主键 ID 查询模型配置。
	 * @param id 配置主键 ID
	 * @return 模型配置实体，不存在时返回 null
	 */
	ModelConfig findById(Integer id);

	/**
	 * 切换激活状态，禁用同类型的其他配置并激活指定配置。
	 * @param id 配置主键 ID
	 * @param type 模型类型
	 */
	void switchActiveStatus(Integer id, ModelType type);

	/**
	 * 列出全部模型配置。
	 * @return 模型配置 DTO 列表
	 */
	List<ModelConfigDTO> listConfigs();

	/**
	 * 新增模型配置，仅入库不切换激活状态。
	 * @param dto 待新增的模型配置 DTO
	 */
	void addConfig(ModelConfigDTO dto);

	/**
	 * 更新数据库中的模型配置（不处理热切换）。
	 * @param dto 待更新的模型配置 DTO
	 * @return 更新后的实体
	 */
	ModelConfig updateConfigInDb(ModelConfigDTO dto);

	/**
	 * 删除模型配置（软删除），激活状态的配置不允许删除。
	 * @param id 配置主键 ID
	 */
	void deleteConfig(Integer id);

	/**
	 * 根据模型类型获取当前激活的配置。
	 * @param modelType 模型类型
	 * @return 激活的模型配置 DTO，不存在时返回 null
	 */
	ModelConfigDTO getActiveConfigByType(ModelType modelType);

}
