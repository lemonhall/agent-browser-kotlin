# v10-index：agent-browser-kotlin

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **版本**：v10
- **日期**：2026-02-16
- **对齐起点**：`docs/align/v10-prd-v4-alignment.md`

## v10 目标

把“人类可感知证据链”做成真正的一键可回看包，并收敛 tool schema 覆盖面，减少上层工具漂移：

1) `pull-e2e-video.ps1` 同时拉取 `snapshots/` 归档（视频 + dumps 一起回看）。
2) 扩充 `docs/tools/web-tools.openai.json`，覆盖库已支持的常用 action/query。

## 里程碑（v10）

| Milestone | 范围 | DoD（可二元判定） | 验证方式 | 状态 |
|---|---|---|---|---|
| M36 | 拉取 snapshots | `scripts/pull-e2e-video.ps1` 能拉 frames + snapshots，按 runId 归档，并更新 `latest/` 指针 | 运行脚本 | done |
| M37 | Schema 补齐 | `docs/tools/web-tools.openai.json` 增加 `web_clear/web_focus/web_hover/web_scroll_into_view/web_query` | 文档 review | done |
| M38 | v10 证据 | v10 回归与脚本输出落盘 evidence | connectedAndroidTest + 脚本 | done |

## 计划索引

- `docs/plan/v10-e2e-artifacts-and-schema.md`
