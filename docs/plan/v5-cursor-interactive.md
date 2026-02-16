# v5：Cursor-Interactive（-C）对齐（PRD-V4 5.2.5）

## 目标（v5）

实现 PRD-V4 的 gate 行为：

- `cursorInteractive=false`：不把 `cursor:pointer/onclick/tabindex` 作为交互候选（避免过多噪声节点）
- `cursorInteractive=true`：纳入“非标准交互元素”，但必须跳过：
  - 已有 ARIA 交互 role 的元素（INTERACTIVE_ROLES）
  - 原生交互标签（a/button/input/select/textarea/details/summary）

## DoD

- E2E：页面中一个 `<div tabindex="0" style="cursor:pointer" onclick="...">`：
  - `cursorInteractive=false`：不应获得 ref
  - `cursorInteractive=true`：应获得 ref，并可 click 触发页面状态变化（可 snapshot/query 验证）

