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
package com.alibaba.cloud.ai.dataagent.prompt;

import com.alibaba.cloud.ai.dataagent.bo.schema.DisplayStyleBO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.EvidenceQueryRewriteDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.IntentRecognitionOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SemanticConsistencyDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.ColumnDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.entity.UserPromptConfig;
import org.springframework.stereotype.Component;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.ai.converter.BeanOutputConverter;

import static com.alibaba.cloud.ai.dataagent.util.ReportTemplateUtil.cleanJsonExample;

/**
 * 提示词构建辅助工具类。
 * <p>
 * 提供各类提示词（Prompt）的构建方法，将用户输入、数据库 Schema、证据信息等 填充到对应的提示词模板中，生成最终发送给大模型的提示文本。
 */
@Component
public class PromptHelper {

	private final SkillInjector skillInjector;

	public PromptHelper(SkillInjector skillInjector) {
		this.skillInjector = skillInjector;
	}

	/**
	 * 按作用域注入智能体技能。report 作用域用于报告提示词，sql/python 作用域共用同一入口。
	 * @param scope 作用域：report、sql 或 python
	 * @param agentId 智能体 ID
	 * @param basePrompt 基础提示词
	 * @return 注入后的提示词
	 */
	public String injectSkills(String scope, Long agentId, String basePrompt) {
		return skillInjector.inject(scope, agentId, basePrompt);
	}

	/**
	 * 构建 Schema 混合选择器提示词。
	 * @param evidence 参考信息
	 * @param question 用户问题
	 * @param schemaDTO 数据库 Schema 信息
	 * @return 渲染后的提示词
	 */
	public static String buildMixSelectorPrompt(String evidence, String question, SchemaDTO schemaDTO) {
		String schemaInfo = buildMixMacSqlDbPrompt(schemaDTO, true);
		Map<String, Object> params = new HashMap<>();
		params.put("schema_info", schemaInfo);
		params.put("question", question);
		if (StringUtils.isBlank(evidence))
			params.put("evidence", "无");
		else
			params.put("evidence", evidence);
		return PromptConstant.getMixSelectorPromptTemplate().render(params);
	}

	/** Schema 列样本：最多注入条数（R3 token 控制）。 */
	private static final int MAX_COLUMN_EXAMPLES = 2;

	/** Schema 提示词：单表最多列数。 */
	private static final int MAX_COLUMNS_PER_TABLE_PROMPT = 40;

	/** Schema 列样本：单条最大字符。 */
	private static final int MAX_EXAMPLE_CHARS = 40;

	/** 截断过长样本值，避免 free-text 列污染 schema 提示词。 */
	static String shortenExampleValue(String value) {
		if (value == null) {
			return "";
		}
		String v = value.trim();
		if (v.length() <= MAX_EXAMPLE_CHARS) {
			return v;
		}
		return v.substring(0, MAX_EXAMPLE_CHARS) + "…";
	}

	
	/**
	 * 宽表截断前重排：主键列置前，其余保持原序（稳定）。
	 */
	static List<ColumnDTO> prioritizePrimaryKeyColumns(List<ColumnDTO> columns, List<String> primaryKeys) {
		if (columns == null || columns.isEmpty()) {
			return List.of();
		}
		java.util.Set<String> pks = primaryKeys == null ? java.util.Set.of()
				: new java.util.LinkedHashSet<>(primaryKeys);
		List<ColumnDTO> pkCols = new ArrayList<>();
		List<ColumnDTO> idLikeCols = new ArrayList<>();
		List<ColumnDTO> rest = new ArrayList<>();
		for (ColumnDTO col : columns) {
			if (col == null || col.getName() == null) {
				rest.add(col);
				continue;
			}
			String name = col.getName();
			if (pks.contains(name)) {
				pkCols.add(col);
			}
			else if (isLikelyForeignKeyColumn(name)) {
				idLikeCols.add(col);
			}
			else {
				rest.add(col);
			}
		}
		List<ColumnDTO> ordered = new ArrayList<>(columns.size());
		ordered.addAll(pkCols);
		ordered.addAll(idLikeCols);
		ordered.addAll(rest);
		return ordered;
	}

	/** 启发式：*_id / id_* / 纯 id 视作关联列，截断时次优先于普通列。 */
	static boolean isLikelyForeignKeyColumn(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		String n = name.toLowerCase(java.util.Locale.ROOT);
		return "id".equals(n) || n.endsWith("_id") || n.startsWith("id_");
	}

public static String buildMixMacSqlDbPrompt(SchemaDTO schemaDTO, Boolean withColumnType) {
		StringBuilder sb = new StringBuilder();
		sb.append("【DB_ID】 ").append(schemaDTO.getName() == null ? "" : schemaDTO.getName()).append("\n");
		for (TableDTO tableDTO : schemaDTO.getTable()) {
			sb.append(buildMixMacSqlTablePrompt(tableDTO, withColumnType)).append("\n");
		}
		if (CollectionUtils.isNotEmpty(schemaDTO.getForeignKeys())) {
			sb.append("【Foreign keys】\n").append(StringUtils.join(schemaDTO.getForeignKeys(), "\n"));
		}
		return sb.toString();
	}

	public static String buildMixMacSqlTablePrompt(TableDTO tableDTO, Boolean withColumnType) {
		StringBuilder sb = new StringBuilder();
		// sb.append("# Table:
		// ").append(tableDTO.getName()).append(StringUtils.isBlank(tableDTO.getDescription())
		// ? "" : ", " + tableDTO.getDescription()).append("\n");
		sb.append("# Table: ").append(tableDTO.getName());
		if (!StringUtils.equals(tableDTO.getName(), tableDTO.getDescription())) {
			sb.append(StringUtils.isBlank(tableDTO.getDescription()) ? "" : ", " + tableDTO.getDescription())
				.append("\n");
		}
		else {
			sb.append("\n");
		}
		sb.append("[\n");
		List<String> columnLines = new ArrayList<>();
		List<ColumnDTO> columns = tableDTO.getColumn() == null ? List.of() : tableDTO.getColumn();
		// R5: 宽表截断时优先保留主键列，再保留其余列顺序
		List<ColumnDTO> ordered = prioritizePrimaryKeyColumns(columns, tableDTO.getPrimaryKeys());
		int colLimit = Math.min(ordered.size(), MAX_COLUMNS_PER_TABLE_PROMPT);
		for (int ci = 0; ci < colLimit; ci++) {
			ColumnDTO columnDTO = ordered.get(ci);
			StringBuilder line = new StringBuilder();
			line.append("(")
				.append(columnDTO.getName())
				.append(BooleanUtils.isTrue(withColumnType)
						? ":" + StringUtils.defaultString(columnDTO.getType(), "").toUpperCase(Locale.ROOT) : "");
			if (!StringUtils.equals(columnDTO.getDescription(), columnDTO.getName())) {
				line.append(", ").append(StringUtils.defaultString(columnDTO.getDescription(), ""));
			}
			if (CollectionUtils.isNotEmpty(tableDTO.getPrimaryKeys())
					&& tableDTO.getPrimaryKeys().contains(columnDTO.getName())) {
				line.append(", Primary Key");
			}
			// R3: 样本值有界 — 最多 2 个、单值 ≤40 字符，避免 schema 提示词被长样本撑爆
			List<String> enumData = Optional.ofNullable(columnDTO.getData())
				.orElse(new ArrayList<>())
				.stream()
				.filter(d -> !StringUtils.isEmpty(d))
				.map(PromptHelper::shortenExampleValue)
				.distinct()
				.limit(MAX_COLUMN_EXAMPLES)
				.collect(Collectors.toList());
			if (CollectionUtils.isNotEmpty(enumData) && !"id".equals(columnDTO.getName())) {
				line.append(", Examples: [");
				line.append(StringUtils.join(enumData, ",")).append("]");
			}

			line.append(")");
			columnLines.add(line.toString());
		}
		if (ordered.size() > colLimit) {
			columnLines.add("(... " + (ordered.size() - colLimit) + " more columns omitted)");
		}
		sb.append(StringUtils.join(columnLines, ",\n"));
		sb.append("\n]");
		return sb.toString();
	}

	public static String buildNewSqlGeneratorPrompt(SqlGenerationDTO sqlGenerationDTO) {
		String schemaInfo = buildMixMacSqlDbPrompt(sqlGenerationDTO.getSchemaDTO(), true);
		Map<String, Object> params = new HashMap<>();
		params.put("dialect", sqlGenerationDTO.getDialect());
		params.put("question", sqlGenerationDTO.getQuery());
		params.put("schema_info", schemaInfo);
		params.put("evidence", sqlGenerationDTO.getEvidence());
		params.put("execution_description", sqlGenerationDTO.getExecutionDescription());
		return PromptConstant.getNewSqlGeneratorPromptTemplate().render(params);
	}

	public static String buildSemanticConsistenPrompt(SemanticConsistencyDTO semanticConsistencyDTO) {
		Map<String, Object> params = new HashMap<>();
		params.put("dialect", semanticConsistencyDTO.getDialect());
		params.put("execution_description", semanticConsistencyDTO.getExecutionDescription());
		params.put("user_query", semanticConsistencyDTO.getUserQuery());
		params.put("evidence", semanticConsistencyDTO.getEvidence());
		params.put("schema_info", semanticConsistencyDTO.getSchemaInfo());
		params.put("sql", semanticConsistencyDTO.getSql());
		return PromptConstant.getSemanticConsistencyPromptTemplate().render(params);
	}

	/**
	 * 构建带自定义优化的报告生成提示词。
	 * @param userRequirementsAndPlan 用户需求和执行计划
	 * @param analysisStepsAndData 分析步骤和数据
	 * @param summaryAndRecommendations 总结和建议
	 * @param optimizationConfigs 用户自定义优化配置列表
	 * @return 渲染后的报告生成提示词
	 */
	public static String buildReportGeneratorPromptWithOptimization(String userRequirementsAndPlan,
			String analysisStepsAndData, String summaryAndRecommendations, List<UserPromptConfig> optimizationConfigs) {

		Map<String, Object> params = new HashMap<>();
		params.put("user_requirements_and_plan", userRequirementsAndPlan);
		params.put("analysis_steps_and_data", analysisStepsAndData);
		params.put("summary_and_recommendations", summaryAndRecommendations);
		params.put("json_example", cleanJsonExample);

		// Build optional optimization section content from user configs
		String optimizationSection = buildOptimizationSection(optimizationConfigs, params);
		params.put("optimization_section", optimizationSection);

		// only plain report
		return PromptConstant.getReportGeneratorPlainPromptTemplate().render(params);
	}

	public static String buildSqlErrorFixerPrompt(SqlGenerationDTO sqlGenerationDTO) {
		String schemaInfo = buildMixMacSqlDbPrompt(sqlGenerationDTO.getSchemaDTO(), true);

		Map<String, Object> params = new HashMap<>();
		params.put("dialect", sqlGenerationDTO.getDialect());
		params.put("question", sqlGenerationDTO.getQuery());
		params.put("schema_info", schemaInfo);
		params.put("evidence", sqlGenerationDTO.getEvidence());
		params.put("error_sql", sqlGenerationDTO.getSql());
		params.put("error_message", sqlGenerationDTO.getExceptionMessage());
		params.put("execution_description", sqlGenerationDTO.getExecutionDescription());

		return PromptConstant.getSqlErrorFixerPromptTemplate().render(params);
	}

	public static String buildBusinessKnowledgePrompt(String businessTerms) {
		Map<String, Object> params = new HashMap<>();
		if (StringUtils.isNotBlank(businessTerms))
			params.put("businessKnowledge", businessTerms);
		else
			params.put("businessKnowledge", "无");
		return PromptConstant.getBusinessKnowledgePromptTemplate().render(params);
	}

	// agentKnowledge
	public static String buildAgentKnowledgePrompt(String agentKnowledge) {
		Map<String, Object> params = new HashMap<>();
		if (StringUtils.isNotBlank(agentKnowledge))
			params.put("agentKnowledge", agentKnowledge);
		else
			params.put("agentKnowledge", "无");
		return PromptConstant.getAgentKnowledgePromptTemplate().render(params);
	}

	public static String buildSemanticModelPrompt(List<SemanticModel> semanticModels) {
		Map<String, Object> params = new HashMap<>();
		String semanticModel = CollectionUtils.isEmpty(semanticModels) ? ""
				: semanticModels.stream().map(SemanticModel::getPromptInfo).collect(Collectors.joining(";\n"));
		params.put("semanticModel", semanticModel);
		return PromptConstant.getSemanticModelPromptTemplate().render(params);
	}

	/**
	 * 构建优化提示词部分内容
	 * @param optimizationConfigs 优化配置列表
	 * @param params 模板参数
	 * @return 优化部分的内容
	 */
	/** 无用户自定义配置时的全局默认优化（V4 质量兜底）。 */
	private static final String DEFAULT_REPORT_OPTIMIZATION = """
		- 结论先行：直接回答用户问题，含关键数字与单位
		- 信息密度优先：默认 80–250 字；禁止「背景/过程回顾/后续行动」等注水段
		- 不得杜撰数据；无数据时一句话说明即可
		- 图表仅在有助于理解时使用，且必须是 echarts 纯 JSON 代码块
		""".strip();

	private static String buildOptimizationSection(List<UserPromptConfig> optimizationConfigs,
			Map<String, Object> params) {

		if (optimizationConfigs == null || optimizationConfigs.isEmpty()) {
			return "## 优化要求\n" + DEFAULT_REPORT_OPTIMIZATION;
		}

		StringBuilder result = new StringBuilder();
		result.append("## 优化要求\n");

		for (UserPromptConfig config : optimizationConfigs) {
			String optimizationContent = renderOptimizationPrompt(config.getOptimizationPrompt(), params);
			if (!optimizationContent.trim().isEmpty()) {
				result.append("- ").append(optimizationContent).append("\n");
			}
		}

		String body = result.toString().trim();
		// 配置存在但全空时仍兜底
		if ("## 优化要求".equals(body)) {
			return body + "\n" + DEFAULT_REPORT_OPTIMIZATION;
		}
		return body;
	}

	/**
	 * 构建意图识别提示词
	 * @param multiTurn 多轮对话历史
	 * @param latestQuery 最新用户输入
	 * @return 意图识别提示词
	 */
	public static String buildIntentRecognitionPrompt(String multiTurn, String latestQuery) {
		Map<String, Object> params = new HashMap<>();
		params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
		params.put("latest_query", latestQuery);
		BeanOutputConverter<IntentRecognitionOutputDTO> beanOutputConverter = new BeanOutputConverter<>(
				IntentRecognitionOutputDTO.class);
		params.put("format", beanOutputConverter.getFormat());
		return PromptConstant.getIntentRecognitionPromptTemplate().render(params);
	}

	/**
	 * 构建查询处理提示词
	 * @param multiTurn 多轮对话历史
	 * @param latestQuery 最新用户输入
	 * @return 查询处理提示词
	 */
	public static String buildQueryEnhancePrompt(String multiTurn, String latestQuery, String evidence) {
		Map<String, Object> params = new HashMap<>();
		params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
		params.put("latest_query", latestQuery);
		if (StringUtils.isEmpty(evidence))
			params.put("evidence", "无");
		else
			params.put("evidence", evidence);
		params.put("current_time_info", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		BeanOutputConverter<QueryEnhanceOutputDTO> beanOutputConverter = new BeanOutputConverter<>(
				QueryEnhanceOutputDTO.class);
		params.put("format", beanOutputConverter.getFormat());
		return PromptConstant.getQueryEnhancementPromptTemplate().render(params);
	}

	public static String buildDataViewAnalysisPrompt() {
		Map<String, Object> params = new HashMap<>();
		BeanOutputConverter<DisplayStyleBO> beanOutputConverter = new BeanOutputConverter<>(DisplayStyleBO.class);
		params.put("format", beanOutputConverter.getFormat());
		return PromptConstant.getDataViewAnalyzePromptTemplate().render(params);
	}

	/**
	 * 构建可行性评估提示词
	 * @param canonicalQuery 规范化查询
	 * @param recalledSchema 召回的数据库Schema
	 * @param evidence 参考信息
	 * @param multiTurn 多轮对话历史
	 * @return 可行性评估提示词
	 */
	public static String buildFeasibilityAssessmentPrompt(String canonicalQuery, SchemaDTO recalledSchema,
			String evidence, String multiTurn) {
		Map<String, Object> params = new HashMap<>();
		String schemaInfo = buildMixMacSqlDbPrompt(recalledSchema, true);
		params.put("canonical_query", canonicalQuery != null ? canonicalQuery : "");
		params.put("recalled_schema", schemaInfo);
		params.put("evidence", evidence != null ? evidence : "");
		params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
		return PromptConstant.getFeasibilityAssessmentPromptTemplate().render(params);
	}

	/**
	 * 构建查询重写提示词
	 * @param multiTurn 多轮对话历史
	 * @param latestQuery 最新用户输入
	 * @return 查询重写提示词
	 */
	public static String buildEvidenceQueryRewritePrompt(String multiTurn, String latestQuery) {
		Map<String, Object> params = new HashMap<>();
		params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
		params.put("latest_query", latestQuery);
		BeanOutputConverter<EvidenceQueryRewriteDTO> beanOutputConverter = new BeanOutputConverter<>(
				EvidenceQueryRewriteDTO.class);
		params.put("format", beanOutputConverter.getFormat());
		return PromptConstant.getEvidenceQueryRewritePromptTemplate().render(params);
	}

	/**
	 * 渲染优化提示词模板
	 * @param optimizationPrompt 优化提示词模板
	 * @param params 参数
	 * @return 渲染后的内容
	 */
	private static String renderOptimizationPrompt(String optimizationPrompt, Map<String, Object> params) {
		if (optimizationPrompt == null || optimizationPrompt.trim().isEmpty()) {
			return "";
		}
		try {
			return new PromptTemplate(optimizationPrompt).render(params);
		}
		catch (Exception e) {
			// 如果模板渲染失败，直接返回原始内容
			return optimizationPrompt;
		}
	}

}
