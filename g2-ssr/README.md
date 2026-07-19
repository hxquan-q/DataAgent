# 门禁结论：PASS

DataAgent 的 `@antv/g2-ssr` 服务端渲染 POC。HTTP 契约：`POST /render`，请求体为 `{type, axis, data, path}`，15 秒超时，PNG 落盘并返回 URL。

> 版本说明：PRD 写的是官方包仅到 `0.0.4`；2026-07-15 实测 npm 官方包已发布 `0.2.0`，本 POC 固定使用 `0.2.0`。

## 启动

```bash
npm install
npm run samples
npm start
curl -X POST http://localhost:3000/render \
  -H 'content-type: application/json' \
  -d '{"type":"column","axis":{"x":"月份","y":"订单数"},"data":[{"月份":"一月","订单数":120}],"path":"demo.png"}'
```

Docker：

```bash
docker build -t dataagent-g2-ssr .
docker run --rm -p 3000:3000 -v "$PWD/output:/app/output" dataagent-g2-ssr
```

健康检查：`GET /health`。渲染结果可通过 `GET /charts/<文件名>` 下载。

## 图表样例

| 类型 | 截图 |
|---|---|
| bar | `samples/bar.png` |
| column | `samples/column.png` |
| line | `samples/line.png` |
| pie | `samples/pie.png` |
| 堆叠柱 | `samples/stacked-column.png` |
| 双轴 | `samples/dual-axis.png` |
| gauge | `samples/gauge.png` |
| 散点 | `samples/scatter.png` |
| 漏斗 | `samples/funnel.png` |

## 验证结论

- 已验证九种图表均可本地生成 `1600 × 960` PNG。
- 已验证本地 HTTP 服务、Docker 构建、容器健康检查、容器内渲染和 PNG 下载。
- 已验证内嵌 `Droid Sans Fallback` 中文字体加载，样例均使用中文标签。
- 官方原生 `gauge` 在 SSR 中报错，本 POC 用 G2 theta/interval 组合实现等价仪表盘。
- 推荐官方 `@antv/g2-ssr@0.2.0`；当前需求已覆盖，无需切换到未验证的 `@berryv/g2-ssr-node`。

## 降级建议

若后续数据规模或部署环境导致 SSR 门禁回归失败，回退到“前端 ECharts 约束化 spec”。交互报告仍可展示图表，但 HTML/PDF 导出将无法获得稳定的服务端 PNG，需要另行增加浏览器截图链路。
