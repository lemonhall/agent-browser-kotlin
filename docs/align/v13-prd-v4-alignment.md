# v13 对齐（开工前）：PRD-V4 vs v12 现状（聚焦 tests-checklist）

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 测试清单：`docs/prd/archive/tests-checklist.md`（优先对齐其中 ★ 项）
- 现状对齐（v12 结束）：`docs/align/v12-prd-v4-alignment.md`
- 日期：2026-02-16

## v12 结束后的总体判断

- P0（PRD-V4 核心闭环）：`snapshot → refs → action/query/page → re-snapshot` 已具备，并有真机可感知证据链（`docs/evidence/` + `adb_dumps/e2e/latest/`）。
- 下一阶段主要风险：tests-checklist 中仍有少量 ★ 项在“WebView 注入脚本”侧可实现但尚未落地/未提供证据，容易让测试口径继续漂移。

## v13 开工前差异清单（摘差）

### 1) action：`type`（tests-checklist ★）

- 现状：已有 `fill`（一次性 set value + input/change），但缺少更接近“逐字符输入”的 `type(text)` 语义与对应证据。
- 期望：对 ref 指向的 input/textarea/contenteditable 支持 `type`，并能在离线页面里观测到：
  - value 逐步变化（或至少追加）
  - key 相关事件（keydown/keyup/keypress）有可见痕迹

### 2) page：基础导航 + 鼠标事件（tests-checklist ★ 的“可迁移部分”）

> 说明：tests-checklist 原始来源是 Node/Playwright 的 protocol.test；其中 “daemon/protocol JSON 命令解析（id/unknown/invalid JSON）”不适用于本仓库的注入式 API。

- 现状：E2E 里已有“点击链接触发导航 + 旧 ref 失效”的覆盖，但缺少“显式导航/前进/后退/刷新”的 page API（以及证据）。
- 期望（在 WebView 环境可实现的部分）：
  - `page.navigate(url)`：拒绝 `javascript:` 等高风险 scheme；允许 `file:///android_asset/...` 用于离线 E2E
  - `page.back/forward/reload`
  - `page.mouseMove/mouseDown/mouseUp/wheel`：通过 `elementFromPoint(x,y)` 分发事件，离线 fixture 可观测 UI 变化

### 3) Kotlin 解析鲁棒性（补齐“invalid JSON”类风险）

- 现状：`parseSnapshot/parseAction/parseQuery/parsePage` 遇到非法 JSON 会抛异常（易导致上层 tool 崩溃）。
- 期望：新增 `parse*Safe`（或等价）API，遇到非法 JSON 返回结构化 `ok=false` + `error.code=invalid_json`，并补齐 JVM 单测。

## v13 证据口径（预设）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取证据：`pwsh -File .\scripts\pull-e2e-video.ps1`

---

# v13 对齐（完成回填）

## v13 完成情况（结果回填）

- ✅ `action('type')`：已实现（逐字符输入 + key 事件派发 + input/change），并有真机 E2E 证据（复用 `keyboard_state.html`）。
- ✅ `page.navigate/back/forward/reload`：已实现（navigate 具备 scheme 拦截：拒绝 `javascript:`/`data:`/`vbscript:`），并有真机 E2E 证据（`nav1.html`/`nav2.html`）。
- ✅ `page.mouseMove/mouseDown/mouseUp/wheel`：已实现（`elementFromPoint(x,y)` 命中元素并派发 Mouse/WheelEvent），并有真机 E2E 证据（`mouse_wait.html`）。
- ✅ `page.wait`：已实现（selector/text + timeoutMs，忙等实现，仅用于测试/离线验证场景），并有真机 E2E 证据（等待 `#delayed` 出现）。
- ✅ Kotlin `parse*Safe`：已实现（invalid JSON → 结构化 `ok=false` + `error.code=invalid_json`），并补齐 JVM 单测。

## v13 证据指针

- JVM：`docs/evidence/2026-02-16-v13-agent-browser-kotlin-test.txt`
- 真机：`docs/evidence/2026-02-16-v13-connectedAndroidTest.txt`
- 拉取/合成：`docs/evidence/2026-02-16-v13-pull-e2e-artifacts.txt`
- 真机可回看材料（本地）：
  - `adb_dumps/e2e/latest/e2e-latest.mp4`
  - `adb_dumps/e2e/latest/snapshots/`

## v13 仍未完全对齐（剩余差异）

- tests-checklist 中与 Node/Playwright daemon 的 **protocol-json(id)/CDP 注入** 等能力仍不适用于本仓库（注入式 WebView API），继续按“不在本库范围”的口径处理。
