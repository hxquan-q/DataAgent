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
package com.alibaba.cloud.ai.dataagent.properties;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmServiceEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DataAgent 核心配置属性类，绑定 {@code spring.ai.alibaba.data-agent.*} 前缀。
 * <p>
 * 涵盖 LLM 调用方式、向量检索阈值、报告模板、SQL 重试、文本分块等全部业务参数。
 * </p>
 *
 * @author vlsmb
 */
@Getter
@Setter
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX)
public class DataAgentProperties {

	/**
	 * LLM 服务调用方式：{@code stream}（流式）/ {@code block}（阻塞）
	 */
	private LlmServiceEnum llmServiceType = LlmServiceEnum.STREAM;

	/**
	 * Embedding 批处理配置（分批调用向量化接口，避免超 token/条数限制）
	 */
	private EmbeddingBatch embeddingBatch = new EmbeddingBatch();

	/**
	 * 向量存储检索配置（Top-K、相似度阈值等）
	 */
	private VectorStoreProperties vectorStore = new VectorStoreProperties();

	/**
	 * 报告生成模板配置（Marked.js、ECharts CDN 地址）
	 */
	private ReportTemplate reportTemplate = new ReportTemplate();

	/**
	 * SQL 执行失败重试次数
	 */
	private int maxSqlRetryCount = 10;

	/**
	 * SQL 优化最多次数
	 */
	private int maxSqlOptimizeCount = 10;

	/**
	 * SQL 优化分数阈值（达到该分数即认为 SQL 质量合格）
	 */
	private double sqlScoreThreshold = 0.95;

	/**
	 * 文本分块策略配置
	 */
	private TextSplitter textSplitter = new TextSplitter();

	/**
	 * 多轮对话中最多保留的历史轮数
	 */
	private int maxturnhistory = 5;

	/**
	 * 单次规划（Plan）最大长度限制
	 */
	private int maxplanlength = 2000;

	/**
	 * 每张表的最大预估列数（用于 Schema 召回时的截断控制）
	 */
	private int maxColumnsPerTable = 50;

	/**
	 * 是否启用SQL执行结果图表判断，默认启用
	 */
	private boolean enableSqlResultChart = true;

	/**
	 * 执行SQL结果图表化超时时间，默认3000ms
	 */
	private Long enrichSqlResultTimeout = 3000L;

	/**
	 * 是否启用 g2-ssr 服务端图表渲染（#6 图文并茂），默认启用；服务不可用时 fail-safe 回退纯文本报告
	 */
	private boolean enableChartRender = true;

	/**
	 * 是否启用 SQL 只读护栏（#17），默认启用；用 jsqlparser 拦截 DDL/DML，解析失败 fail-open 放行
	 */
	private boolean enableSqlGuard = true;

	/**
	 * 是否启用独立 SQL 步骤并发执行（#10），默认关闭；开启后无依赖步骤经 dbOperationExecutor 并发执行。
	 * 并发通道暂不含语义校验与图表渲染，默认关闭即退回含全部特性的串行模式
	 */
	private boolean enableConcurrentSteps = false;

	/**
	 * 报告生成模板配置：Markdown 解析器与图表库的 CDN 地址。
	 */
	@Getter
	@Setter
	public static class ReportTemplate {

		// Marked.js (Markdown 解析器) 南方科技大学开源软件镜像站
		private String markedUrl = "https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/marked/12.0.0/marked.min.js";

		// ECharts (图表库) 南方科技大学开源软件镜像站
		private String echartsUrl = "https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/echarts/5.5.0/echarts.min.js";

	}

	/**
	 * 文本分块策略配置，支持 Token、递归字符、句子、语义、段落五种分块方式。
	 */
	@Getter
	@Setter
	public static class TextSplitter {

		/**
		 * 默认分块大小，基于token数量 默认值：1000
		 */
		private int chunkSize = 1000;

		/**
		 * TokenTextSplitter 策略配置
		 */
		private TokenTextSplitterConfig token = new TokenTextSplitterConfig();

		/**
		 * RecursiveCharacterTextSplitter 策略配置
		 */
		private RecursiveTextSplitterConfig recursive = new RecursiveTextSplitterConfig();

		/**
		 * SentenceTextSplitter 策略配置
		 */
		private SentenceTextSplitterConfig sentence = new SentenceTextSplitterConfig();

		/**
		 * SemanticTextSplitter 策略配置
		 */
		private SemanticTextSplitterConfig semantic = new SemanticTextSplitterConfig();

		/**
		 * ParagraphTextSplitter 策略配置
		 */
		private ParagraphTextSplitterConfig paragraph = new ParagraphTextSplitterConfig();

		/**
		 * TokenTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class TokenTextSplitterConfig {

			/**
			 * 最小分块字符数 默认值：400
			 */
			private int minChunkSizeChars = 400;

			/**
			 * 嵌入最小分块长度 默认值：10
			 */
			private int minChunkLengthToEmbed = 10;

			/**
			 * 最大分块数量 默认值：5000
			 */
			private int maxNumChunks = 5000;

			/**
			 * 是否保留分隔符 默认值：true
			 */
			private boolean keepSeparator = true;

		}

		/**
		 * RecursiveCharacterTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class RecursiveTextSplitterConfig {

			/**
			 * 重叠区域字符数 默认值：200
			 */
			private int chunkOverlap = 200;

			/**
			 * 分隔符列表（如果为 null，该类内部有默认的分隔符列表）
			 */
			private String[] separators = null;

		}

		/**
		 * SentenceTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class SentenceTextSplitterConfig {

			/**
			 * 句子重叠数量 默认值：1（保留前一个分块的最后1个句子）
			 */
			private int sentenceOverlap = 1;

		}

		/**
		 * SemanticTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class SemanticTextSplitterConfig {

			/**
			 * 最小分块大小 默认值：200
			 */
			private int minChunkSize = 200;

			/**
			 * 最大分块大小 默认值：1000
			 */
			private int maxChunkSize = 1000;

			/**
			 * 语义相似度阈值 默认值：0.5（0-1之间，越低越容易分块）
			 */
			private double similarityThreshold = 0.5;

		}

		/**
		 * ParagraphTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class ParagraphTextSplitterConfig {

			/**
			 * 段落重叠字符数 默认值：200（保留前一个分块的最后200个字符，而非段落数量）
			 */
			private int paragraphOverlapChars = 200;

		}

	}

	/**
	 * Embedding 批处理配置：控制每次向量化请求的 token 上限与文本条数， 避免超出 Embedding 模型 API 限制（如 DashScope 单批最多
	 * 10 条）。
	 */
	@Getter
	@Setter
	public static class EmbeddingBatch {

		/**
		 * encodingType 默认值：cl100k_base，适用于OpenAI等模型
		 */
		private String encodingType = "cl100k_base";

		/**
		 * 每批次最大令牌数 值越小，每批次文档越少，但更安全 值越大，处理效率越高，但可能超出API限制 建议值：2000-8000，根据实际API限制调整
		 */
		private int maxTokenCount = 8000;

		/**
		 * 预留百分比 用于预留缓冲空间，避免超出限制 建议值：0.1-0.2（10%-20%）
		 */
		private double reservePercentage = 0.2;

		/**
		 * 每批次最大文本数量 适用于DashScope等有文本数量限制的API DashScope限制为10
		 */
		private int maxTextCount = 10;

	}

	/**
	 * 向量存储检索配置：控制召回 Top-K、相似度阈值、混合搜索等。
	 * <p>
	 * 表级召回（tableTopkLimit）阈值较低，尽量不漏表；通用召回（defaultTopkLimit）阈值较高，保证精度。
	 * </p>
	 */
	@Getter
	@Setter
	public static class VectorStoreProperties {

		/**
		 * 表级召回返回的最大表数量（Top-K）
		 */
		private int tableTopkLimit = 10;

		/**
		 * 表级相似度下限；设置较低以避免漏召回表
		 */
		private double tableSimilarityThreshold = 0.2;

		/**
		 * 全局默认相似度阈值（用于 BusinessTerm、AgentKnowledge 等），过滤分数低于此值的文档
		 */
		private double defaultSimilarityThreshold = 0.4;

		/**
		 * 查询时返回的最大文档数量
		 */
		private int defaultTopkLimit = 8;

		/**
		 * 一次删除操作中，最多删除的文档数量
		 */
		private int batchDelTopkLimit = 5000;

		/**
		 * 是否启用混合搜索（向量 + 关键词）
		 */
		private boolean enableHybridSearch = false;

		/**
		 * Elasticsearch最小分数阈值，用于es执行关键词搜索时过滤相关性较低的文档
		 */
		private double elasticsearchMinScore = 0.5;

		/**
		 * SimpleVectorStore本地序列化文件地址
		 */
		private String filePath = "./vectorstore/vectorstore.json";

	}

}
