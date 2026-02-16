# v2-agent-browser-core：Kotlin 库 + JS 脚本（补齐 PRD 缺口）

## PRD Trace

- REQ-0002-006（select）
- REQ-0002-008（interactiveOnly=false 内容节点）
- REQ-0002-009（compact 裁剪）
- REQ-0002-010（不可见过滤）

## Scope（v2）

- 做：
  - JS：`clearOldRefs()`；更严格的 `isVisible()`；新增 `action(select)`；补齐 `query(value/html)`（如需）
  - Kotlin：`ActionKind.SELECT` + payload；`SnapshotJsOptions` 与 JS 参数对齐；render 输出补关键 attrs；补齐单测覆盖
- 不做：
  - iframe / file upload / waitFor / 真正的 Playwright 语义定位器

## DoD（硬口径）

- `.\gradlew :agent-browser-kotlin:test` 全绿，且新增用例覆盖：
  - `interactiveOnly=false`：能保留 heading/list/listitem/img 等内容节点（REQ-0002-008）
  - `compact=true`：无 ref 子孙且无文本的结构分支被裁剪（REQ-0002-009）
  - 不可见节点：`display:none` / `hidden` / `aria-hidden=true` 等被跳过（REQ-0002-010）
- `select`：
  - JS 返回 `{ ok:true, type:'action', action:'select', ... }`（失败返回 `ref_not_found`/`not_a_select`）
  - Kotlin `actionJs(..., SELECT, payload)` 与 `parseAction(...)` 行为可单测（REQ-0002-006）

## Steps（Strict TDD）

### ACTION-SELECT

1. 红：为 `ActionKind.SELECT` 写单测（JS 生成 + parse ok + parse error）。
2. 绿：实现 Kotlin payload 与 JS 生成；实现 JS `select` 分支。
3. 绿：在 Android E2E 中加一段 select 操作并断言 DOM 变化。

### SNAPSHOT-VISIBILITY

1. 红：写 fixture JSON（包含 hidden/aria-hidden/display none 节点）+ Kotlin render 断言不可见不出现。
2. 绿：实现/收紧 JS `isVisible()` 并清理旧 refs。

### SNAPSHOT-INTERACTIVE-ONLY

1. 红：写 snapshot fixture（interactiveOnly=false）解析/渲染测试，断言内容节点出现。
2. 绿：必要时调整 JS 端 content 节点的 include 规则（保守迭代）。

### RENDER-COMPACT

1. 红：构造“空结构分支 + 有 ref 分支”的 tree fixture，断言 compact 裁剪只删空支。
2. 绿：修正 renderer（尽量不扩大行为面）。

