# v3-index：agent-browser-kotlin

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **执行口径 PRD**：`docs/prd/PRD-0002-agent-browser-kotlin.md`
- **版本**：v3
- **日期**：2026-02-16
- **对齐起点**：`docs/align/v2-prd-v4-alignment.md`

## v3 目标

以“补齐 PRD-V4 的 Action/Query 主干”为主，让闭环不仅能 click/fill/select，还能完成常见表单交互与可观测查询：

- 补齐主要 action：`check/uncheck/clear/focus/hover/scroll_into_view`（并把 click/fill/select 统一 scrollIntoView）
- 补齐 query：`value/html/computed_styles`，并收敛 Kotlin API（`queryJs/parseQuery`）
- 真机 E2E 增强：用 query 让“fill/clear/check”这些效果可被人类与断言直接感知

## 里程碑（v3）

| Milestone | 范围 | DoD（可二元判定） | 验证方式 | 状态 |
|---|---|---|---|---|
| M11 | Action 扩展 | JS 支持新增 action；Kotlin `ActionKind` + payload 对齐；单测覆盖 | `.\gradlew :agent-browser-kotlin:test` | todo |
| M12 | Query 扩展 | JS 支持 `value/html/computed_styles`；Kotlin 增加 `queryJs/parseQuery`；单测覆盖 | `.\gradlew :agent-browser-kotlin:test` | todo |
| M13 | 真机 E2E 可观测 | E2E 增加 clear/check/uncheck 的断言（通过 query/value 或 DOM 文本体现），并输出截图帧 + mp4 | `.\gradlew :app:connectedAndroidTest` | todo |

## 计划索引

- `docs/plan/v3-agent-browser-core.md`
- `docs/plan/v3-android-e2e.md`

## 追溯矩阵（PRD-V4 条目 → v3 plan → tests/commands → 证据）

| PRD-V4 模块 | v3 Plan | tests/commands | 证据 | 状态 |
|---|---|---|---|---|
| Action（5.3） | v3-agent-browser-core §ACTION-MORE | `.\gradlew :agent-browser-kotlin:test` + E2E | — | todo |
| Query（5.4） | v3-agent-browser-core §QUERY-MORE | `.\gradlew :agent-browser-kotlin:test` + E2E | — | todo |

