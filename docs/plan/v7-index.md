# v7-index：agent-browser-kotlin

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **版本**：v7
- **日期**：2026-02-16
- **对齐起点**：`docs/align/v7-prd-v4-alignment.md`

## v7 目标

补齐 PRD-V4 6.2 的 Kotlin “page helpers / query limit” 便捷入口，并把 E2E 证据从“只有截图帧”增强为“截图帧 + snapshot 文本/JSON 落盘”，让人类可以离线核对 snapshot 结构与 refs。

## 里程碑（v7）

| Milestone | 范围 | DoD（可二元判定） | 验证方式 | 状态 |
|---|---|---|---|---|
| M27 | Kotlin helpers | 新增 `scrollJs/pressKeyJs/getUrlJs/getTitleJs` + `queryJs(ref, kind, limit)` overload；JVM 单测覆盖 | `.\gradlew :agent-browser-kotlin:test` | todo |
| M28 | E2E snapshot dumps | connectedAndroidTest 每个关键 step 产出 `Downloads/.../snapshots/*.txt`（以及可选 `.json`），且可被读取校验 | `.\gradlew :app:connectedAndroidTest` | todo |
| M29 | 真机证据 | v7 完整回归并落盘 evidence | `.\gradlew :app:connectedAndroidTest` | todo |

## 计划索引

- `docs/plan/v7-kotlin-page-helpers.md`

