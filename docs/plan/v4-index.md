# v4-index：agent-browser-kotlin

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **版本**：v4
- **日期**：2026-02-16
- **对齐起点**：`docs/align/v3-prd-v4-alignment.md`

## v4 目标

补齐 PRD-V4 里目前最大的“结构性缺口”：

- Page（5.5）：实现最小可用的页面级操作与查询（info/scroll）
- Snapshot（5.2）：对齐 CONTENT ref 分配规则（interactiveOnly=false 时为内容节点分配 ref）
- Query（5.4）：`attrs` 对齐为“全量 attributes”口径
- Stats：补充 `skippedHidden/domNodes` 等可用于压缩率解释的字段（最小集）

## 里程碑（v4）

| Milestone | 范围 | DoD（可二元判定） | 验证方式 | 状态 |
|---|---|---|---|---|
| M14 | Page API | JS 实现 `page('info')/page('scroll')`；Kotlin 增加 `pageJs/parsePage`；单测覆盖 | `.\gradlew :agent-browser-kotlin:test` | done |
| M15 | CONTENT refs | interactiveOnly=false 时为 heading/listitem/img 等内容节点分配 ref；单测覆盖 | `.\gradlew :agent-browser-kotlin:test` + E2E | done |
| M16 | Query attrs 全量 | `query(kind='attrs')` 输出全量 attributes（JSON） | `.\gradlew :agent-browser-kotlin:test` + E2E | done |
| M17 | 真机证据 | E2E 覆盖 page/info+scroll 与内容节点 ref | `.\gradlew :app:connectedAndroidTest` | done |

## 计划索引

- `docs/plan/v4-page.md`
- `docs/plan/v4-snapshot-ref-rules.md`

## Evidence quick links

- 人类可回看 mp4 产物：`docs/evidence/2026-02-16-e2e-video.txt`
- v4 验收摘要：`docs/evidence/2026-02-16-v4-connectedAndroidTest.txt`
