# v6-index：agent-browser-kotlin

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **版本**：v6
- **日期**：2026-02-16
- **对齐起点**：`docs/align/v6-prd-v4-alignment.md`

## v6 目标

把主要差异从“JS 侧 5.x”切到“Kotlin 侧 6.x”收敛：对齐 PRD 的 Kotlin API 最小形态，并修复执行口径文档缺失。

## 里程碑（v6）

| Milestone | 范围 | DoD（可二元判定） | 验证方式 | 状态 |
|---|---|---|---|---|
| M23 | OutputFormat | 新增 `OutputFormat(PLAIN_TEXT_TREE/JSON)`；`RenderOptions.format` 可用；默认不破坏既有调用 | JVM 单测 | todo |
| M24 | renderSnapshot 产物 | 提供“单入口”输出 `text + refs + stats`（PRD-V4 6.x 口径）；Android E2E 调用改为新入口 | JVM + E2E | todo |
| M25 | 修复执行口径 PRD | 恢复 `docs/prd/PRD-0002-agent-browser-kotlin.md`（从归档抽取）并修正文档引用 | `rg PRD-0002` 无 broken link | todo |
| M26 | 真机证据 | v6 完整回归并落盘 evidence | `.\gradlew :app:connectedAndroidTest` | todo |

## 计划索引

- `docs/plan/v6-kotlin-api.md`

