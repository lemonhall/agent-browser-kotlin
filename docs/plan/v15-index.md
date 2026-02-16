# v15 计划：OpenAgentic 真 Agent 接入（tool-loop + sessions 证据）

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 扩展 PRD：`docs/prd/PRD-0003-openagentic-agent-e2e.md`
- 对齐清单：`docs/prd/archive/tests-checklist.md`（优先 ★）
- 对齐摘差：`docs/align/v15-prd-v4-alignment.md`
- 日期：2026-02-16

## 目标（本轮要解决什么）

1) **把真实 Agent（OpenAgentic SDK）接进真机 WebView**：不是“写死动作序列”，而是由模型通过 `web_*` tools 驱动页面完成任务。
2) **补齐可追溯证据链**：同一 runPrefix 下，能拉到 `sessions/events.jsonl` 并在 `report.html` 可审阅。
3) **保持默认稳定**：没有 key 的环境不失败（agent-run E2E 自动 skip）。

## 里程碑（完成判定）

- M1：仓库以可复现方式接入 `openagentic-sdk-kotlin`（推荐 submodule + composite build）。
- M2：新增 1 条 agent-run instrumentation E2E（离线 assets 页面），并断言最终状态正确。
- M3：`pull-e2e-video.ps1` 在检测到 session_id 时自动拉取 `events.jsonl/meta.json`，并在 report 中提供入口。
- M4：本轮验证日志写入 `docs/evidence/`。

## 验证命令（必须留日志）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取/生成报告：`pwsh -File .\scripts\pull-e2e-video.ps1`

