# v18 对齐（开工前）：PRD-V4 / PRD-0003 / tests-checklist ★ vs v17 现状（更强人类可审阅证据）

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 扩展 PRD（Agent 接入）：`docs/prd/PRD-0003-openagentic-agent-e2e.md`
- 测试对齐清单：`docs/prd/archive/tests-checklist.md`（优先 ★）
- 现状对齐（v17 结束）：`docs/align/v17-prd-v4-alignment.md`
- 日期：2026-02-16

## v17 结束后的总体判断

- ✅ PRD-V4 的核心闭环（snapshot → refs → action/query/page → re-snapshot）在真机上稳定可复现，并已具备“人类可感知证据链”（frames + mp4/report + snapshots）。
- ✅ PRD-0003 的核心接入（真实 Agent tool-loop + Responses/SSE provider + sessions/events 拉取）已落地。
- ✅ 对外交付物已成套：
  - OpenAI tools contract：`docs/tools/web-tools.openai.json`
  - system prompt 模板：`docs/prompt/webview-webtools-system-prompt.md`
  - schema 漂移门禁：`WebToolsOpenAiSchemaContractTest`
- ⚠️ 但 v17 仍存在一个“信心缺口”：**报告里可审阅的 agent 行为证据仍偏弱**（只能看 events 预览，缺少 tool_calls 摘要与关键断言的可视化标记）。

## v18 开工前差异清单（摘差）

### 1) REQ-0003 路线 v18：更强证据（核心差异）

- 现状（v17）：
  - `scripts/pull-e2e-video.ps1` 生成的 `report.html`：
    - 有 step-by-step 截图 + snapshot 文本
    - 有 events.jsonl 链接 + 200 行预览
  - 但缺少：
    - tool_calls/工具使用的结构化摘要（人类快速扫一眼就知道 agent 用了哪些工具、顺序如何）
    - 关键断言的可视化标记（例如“agree: true”、ref_not_found、truncated=true 等）
- v18 期望：
  - `report.html` 在保留现有内容基础上，补齐：
    - Agent 运行摘要（final_text / steps / usage / tool_uses 按 name 统计）
    - tool_calls 时间线（tool.use/tool.result 的简要对照，预算截断展示）
    - 快速高亮关键断言（最少覆盖本仓库 E2E 里的关键文本断言）

### 2) tests-checklist ★：agent-driven 覆盖仍可补齐（次要差异）

- 现状（v17）：
  - Agent-run E2E 已覆盖：`web_wait`、`web_type`、`web_query(kind=ischecked)`、`web_scroll_into_view` 等（★ 相关）。
- v18 建议：
  - 新增 1 条 agent-run E2E 覆盖导航 tools：`web_open/web_back/web_forward/web_reload`（对齐 agent-browser Core 的直觉与可用性），并留人类可感知证据。

## v18 证据口径（预设）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取/生成报告：`pwsh -File .\scripts\pull-e2e-video.ps1`

---

# v18 对齐（完成回填）

## v18 完成情况（结果回填）

- ✅ report 证据增强（对齐 PRD-0003 v18）：
  - `report.html` 增加 Agent Summary（final_text/steps/usage/tool_uses by name）。
  - 增加 tool_calls 时间线（tool.use/tool.result 摘要）。
  - snapshot 文本增加关键字高亮（`agree: true` / `ref_not_found` / `truncated=true` 等）。
- ✅ 新增 agent-driven 导航用例：
  - 在真机 agent-run E2E 中显式覆盖 `web_open/web_back/web_forward/web_reload`（并在 report 的 tool_uses by name 可见）。
- ✅ 证据链不回退：frames + mp4/report + snapshots + sessions/events 持续可拉取、可离线审阅。

## v18 证据指针

- JVM：`docs/evidence/2026-02-16-v18-agent-browser-kotlin-test.txt`
- 真机：`docs/evidence/2026-02-16-v18-connectedAndroidTest.txt`
- 拉取/生成报告：`docs/evidence/2026-02-16-v18-pull-e2e-artifacts.txt`
- 真机可感知证据（本地）：
  - `adb_dumps/e2e/latest/index.html`
  - agent-nav 报告（示例）：`adb_dumps/e2e/latest/runs/run-1771239830750/report.html`
  - agent 报告（含 agree 高亮示例）：`adb_dumps/e2e/latest/runs/run-1771239922643/report.html`

## v18 仍未完全对齐（剩余差异）

- PRD-0003 v16+ 里提到的“失败自愈策略”（overlay/超时重试/ref 失效 → 自动 re-snapshot 的更强编排）仍可继续用 v19+ 推进（本轮聚焦证据呈现与导航工具覆盖）。
