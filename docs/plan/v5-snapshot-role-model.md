# v5：Snapshot 角色模型与 Ref 规则（对齐 PRD-V4 5.2.3）

## 背景

PRD-V4 要求 Snapshot 以 `INTERACTIVE/CONTENT/STRUCTURAL` 三集合角色分类，并以此决定：

- 哪些节点需要 ref（可操作/可引用）
- 哪些节点需要作为结构保留（为 ref 子孙提供路径）

## 目标（v5）

- 引入 PRD-V4 的三集合角色集合（JS 侧常量）。
- Ref 分配规则对齐：
  - INTERACTIVE：始终 ref
  - CONTENT：`interactiveOnly=false` 时 ref；`interactiveOnly=true` 时 **有 name 的也 ref**
  - STRUCTURAL：自身不 ref，但如果有 ref 子孙，保留其结构节点

## DoD

- `interactiveOnly=true` 时，页面上的 `h1`（role=heading）若 name/text 可用，应当拥有 ref（E2E 断言）。
- `interactiveOnly=true` 且 `h1` 无任何 name/text 时，不强行分配 ref（避免噪声）。

