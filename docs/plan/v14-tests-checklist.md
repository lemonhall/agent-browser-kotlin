# v14：tests-checklist（★）对齐映射（测试 → 证据）

来源：`docs/prd/archive/tests-checklist.md`

> 目标：把 tests-checklist 里标注 ★ 的项，逐条落到“哪个测试覆盖 + 证据在哪里”，避免口径漂移。

## 1) Snapshot（P0）

- S-01..S-08（角色/文本/refs、interactiveOnly、maxDepth、compact、cursorInteractive gate）
  - JVM：`agent-browser-kotlin/src/test/kotlin/com/lsl/agentbrowser/AgentBrowserTest.kt`
    - `renderSnapshot_truncatesAtMaxDepth()`
    - `renderSnapshot_compactPrunesEmptyStructuralNodes()`
  - 真机：`app/src/androidTest/java/com/lsl/agent_browser_kotlin/WebViewE2eTest.kt`
    - `snapshot_fill_click_resnapshot_sees_dom_change()`
    - `ref_lifecycle_navigation_makes_old_ref_invalid()`
- S-09..S-12（预算截断/不可见过滤/compact 剪枝细则）
  - JVM：
    - `renderSnapshot_respectsMaxCharsTotal()`
    - `renderSnapshot_mergesJsTruncationIntoRenderStats()`
  - 真机：
    - `snapshot_fill_click_resnapshot_sees_dom_change()`（含 `aria-hidden` / hidden 内容不出现断言）

## 2) Action（P0）

- A-01（click 触发真实行为 + DOM 变化）
  - 真机：`snapshot_fill_click_resnapshot_sees_dom_change()`
- A-02（重复结构 ref 唯一 + 点击命中正确元素）
  - 真机：`repeated_structure_refs_are_unique_and_click_hits_correct_card()`
- A-03（scrollIntoView(ref) 成功）
  - 真机：`scroll_into_view_moves_page_and_allows_click()`
- A-04（scroll 命令支持 ref 作为 selector）
  - 说明：本仓库实现为 `scroll_into_view(ref)`（注入式 WebView API），对应 tests-checklist “scroll into view with refs”可迁移部分。
  - 真机：`scroll_into_view_moves_page_and_allows_click()`
- A-05（ref_not_found 明确错误，不崩溃）
  - JVM：`parseAction_successAndRefNotFound_areBothStructured()`
  - 真机：`ref_lifecycle_navigation_makes_old_ref_invalid()`
- A-06（fill 触发 input/change）
  - 真机：`snapshot_fill_click_resnapshot_sees_dom_change()`
- A-08（check/uncheck checkbox 状态正确）
  - 真机：`snapshot_fill_click_resnapshot_sees_dom_change()`
- A-09/A-10（遮挡点击的 AI 友好错误 + cookie banner 提示）
  - 真机：`click_blocked_by_cookie_banner_is_ai_friendly_then_dismiss_allows_click()`

## 3) Ref 生命周期（P0）

- R-01（重新 snapshot/导航后旧 ref 失效）
  - 真机：`ref_lifecycle_navigation_makes_old_ref_invalid()`
- R-02（ref 输入格式解析：@e1 / ref=e1 / [ref=e1] / e1）
  - JVM：`actionAndQueryJs_normalizeRefInputFormats()`

## 4) 协议解析与鲁棒性（P1，★ 风险点）

- P-06（非法 JSON 不崩溃，返回结构化错误）
  - JVM：`parseSafe_methods_turnInvalidJson_intoStructuredError()`

## 5) 额外：v12/v13 扩展（tests-checklist ★ 的可迁移补齐）

- Keyboard（keyDown/keyUp/char）+ state queries（isvisible/isenabled/ischecked）
  - JVM：`queryJs_supportsExtendedStateKinds()`、`pageJs_supportsKeyDownKeyUpChar()`
  - 真机：`keyboard_and_state_queries_keyDown_keyUp_char_isvisible_isenabled_ischecked()`
- type + mouse + wait + navigation helpers
  - JVM：`actionJs_supportsTypePayload()`、`pageJs_supportsNavigationAndMouseKinds()`
  - 真机：`v13_type_mouse_wait_and_page_navigation_e2e()`

## 6) 证据目录（本轮统一入口）

- Gradle 日志（可回溯）：`docs/evidence/`
- 人类可感知 E2E 包：
  - `adb_dumps/e2e/latest/index.html`
  - `adb_dumps/e2e/latest/e2e-latest.mp4`
  - `adb_dumps/e2e/latest/runs/`（每个 run 的 frames/snapshots/report）

