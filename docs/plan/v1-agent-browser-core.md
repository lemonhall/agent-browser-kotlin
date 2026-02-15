# v1-agent-browser-core：Kotlin 库 + JS 脚本（最小闭环的可测试内核）

## Goal

在不依赖 Android 的前提下，交付可复用的 Kotlin/JVM 库：提供 JS 脚本注入内容、生成 WebView 可执行表达式、解析 JSON、按预算渲染 snapshot 文本；并为 action/query 提供稳定协议与错误处理。

## PRD Trace

- REQ-0002-003 / REQ-0002-007 / REQ-0002-008 / REQ-0002-009 / REQ-0002-010 / REQ-0002-011 / REQ-0002-012

## Scope

- 做：Kotlin 数据模型 + JSON 解析 + 渲染（预算/compact）+ JS 表达式生成（snapshot/action/query）。
- 不做：Android WebView 适配层（evaluateJavascript 回调管理、页面等待等）；性能基准；select/复杂 action（v1 先不做）。

## Acceptance（硬口径）

- `.\gradlew :agent-browser-kotlin:test` 全绿，exit code 0。
- 所有输出文本满足 `maxCharsTotal` 上限；命中预算时 `truncated=true` 且 `truncateReasons` 非空。
- `ref_not_found` 等错误被解析为结构化错误类型（不抛出未捕获异常）。

## Files（预计）

- `agent-browser-kotlin/build.gradle.kts`
- `agent-browser-kotlin/src/main/kotlin/...`
- `agent-browser-kotlin/src/main/resources/agent-browser.js`
- `agent-browser-kotlin/src/test/kotlin/...`
- `agent-browser-js/agent-browser.js`（源文件；资源由构建同步/拷贝）

## Steps（Strict TDD）

### TDD-Render（红→绿→重构）

1. 红：为渲染器写失败测试（预算截断/compact/节点数限制/深度限制）。
2. 红：运行 `.\gradlew :agent-browser-kotlin:test`，确认失败原因与断言匹配。
3. 绿：实现最小渲染器与预算控制。
4. 绿：重复运行测试全绿。
5. 重构：整理模型与渲染逻辑（不改变行为）。

### TDD-SnapshotSchema（红→绿→重构）

1. 红：给 snapshot JSON 的解析写失败测试（最小字段、refs、tree、错误形态）。
2. 绿：实现 `SnapshotResult` 反序列化与必要字段校验。

### TDD-Action（红→绿→重构）

1. 红：为 `action` 成功/失败 JSON 解析写测试，覆盖 `ref_not_found`。
2. 绿：实现 `ActionResult` 解析与错误类型。

## Risks

- JSON schema 变更导致 Kotlin/JS 不一致：通过 fixtures + 单测固化 schema，避免“感觉一致”。
- compact 规则过强误删：先做保守版（只删空结构且无 ref 子孙），再迭代。

