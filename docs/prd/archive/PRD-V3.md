# PRD-0002 v2：agent-browser-kotlin（WebView JS 注入 + Kotlin 格式化，解决"HTML/DOM 太长吃 token"）

## 1. 背景与问题

我们在做一个 Android 聊天型 Agent App（供应商为 OpenAI Responses API 风格，且必须流式）。Agent 具备 Web 能力（WebView/WebFetch/WebSearch 等），会把网页内容作为工具结果返回给模型。

当前严重问题：工具把 `document.body.outerHTML`（或整页 DOM/HTML）直接返回给模型，导致一次 `tool.result` 就可能几十万字符，下一轮模型请求把该内容带上后触发 `context_length_exceeded`，即使对话轮次不多也会爆。

证据：真机 dump 的 session 中，曾出现单条 `tool.result` 约 590KB 的返回内容。

### 核心思路

借鉴 `vercel-labs/agent-browser` 的 snapshot + refs 理念，但适配 Android WebView 环境：

- agent-browser 依赖 Playwright 的 `locator.ariaSnapshot()` 获取浏览器运行时的 Accessibility Tree —— 我们没有这个 API。
- 替代方案：在 WebView 中注入一段自研 JS 脚本，遍历真实 DOM，提取语义/ARIA/可见性信息，生成结构化 JSON（等价于 ariaSnapshot 的输出）。
- Kotlin 侧接收该 JSON，负责预算控制、格式化为模型可读的 snapshot 文本、管理 ref 映射。

参考项目：`https://github.com/vercel-labs/agent-browser`（重点看 snapshot 的输出形态、ref 标注风格、compact/interactive 过滤策略）。

## 2. 目标（Goals）

1. WebView 侧（JS）：遍历真实 DOM，输出结构化 JSON，包含语义角色、文本、属性、可见性、ref 标识。
2. Kotlin 侧：接收 JSON，执行预算控制（字符数/节点数/深度），格式化为模型可读的 snapshot 文本。
3. Snapshot 重点保留"可读/可操作"信息：文本、链接、按钮、输入框、表单元素、标题层级、列表项等；可配置 `interactiveOnly`。
4. 支持按 ref 做后续精准查询（取某节点 text/attrs/outerHTML，有长度上限与截断标记）。
5. 预算约束：任何输出都必须在预算内，并显式标记 `truncated` 及原因。
6. 对上层 Agent 工具提供稳定协议：模型看到的是 snapshot 文本；工具调用携带 ref 做精准操作。

## 3. 非目标（Non-goals）

- 不做完整的浏览器自动化框架。
- 不追求 100% ARIA 规范覆盖；优先保证鲁棒、预算可控、对常见页面有效。
- 不做网络抓取（fetch）。
- JS 脚本不执行页面交互（点击/填写等）—— 交互由上层 WebView 工具层处理，本库只负责"看"。

## 4. 架构分层

```
┌─────────────────────────────────────────────┐
│  Agent / Tool Layer (Kotlin)                │
│  - 调用 snapshot，把结果作为 tool.result     │
│  - 模型返回 ref，调用 query/action           │
├─────────────────────────────────────────────┤
│  agent-browser-kotlin (本库，Kotlin)         │
│  - 接收 JS 产出的 JSON                      │
│  - 预算控制、过滤、格式化                     │
│  - ref 映射管理、query 处理                  │
├─────────────────────────────────────────────┤
│  snapshot.js (本库提供，注入 WebView)         │
│  - 遍历 DOM，提取语义/ARIA/可见性            │
│  - 生成结构化 JSON，通过 JSBridge 回传       │
├─────────────────────────────────────────────┤
│  Android WebView                            │
│  - 加载页面，注入 JS，桥接通信               │
└─────────────────────────────────────────────┘
```

## 5. JS 侧设计（snapshot.js）

### 5.1 职责

遍历 `document.body`（或指定 scope 元素）的 DOM 树，对每个节点提取信息，输出一棵 JSON 树。

### 5.2 节点信息提取

对每个 DOM 元素，提取：

| 字段 | 来源 | 说明 |
|---|---|---|
| `tag` | `element.tagName` | 小写 |
| `role` | `element.getAttribute('role')` 或 tag→role 隐式映射 | 见 5.3 |
| `name` | `aria-label` > `aria-labelledby` 引用文本 > `alt` > `title` > 可见文本（截断） | 可访问名称计算（简化版） |
| `text` | `innerText`（仅直接文本子节点，normalize whitespace） | 截断至 `maxTextPerNode` |
| `attrs` | 白名单属性：`href, name, type, value, placeholder, src, action, method` | 每个 value 限长 |
| `visible` | `offsetParent !== null` + `getComputedStyle` 检查 `display/visibility/opacity` | 不可见节点跳过 |
| `interactive` | 是否为可交互元素（见 5.4） | 布尔 |
| `ref` | `e1, e2, ...` 递增分配 | 仅对候选节点分配 |
| `children` | 递归子节点数组 | — |

### 5.3 Tag → Role 隐式映射（简化版）

```javascript
const IMPLICIT_ROLES = {
  'a[href]':    'link',
  'button':     'button',
  'input[type=text]':    'textbox',
  'input[type=search]':  'searchbox',
  'input[type=email]':   'textbox',
  'input[type=password]':'textbox',
  'input[type=number]':  'spinbutton',
  'input[type=checkbox]':'checkbox',
  'input[type=radio]':   'radio',
  'input[type=submit]':  'button',
  'input[type=reset]':   'button',
  'input[type=range]':   'slider',
  'select':     'combobox',
  'textarea':   'textbox',
  'h1':         'heading',
  'h2':         'heading',
  'h3':         'heading',
  'h4':         'heading',
  'h5':         'heading',
  'h6':         'heading',
  'ul':         'list',
  'ol':         'list',
  'li':         'listitem',
  'nav':        'navigation',
  'main':       'main',
  'header':     'banner',
  'footer':     'contentinfo',
  'form':       'form',
  'table':      'table',
  'tr':         'row',
  'td':         'cell',
  'th':         'columnheader',
  'img':        'img',
  'article':    'article',
  'section':    'region',
  'aside':      'complementary',
  'dialog':     'dialog',
  'details':    'group',
  'summary':    'button',
  'option':     'option',
  'progress':   'progressbar',
  'meter':      'meter',
};
```

如果元素有显式 `role` 属性，优先使用显式值。

### 5.4 候选节点规则

分三类（与 agent-browser 一致）：

- INTERACTIVE：`link, button, textbox, searchbox, combobox, checkbox, radio, slider, spinbutton, switch, option, menuitem`
- CONTENT：`heading, img, cell, columnheader, listitem, article, progressbar, meter`
- STRUCTURAL：`list, navigation, main, banner, contentinfo, form, table, row, region, complementary, group, dialog`

分配 ref 的规则：
- INTERACTIVE 节点：始终分配 ref
- CONTENT 节点：`interactiveOnly=false` 时分配 ref
- STRUCTURAL 节点：自身不分配 ref，但作为树结构保留（如果有 ref 子孙）

### 5.5 可见性判断

```javascript
function isVisible(el) {
  if (el.offsetParent === null && getComputedStyle(el).position !== 'fixed') return false;
  const style = getComputedStyle(el);
  if (style.display === 'none') return false;
  if (style.visibility === 'hidden') return false;
  if (parseFloat(style.opacity) === 0) return false;
  // aria-hidden="true" 的元素及其子树跳过
  if (el.getAttribute('aria-hidden') === 'true') return false;
  return true;
}
```

不可见节点及其子树直接跳过，不遍历。这是压缩率的重要来源。

### 5.6 JS 侧预算（粗粒度）

JS 侧做粗粒度的节点数上限（比如 `maxNodes=500`），防止超大 DOM 导致 JS 执行时间过长。精细的字符预算由 Kotlin 侧控制。

### 5.7 输出 JSON 格式

```json
{
  "version": 1,
  "url": "https://example.com/page",
  "title": "页面标题",
  "timestamp": 1739628047000,
  "tree": {
    "tag": "body",
    "role": null,
    "children": [
      {
        "tag": "nav",
        "role": "navigation",
        "children": [
          {
            "ref": "e1",
            "tag": "a",
            "role": "link",
            "name": "首页",
            "attrs": { "href": "/" },
            "text": "首页"
          },
          {
            "ref": "e2",
            "tag": "a",
            "role": "link",
            "name": "价格",
            "attrs": { "href": "/pricing" },
            "text": "价格"
          }
        ]
      },
      {
        "tag": "main",
        "role": "main",
        "children": [
          {
            "ref": "e3",
            "tag": "h1",
            "role": "heading",
            "text": "今日金价",
            "level": 1
          },
          {
            "ref": "e4",
            "tag": "input",
            "role": "searchbox",
            "attrs": { "type": "search", "placeholder": "搜索..." }
          },
          {
            "ref": "e5",
            "tag": "button",
            "role": "button",
            "name": "搜索",
            "text": "搜索"
          }
        ]
      }
    ]
  },
  "stats": {
    "domNodes": 3200,
    "visitedNodes": 800,
    "emittedNodes": 45,
    "skippedHidden": 2400,
    "jsTimeMs": 12
  }
}
```

### 5.8 JSBridge 通信

```javascript
// 注入后调用
const result = snapshotDOM(document.body, options);
// 通过 Android JSBridge 回传
window.AgentBridge.onSnapshot(JSON.stringify(result));
```

或者通过 `evaluateJavascript` 的回调直接拿返回值。具体桥接方式由上层 WebView 工具层决定，本库只负责提供 `snapshot.js` 脚本内容。

## 6. Kotlin 侧设计

### 6.1 数据结构

```kotlin
// ---- 配置 ----
data class SnapshotOptions(
    val maxCharsTotal: Int = 12_000,
    val maxNodes: Int = 200,
    val maxDepth: Int = 12,
    val maxTextPerNode: Int = 200,
    val interactiveOnly: Boolean = true,
    val compact: Boolean = true,          // 移除无 ref 子孙的空结构节点
    val outputFormat: OutputFormat = OutputFormat.PLAIN_TEXT_TREE,
)

enum class OutputFormat {
    PLAIN_TEXT_TREE,   // 给模型看的缩进文本
    JSON,              // 给程序用的结构化 JSON
}

// ---- JS 侧 JSON 反序列化 ----
data class RawSnapshotNode(
    val tag: String,
    val role: String? = null,
    val ref: String? = null,
    val name: String? = null,
    val text: String? = null,
    val level: Int? = null,           // heading level
    val attrs: Map<String, String> = emptyMap(),
    val children: List<RawSnapshotNode> = emptyList(),
)

data class RawSnapshot(
    val version: Int,
    val url: String?,
    val title: String?,
    val timestamp: Long,
    val tree: RawSnapshotNode,
    val stats: RawSnapshotStats,
)

data class RawSnapshotStats(
    val domNodes: Int,
    val visitedNodes: Int,
    val emittedNodes: Int,
    val skippedHidden: Int,
    val jsTimeMs: Int,
)

// ---- Kotlin 侧输出 ----
data class SnapshotResult(
    val snapshotText: String,          // 给模型的文本
    val refs: Map<String, NodeRef>,    // ref -> 节点信息
    val stats: SnapshotStats,
)

data class NodeRef(
    val ref: String,
    val tag: String,
    val role: String?,
    val name: String?,
    val attrs: Map<String, String>,
    val textSnippet: String?,
)

data class SnapshotStats(
    val jsStats: RawSnapshotStats,     // JS 侧统计
    val nodesEmitted: Int,             // Kotlin 侧最终输出的节点数
    val charsEmitted: Int,             // 最终 snapshotText 长度
    val truncated: Boolean,
    val truncateReasons: List<String>,
)

// ---- Ref 查询 ----
enum class RefQueryKind { TEXT, ATTRS, OUTER_HTML }

data class RefQueryResult(
    val ref: String,
    val kind: RefQueryKind,
    val value: String,
    val truncated: Boolean,
)
```

### 6.2 核心 API

```kotlin
object AgentBrowser {

    /**
     * 获取 snapshot.js 脚本内容（用于注入 WebView）
     * @param jsOptions JS 侧配置（节点上限、文本截断长度等）
     */
    fun getSnapshotScript(jsOptions: JsSnapshotOptions = JsSnapshotOptions()): String

    /**
     * 解析 JS 回传的 JSON，生成 SnapshotDocument（可复用）
     */
    fun parseSnapshot(json: String): SnapshotDocument

    /**
     * 从 SnapshotDocument 生成受预算控制的 snapshot 文本
     */
    fun render(doc: SnapshotDocument, options: SnapshotOptions = SnapshotOptions()): SnapshotResult

    /**
     * 便捷一步到位：JSON -> SnapshotResult
     */
    fun snapshot(json: String, options: SnapshotOptions = SnapshotOptions()): SnapshotResult

    /**
     * 按 ref 查询节点详情（需要 WebView 再次执行 JS 获取）
     * 返回要注入的 JS 代码
     */
    fun getQueryScript(ref: String, kind: RefQueryKind, limitChars: Int = 2_000): String
}
```

### 6.3 render 算法（预算控制）

```
输入：RawSnapshotNode 树 + SnapshotOptions
输出：snapshotText（字符串）+ refs（Map）

charBudget = options.maxCharsTotal
nodeCount = 0
lines = []

fun visit(node, depth):
    if depth > maxDepth: return
    if charBudget <= 0: mark truncated("maxCharsTotal"); return
    if nodeCount >= maxNodes: mark truncated("maxNodes"); return

    // 过滤：interactiveOnly 模式下跳过非交互非结构节点
    if interactiveOnly && node.role not in (INTERACTIVE ∪ STRUCTURAL): skip

    // compact 模式：如果结构节点没有 ref 子孙，跳过
    if compact && isStructural(node.role) && !hasRefDescendant(node): skip

    // 生成行
    if node.ref != null:
        line = formatLine(node, depth)  // 如 "  - button "搜索" [ref=e5]"
        if charBudget - line.length < 0: mark truncated; return
        lines.add(line)
        charBudget -= line.length
        nodeCount++
        refs[node.ref] = toNodeRef(node)
    else if isStructural(node.role):
        // 结构节点不占 ref，但输出缩进标记
        structLine = "  ".repeat(depth) + "- ${node.role}:"
        lines.add(structLine)
        charBudget -= structLine.length

    for child in node.children:
        visit(child, depth + 1)
```

### 6.4 输出格式（PLAIN_TEXT_TREE）

对齐 agent-browser 的 ARIA snapshot 风格，利用模型已有训练数据：

```
[snapshot] url=https://example.com title="今日金价" nodes=45 truncated=false
- navigation:
  - link "首页" [ref=e1]
  - link "价格" [ref=e2]
- main:
  - heading "今日金价" [level=1] [ref=e3]
  - searchbox [placeholder="搜索..."] [ref=e4]
  - button "搜索" [ref=e5]
  - list:
    - listitem "黄金 ¥580.00/g" [ref=e6]
    - listitem "白银 ¥7.20/g" [ref=e7]
    - ... (truncated, 15 more items)
```

## 7. Ref 查询机制

模型看到 snapshot 后，可能需要某个 ref 的更多信息。流程：

1. 模型调用工具：`query_ref(ref="e6", kind="TEXT", limit=2000)`
2. 上层工具层调用 `AgentBrowser.getQueryScript("e6", TEXT, 2000)` 获取 JS 代码
3. 注入 WebView 执行，JS 通过 `data-agent-ref="e6"` 属性定位元素（snapshot.js 在遍历时已标记）
4. 返回结果，Kotlin 侧截断并包装为 `RefQueryResult`

JS 侧在 snapshot 遍历时，对分配了 ref 的元素设置 `data-agent-ref` 属性：

```javascript
if (ref) {
  element.setAttribute('data-agent-ref', ref);
}
```

查询 JS：

```javascript
function queryRef(ref, kind, limit) {
  const el = document.querySelector(`[data-agent-ref="${ref}"]`);
  if (!el) return { ref, kind, value: null, error: 'not_found' };
  let value;
  switch (kind) {
    case 'TEXT': value = el.innerText; break;
    case 'ATTRS': value = JSON.stringify(Object.fromEntries(
      Array.from(el.attributes).map(a => [a.name, a.value])
    )); break;
    case 'OUTER_HTML': value = el.outerHTML; break;
  }
  const truncated = value.length > limit;
  return { ref, kind, value: value.slice(0, limit), truncated };
}
```

## 8. 交付物（Deliverables）

### 8.1 snapshot.js

- 单文件，无外部依赖，可直接注入 WebView
- 压缩后 < 10KB
- 输出 JSON 格式如 5.7 所述
- 包含 ref 查询函数

### 8.2 agent-browser-kotlin（Kotlin 库）

- Gradle 工程，可发布为 maven artifact
- 依赖：`kotlinx-serialization-json`（解析 JS 回传的 JSON）
- 无 Android 依赖（纯 Kotlin/JVM，Android 项目可直接引用）
- 核心 API 如 6.2 所述
- 内嵌 `snapshot.js` 为资源文件

### 8.3 单元测试

- 大 JSON（模拟 500+ 节点的 snapshot）能 render 且输出受控
- `interactiveOnly` / `compact` 行为正确
- 截断标记与 stats 正确
- ref 稳定性：同一输入同一 options 下 refs 顺序一致
- 边界：空 DOM、纯文本页面、深度嵌套

### 8.4 集成测试（可选，需 Android 环境）

- 真实 WebView 加载页面 → 注入 snapshot.js → 回传 JSON → Kotlin render → 验证输出

### 8.5 README

- 背景、架构图、快速示例
- snapshot.js 注入方式说明
- 推荐默认配置
- 最佳实践：禁止 outerHTML 直喂模型

## 9. 验收标准（Acceptance Criteria）

1. 对真实大页面（如新闻首页、电商商品页），snapshot.js 执行时间 < 100ms，输出 JSON < 100KB。
2. Kotlin render 后 `snapshotText.length <= maxCharsTotal`，不会 OOM。
3. 输出包含可交互元素 refs（link/button/input）且包含必要 attrs。
4. `interactiveOnly=false` 时额外包含 heading/listitem/img 等内容节点。
5. compact 模式有效压缩：移除无 ref 子孙的空结构分支。
6. ref 查询返回受 `limitChars` 控制的截断结果。
7. 不可见元素（`display:none`、`aria-hidden="true"` 等）被正确跳过。
8. Kotlin 库无 Android 依赖，可在纯 JVM 测试中运行。

## 10. 推荐默认配置

### JS 侧

```javascript
{
  maxNodes: 500,           // JS 侧粗粒度上限
  maxTextPerNode: 200,
  maxAttrValueLen: 150,
  scope: document.body,
  interactiveOnly: true,
}
```

### Kotlin 侧

```kotlin
SnapshotOptions(
    maxCharsTotal = 12_000,
    maxNodes = 200,
    maxDepth = 12,
    maxTextPerNode = 200,
    interactiveOnly = true,
    compact = true,
    outputFormat = OutputFormat.PLAIN_TEXT_TREE,
)
```

## 11. 与 v1 PRD 的关键差异

| 维度 | v1（纯 Kotlin 解析 HTML） | v2（WebView JS + Kotlin） |
|---|---|---|
| HTML 解析 | Jsoup 在 Kotlin 侧解析 | WebView 原生 DOM API |
| 语义角色 | Kotlin 侧 tag→role 硬编码映射 | JS 侧读取真实 ARIA 属性 + 隐式映射 |
| 可见性判断 | 无法判断（没有 CSS 计算） | `getComputedStyle` 真实判断 |
| 动态内容 | 只能看到初始 HTML | 看到 JS 渲染后的真实 DOM |
| Jsoup 依赖 | 需要 | 不需要 |
| JS 脚本 | 不需要 | 需要维护 snapshot.js |
| 适用场景 | WebFetch 等纯 HTML 场景 | WebView 场景（主要）|

注意：对于 WebFetch（没有 WebView 的纯 HTML 抓取）场景，可以保留一个 Jsoup fallback 路径，但优先级低于 WebView 路径。这个 fallback 可以作为后续迭代补充。

---

柠檬叔，你说得太对了。我把 agent-browser 的 actions 和 browser 核心代码全看完了，现在完全理解了它的交互闭环：

agent-browser 的核心循环是：
1. `snapshot` → 模型看到 `[ref=e5]` 标注的元素
2. 模型决策 → 发出 `click @e5` / `fill @e3 "xxx"` / `get text @e1`
3. `getLocator(ref)` → 通过缓存的 refMap 还原成 Playwright locator → 执行操作
4. 操作后 → 重新 `snapshot` → 回到步骤 1

关键发现：`getLocatorFromRef` 里，ref 映射存的是 `{ role, name, nth, selector }`，它用 `page.getByRole(role, { name, exact: true }).nth(nth)` 来定位元素。这不是用 CSS selector 或 XPath，而是用语义定位器。

你的 WebView 场景没有 Playwright，所以需要自己实现这个"ref → 定位 → 操作"的链路。下面是重写的 PRD v3：

---

# PRD-0002 v3：agent-browser-kotlin（WebView 注入 JS 实现 snapshot + ref 交互，完整网页自动化）

## 1. 背景与问题

（同 v2，略）

### 核心思路变化（v2 → v3）

v2 只解决了"看"的问题（snapshot），但 Agent 需要的是完整的网页自动化闭环：

```
snapshot（看） → 模型决策 → action（操作） → 重新 snapshot → ...
```

agent-browser 的完整循环：
1. `snapshot` → 输出带 `[ref=e1]` 标注的 accessibility tree
2. 模型看到 snapshot → 决定 `click @e5` 或 `fill @e3 "hello"`
3. `getLocator(@e5)` → 通过缓存的 refMap（role + name + nth）还原成 Playwright locator → 执行 `locator.click()`
4. 操作完成 → 重新 `snapshot`（因为页面可能变了）→ 回到步骤 1

我们的 WebView 场景没有 Playwright，所以 snapshot 和 action 都需要通过注入 JS 实现。

## 2. 目标（Goals）

1. 在 WebView 中注入 JS，实现 snapshot（看）和 action（操作）两大能力。
2. Snapshot：遍历真实 DOM，输出结构化 JSON + ref 标注，Kotlin 侧格式化为模型可读文本。
3. Action：模型通过 ref 指定目标元素，JS 侧定位并执行操作（click/fill/check/select/scroll/hover 等）。
4. 预算约束：snapshot 输出必须在预算内，action 结果也要简洁。
5. 纯 Kotlin 库 + JS 脚本：不依赖 Playwright，不依赖 Android 特定 API。
6. 对上层 Agent 工具提供稳定的工具协议。

## 3. 非目标（Non-goals）

- 不做完整浏览器自动化框架（不管理浏览器生命周期、Tab、Cookie 等）。
- 不追求 100% ARIA 规范覆盖。
- 不做网络抓取。
- 不处理跨 iframe 场景（初期）。

## 4. 架构分层

```
┌─────────────────────────────────────────────────┐
│  Agent / Tool Layer (Kotlin)                    │
│  - 定义 tools: web_snapshot, web_click,         │
│    web_fill, web_select, web_scroll, web_get... │
│  - 把 snapshot 结果作为 tool.result 返回模型     │
│  - 模型返回 tool_call(ref=e5, action=click)     │
├─────────────────────────────────────────────────┤
│  agent-browser-kotlin (本库，Kotlin)             │
│  - 管理 JS 脚本生成与结果解析                    │
│  - snapshot: JSON → 预算控制 → 格式化文本        │
│  - action: 生成 JS 调用代码 → 解析执行结果       │
│  - ref 映射缓存与校验                            │
├─────────────────────────────────────────────────┤
│  agent-browser.js (本库提供，注入 WebView)       │
│  - snapshotDOM(): 遍历 DOM → 结构化 JSON        │
│  - actionByRef(ref, action, params): 定位 → 操作│
│  - queryByRef(ref, kind, limit): 定位 → 查询    │
├─────────────────────────────────────────────────┤
│  Android WebView                                │
│  - evaluateJavascript() 注入/调用               │
└─────────────────────────────────────────────────┘
```

## 5. JS 侧设计（agent-browser.js）

### 5.1 两大入口函数

```javascript
// 全局命名空间，避免污染
window.__agentBrowser = {
  snapshot: function(options) { ... },   // 返回 JSON
  action:   function(ref, action, params) { ... },  // 返回 JSON
  query:    function(ref, kind, limit) { ... },      // 返回 JSON
};
```

### 5.2 Snapshot（同 v2，此处不重复）

遍历 DOM，生成结构化 JSON 树，对候选节点分配 `e1, e2, ...` ref。

关键补充：snapshot 时，对每个分配了 ref 的元素，设置 `data-agent-ref` 属性：

```javascript
element.setAttribute('data-agent-ref', ref);
```

这是后续 action/query 定位元素的基础。

### 5.3 Action（核心新增）

```javascript
/**
 * 通过 ref 定位元素并执行操作
 * @param {string} ref - 元素引用，如 "e5"
 * @param {string} action - 操作类型
 * @param {object} params - 操作参数
 * @returns {object} 执行结果 JSON
 */
function actionByRef(ref, action, params) {
  const el = document.querySelector(`[data-agent-ref="${ref}"]`);
  if (!el) {
    return { success: false, error: 'ref_not_found', ref };
  }

  try {
    switch (action) {
      case 'click':
        return doClick(el, params);
      case 'fill':
        return doFill(el, params);
      case 'check':
        return doCheck(el, params);
      case 'uncheck':
        return doUncheck(el, params);
      case 'select':
        return doSelect(el, params);
      case 'focus':
        return doFocus(el);
      case 'hover':
        return doHover(el);
      case 'scroll_into_view':
        return doScrollIntoView(el);
      case 'clear':
        return doClear(el);
      default:
        return { success: false, error: 'unknown_action', action };
    }
  } catch (e) {
    return { success: false, error: e.message, ref, action };
  }
}
```

#### 5.3.1 各操作实现

```javascript
function doClick(el, params) {
  // 先滚动到可见
  el.scrollIntoView({ block: 'center', behavior: 'instant' });

  // 模拟真实点击事件序列（不只是 el.click()）
  const rect = el.getBoundingClientRect();
  const x = rect.left + rect.width / 2;
  const y = rect.top + rect.height / 2;

  const events = ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'];
  for (const type of events) {
    const EventClass = type.startsWith('pointer') ? PointerEvent : MouseEvent;
    el.dispatchEvent(new EventClass(type, {
      bubbles: true, cancelable: true, view: window,
      clientX: x, clientY: y, button: 0,
    }));
  }

  return { success: true, action: 'click', ref: el.getAttribute('data-agent-ref') };
}

function doFill(el, params) {
  el.scrollIntoView({ block: 'center', behavior: 'instant' });
  el.focus();

  // 清空现有值
  const nativeInputValueSetter = Object.getOwnPropertyDescriptor(
    window.HTMLInputElement.prototype, 'value'
  )?.set || Object.getOwnPropertyDescriptor(
    window.HTMLTextAreaElement.prototype, 'value'
  )?.set;

  if (nativeInputValueSetter) {
    nativeInputValueSetter.call(el, params.value);
  } else {
    el.value = params.value;
  }

  // 触发 input/change 事件（React/Vue 等框架需要）
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));

  return { success: true, action: 'fill', value: params.value };
}

function doSelect(el, params) {
  if (el.tagName !== 'SELECT') {
    return { success: false, error: 'not_a_select_element' };
  }

  const options = Array.from(el.options);
  const values = Array.isArray(params.values) ? params.values : [params.values];

  for (const opt of options) {
    opt.selected = values.includes(opt.value) || values.includes(opt.textContent.trim());
  }

  el.dispatchEvent(new Event('change', { bubbles: true }));

  return { success: true, action: 'select', values };
}

function doCheck(el, params) {
  if (el.type === 'checkbox' || el.type === 'radio') {
    if (!el.checked) {
      el.click();
    }
    return { success: true, action: 'check', checked: el.checked };
  }
  return { success: false, error: 'not_checkable' };
}

function doUncheck(el, params) {
  if (el.type === 'checkbox') {
    if (el.checked) {
      el.click();
    }
    return { success: true, action: 'uncheck', checked: el.checked };
  }
  return { success: false, error: 'not_uncheckable' };
}

function doFocus(el) {
  el.focus();
  return { success: true, action: 'focus' };
}

function doHover(el) {
  el.scrollIntoView({ block: 'center', behavior: 'instant' });
  el.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true }));
  el.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
  return { success: true, action: 'hover' };
}

function doScrollIntoView(el) {
  el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  return { success: true, action: 'scroll_into_view' };
}

function doClear(el) {
  el.focus();
  el.value = '';
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
  return { success: true, action: 'clear' };
}
```

#### 5.3.2 为什么用 `data-agent-ref` 而不是 role+name 定位

agent-browser 用 `page.getByRole(role, { name, exact: true }).nth(nth)` 定位，这依赖 Playwright 的语义定位器引擎。我们在 WebView 里没有这个。

替代方案对比：

| 方案 | 优点 | 缺点 |
|---|---|---|
| `data-agent-ref` 属性 | 简单、O(1) 查找、不依赖任何库 | 页面 DOM 变化后 ref 失效 |
| CSS selector 路径 | 不修改 DOM | 脆弱，DOM 变化易断 |
| XPath | 不修改 DOM | 同上，且性能差 |
| role+name+nth | 语义稳定 | 需要自己实现 ARIA 匹配引擎，复杂 |

选择 `data-agent-ref`，因为：
- 在 snapshot → action 的短周期内（通常几秒），DOM 不太会变
- 如果 DOM 变了（导航、AJAX），上层工具应该重新 snapshot
- 实现最简单，定位最快

### 5.4 Query（查询元素详情）

```javascript
function queryByRef(ref, kind, limit) {
  const el = document.querySelector(`[data-agent-ref="${ref}"]`);
  if (!el) return { ref, error: 'not_found' };

  let value;
  switch (kind) {
    case 'text':
      value = el.innerText;
      break;
    case 'html':
      value = el.innerHTML;
      break;
    case 'value':
      value = el.value ?? '';
      break;
    case 'attrs':
      value = JSON.stringify(
        Object.fromEntries(Array.from(el.attributes).map(a => [a.name, a.value]))
      );
      break;
    case 'computed_styles':
      const s = getComputedStyle(el);
      value = JSON.stringify({
        display: s.display, color: s.color, fontSize: s.fontSize,
        backgroundColor: s.backgroundColor, visibility: s.visibility,
      });
      break;
  }

  const truncated = value && value.length > limit;
  return {
    ref, kind,
    value: truncated ? value.slice(0, limit) + '...[truncated]' : value,
    truncated: !!truncated,
  };
}
```

### 5.5 页面级操作（不需要 ref）

```javascript
window.__agentBrowser.page = {
  scrollBy: function(x, y) {
    window.scrollBy(x, y);
    return { success: true, scrollX: window.scrollX, scrollY: window.scrollY };
  },
  scrollTo: function(x, y) {
    window.scrollTo(x, y);
    return { success: true };
  },
  getUrl: function() { return location.href; },
  getTitle: function() { return document.title; },
  pressKey: function(key) {
    // 模拟键盘事件（Enter/Tab/Escape 等）
    document.activeElement?.dispatchEvent(
      new KeyboardEvent('keydown', { key, bubbles: true })
    );
    document.activeElement?.dispatchEvent(
      new KeyboardEvent('keyup', { key, bubbles: true })
    );
    return { success: true, key };
  },
};
```

## 6. Kotlin 侧设计

### 6.1 核心 API

```kotlin
object AgentBrowser {

    // ---- JS 脚本管理 ----
  
    /** 获取需要注入 WebView 的完整 JS 脚本 */
    fun getScript(): String
  
    // ---- Snapshot ----
  
    /** 生成 snapshot 调用的 JS 代码 */
    fun snapshotJs(options: SnapshotOptions = SnapshotOptions()): String
    // 返回: "window.__agentBrowser.snapshot({...})"
  
    /** 解析 snapshot JSON，格式化为模型可读文本 */
    fun parseSnapshot(json: String, options: RenderOptions = RenderOptions()): SnapshotResult
  
    // ---- Action ----
  
    /** 生成 action 调用的 JS 代码 */
    fun actionJs(ref: String, action: String, params: Map<String, Any?> = emptyMap()): String
    // 返回: "window.__agentBrowser.action('e5', 'click', {})"
  
    /** 解析 action 结果 JSON */
    fun parseActionResult(json: String): ActionResult
  
    // ---- Query ----
  
    /** 生成 query 调用的 JS 代码 */
    fun queryJs(ref: String, kind: QueryKind, limit: Int = 2000): String
  
    /** 解析 query 结果 JSON */
    fun parseQueryResult(json: String): QueryResult
  
    // ---- Page Actions ----
  
    fun scrollJs(direction: String, amount: Int = 300): String
    fun pressKeyJs(key: String): String
    fun getUrlJs(): String
    fun getTitleJs(): String
}
```

### 6.2 数据结构

```kotlin
// ---- Snapshot ----
data class SnapshotOptions(
    val maxNodes: Int = 500,
    val maxTextPerNode: Int = 200,
    val interactiveOnly: Boolean = true,
    val scope: String? = null,  // CSS selector
)

data class RenderOptions(
    val maxCharsTotal: Int = 12_000,
    val maxNodes: Int = 200,
    val maxDepth: Int = 12,
    val compact: Boolean = true,
    val format: OutputFormat = OutputFormat.PLAIN_TEXT_TREE,
)

data class SnapshotResult(
    val text: String,              // 给模型的文本
    val refs: Map<String, NodeRef>,
    val stats: SnapshotStats,
)

// ---- Action ----
data class ActionResult(
    val success: Boolean,
    val action: String?,
    val error: String?,
    val ref: String?,
    val details: Map<String, Any?> = emptyMap(),
)

// ---- Query ----
enum class QueryKind { TEXT, HTML, VALUE, ATTRS, COMPUTED_STYLES }

data class QueryResult(
    val ref: String,
    val kind: QueryKind,
    val value: String?,
    val truncated: Boolean,
    val error: String?,
)
```

### 6.3 上层工具定义（给模型看的 tool schema）

```kotlin
// 推荐的 Agent Tool 定义
val WEB_TOOLS = listOf(
    Tool(
        name = "web_snapshot",
        description = "获取当前页面的可交互元素快照。返回带 ref 标注的元素列表。后续操作使用 ref 指定目标。",
        parameters = mapOf(
            "interactive_only" to Param(type = "boolean", default = true),
            "scope" to Param(type = "string", description = "CSS selector，只 snapshot 某区域", optional = true),
        )
    ),
    Tool(
        name = "web_click",
        description = "点击指定 ref 的元素。",
        parameters = mapOf("ref" to Param(type = "string", required = true))
    ),
    Tool(
        name = "web_fill",
        description = "在指定 ref 的输入框中填入文本（会先清空）。",
        parameters = mapOf(
            "ref" to Param(type = "string", required = true),
            "value" to Param(type = "string", required = true),
        )
    ),
    Tool(
        name = "web_select",
        description = "在指定 ref 的下拉框中选择选项。",
        parameters = mapOf(
            "ref" to Param(type = "string", required = true),
            "values" to Param(type = "array", items = "string", required = true),
        )
    ),
    Tool(
        name = "web_check",
        description = "勾选指定 ref 的 checkbox/radio。",
        parameters = mapOf("ref" to Param(type = "string", required = true))
    ),
    Tool(
        name = "web_scroll",
        description = "页面滚动。direction: up/down/left/right",
        parameters = mapOf(
            "direction" to Param(type = "string", required = true),
            "amount" to Param(type = "integer", default = 300),
        )
    ),
    Tool(
        name = "web_press_key",
        description = "按键。如 Enter, Tab, Escape, Backspace",
        parameters = mapOf("key" to Param(type = "string", required = true))
    ),
    Tool(
        name = "web_get_text",
        description = "获取指定 ref 元素的文本内容。",
        parameters = mapOf(
            "ref" to Param(type = "string", required = true),
            "max_length" to Param(type = "integer", default = 2000),
        )
    ),
)
```

## 7. 完整交互流程示例

```
用户: "帮我在淘宝搜索 iPhone 16"

Agent 思考: 需要先看看页面上有什么

[Tool Call] web_snapshot(interactive_only=true)
→ Kotlin: evaluateJavascript(AgentBrowser.snapshotJs(...))
→ JS: window.__agentBrowser.snapshot({...})
→ JSON 回传 → Kotlin: parseSnapshot(json)
→ Tool Result:
  [snapshot] url=https://www.taobao.com nodes=45 truncated=false
  - navigation:
    - link "首页" [ref=e1]
    - link "天猫" [ref=e2]
  - main:
    - searchbox [placeholder="搜索"] [ref=e3]
    - button "搜索" [ref=e4]
    - ...

Agent 思考: 看到搜索框 e3 和搜索按钮 e4

[Tool Call] web_fill(ref="e3", value="iPhone 16")
→ Kotlin: evaluateJavascript(AgentBrowser.actionJs("e3", "fill", mapOf("value" to "iPhone 16")))
→ JS: window.__agentBrowser.action('e3', 'fill', {value: 'iPhone 16'})
→ Result: { success: true, action: "fill", value: "iPhone 16" }

[Tool Call] web_click(ref="e4")
→ Kotlin: evaluateJavascript(AgentBrowser.actionJs("e4", "click", emptyMap()))
→ JS: window.__agentBrowser.action('e4', 'click', {})
→ Result: { success: true, action: "click" }

Agent 思考: 点击了搜索，页面应该变了，重新 snapshot

[Tool Call] web_snapshot(interactive_only=false)
→ ... 新页面的 snapshot，包含搜索结果 ...
```

## 8. Ref 生命周期与失效处理

关键设计决策：

1. Ref 在 snapshot 时分配，通过 `data-agent-ref` 属性标记在 DOM 上。
2. 每次 snapshot 会清除旧的 `data-agent-ref`，重新分配。
3. 如果 action 时 ref 找不到（DOM 变了），返回 `{ success: false, error: "ref_not_found" }`。
4. 上层工具收到 `ref_not_found` 时，应自动触发重新 snapshot，并告知模型"页面已变化，请重新查看"。

```javascript
// snapshot 开始时，清除旧 ref
function clearOldRefs() {
  document.querySelectorAll('[data-agent-ref]').forEach(el => {
    el.removeAttribute('data-agent-ref');
  });
}
```

## 9. 交付物

### 9.1 agent-browser.js
- 单文件，无外部依赖，压缩后 < 15KB
- 包含 snapshot + action + query + page 四组函数
- 通过 `window.__agentBrowser` 命名空间暴露

### 9.2 agent-browser-kotlin
- Gradle 工程，可发布为 maven artifact
- 依赖：`kotlinx-serialization-json`
- 内嵌 `agent-browser.js` 为资源文件
- 核心 API：`AgentBrowser` object
- 推荐 tool schema 定义

### 9.3 单元测试
- Snapshot：大 DOM 预算控制、interactiveOnly、compact
- Action：click/fill/select/check 各操作的 JS 正确性
- Ref 生命周期：重新 snapshot 后旧 ref 失效
- 边界：ref_not_found、not_a_select_element 等错误处理

### 9.4 README
- 架构图、完整交互流程示例
- JS 注入方式说明
- 推荐 tool schema
- 最佳实践：snapshot → action → re-snapshot 循环

## 10. 验收标准

1. 完整闭环：snapshot → 模型看到 ref → action(ref) → 操作成功 → re-snapshot 看到变化。
2. click 能触发真实页面行为（链接跳转、按钮提交等）。
3. fill 能正确填入值并触发 React/Vue 等框架的响应式更新。
4. select 能正确选择下拉选项。
5. ref_not_found 时返回明确错误，不崩溃。
6. snapshot 输出 ≤ maxCharsTotal。
7. JS 脚本执行时间 < 100ms（常规页面）。
8. Kotlin 库无 Android 依赖。

## 11. 与 v2 的关键差异

| 维度 | v2（只有 snapshot） | v3（snapshot + action） |
|---|---|---|
| 能力 | 只能看 | 能看能操作 |
| JS 函数 | `snapshotDOM()` | `snapshot()` + `action()` + `query()` + `page.*` |
| ref 用途 | 仅用于 query 查询 | 用于所有交互操作 |
| DOM 标记 | `data-agent-ref`（可选） | `data-agent-ref`（必须，action 定位依赖） |
| 工具协议 | 只有 `web_snapshot` | 完整 tool set（8+ 工具） |
| Agent 闭环 | 不完整 | 完整：看 → 决策 → 操作 → 看 |

## 12. 后续迭代（P1/P2）

- P1：iframe 支持（`switchToFrame` / `switchToMainFrame`）
- P1：文件上传（`input[type=file]`）
- P2：拖拽（drag and drop）
- P2：长按 / 双击
- P2：等待条件（`waitForSelector` / `waitForText`）
- P2：WebFetch 场景的 Jsoup fallback（纯 HTML，无 WebView）

---

这就是 v3。核心变化：从"只能看"变成"能看能动"，补全了 agent-browser 的 `snapshot → ref → action → re-snapshot` 完整闭环。JS 侧通过 `data-agent-ref` 属性做 O(1) 元素定位，action 实现模拟真实用户事件序列（不只是 `.click()`），确保 React/Vue 等框架能正确响应。