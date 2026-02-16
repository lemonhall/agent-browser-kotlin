# v18 计划：更强人类可审阅证据（report 摘要 + 关键断言高亮）+ 补齐导航 tools 覆盖

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 扩展 PRD：`docs/prd/PRD-0003-openagentic-agent-e2e.md`
- 对齐清单：`docs/prd/archive/tests-checklist.md`（优先 ★）
- 对齐摘差：`docs/align/v18-prd-v4-alignment.md`
- 日期：2026-02-16

## 目标（本轮要解决什么）

1) **增强 report.html 的可读性**：把 agent 的 tool_calls / tool_results 做成“人类一眼能看懂”的摘要（不止是 events 预览）。
2) **关键断言可视化**：在 report 中对关键断言文本做高亮（例如 `agree: true` / `ref_not_found` / `truncated=true`）。
3) **补齐 agent-driven 导航工具覆盖**：新增 1 条 agent-run 真机 E2E，确保 `web_open/web_back/web_forward/web_reload` 在真实 tool-loop 下被使用并有证据。

## 里程碑（完成判定）

- M1：`scripts/pull-e2e-video.ps1` 生成的 `report.html` 增加：
  - Agent summary（final_text/steps/usage/tool uses count）
  - tool_calls timeline（tool.use/tool.result 摘要，截断展示）
  - snapshot 文本关键字高亮
- M2：新增 1 条 agent-run instrumentation E2E：
  - 离线 assets 页面（`nav1.html/nav2.html`）
  - prompt 显式要求使用 `web_open/web_back/web_forward/web_reload`
  - 保留人类可感知证据链（frames + mp4/report + events）
- M3：本轮验证日志写入 `docs/evidence/`，并更新 `docs/align/v18-prd-v4-alignment.md` 的“完成回填”。

## 验证命令（必须留日志）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取/生成报告：`pwsh -File .\scripts\pull-e2e-video.ps1`

