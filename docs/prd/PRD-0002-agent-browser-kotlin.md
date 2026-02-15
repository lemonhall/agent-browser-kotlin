# PRD-0002：agent-browser-kotlin（WebView JS 注入实现 Snapshot + Ref 交互）

- **版本**：v4（基于 PRD-V4.md 清洗）
- **日期**：2026-02-15

## Vision

在 Android WebView 环境中，用**可控预算**的页面语义快照（snapshot）替代“整页 outerHTML 回传”，并通过 `ref` 实现 **snapshot → 模型决策 → action(ref) → re-snapshot** 的闭环网页自动化能力。

## 1. 背景与问题

我们在做一个 Android 聊天型 Agent App（供应商为 OpenAI Responses API 风格，流式调用）。Agent 具备 Web 能力（WebView / WebFetch / WebSearch 等），会把网页内容作为工具结果返回给模型。

当前严重问题：工具把 `document.body.outerHTML`（或整页 DOM/HTML）直接返回给模型，导致一次 `tool.result` 就可能几十万字符，下一轮模型请求把该内容带上后触发 `context_length_exceeded`，即使对话轮次不多也会爆。

核心思路：借鉴 `vercel-labs/agent-browser` 的 snapshot + refs 理念，但适配 Android WebView 环境：

- 我们在 WebView 里没有 Playwright 的 `ariaSnapshot()` API，所以必须自己写 JS 遍历 DOM 来生成等价输出。
- 我们没有 Playwright locator 引擎，所以 ref 定位改为 `data-agent-ref` 属性 + `querySelector`。
- Kotlin 侧接收 JSON，负责预算控制、格式化为模型可读的 snapshot 文本、管理 ref 映射与失效。
- 同时实现 action 能力（click/fill/select 等），补全 `snapshot → 模型决策 → action → re-snapshot` 的完整闭环。

## 2. 目标（Goals）

1. JS（WebView 注入）：遍历真实 DOM，输出结构化 JSON（语义角色/可见性/文本/属性/ref）；支持通过 ref 执行操作与查询。
2. Kotlin（纯 JVM 库）：解析 JS 回传 JSON，做预算控制与输出格式化；生成各类 `evaluateJavascript(...)` 的 JS 表达式。
3. 端到端：对真实页面完成闭环（snapshot → action → re-snapshot），且任何输出严格受预算约束，不会 OOM。

## 3. 非目标（Non-goals）

- 不做 Playwright 级别的跨浏览器自动化能力；运行时限定 Android WebView。
- 不追求 100% 还原浏览器可访问性树（Accessibility Tree）；优先保证鲁棒、预算可控、对常见页面有效。
- 不做网络抓取（fetch）与代理配置管理（由上层 Web 工具层负责）。

## 4. 架构分层（高层）

- **JS 侧（agent-browser.js）**：`snapshot / action / query / page`，通过 `window.__agentBrowser` 暴露。
- **Kotlin 侧（agent-browser-kotlin）**：脚本内嵌与管理；`snapshotJs()/actionJs()/queryJs()` 生成；JSON 解析与渲染。
- **上层 WebView 工具层（不在本库交付范围内）**：负责 `WebView.evaluateJavascript(...)`、JSBridge 回调、页面加载/等待等。

## 5. JS 侧设计（agent-browser.js）

### 5.1 全局入口

- 在 `window.__agentBrowser` 下暴露：
  - `snapshot(options)`
  - `action(ref, kind, payload)`
  - `query(ref, kind, payload)`
  - `page(kind, payload)`（页面级操作，v1 可先留空壳）

### 5.2 Snapshot（核心）

目标：遍历 DOM，生成“模型可用”的结构化 JSON，并给候选节点分配 `e1,e2,...` ref，同时对元素标记 `data-agent-ref="<ref>"`，以便后续 O(1) 定位执行 action/query。

#### 5.2.1 节点信息提取（v1 必须）

- `tagName`（小写）
- `role`（显式 role 或 Tag→Role 推断）
- `name`（aria-label / aria-labelledby / 元素文本摘要 / alt 等）
- `attrs`：白名单（如 `href,type,name,value,placeholder,aria-label,role`），单值限长
- `text`：文本摘要（whitespace normalize），单节点限长
- `visible`：可见性判断
- `interactive`：是否可交互（依据 role/tag/attrs/事件属性等）

#### 5.2.2 Tag → Role 隐式映射（v1 必须）

最小集合：`a→link`、`button→button`、`input[type=text]→textbox`、`input[type=search]→searchbox`、`textarea→textbox`、`select→combobox`、`img→img`、`h1-h6→heading`、`li→listitem`、`ul/ol→list`、`nav→navigation`、`main→main`、`header→banner`、`footer→contentinfo`、`form→form`。

#### 5.2.3 可见性判断（v1 必须）

跳过不可见元素：

- `display:none`、`visibility:hidden`、`opacity:0`（可配置是否视作不可见）
- `aria-hidden="true"`
- `hidden` 属性
- `offsetParent===null`（部分场景需兜底）

#### 5.2.4 JS 侧预算（v1 必须）

- 粗粒度限制：`maxNodes`（默认 500）防止超大 DOM 过慢。
- 细粒度预算由 Kotlin 渲染阶段控制（`maxCharsTotal / maxNodes / maxDepth`）。

#### 5.2.5 输出 JSON 格式（v1 必须）

输出对象（示意）：

```json
{
  "ok": true,
  "type": "snapshot",
  "meta": { "url": "...", "title": "...", "ts": 0 },
  "stats": { "nodesVisited": 0, "nodesEmitted": 0, "truncated": false, "truncateReasons": [] },
  "refs": {
    "e1": { "ref":"e1", "tag":"button", "role":"button", "name":"搜索", "attrs":{...}, "path":"..." }
  },
  "tree": {
    "tag":"body",
    "role":"document",
    "children":[
      { "ref":"e1", "tag":"button", "role":"button", "name":"搜索", "text":"搜索", "children":[] }
    ]
  }
}
```

### 5.3 Action（v1 必须最小闭环）

对 `ref` 对应元素执行操作（至少）：

- `click`
- `fill`（设置 value + 触发 input/change）

输出（示意）：

```json
{ "ok": true, "type": "action", "action": "click", "ref": "e1", "meta": { "ts": 0 } }
```

失败输出（示意）：

```json
{ "ok": false, "type": "action", "action": "click", "ref": "e404", "error": { "code": "ref_not_found", "message": "..." } }
```

### 5.4 Query（v1 必须）

对 `ref` 对应元素查询（至少）：

- `text`
- `attrs`
- `outerHTML`（必须可限长并标记截断）

### 5.5 JSBridge 通信（上层决定）

本库以 “返回 JSON 字符串” 为第一优先：`return JSON.stringify(result)`；也允许上层通过 `window.AgentBridge.onSnapshot(...)` 回传。

## 6. Kotlin 侧设计（agent-browser-kotlin）

### 6.1 数据结构（v1 必须）

- Snapshot/Action/Query 的结果模型（`ok/type/meta/error/...`）
- Snapshot tree 节点结构 + `refs` 映射
- RenderOptions（预算与格式控制）
- RenderResult（文本 + 截断原因 + 统计）

### 6.2 核心 API（v1 必须）

`AgentBrowser`（object / class 均可）对外提供：

- `getScript(): String`：完整 JS 脚本（用于一次性注入 WebView）
- `snapshotJs(options): String`：生成 `evaluateJavascript` 可执行表达式
- `renderSnapshot(json: String, options): RenderResult`：JSON → 预算控制 → 文本
- `actionJs(ref, kind, payload?): String` / `parseActionResult(json): ActionResult`
- `queryJs(ref, kind, payload?): String` / `parseQueryResult(json): QueryResult`

### 6.3 Render 算法（预算控制，v1 必须）

必须保证：

- 输出 `text.length <= maxCharsTotal`
- 受控的 `maxNodes / maxDepth`
- 命中预算时显式 `truncated=true` 且提供 `truncateReasons`
- compact 模式：裁剪“没有 ref 子孙也没有文本”的结构分支（对齐 agent-browser 的 compact 思路）

### 6.4 输出格式（v1 先实现 PLAIN_TEXT_TREE）

示意（非强制逐字）：

```
[snapshot] url=... title="..." nodes=120 truncated=true truncateReasons=["maxNodes"]
- main:
  - button "搜索" [ref=e1]
  - searchbox [placeholder="搜索"] [ref=e2]
```

## 7. Ref 生命周期与失效处理（v1 必须）

- ref 与一次 snapshot 绑定：re-snapshot 后旧 ref 可能失效。
- action/query 对找不到 ref 必须返回明确错误（`ref_not_found`），不能崩溃。

## 8. 上层工具定义（Tool Schema）

本库不强制绑定某个 LLM 工具协议；但推荐上层工具层提供：`web_snapshot`、`web_click`、`web_fill`、`web_query` 等，并将本库输出作为 tool.result。

## 9. 完整交互流程示例（摘要）

1) Kotlin 注入脚本：`webView.evaluateJavascript(AgentBrowser.getScript(), null)`
2) snapshot：`webView.evaluateJavascript(AgentBrowser.snapshotJs(...), callback)`
3) 模型选择 ref → action：`webView.evaluateJavascript(AgentBrowser.actionJs("e3","click"), callback)`
4) re-snapshot：同第 2 步

## 10. 推荐默认配置（起点）

- JS：`maxNodes=500`，`interactiveOnly=true`
- Kotlin：`maxCharsTotal=12000`，`maxNodes=200`，`maxDepth=12`，`compact=true`

## 11. 交付物（Deliverables）

- `agent-browser.js`：单文件、无外部依赖、可直接注入 WebView
- `agent-browser-kotlin`：纯 Kotlin/JVM 库（无 Android 依赖），内嵌 JS 脚本为资源
- 单元测试：预算/截断/compact/ref_not_found/解析稳定性
- Android 示例项目：用于 E2E 验证（不是最终交付物）
- README：架构、接入、最佳实践与禁忌（禁止 outerHTML 直喂模型）

## 12. 验收标准（Acceptance Criteria / Req IDs）

- REQ-0002-001：完整闭环：snapshot → 模型看到 ref → action(ref) → 操作成功 → re-snapshot 看到变化。
- REQ-0002-002：对真实大页面（新闻首页、电商商品页），`snapshot.js` 执行时间 < 100ms，输出 JSON < 100KB。
- REQ-0002-003：Kotlin render 后 `text.length <= maxCharsTotal`，不会 OOM。
- REQ-0002-004：`click` 能触发真实页面行为（链接跳转、按钮提交等）。
- REQ-0002-005：`fill` 能正确填入值并触发 React/Vue 等框架的响应式更新（至少触发 input/change 事件序列）。
- REQ-0002-006：`select` 能正确选择下拉选项（v1 可延后，但需在计划里明确差异）。
- REQ-0002-007：输出包含可交互元素 refs（link/button/input）且包含必要 attrs。
- REQ-0002-008：`interactiveOnly=false` 时额外包含 heading/listitem/img 等内容节点。
- REQ-0002-009：compact 模式有效压缩：移除无 ref 子孙的空结构分支。
- REQ-0002-010：不可见元素（`display:none`、`aria-hidden="true"` 等）被正确跳过。
- REQ-0002-011：`ref_not_found` 时返回明确错误，不崩溃。
- REQ-0002-012：Kotlin 库无 Android 依赖，可在纯 JVM 测试中运行。

## 13. 后续迭代

- iframe 支持（`switchToFrame` / `switchToMainFrame`）
- 文件上传（`input[type=file]`）
- WebFetch 场景的 Jsoup fallback（纯 HTML，无 WebView）
- 拖拽（drag and drop）、长按/双击、等待条件、截图能力等

