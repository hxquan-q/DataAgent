# DataAgent v0.1 评测基线

- 基线提交：`dev@ff93c0a`
- 评测日期：2026-07-15
- 入口：`DataAgentBaselineEvalTest`
- 命令：`./mvnw -q -Dtest=*Eval* test`
- 环境：H2 内存库、冻结 NL2SQL 输出，不访问外部模型或数据源

## 基线数值

| 维度 | 样本 | 当前 dev 基线 |
|---|---:|---:|
| SQL 执行成功率 | 6 个自然语言问题 | 5/6 = **83.33%** |
| Schema 召回 Macro-F1 | 4 个多表 JOIN 标注 | **81.67%** |
| 图表渲染成功率 | 5 个 `echarts` 代码块 | 4/5 = **80.00%** |
| 请求到首字节 | 6 次离线链路，P50 | **0.413 ms** |
| 请求到完成 | 6 次离线链路，P50 | **1.704 ms** |

## 口径

1. **SQL 执行成功率**：自然语言问题进入生产 `SqlGenerateNode`，由冻结的 `Nl2SqlService.generateSql` 返回流式 SQL，经生产 `sqlTrim` 清理后在 H2 执行；无 SQL 异常即成功。
2. **Schema 召回 F1**：冻结召回文档经生产 `SchemaRecallNode` 输出后，逐题与人工标注表集合比较，计算每题 F1 后取 Macro-F1。当前缺失主要是多跳 JOIN 的桥接表。
3. **图表渲染成功率**：提取 Markdown 中所有 `echarts` fenced code block；配置必须为合法 JSON、根节点为对象、`series` 非空且每个 series 有 `type`。
4. **端到端耗时**：从构造 NL2SQL 请求开始；节点首个流式响应为首字节，SQL 执行结束为完成。这里是稳定可重复的离线回归耗时，**不包含远端 LLM 网络与推理耗时**。

冻结输出让 CI 无需 API Key 即可重复运行；线上模型质量评测应在有固定模型、温度和数据快照时另行跑批。

## 当前 dev 门禁阻塞

`dev@ff93c0a` 的 `ColumnTypeUtil.java:30` 含 `varchar*/char*`，其中 `*/` 提前结束 Javadoc，导致上述 Maven 命令在执行 Eval 前即解析失败。该既有源码不在本任务允许修改范围内；Eval 用例已通过直接 Surefire 执行及临时修正该注释后的完整生命周期验证。
