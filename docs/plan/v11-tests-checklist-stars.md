# v11：对齐 tests-checklist（★ 项）

摘差来源：`docs/prd/archive/tests-checklist.md`

## 已覆盖（v10 已有测试/证据）

- Snapshot/refs 基础闭环（refs object、内容节点 ref、aria-labelledby 的 name）
  - 真机：`app/src/androidTest/java/com/lsl/agent_browser_kotlin/WebViewE2eTest.kt`
- cursorInteractive gate（false 不抓，true 抓并可 click）
  - 真机：`app/src/androidTest/java/com/lsl/agent_browser_kotlin/WebViewE2eTest.kt`
- fill/clear/query(value)/select/check/uncheck/page(scroll/info)/导航后旧 ref 失效
  - 真机：`app/src/androidTest/java/com/lsl/agent_browser_kotlin/WebViewE2eTest.kt`

## v11 需要补齐（★ 缺口 → 对应实现/测试）

### 1) click 被遮挡（overlay / cookie banner）

- 缺口：当前 click 不判断遮挡；无 “blocked by another element / modal or overlay / cookie banners” 口径。
- v11 落地：
  - JS：`agent-browser-js/agent-browser.js` click 前做 elementFromPoint 命中检查，遮挡时返回结构化错误。
  - Fixture：`app/src/main/assets/e2e/complex.html` 增加可 dismiss 的 cookie banner overlay。
  - 真机：新增 E2E case，先断言被遮挡错误，再 dismiss 后 click 成功。

### 2) `scroll_into_view(ref)` 端到端验证

- 缺口：无专门 E2E 覆盖。
- v11 落地：
  - Fixture：新增长页面（底部目标按钮）。
  - 真机：执行 `ActionKind.SCROLL_INTO_VIEW`，断言 `PageKind.INFO.scrollY` 变化 + 目标 click 生效。

### 3) 重复结构 refs 唯一 + 点击命中正确元素

- 缺口：无重复结构材料与断言。
- v11 落地：
  - Fixture：新增重复 card 列表（多个同名按钮/同结构）。
  - 真机：收集同名元素 refs，断言全部唯一；点击特定 ref 后只对应 card 出现变化文本。

### 4) ref 输入容错（@e1 / ref=e1 / e1）

- 缺口：Kotlin 侧不做归一化，传入 `@e1` 会导致 ref_not_found。
- v11 落地：
  - Kotlin：在 `AgentBrowser.actionJs/queryJs` 等入口归一化 ref。
  - JVM：新增单测覆盖三种写法等价。

### 5) RenderOptions（maxDepth / compact）JVM 单测

- 缺口：目前只有运行时使用，缺少二元断言防回归。
- v11 落地：
  - JVM：补齐 `maxDepth` 截断与 `compact` 剪枝的最小测试用例。

