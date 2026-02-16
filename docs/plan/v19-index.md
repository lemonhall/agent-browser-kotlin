# v19 计划：失败自愈与稳定性（agent-loop 证据 + prompt playbook）

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 扩展 PRD：`docs/prd/PRD-0003-openagentic-agent-e2e.md`
- 对齐清单：`docs/prd/archive/tests-checklist.md`（优先 ★）
- 对齐摘差：`docs/align/v19-prd-v4-alignment.md`
- 日期：2026-02-16

## 目标（本轮要解决什么）

1) **把“失败自愈”做成可审阅证据**：在真实 agent tool-loop 下，刻意触发 `element_blocked` / `ref_not_found`，并能按 prompt 指引恢复完成任务。
2) **补齐对外交付 prompt**：system prompt 增加 Error Recovery Playbook（ref_not_found / element_blocked / timeout）与最小 few-shot。
3) **报告可读性再加一档**：让人类在 `report.html` 一眼看到关键失败码（element_blocked / timeout / ref_not_found）与恢复轨迹（timeline/高亮）。

## 里程碑（完成判定）

- M1：`docs/prompt/webview-webtools-system-prompt.md` 增加：
  - Error Recovery Playbook（ref_not_found / element_blocked / timeout）
  - 至少 2 个 few-shot（移动端公共站点 + 离线 fixture）
- M2：新增 1 条 agent-run instrumentation E2E：
  - 触发并在 events 中可见：`element_blocked`（cookie overlay）
  - 触发并在 events 中可见：`ref_not_found`（stale ref）
  - 最终断言：页面状态与任务目标一致（可用 snapshot/query 确认）
- M3：`scripts/pull-e2e-video.ps1` 的 report 高亮覆盖：
  - `element_blocked` / `cookie banners` / `timeout`

## 验证命令（必须留日志）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取/生成报告：`pwsh -File .\scripts\pull-e2e-video.ps1`

