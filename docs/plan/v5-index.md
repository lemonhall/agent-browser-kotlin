# v5-index：agent-browser-kotlin

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **版本**：v5
- **日期**：2026-02-16
- **对齐起点**：`docs/align/v5-prd-v4-alignment.md`

## v5 目标

把 v4 之后剩下的 Snapshot 细节差异收敛到 PRD-V4 口径，重点是：

- 角色分类（INTERACTIVE/CONTENT/STRUCTURAL）与 Ref 分配规则（5.2.3）
- cursor-interactive gate（5.2.5）
- name/attrs 细节（aria-labelledby/title + attrs whitelist）（5.2.1）
- stats 字段命名兼容（visitedNodes/emittedNodes）（5.2.8）

## 里程碑（v5）

| Milestone | 范围 | DoD（可二元判定） | 验证方式 | 状态 |
|---|---|---|---|---|
| M18 | Snapshot roles/refs | 按 PRD 三集合分类；CONTENT 在 `interactiveOnly=true` 且有 name 时也分配 ref；结构节点不分 ref 但保留有 ref 子孙 | JVM + E2E 新断言 | todo |
| M19 | Cursor-interactive gate | `cursorInteractive=false` 时不纳入 onclick/tabindex/cursor:pointer；`cursorInteractive=true` 时按 PRD 规则纳入，并跳过交互 roles + 原生交互标签 | E2E 新断言 | todo |
| M20 | name/attrs | name 支持 `aria-labelledby/title`；snapshot attrs whitelist 对齐 PRD | JVM + E2E | todo |
| M21 | stats 命名兼容 | 输出同时包含 `nodesVisited/nodesEmitted` 与 `visitedNodes/emittedNodes` | JVM + E2E | todo |
| M22 | 真机证据 | v5 完整回归并落盘 evidence | `.\gradlew :app:connectedAndroidTest` | todo |

## 计划索引

- `docs/plan/v5-snapshot-role-model.md`
- `docs/plan/v5-cursor-interactive.md`

