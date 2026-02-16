# v2 对齐：PRD-V4（archive） vs 当前实现

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **当前执行口径**：`docs/prd/PRD-0002-agent-browser-kotlin.md`
- **本轮范围（v2）**：`docs/plan/v2-index.md`
- **证据总览**：`docs/evidence/2026-02-16-v2-connectedAndroidTest.txt`

## v2 已收敛的差异（相对 PRD-V4）

- Snapshot
  - ✅ 每次 snapshot 先 `clearOldRefs()`，避免旧 ref 残留
  - ✅ 可见性收紧：`hidden`/`aria-hidden=true`/`display:none`/`visibility:hidden`/`opacity=0` + `offsetParent`（非 fixed）过滤
  - ✅ 统计字段补齐 `jsTimeMs`（用于 perf 证据口径）
- Action
  - ✅ `select`（`ActionKind.SELECT` + JS `select` 分支）端到端打通并有真机 E2E
- Render / 输出可读性
  - ✅ Kotlin renderer 输出包含关键 attrs（`href/type/placeholder/value/...`）
- E2E & 人类可感知证据
  - ✅ 真机 instrumentation：每步停顿 + 截图帧写入 Downloads（MediaStore）
  - ✅ PC 侧一键拉取帧并合成 mp4（`scripts/pull-e2e-video.ps1`）
- Perf/Size 基线（离线 fixture）
  - ✅ `stress.html`：`snapshot(interactiveOnly=true)` 断言 `jsonBytes < 100KB`，并记录 `jsTimeMs`

## 仍未对齐（驱动 v3 的差异清单）

### Action（PRD-V4 规划但未实现）

- ⛔ `check` / `uncheck`
- ⛔ `focus` / `hover`
- ⛔ `scroll_into_view`
- ⛔ `clear`（清空输入）
- ⛔ 更“真实”的点击序列（pointerdown/mousedown/...）+ `scrollIntoView({block:'center'})`（当前 click 以 `el.click()` 为主）

### Query（PRD-V4 规划但未实现或不完整）

- ⚠️ `attrs`：当前为白名单 attrs（PRD 倾向“全量 attrs”或更明确的查询口径）
- ⛔ `computed_styles`
- ⛔ Kotlin 侧缺少 `queryJs(...) / parseQuery(...)` 的一等 API（目前仅 JS 里有 `query`）

### Page（PRD-V4 规划但未实现）

- ⛔ `page.*`（url/title/scroll/key 等页面级操作）当前返回 `not_implemented`
- ⛔ Kotlin 侧缺少 `pageJs(...) / parsePage(...)`

### Snapshot（PRD-V4 细节尚未对齐）

- ⚠️ CONTENT 节点 ref 分配规则：PRD-V4 期望在 `interactiveOnly=false` 时为 CONTENT 分配 ref；当前仅 INTERACTIVE 分配 ref
- ⚠️ 角色集合（INTERACTIVE/CONTENT/STRUCTURAL）与 compact 剪枝策略仍偏“实现驱动”，未完全按 PRD-V4 的集合与规则收敛
- ⚠️ Stats 字段：PRD-V4 示例包含更多统计维度（domNodes/skippedHidden 等），当前仅保留最小集 + `jsTimeMs`

## 覆盖率粗估（用于“≥90%”收敛跟踪）

以 PRD-V4 的 4 大模块做粗粒度分解（Snapshot / Action / Query / Page）：

- Snapshot：**部分覆盖**
- Action：**部分覆盖**（已：click/fill/select；未：其余动作）
- Query：**部分覆盖**（已：text/attrs/value/html/outerHTML；未：computed_styles + Kotlin API）
- Page：**未覆盖**

结论：v2 已把“闭环 + 证据链”打硬，但离 90% 还有明显差距；v3 优先补齐 Action/Query/Page 的缺口。

