# v12：PRD-V4 对齐（聚焦 tests-checklist）

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 测试清单：`docs/prd/archive/tests-checklist.md`（本轮继续对齐其中 ★ 相关项）
- 日期：2026-02-16

## 1. 本轮范围（摘差）

> v11 已补齐“点击被遮挡的 AI 友好错误 + scroll_into_view + 重复结构 ref 唯一性 + ref 输入归一化 + render 选项”并产出真机可感知证据。

本轮（v12）继续对齐 tests-checklist 中仍缺证据/覆盖的点，重点补齐：

- **Keyboard 相关（tests-checklist ★：input_keyboard 部分）**：
  - 目前仅有 `page.pressKey(key)`（PRD-V4 5.5）能力；缺少更细粒度的 `keyDown/keyUp/char` 能力与 E2E 证据。
- **元素状态查询（tests-checklist ★：element state queries）**：
  - 目前 `query()` 仅支持 `text/html/value/attrs/computed_styles/outerHTML`；
  - 缺少 `isvisible/isenabled/ischecked`（或等价）查询与 E2E 证据。

## 2. 当前实现状态（愿景 vs 现实）

### 2.1 已实现（有测试/证据）

- Snapshot：refs、interactiveOnly、compact、maxDepth、cursorInteractive gate（v5 相关 E2E）
- Action：fill/clear/select/check/uncheck/scroll_into_view、重复结构 ref 唯一性（v11 E2E）、遮挡错误（v11 E2E）
- Page：info/scroll（E2E）、pressKey（实现存在，未补强证据）

### 2.2 缺口（需要补齐）

- `page.keyDown/keyUp/char`（tests-checklist ★）：
  - 需要 JS 侧实现 + Kotlin 侧 wire 封装 + 真机 E2E（离线页面监听 key 事件/输入变化）。
- `query(isvisible|isenabled|ischecked)`（tests-checklist ★）：
  - 需要 JS 侧实现 + Kotlin `QueryKind` 扩展 + 真机 E2E（离线页面包含 disabled/checkbox/隐藏元素）。

### 2.3 不在本库范围（记录口径，避免漂移）

tests-checklist 中与 Playwright/daemon/CDP/protocol-json 解析相关的项（如 navigate/back/forward/reload、invalid commands、CDP input injection 等）属于 **agent-browser Node/Playwright 工程**能力，本仓库（WebView + 注入脚本）不做等价实现；本轮不作为缺口推进。

## 3. v12 验收证据（将于执行完成后回填）

- Kotlin 单测：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- Android 真机 E2E：`.\gradlew :app:connectedAndroidTest --no-daemon`

执行结果（已回填）：

- 证据文件：
  - `docs/evidence/2026-02-16-v12-agent-browser-kotlin-test.txt`
  - `docs/evidence/2026-02-16-v12-connectedAndroidTest.txt`
  - `docs/evidence/2026-02-16-v12-pull-e2e-artifacts.txt`
- 真机可感知证据（拉取后本地）：
  - `adb_dumps/e2e/latest/e2e-latest.mp4`
  - `adb_dumps/e2e/latest/snapshots/`

本轮完成项（tests-checklist ★ 口径）：

- `page.keyDown/keyUp/char`：已实现 + 真机 E2E 覆盖（离线 `keyboard_state.html`）
- `query(isvisible|isenabled|ischecked)`：已实现 + 真机 E2E 覆盖（同上）
