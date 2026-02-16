# v11 对齐（开工前）：PRD-V4 vs v10 现状

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **现状对齐（v10 结束）**：`docs/align/v10-prd-v4-alignment.md`
- **测试清单来源**：`docs/prd/archive/tests-checklist.md`（重点关注其中标注 ★ 的项）

## v10 结束后的总体判断

功能层面（snapshot/refs/action/query/page + 真机证据链）已经基本与 PRD-V4 的核心闭环对齐；下一阶段主要风险来自“**测试口径对齐**”：PRD 对齐虽高，但与 `agent-browser` 的 ★ 核心测试点还缺少明确的可复现覆盖。

## v11 开工前差异清单（主要是 ★ 测试项缺口）

### 1) click 被遮挡（overlay / cookie banner）时的 AI 友好错误

- 现状：`agent-browser-js` 的 `action('click')` 直接派发事件，不判断是否被遮挡，失败时难以给出“可行动”的错误信息。
- 期望（tests-checklist ★）：当点击点被其他元素拦截时返回明确错误消息：
  - 包含 **"blocked by another element"** + **"modal or overlay"**
  - 若疑似 cookie 同意/弹窗遮挡，应提示 **"cookie banners"**

### 2) `scroll_into_view(ref)` 的端到端可验证覆盖

- 现状：JS action 支持 `scroll_into_view`，但真机 E2E 没有专门的断言与证据。
- 期望（tests-checklist ★）：snapshot 拿到 ref → scroll_into_view(ref) → 页面滚动/目标可点击可验证。

### 3) 重复 DOM 结构下 refs 的稳定性与“点对元素”

- 现状：真机 E2E 没有“重复结构 + 多个同名交互元素”的验证材料与断言。
- 期望（tests-checklist ★）：在重复结构中 refs 必须一一对应，点击某个 ref 只能影响目标元素（不是同结构的另一个）。

### 4) ref 输入格式（@e1 / ref=e1 / e1）兼容

- 现状：Kotlin API 直接把入参当 ref 使用，未做容错。
- 期望（tests-checklist ★）：支持 `@e1`、`ref=e1`、`e1` 三种写法。

### 5) RenderOptions（maxDepth / compact）的 JVM 单测口径补齐

- 现状：connectedAndroidTest 里使用了 `maxDepth/compact`，但缺少 JVM 单测对其行为的二元断言（容易回归漂移）。
- 期望：补齐最小单测，保证 maxDepth/compact 的行为在 CI 上可复现。

## v11 本轮摘差（计划解决）

- 为 `complex.html` 增加可控 cookie banner overlay 场景 + 真机 E2E 断言（AI 友好错误）。
- 增加长页面 fixture + `scroll_into_view` 真机 E2E。
- 增加“重复结构”fixture + refs 唯一性/点击正确性真机 E2E。
- Kotlin 层增加 ref 输入归一化（@e1/ref=e1）并补齐 JVM 单测。
- JVM 单测补齐 render 的 maxDepth/compact 行为断言。

## 证据口径（v11 预设）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- Evidence（落盘到 `docs/evidence/`）：
  - `docs/evidence/2026-02-16-v11-agent-browser-kotlin-test.txt`
  - `docs/evidence/2026-02-16-v11-connectedAndroidTest.txt`

---

# v11 对齐（完成回填）

## v11 完成情况（结果回填）

- ✅ click 被遮挡（overlay/cookie）时返回 AI 友好错误
  - JS `action('click')` 增加 elementFromPoint 命中检查；被遮挡时返回 `ok=false`，`error.code=element_blocked`
  - message 口径包含：
    - `blocked by another element`
    - `modal or overlay`
    - 若 blocker 疑似 cookie banner：`cookie banners`
- ✅ `scroll_into_view(ref)` 端到端覆盖
  - 新增长页面 fixture，并在真机测试中断言 `scrollY` 变化 + 目标 click 生效
- ✅ 重复结构 refs 唯一 + 点击命中正确元素
  - 新增重复 card fixture；真机测试断言同名按钮 refs 全部唯一且点击只影响目标 card
- ✅ ref 输入容错（@e1 / ref=e1 / [ref=e1]）
  - Kotlin 层在 `actionJs/queryJs` 入口归一化 ref
- ✅ JVM 单测补齐 render 的 maxDepth/compact 口径

证据：
- JVM：`docs/evidence/2026-02-16-v11-agent-browser-kotlin-test.txt`
- 真机：`docs/evidence/2026-02-16-v11-connectedAndroidTest.txt`
- 拉取证据包：`docs/evidence/2026-02-16-v11-pull-e2e-artifacts.txt`

## v11 仍未完全对齐（剩余差异）

- tests-checklist ★ 项：本轮已覆盖到位。
- PRD-V4 §13 “后续迭代”（iframe / 文件上传 / waitFor* / 截图 base64）仍属于后续阶段，本轮未纳入。
