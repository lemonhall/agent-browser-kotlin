# v4 对齐（开工前）：PRD-V4 vs v3 现状

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **现状对齐（v3 结束）**：`docs/align/v3-prd-v4-alignment.md`
- **v4 计划**：`docs/plan/v4-index.md`

## v4 本轮摘差（从 PRD-V4 差异列表中选择）

- Page（5.5）：最小可用 `page(info/scroll)` + Kotlin API
- Snapshot（5.2）：CONTENT refs 分配规则（interactiveOnly=false 时给内容节点 ref）
- Query（5.4）：`attrs` 全量 attributes 输出
- Stats：补最小压缩率解释字段（skippedHidden/domNodes）

## v4 完成情况（结果回填）

- ✅ Page（5.5）
  - `page(scroll)`（deltaY/x/y）已实现；并提供 PRD 兼容的 `page.scrollBy/scrollTo`
  - `page(info)` 返回 url/title/scrollY/viewport；并提供 `page.getUrl/getTitle`
  - `page(pressKey)` 已实现；并提供 `page.pressKey`
- ✅ Query（5.4）
  - `attrs` 改为全量 attributes（JSON）输出
- ✅ Snapshot（5.2）
  - `interactiveOnly=false` 时为 CONTENT 节点分配 ref（E2E 断言 h1 拥有 ref）
  - stats 增加 `domNodes/skippedHidden/jsTimeMs`（最小解释口径）
  - 兼容字段：输出补 `version/url/title/timestamp`（不破坏既有 `ok/type/meta` 结构）

证据：
- `docs/evidence/2026-02-16-v4-connectedAndroidTest.txt`

## 仍未完全对齐（剩余差异清单）

- Snapshot 角色集合/分类：尚未完全按 PRD-V4 的 INTERACTIVE/CONTENT/STRUCTURAL 集合与规则收敛（当前为实现驱动的近似）
- Stats 维度：PRD-V4 示例包含更多统计字段（如 visitedNodes/emittedNodes 命名、更多 skip 维度）；当前仅保留最小集
- Kotlin 侧设计细节：PRD-V4 提到的 `OutputFormat(JSON/plain)` 等接口未实现（当前以渲染文本为主）
- Page API 形态：已做兼容糖，但 Kotlin 工具侧仍以 dispatcher（`page(kind,payload)`）为主

## 覆盖率估算（用于“≥90%”收敛判断）

若按 PRD-V4 的 **5.x（JS 侧 Snapshot/Action/Query/Page）功能面** 计数口径：

- Snapshot：核心规则 **大体覆盖**（clear refs/visibility/content refs/统计口径/预算/注入）
- Action：**全覆盖**（PRD 列出的动作均已实现）
- Query：**全覆盖**（PRD 列出的 kind 均已实现，attrs 已对齐全量口径）
- Page：**基本覆盖**（scroll/url/title/pressKey 皆可用，且提供 PRD 兼容方法）

结论：按 5.x 功能面估算已达到 **≈90%+**；剩余工作主要集中在“结构/命名/输出格式”的细节收敛与 Kotlin API 的扩展完善。
