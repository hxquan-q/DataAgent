# Project rules (`.rule/`)

机器与人共用的**硬约束**。Claude Code 摘要：`.claude/rules/`。  
项目入口文档：`CLAUDE.md`（必须遵守本目录）· `AGENTS.md`（短入口）。

| 文件 | 优先级 | 说明 |
|------|--------|------|
| [hybrid-hotdeploy-db-safe.md](./hybrid-hotdeploy-db-safe.md) | **P1 alwaysApply** | 元库零影响 + hybrid 热部署；数据平面 > 开发便利 |
| [Java.md](./Java.md) | 参考 | 通用 Java 洁癖；**本仓库实现栈是 Spring Boot，不是 Quarkus** |
| [Nuxtjs.md](./Nuxtjs.md) | 参考 | Composition/TS 习惯；**本仓库是 Nuxt 4 + Vuetify，不是 Nuxt UI/Tailwind 默认** |
| [Cancel Ralph.md](./Cancel Ralph.md) | 流程 | 取消 Ralph 循环 |

人类长文：`docs/dev/HYBRID_HOTDEPLOY.md` · `docs/dev/LOCAL_RUN_CHECKLIST.md`。
