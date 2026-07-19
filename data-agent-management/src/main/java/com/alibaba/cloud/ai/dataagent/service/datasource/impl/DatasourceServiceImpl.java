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
package com.alibaba.cloud.ai.dataagent.service.datasource.impl;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPool;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.DatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.LogicalRelationMapper;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.registry.DatasourceTypeHandlerRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// todo: 检查Mapper的返回值，判断是否执行成功（或者对Mapper进行AOP）
/**
 * 数据源管理服务实现类，提供数据源增删改查、连接测试、表与字段查询、 逻辑外键管理等功能的完整实现。
 *
 * <p>
 * 通过 {@link DatasourceTypeHandlerRegistry} 实现多数据库类型适配， 通过 {@link AccessorFactory} 和
 * {@link DBConnectionPoolFactory} 管理数据库访问器与连接池。
 * </p>
 */
@Slf4j
@Service
@AllArgsConstructor
public class DatasourceServiceImpl implements DatasourceService {

	/** 数据源数据访问层 */
	private final DatasourceMapper datasourceMapper;

	/** Agent 数据源关联数据访问层 */
	private final AgentDatasourceMapper agentDatasourceMapper;

	/** 逻辑外键数据访问层 */
	private final LogicalRelationMapper logicalRelationMapper;

	/** 数据库连接池工厂 */
	private final DBConnectionPoolFactory poolFactory;

	/** 数据库访问器工厂 */
	private final AccessorFactory accessorFactory;

	/** 数据源类型处理器注册中心 */
	private final DatasourceTypeHandlerRegistry datasourceTypeHandlerRegistry;

	/**
	 * 获取全部数据源列表。
	 * @return 数据源列表
	 */
	@Override
	public List<Datasource> getAllDatasource() {
		return datasourceMapper.selectAll();
	}

	/**
	 * 根据状态获取数据源列表。
	 * @param status 数据源状态
	 * @return 符合状态的数据源列表
	 */
	@Override
	public List<Datasource> getDatasourceByStatus(String status) {
		return datasourceMapper.selectByStatus(status);
	}

	/**
	 * 根据类型获取数据源列表。
	 * @param type 数据源类型
	 * @return 符合类型的数据源列表
	 */
	@Override
	public List<Datasource> getDatasourceByType(String type) {
		return datasourceMapper.selectByType(type);
	}

	/**
	 * 根据主键 ID 获取数据源详情。
	 * @param id 数据源主键 ID
	 * @return 数据源对象，不存在时返回 null
	 */
	@Override
	public Datasource getDatasourceById(Integer id) {
		return datasourceMapper.selectById(id);
	}

	/**
	 * 创建数据源，通过类型处理器生成连接 URL 并设置默认状态值。
	 * @param datasource 待创建的数据源对象
	 * @return 创建后的数据源对象
	 */
	@Override
	public Datasource createDatasource(Datasource datasource) {
		// 通过类型处理器生成连接 URL
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String connectionUrl = handler.resolveConnectionUrl(datasource);
		if (StringUtils.isNotBlank(connectionUrl)) {
			datasource.setConnectionUrl(connectionUrl);
		}

		// 设置默认值
		if (datasource.getStatus() == null) {
			datasource.setStatus("active");
		}
		if (datasource.getTestStatus() == null) {
			datasource.setTestStatus("unknown");
		}

		if (datasource.getPassword() == null) {
			datasource.setPassword("");
		}

		if (datasource.getUsername() == null) {
			datasource.setUsername("");
		}

		datasourceMapper.insert(datasource);
		return datasource;
	}

	/**
	 * 更新数据源，重新生成连接 URL 并在密码为空时保留原密码。
	 * @param id 数据源主键 ID
	 * @param datasource 待更新的数据源对象
	 * @return 更新后的数据源对象
	 */
	@Override
	public Datasource updateDatasource(Integer id, Datasource datasource) {
		// 重新生成连接 URL
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String connectionUrl = handler.resolveConnectionUrl(datasource);
		if (StringUtils.isNotBlank(connectionUrl)) {
			datasource.setConnectionUrl(connectionUrl);
		}
		datasource.setId(id);

		// 密码为空时保留原密码，避免因前端未传密码导致密码被清空
		if (datasource.getPassword() == null || datasource.getPassword().isEmpty()) {
			Datasource existing = datasourceMapper.selectById(id);
			if (existing != null && existing.getPassword() != null) {
				datasource.setPassword(existing.getPassword());
			}
		}
		// 兜底：如果密码仍为 null（原有密码也为 null），设为空字符串
		if (datasource.getPassword() == null) {
			datasource.setPassword("");
		}

		if (datasource.getUsername() == null) {
			datasource.setUsername("");
		}

		datasourceMapper.updateById(datasource);
		return datasource;
	}

	/**
	 * 删除数据源，先清理与 Agent 的关联关系，再删除数据源记录。
	 * @param id 数据源主键 ID
	 */
	@Override
	@Transactional
	public void deleteDatasource(Integer id) {
		// 先删除与 Agent 的关联关系
		agentDatasourceMapper.deleteAllByDatasourceId(id);

		// 再删除数据源本身
		datasourceMapper.deleteById(id);
	}

	/**
	 * 更新数据源的测试状态。
	 * @param id 数据源主键 ID
	 * @param testStatus 测试状态（success、failed、unknown）
	 */
	@Override
	public void updateTestStatus(Integer id, String testStatus) {
		datasourceMapper.updateTestStatusById(id, testStatus);
	}

	/**
	 * 测试数据源连接是否可用，并更新测试状态。
	 * @param id 数据源主键 ID
	 * @return 连接是否成功
	 */
	@Override
	public boolean testConnection(Integer id) {
		Datasource datasource = getDatasourceById(id);
		if (datasource == null) {
			return false;
		}
		try {
			// 执行 ping 连接测试
			boolean connectionSuccess = realConnectionTest(datasource);
			log.info(datasource.getName() + " test connection result: " + connectionSuccess);
			// 根据测试结果更新测试状态
			updateTestStatus(id, connectionSuccess ? "success" : "failed");

			return connectionSuccess;
		}
		catch (Exception e) {
			updateTestStatus(id, "failed");
			log.error("Error testing connection for datasource ID " + id + ": " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * 实际的连接测试方法，通过连接池执行 ping 操作。
	 * @param datasource 数据源实体
	 * @return 连接是否成功
	 */
	private boolean realConnectionTest(Datasource datasource) {
		// 将数据源转换为数据库配置
		DbConfigBO config = new DbConfigBO();
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String originalUrl = handler.resolveConnectionUrl(datasource);

		if (StringUtils.isNotBlank(originalUrl)) {
			originalUrl = handler.normalizeTestUrl(datasource, originalUrl);
		}
		config.setUrl(originalUrl);
		config.setUsername(datasource.getUsername());
		config.setPassword(datasource.getPassword());

		// 通过连接池执行 ping 测试
		DBConnectionPool pool = poolFactory.getPoolByType(datasource.getType());
		if (pool == null) {
			return false;
		}

		ErrorCodeEnum result = pool.ping(config);
		return result == ErrorCodeEnum.SUCCESS;

	}

	/**
	 * 获取与 Agent 关联的数据源列表（已废弃，请使用 AgentDatasourceService）。
	 * @param agentId Agent 主键 ID
	 * @return Agent 关联的数据源列表
	 */
	@Override
	@Deprecated
	public List<AgentDatasource> getAgentDatasource(Long agentId) {
		List<AgentDatasource> adentDatasources = agentDatasourceMapper.selectByAgentIdWithDatasource(agentId);

		// 手动填充数据源信息（因为 MyBatis Plus 不直接支持复杂的关联查询结果映射）
		for (AgentDatasource agentDatasource : adentDatasources) {
			if (agentDatasource.getDatasourceId() != null) {
				Datasource datasource = datasourceMapper.selectById(agentDatasource.getDatasourceId());
				agentDatasource.setDatasource(datasource);
			}
		}

		return adentDatasources;
	}

	/**
	 * 获取数据源的表列表。
	 * @param datasourceId 数据源主键 ID
	 * @return 排序后的表名列表
	 * @throws Exception 查询过程中发生异常时抛出
	 */
	@Override
	public List<String> getDatasourceTables(Integer datasourceId) throws Exception {
		log.info("Getting tables for datasource: {}", datasourceId);

		// 获取数据源信息
		Datasource datasource = this.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new RuntimeException("Datasource not found with id: " + datasourceId);
		}

		// 创建数据库配置
		DbConfigBO dbConfig = getDbConfig(datasource);

		// 创建查询参数
		DbQueryParameter queryParam = DbQueryParameter.from(dbConfig);

		// 提取 schema 名称
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String schemaName = handler.extractSchemaName(datasource);
		queryParam.setSchema(schemaName);

		// 查询表列表
		Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		List<TableInfoBO> tableInfoList = dbAccessor.showTables(dbConfig, queryParam);

		// 提取并排序表名
		List<String> tableNames = tableInfoList.stream()
			.map(TableInfoBO::getName)
			.filter(name -> name != null && !name.trim().isEmpty())
			.sorted()
			.toList();

		log.info("Found {} tables for datasource: {}", tableNames.size(), datasourceId);
		return tableNames;
	}

	/**
	 * 根据数据源实体构建数据库配置对象。
	 * @param datasource 数据源实体
	 * @return 数据库配置对象
	 */
	@Override
	public DbConfigBO getDbConfig(Datasource datasource) {
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		return handler.toDbConfig(datasource);
	}

	/**
	 * 获取数据源表的字段列表。
	 * @param datasourceId 数据源主键 ID
	 * @param tableName 表名
	 * @return 排序后的字段名列表
	 * @throws Exception 查询过程中发生异常时抛出
	 */
	@Override
	public List<String> getTableColumns(Integer datasourceId, String tableName) throws Exception {
		log.info("Getting columns for table: {} in datasource: {}", tableName, datasourceId);

		// 获取数据源信息
		Datasource datasource = this.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new RuntimeException("Datasource not found with id: " + datasourceId);
		}

		// 创建数据库配置
		DbConfigBO dbConfig = getDbConfig(datasource);

		// 创建查询参数
		DbQueryParameter queryParam = DbQueryParameter.from(dbConfig);

		// 提取 schema 名称
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String schemaName = handler.extractSchemaName(datasource);
		queryParam.setSchema(schemaName);
		queryParam.setTable(tableName);

		// 查询字段列表
		Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		List<ColumnInfoBO> columnInfoList = dbAccessor.showColumns(dbConfig, queryParam); // 提取并排序字段名
		List<String> columnNames = columnInfoList.stream()
			.map(ColumnInfoBO::getName)
			.filter(name -> name != null && !name.trim().isEmpty())
			.sorted()
			.toList();

		log.info("Found {} columns for table {} in datasource: {}", columnNames.size(), tableName, datasourceId);
		return columnNames;
	}

	/**
	 * 获取数据源的逻辑外键列表。
	 * @param datasourceId 数据源主键 ID
	 * @return 逻辑外键列表
	 */
	@Override
	public List<LogicalRelation> getLogicalRelations(Integer datasourceId) {
		log.info("Getting logical relations for datasource: {}", datasourceId);
		return logicalRelationMapper.selectByDatasourceId(datasourceId);
	}

	/**
	 * 添加逻辑外键，会先检查是否已存在相同的外键关系。
	 * @param datasourceId 数据源主键 ID
	 * @param logicalRelation 待添加的逻辑外键对象
	 * @return 添加后的逻辑外键对象
	 */
	@Override
	public LogicalRelation addLogicalRelation(Integer datasourceId, LogicalRelation logicalRelation) {
		log.info("Adding logical relation for datasource: {}", datasourceId);

		// 设置数据源ID
		logicalRelation.setDatasourceId(datasourceId);

		// 检查是否已存在相同的外键关系
		int exists = logicalRelationMapper.checkExists(datasourceId, logicalRelation.getSourceTableName(),
				logicalRelation.getSourceColumnName(), logicalRelation.getTargetTableName(),
				logicalRelation.getTargetColumnName());

		if (exists > 0) {
			throw new RuntimeException("该逻辑外键关系已存在");
		}

		// 插入外键
		logicalRelationMapper.insert(logicalRelation);
		log.info("Logical relation added successfully with id: {}", logicalRelation.getId());

		return logicalRelation;
	}

	/**
	 * 更新逻辑外键，验证外键存在且归属正确后再更新。
	 * @param datasourceId 数据源主键 ID
	 * @param logicalRelationId 逻辑外键主键 ID
	 * @param logicalRelation 待更新的逻辑外键对象
	 * @return 更新后的逻辑外键对象
	 */
	@Override
	public LogicalRelation updateLogicalRelation(Integer datasourceId, Integer logicalRelationId,
			LogicalRelation logicalRelation) {
		log.info("Updating logical relation: {} for datasource: {}", logicalRelationId, datasourceId);

		// 验证外键是否存在且属于该数据源
		LogicalRelation existingRelation = logicalRelationMapper.selectById(logicalRelationId);
		if (existingRelation == null) {
			throw new RuntimeException("逻辑外键不存在，ID: " + logicalRelationId);
		}

		if (!existingRelation.getDatasourceId().equals(datasourceId)) {
			throw new RuntimeException("逻辑外键不属于指定的数据源");
		}

		// 设置ID和数据源ID
		logicalRelation.setId(logicalRelationId);
		logicalRelation.setDatasourceId(datasourceId);

		// 更新外键
		int updated = logicalRelationMapper.updateById(logicalRelation);
		if (updated == 0) {
			throw new RuntimeException("更新逻辑外键失败");
		}

		log.info("Logical relation updated successfully: {}", logicalRelationId);

		// 返回更新后的数据
		return logicalRelationMapper.selectById(logicalRelationId);
	}

	/**
	 * 删除逻辑外键，验证外键归属正确后再删除。
	 * @param datasourceId 数据源主键 ID
	 * @param logicalRelationId 逻辑外键主键 ID
	 */
	@Override
	public void deleteLogicalRelation(Integer datasourceId, Integer logicalRelationId) {
		log.info("Deleting logical relation: {} for datasource: {}", logicalRelationId, datasourceId);

		// 验证外键是否属于该数据源
		LogicalRelation logicalRelation = logicalRelationMapper.selectById(logicalRelationId);
		if (logicalRelation == null) {
			throw new RuntimeException("逻辑外键不存在，ID: " + logicalRelationId);
		}

		if (!logicalRelation.getDatasourceId().equals(datasourceId)) {
			throw new RuntimeException("逻辑外键不属于指定的数据源");
		}

		// 删除外键（逻辑删除）
		int deleted = logicalRelationMapper.deleteById(logicalRelationId);
		if (deleted == 0) {
			throw new RuntimeException("删除逻辑外键失败");
		}

		log.info("Logical relation deleted successfully: {}", logicalRelationId);
	}

	/**
	 * 批量保存逻辑外键，删除不在传入列表中的旧记录，对传入列表去重后执行插入或更新。
	 * @param datasourceId 数据源主键 ID
	 * @param logicalRelations 待保存的逻辑外键列表
	 * @return 保存后的逻辑外键列表
	 */
	@Override
	@Transactional
	public List<LogicalRelation> saveLogicalRelations(Integer datasourceId, List<LogicalRelation> logicalRelations) {
		log.info("Saving {} logical relations for datasource: {}", logicalRelations.size(), datasourceId);

		// 获取现有的所有外键关系
		List<LogicalRelation> existingRelations = logicalRelationMapper.selectByDatasourceId(datasourceId);
		Map<Integer, LogicalRelation> existingMap = existingRelations.stream()
			.collect(Collectors.toMap(LogicalRelation::getId, relation -> relation));

		// 收集传入列表中已存在的ID
		Set<Integer> incomingIds = logicalRelations.stream()
			.map(LogicalRelation::getId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());

		// 删除那些不在传入列表中的外键
		int deletedCount = 0;
		for (LogicalRelation existing : existingRelations) {
			if (!incomingIds.contains(existing.getId())) {
				logicalRelationMapper.deleteById(existing.getId());
				deletedCount++;
				log.info("Deleted logical relation: {} -> {}", existing.getSourceTableName(),
						existing.getTargetTableName());
			}
		}
		log.info("Deleted {} logical relations for datasource: {}", deletedCount, datasourceId);

		// 去重检查
		List<LogicalRelation> uniqueRelations = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		for (LogicalRelation logicalRelation : logicalRelations) {
			String key = logicalRelation.getSourceTableName() + "|" + logicalRelation.getSourceColumnName() + "|"
					+ logicalRelation.getTargetTableName() + "|" + logicalRelation.getTargetColumnName();

			if (!seen.contains(key)) {
				seen.add(key);
				uniqueRelations.add(logicalRelation);
			}
			else {
				log.warn("跳过重复的逻辑外键: {} -> {}", logicalRelation.getSourceTableName(),
						logicalRelation.getTargetTableName());
			}
		}

		int duplicateCount = logicalRelations.size() - uniqueRelations.size();
		if (duplicateCount > 0) {
			log.warn("检测到并去重了 {} 条重复的逻辑外键", duplicateCount);
		}

		// 插入或更新去重后的外键列表
		int insertedCount = 0;
		int updatedCount = 0;
		for (LogicalRelation logicalRelation : uniqueRelations) {
			logicalRelation.setDatasourceId(datasourceId);

			if (logicalRelation.getId() != null && existingMap.containsKey(logicalRelation.getId())) {
				// 更新现有记录
				logicalRelationMapper.updateById(logicalRelation);
				updatedCount++;
				log.debug("Updated logical relation: {} -> {}", logicalRelation.getSourceTableName(),
						logicalRelation.getTargetTableName());
			}
			else {
				// 插入新记录
				logicalRelation.setId(null);
				logicalRelationMapper.insert(logicalRelation);
				insertedCount++;
				log.debug("Inserted logical relation: {} -> {}", logicalRelation.getSourceTableName(),
						logicalRelation.getTargetTableName());
			}
		}

		log.info("Saved logical relations for datasource {}: {} inserted, {} updated, {} deleted", datasourceId,
				insertedCount, updatedCount, deletedCount);

		return logicalRelationMapper.selectByDatasourceId(datasourceId);
	}

}
