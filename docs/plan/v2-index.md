# v2-index：agent-browser-kotlin

- **PRD**：`docs/prd/archive/PRD-V4.md`
- **版本**：v2
- **日期**：2026-02-16

## v2 目标

在 v1 已跑通最小闭环（snapshot → fill/click → re-snapshot）的基础上，补齐 PRD 中 v2 最有价值、且可稳定验收的缺口：

- 完成 REQ-0002-006（`select`）
- 为 REQ-0002-002 建立可复现的“JSON 大小 + 耗时”证据口径（先用离线 stress fixture 固化，再扩展到真实站点）
- 补齐 REQ-0002-008/009/010 的自动化覆盖（interactiveOnly=false / compact / 不可见过滤）
- 把 JS/Kotlin 的 options/schema 收敛到一致（避免“JS 支持但 Kotlin 配不了”）
- 让 E2E 证据更“人类可感知”（真机截图帧 + mp4）

## 里程碑（v2）

| Milestone | 范围 | DoD（可二元判定） | 验证方式 | 状态 |
|---|---|---|---|---|
| M6 | Action: select | JS 支持 `select`；Kotlin 支持 `ActionKind.SELECT` + payload；E2E 覆盖并截图 | `.\gradlew :app:connectedAndroidTest` | done |
| M7 | Snapshot 可靠性 | snapshot 先清理旧 ref；可见性按 PRD（含 offsetParent）收紧；覆盖不可见过滤（E2E） | `.\gradlew :agent-browser-kotlin:test` + `.\gradlew :app:connectedAndroidTest` | done |
| M8 | Render 覆盖 | `interactiveOnly=false`、`compact` 行为单测覆盖；文本输出包含关键 attrs（placeholder/href/value） | `.\gradlew :agent-browser-kotlin:test` | done |
| M9 | Perf/Size 证据 | 对离线 stress fixture：`jsonBytes < 100KB`（interactiveOnly=true），并记录 jsTimeMs | `.\gradlew :app:connectedAndroidTest` + evidence | done |
| M10 | README | 最小接入指南 + 推荐 tool schema + 禁忌（outerHTML） | 人工审阅 | done |

## 计划索引

- `docs/plan/v2-agent-browser-core.md`
- `docs/plan/v2-android-e2e.md`
- `docs/plan/v2-perf-budget.md`

## 追溯矩阵（Req ID → v2 plan → tests/commands → 证据）

| Req ID | v2 Plan | tests/commands | 证据 | 状态 |
|---|---|---|---|---|
| REQ-0002-002 | v2-perf-budget §E2E-PERF-1 | `.\gradlew :app:connectedAndroidTest` | `docs/evidence/2026-02-16-v2-connectedAndroidTest.txt` | done |
| REQ-0002-006 | v2-agent-browser-core §ACTION-SELECT | `.\gradlew :app:connectedAndroidTest` | `docs/evidence/2026-02-16-v2-connectedAndroidTest.txt` | done |
| REQ-0002-008 | v2-agent-browser-core §SNAPSHOT-INTERACTIVE-ONLY | `.\gradlew :agent-browser-kotlin:test` + E2E | `docs/evidence/2026-02-16-v2-connectedAndroidTest.txt` | done |
| REQ-0002-009 | v2-agent-browser-core §RENDER-COMPACT | `.\gradlew :agent-browser-kotlin:test` | `docs/evidence/2026-02-16-v2-connectedAndroidTest.txt` | done |
| REQ-0002-010 | v2-agent-browser-core §SNAPSHOT-VISIBILITY | `.\gradlew :app:connectedAndroidTest` | `docs/evidence/2026-02-16-v2-connectedAndroidTest.txt` | done |

## Evidence quick links

- 人类可回看 mp4 产物：`docs/evidence/2026-02-16-e2e-video.txt`
- v2 验收摘要（含 perf/size 数字）：`docs/evidence/2026-02-16-v2-connectedAndroidTest.txt`
