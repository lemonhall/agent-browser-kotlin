# v3-agent-browser-core：Action/Query 主干补齐

## PRD Trace（PRD-V4）

- Action（5.3）：`check/uncheck/focus/hover/scroll_into_view/clear`（+ click/fill/select 的 scrollIntoView）
- Query（5.4）：`value/html/computed_styles`（并给 Kotlin 一等 API）

## Scope（v3）

- 做：
  - JS：扩展 `window.__agentBrowser.action(...)` 与 `query(...)`
  - Kotlin：补齐 `ActionKind`、payload、`queryJs/parseQuery`、QueryResult 模型
  - 单测：JS 生成字符串 + 解析结构化错误/成功
- 不做：
  - page.*（页面级操作）留到 v4
  - CONTENT 节点 ref 分配规则收敛留到 v4

## DoD（硬口径）

- `.\gradlew :agent-browser-kotlin:test` 全绿
- 新增 action/query 的失败/成功都可结构化解析（不依赖字符串 contains）

## Steps（Strict TDD）

### ACTION-MORE

1. 红：为 `ActionKind.CLEAR/CHECK/UNCHECK/FOCUS/HOVER/SCROLL_INTO_VIEW` 写单测（JS 生成 + parse ok/err）。
2. 绿：实现 Kotlin 侧 kind 映射与 payload（如无需则统一 `{}`）；实现 JS action 分支。

### QUERY-MORE

1. 红：为 `queryJs/parseQuery` 写单测（`value/html/computed_styles` + `ref_not_found`）。
2. 绿：实现 Kotlin 侧模型；实现 JS `query` 分支。

