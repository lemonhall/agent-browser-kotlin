# v19 对齐（开工前）：PRD-V4 / PRD-0003 / tests-checklist ★ vs v18 现状（失败自愈与稳定性）

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 扩展 PRD（Agent 接入）：`docs/prd/PRD-0003-openagentic-agent-e2e.md`
- 测试对齐清单：`docs/prd/archive/tests-checklist.md`（优先 ★）
- 现状对齐（v18 结束）：`docs/align/v18-prd-v4-alignment.md`
- 日期：2026-02-16

## v18 结束后的总体判断

- ✅ “闭环能力”与“人类可审阅证据链”已具备：snapshot → refs → action → re-snapshot + frames/mp4/report + sessions/events。
- ✅ 对外交付物成套：tools contract / public API PRD / system prompt 模板 / schema 漂移门禁。
- ⚠️ 仍欠缺一块与 PRD-V4 §7 / PRD-0003 v16+ 强相关的能力：**失败自愈的可验证性**（不仅“口头建议”，要在真实 agent-loop 下留下可审阅证据）。

## v19 开工前差异清单（摘差）

### 1) PRD-V4 §7：ref_not_found 的失效处理（需要“可验证的自愈策略”）

- PRD-V4 口径：
  - snapshot 会清除旧 `data-agent-ref` 并重分配（ref 短生命周期）。
  - action/query 遇到 `ref_not_found` 后，上层应触发重新 snapshot，并让模型按新快照继续。
- v18 现状：
  - `ref_not_found` 能稳定产生且被高亮；但在 **真实 agent tool-loop** 下缺少“刻意触发 + 恢复”的证据用例（证明 prompt/schema 真能驱动模型完成自愈）。
- v19 期望：
  - 新增 1 条 agent-run 真机 E2E：**刻意制造 stale ref → 触发 ref_not_found → re-snapshot → 继续完成任务**，并在 report/events 中可回溯。

### 2) tests-checklist ★：element_blocked（overlay/cookie banner）恢复策略需要 agent-loop 证据

- v18 现状：
  - JS action(click) 已能识别遮挡并返回 AI 友好错误（含 “blocked by another element (modal or overlay)” / “cookie banners”）。
  - 但仍缺少：在真实 agent-loop 下，模型是否会按提示先处理 overlay，再继续主任务的证据。
- v19 期望：
  - agent-run E2E 中必须出现至少一次 `element_blocked`，并随后成功通过点击 “Accept cookies/关闭” 等路径恢复。

### 3) 对外交付物：system prompt 需要显式“失败恢复 playbook + few-shot”

- v18 现状：
  - system prompt 模板提供了基本规则与基础 few-shot，但对“失败后怎么做”的具体策略仍偏短。
- v19 期望：
  - system prompt 增加一段 **Error Recovery Playbook**（ref_not_found / element_blocked / timeout）并提供最小 few-shot（离线 fixture + 移动端公共站点各 1）。

## v19 证据口径（预设）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取/生成报告：`pwsh -File .\scripts\pull-e2e-video.ps1`

---

# v19 对齐（完成回填）

## v19 完成情况（结果回填）

- ✅ agent-loop 失败自愈证据补齐：
  - 新增 agent-run 真机 E2E：必须触发 `element_blocked`（cookie overlay）与 `ref_not_found`（模拟 stale ref），并完成恢复后达成最终断言。
- ✅ 对外交付 prompt 补齐：
  - `docs/prompt/webview-webtools-system-prompt.md` 增加 Error Recovery Playbook + 最小 few-shot（覆盖 element_blocked/ref_not_found/timeout）。
- ✅ 报告可读性增强：
  - `report.html` 关键字高亮补齐：`element_blocked` / `cookie banners` / `web_wait timed out`。

## v19 证据指针

- JVM：`docs/evidence/2026-02-16-v19-agent-browser-kotlin-test.txt`
- 真机：`docs/evidence/2026-02-16-v19-connectedAndroidTest.txt`
- 拉取/生成报告：`docs/evidence/2026-02-16-v19-pull-e2e-artifacts.txt`
- 真机可感知证据（本地）：
  - `adb_dumps/e2e/latest/index.html`
  - agent-recover 报告（示例）：`adb_dumps/e2e/latest/runs/run-1771242491763/report.html`

## v19 仍未完全对齐（剩余差异）

- PRD-0003 v16+ 的“更强重试/超时策略”（比如自动化 backoff、对动态加载的更细粒度等待语义）仍可继续用 v20+ 推进；本轮聚焦的是“失败自愈证据 + prompt 口径”。
