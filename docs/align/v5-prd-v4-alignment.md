# v5 对齐（开工前）：PRD-V4 vs v4 现状

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **现状对齐（v4 结束）**：`docs/align/v4-prd-v4-alignment.md`
- **v5 计划**：`docs/plan/v5-index.md`

## v4 结束后的总体判断

v4 之后，5.x（JS 侧 Snapshot/Action/Query/Page）的“功能面”基本齐备；剩余差异主要集中在 Snapshot 的**角色集合/Ref 分配规则**与 **cursor-interactive（-C）gate** 的细节收敛，以及与 PRD-V4 示例一致的字段命名（stats 等）。

## 仍未完全对齐（v5 开工前差异清单）

### Snapshot（5.2）差异

- 角色分类（5.2.3）
  - 当前实现未按 PRD-V4 的 `INTERACTIVE_ROLES / CONTENT_ROLES / STRUCTURAL_ROLES` 三集合收敛，CONTENT/STRUCTURAL 的判断为近似规则。
  - **缺口**：Ref 分配规则未完全对齐：
    - PRD-V4：CONTENT 节点在 `interactiveOnly=true` 时，**有 name 的也分配 ref**（对齐 agent-browser 行为）。
    - 当前实现：CONTENT ref 仅在 `interactiveOnly=false` 时分配。
- cursor-interactive 检测（5.2.5）
  - PRD-V4：`cursorInteractive=true` 时才把“非标准交互元素（cursor:pointer/onclick/tabindex）”纳入 ref；并明确跳过已有交互 role 与原生交互标签。
  - 当前实现：`onclick/tabindex` 作为交互判断的一部分，**即使 cursorInteractive=false 也会被当作 interactive**（与 PRD gate 不一致）。
- name / attrs（5.2.1）
  - name：缺少 `aria-labelledby` 与 `title` 的优先级计算（目前以 `aria-label/alt/placeholder/innerText` 为主）。
  - attrs：snapshot 的 attrs whitelist 与 PRD-V4（`href,name,type,value,placeholder,src,action,method`）不一致。
- stats（5.2.8）
  - PRD-V4 示例 stats 使用 `visitedNodes/emittedNodes` 命名；当前实现使用 `nodesVisited/nodesEmitted`（示例口径不一致）。

### Kotlin 侧差异（6.x）提示（暂不作为 v5 主摘差）

- PRD-V4 的 Kotlin 侧 `RenderOptions.format(OutputFormat)`、以及“renderSnapshot → 返回 text + refs + stats”的整体 API 形态尚未完全收敛（目前为 parse + renderer 的最小实现）。

## v5 本轮摘差（计划解决）

- Snapshot（5.2.3）：引入三集合角色分类并对齐 Ref 分配规则（包含 `interactiveOnly=true 且 CONTENT 有 name 也 ref`）。
- Snapshot（5.2.5）：实现 PRD-V4 的 cursor-interactive gate（只在 `cursorInteractive=true` 时纳入；并跳过交互 roles + 原生交互标签）。
- Snapshot（5.2.1）：补齐 `aria-labelledby/title` name 计算与 attrs whitelist 收敛。
- Stats（5.2.8）：补 `visitedNodes/emittedNodes` 命名兼容（不破坏现有字段）。

## 证据口径（v5 预设）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- Evidence：`docs/evidence/2026-02-16-v5-connectedAndroidTest.txt`（完成后落盘）

---

# v5 对齐（完成回填）

## v5 完成情况（结果回填）

- ✅ Snapshot roles/refs（5.2.3）
  - 引入 PRD-V4 三集合角色分类（INTERACTIVE/CONTENT/STRUCTURAL）作为判断基础
  - CONTENT 节点在 `interactiveOnly=true` 且存在 name 时也分配 ref（E2E 断言 `h1/h2`）
- ✅ Cursor-interactive gate（5.2.5）
  - `cursorInteractive=false`：不再把 `onclick/tabindex/cursor:pointer` 当作交互候选
  - `cursorInteractive=true`：按 PRD gate 纳入，并合成角色 `focusable/clickable`（E2E 点击后验证 `h1` 文本变化）
- ✅ name/attrs（5.2.1）
  - name：支持 `aria-labelledby` 与 `title`（E2E 使用 `h2#h2Labelled` 验证）
  - snapshot attrs whitelist 对齐 PRD（`href,name,type,value,placeholder,src,action,method`）
- ✅ stats（5.2.8）
  - stats 同时输出 `nodesVisited/nodesEmitted` 与 `visitedNodes/emittedNodes`（E2E 断言字段存在）

证据：
- `docs/evidence/2026-02-16-v5-connectedAndroidTest.txt`

## v5 仍未完全对齐（剩余差异）

- Kotlin 侧 API 形态（6.x）：`OutputFormat` / “renderSnapshot → 返回 text+refs+stats” 的完整 PRD 形态仍未完全收敛（当前仍以 parse + renderer 的最小实现为主）。

