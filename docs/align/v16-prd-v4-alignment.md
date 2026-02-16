# v16 对齐（开工前）：PRD-V4 / PRD-0003 vs v15 现状（切回 Responses + SSE）

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 扩展 PRD（Agent 接入）：`docs/prd/PRD-0003-openagentic-agent-e2e.md`
- 对齐清单：`docs/prd/archive/tests-checklist.md`（优先 ★）
- 现状对齐（v15 结束）：`docs/align/v15-prd-v4-alignment.md`
- 日期：2026-02-16

## v15 结束后的总体判断

- 已跑通“真实 Agent 参与”的真机 E2E，并补齐 sessions/events 的人类可审阅证据链（mp4/report + events/meta）。
- 但 v15 为了绕过 upstream 返回 **SSE（text/event-stream）** 的行为，改走了 legacy `chat/completions` 协议；这与 PRD-0003 的目标（Responses 协议）存在执行口径偏离。

## v16 开工前差异清单（摘差）

### 1) REQ-0003-001 / G1：Provider 协议偏离（核心差异）

- 现状（v15）：
  - `WebViewAgentE2eTest` 使用 legacy `chat/completions` provider（tool-loop 可跑）。
  - 原因：upstream 会返回 SSE；在“非 stream 的 complete()”路径下会被当成非 JSON，导致失败。
- PRD 期望：
  - 使用 OpenAI **Responses** 协议完成同样的 tool-loop（允许 SSE/stream）。
- v16 计划：
  - 引入兼容 SSE 的 Responses provider（即使 upstream 只提供 SSE 也能完成 complete）。
  - 默认走 Responses；必要时保留可控开关回退 legacy（只用于排障）。

### 2) tests-checklist（★）中与“真实 Agent 适配”相关项需要继续加固

- 现状：
  - 已有 1 条 agent-run E2E（离线复杂页面 fixtures），并留有人类可感知证据链。
- 仍需（后续 v16+）：
  - 更强的 prompt 模板与失败建议（ref 失效/overlay 等）。
  - 扩展 2–3 条 agent tasks 覆盖 overlay/scroll/query(kind) 等（见 PRD-0003 v16–v18 路线）。

## v16 证据口径（预设）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取/生成报告：`pwsh -File .\scripts\pull-e2e-video.ps1`

---

# v16 对齐（完成回填）

## v16 完成情况（结果回填）

- ✅ 默认把 agent-run E2E 的 Provider 切回 **Responses**：
  - `WebViewAgentE2eTest` 默认 `OPENAI_PROTOCOL=responses`；
  - 保留排障回退：传 `OPENAI_PROTOCOL=legacy` 可走 `chat/completions`（仅用于对比/排障）。
- ✅ 兼容 upstream 固定 SSE 的 `/responses`：
  - 新增 `OpenAIResponsesSseHttpProvider`：把 SSE 当作主通道，`complete()` 通过收敛 `stream()` 产出最终 `ModelOutput`，避免“非 JSON”误判。
- ✅ 真机证据链不回退：
  - 依旧输出 frames/snapshots + mp4/report；
  - sessions/events 拉取与 report 展示保持可用。

## v16 证据指针

- JVM：`docs/evidence/2026-02-16-v16-agent-browser-kotlin-test.txt`
- 真机：`docs/evidence/2026-02-16-v16-connectedAndroidTest.txt`
- 拉取/生成报告：`docs/evidence/2026-02-16-v16-pull-e2e-artifacts.txt`
- 真机可感知证据（本地）：
  - `adb_dumps/e2e/latest/index.html`
  - `adb_dumps/e2e/latest/e2e-latest.mp4`
  - `adb_dumps/e2e/latest/runs/`

## v16 仍未完全对齐（剩余差异）

- PRD-0003 路线中的 v16+（overlay/ref 失效/更多 agent tasks、schema 漂移门禁）仍待后续版本继续推进。
