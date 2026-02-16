# v1-index：agent-browser-kotlin

- **PRD**：`docs/prd/PRD-0002-agent-browser-kotlin.md`
- **版本**：v1
- **日期**：2026-02-15

## 愿景

对齐 PRD-0002 Vision：在 Android WebView 环境完成 snapshot+ref+action 的最小闭环，并保证输出预算可控、可回归验证。

## 里程碑

| Milestone | 范围 | DoD（可二元判定） | 验证方式 | 状态 |
|---|---|---|---|---|
| M1 | 项目结构 + 文档追溯 | `docs/plan/` 与追溯矩阵存在；引用 PRD Req IDs；无“软 DoD” | 打开文档人工审阅 | done |
| M2 | Kotlin 渲染（预算/compact） | JVM 单测覆盖预算截断/ref_not_found；`.\gradlew :agent-browser-kotlin:test` exit=0 | `.\gradlew :agent-browser-kotlin:test` | done（见 `docs/evidence/2026-02-15-agent-browser-kotlin-test.txt`） |
| M3 | JS snapshot + Kotlin 解析 | `snapshotJs()` 生成可执行表达式；解析 snapshot JSON 成功；单测覆盖最小 schema + unknown keys | `.\gradlew :agent-browser-kotlin:test` | done（见 `docs/evidence/2026-02-15-agent-browser-kotlin-test.txt`） |
| M4 | action(click/fill) 最小闭环 | `actionJs()` + `parseAction()` 覆盖 ok/ref_not_found；JVM 单测全绿 | `.\gradlew :agent-browser-kotlin:test` | done（见 `docs/evidence/2026-02-15-agent-browser-kotlin-test.txt`） |
| M5 | Android E2E（WebView） | 在测试页完成 click/fill 导致 DOM 变化并被 re-snapshot 捕获（instrumentation test） | `.\gradlew :app:connectedAndroidTest` | done（见 `docs/evidence/2026-02-15-connectedAndroidTest.txt`；视频见 `docs/evidence/2026-02-16-e2e-video.txt`） |

## 计划索引

- `docs/plan/v1-agent-browser-core.md`
- `docs/plan/v1-android-e2e.md`

## 追溯矩阵（Req ID → v1 plan → tests/commands → 证据）

| Req ID | v1 Plan | tests/commands | 证据 | 状态 |
|---|---|---|---|---|
| REQ-0002-001 | v1-android-e2e §E2E-1 | `.\gradlew :app:connectedAndroidTest` | `docs/evidence/2026-02-15-connectedAndroidTest.txt` | done |
| REQ-0002-003 | v1-agent-browser-core §TDD-Render | `.\gradlew :agent-browser-kotlin:test` | `docs/evidence/2026-02-15-agent-browser-kotlin-test.txt` | done |
| REQ-0002-004 | v1-android-e2e §E2E-1 | `.\gradlew :app:connectedAndroidTest` | `docs/evidence/2026-02-15-connectedAndroidTest.txt` | done |
| REQ-0002-005 | v1-android-e2e §E2E-1 | `.\gradlew :app:connectedAndroidTest` | `docs/evidence/2026-02-15-connectedAndroidTest.txt` | done |
| REQ-0002-007 | v1-android-e2e §E2E-1 | `.\gradlew :app:connectedAndroidTest` | `docs/evidence/2026-02-15-connectedAndroidTest.txt` | done |
| REQ-0002-008 | v1-agent-browser-core §TDD-SnapshotSchema | `.\gradlew :agent-browser-kotlin:test` | — | todo |
| REQ-0002-009 | v1-agent-browser-core §TDD-Render | `.\gradlew :agent-browser-kotlin:test` | — | todo |
| REQ-0002-010 | v1-agent-browser-core §TDD-SnapshotSchema | `.\gradlew :agent-browser-kotlin:test` | — | todo |
| REQ-0002-011 | v1-agent-browser-core §TDD-Action | `.\gradlew :agent-browser-kotlin:test` | `docs/evidence/2026-02-15-agent-browser-kotlin-test.txt` | done |
| REQ-0002-012 | v1-agent-browser-core §TDD-Render | `.\gradlew :agent-browser-kotlin:test` | `docs/evidence/2026-02-15-agent-browser-kotlin-test.txt` | done |

> 注：REQ-0002-002（性能/JSON<100KB）与 REQ-0002-006（select）在 v1 只做“接口与差异留痕”，不作为本轮 DoD。

## ECN 索引

（v1 暂无）

## 差异列表（愿景 vs. 现实）

- REQ-0002-002：性能阈值与 JSON 大小尚未建立自动化基准与证据。
- REQ-0002-006：select 动作未纳入 v1 范围。
- Query/page 的 Kotlin API 尚未纳入 v1 范围（JS 侧已有 `query/page` 骨架）。
- README 未产出（放入 v2 或 v1 后半段里程碑）。
