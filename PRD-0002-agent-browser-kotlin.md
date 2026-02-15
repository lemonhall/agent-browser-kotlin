# PRD-0002：agent-browser-kotlin（Kotlin 纯库，解决“HTML/DOM 太长吃 token”）

## 1. 背景与问题

我们在做一个 Android 聊天型 Agent App（供应商为 **OpenAI Responses API 风格**，且**必须流式**）。Agent 具备 Web 能力（WebView/WebFetch/WebSearch 等），会把网页内容作为工具结果返回给模型。

当前严重问题：工具把 `document.body.outerHTML`（或整页 DOM/HTML）直接返回给模型，导致一次 `tool.result` 就可能几十万字符，下一轮模型请求把该内容带上后触发 `context_length_exceeded`（上下文窗口超限），即使“对话轮次不多”也会爆。

证据（真机 dump 的 session）：曾出现单条 `tool.result` 约 590KB 的返回内容。

我们希望借鉴 `vercel-labs/agent-browser` 的核心理念：**snapshot + refs**，即用“低 token 的页面表征 + 引用(ref)交互”替代“整页 HTML 回传”：

- `snapshot` 返回可控长度的页面结构摘要，并给可交互/可读元素分配 `@e1/@e2/...` 引用；
- 后续按 ref 精准读取/操作，而不是反复喂整页 DOM。

参考项目：`https://github.com/vercel-labs/agent-browser`（重点看 snapshot/refs 的输出形态与压缩策略）。

## 2. 目标（Goals）

1) 输入：一段很大的 HTML（`String`），输出：**可控上限**的 `Snapshot`（结构化、短）+ `Ref` 映射。  
2) Snapshot 重点保留“可读/可操作”信息：文本、链接、按钮、输入框、表单元素、标题层级、列表项等；可配置 `interactiveOnly`。  
3) 支持按 ref 做后续查询：取某节点 `text/attrs/outerHTML`（但必须有长度上限与截断标记）。  
4) 预算约束：任何输出都必须在预算内（字符数、节点数、深度、每节点文本长度等），并显式标记 `truncated`。  
5) 纯 Kotlin 库：**不依赖 Android/WebView**；仅需要塞入 HTML 文本即可启动。  
6) 对上层 Agent 工具提供稳定协议：模型看到的是 snapshot；工具调用携带 ref 做精准查询。

## 3. 非目标（Non-goals）

- 不做真实浏览器自动化、不执行 JS、不处理动态 DOM（这些由适配层负责：例如 WebView 注入 JS 后把 HTML/片段交给本库）。  
- 不追求 100% HTML 规范解析覆盖；优先保证鲁棒、预算可控、对常见页面有效。  
- 不做网络抓取（fetch）。

## 4. 设计概览（API 设想）

### 4.1 数据结构（建议）

```kotlin
data class SnapshotOptions(
  val maxCharsTotal: Int = 12_000,
  val maxNodes: Int = 200,
  val maxDepth: Int = 12,
  val maxTextPerNode: Int = 200,
  val interactiveOnly: Boolean = true,
  val includeAttrs: Set<String> = setOf("href","name","type","value","placeholder","aria-label","role"),
  val scopeSelector: String? = null, // 可选：只 snapshot 某区域；初期可先不实现复杂 selector
  val outputFormat: OutputFormat = OutputFormat.PLAIN_TEXT_TREE,
  val dedupeText: Boolean = true,
)

data class SnapshotStats(
  val inputChars: Int,
  val nodesVisited: Int,
  val nodesEmitted: Int,
  val truncated: Boolean,
  val reasons: List<String> = emptyList(),
)

data class NodeRef(
  val ref: String,           // e1/e2/...
  val tag: String,           // a/button/input/...
  val role: String? = null,  // aria/语义推断
  val name: String? = null,  // aria-label/文本摘要
  val attrs: Map<String,String> = emptyMap(), // 受 includeAttrs 与截断控制
  val path: String? = null,  // 简短路径，可选
  val textSnippet: String? = null,
)

data class SnapshotResult(
  val snapshotText: String,
  val refs: Map<String, NodeRef>,
  val stats: SnapshotStats,
)

enum class RefQueryKind { TEXT, ATTR, OUTER_HTML }

data class RefQueryResult(
  val ref: String,
  val kind: RefQueryKind,
  val value: String,
  val truncated: Boolean,
)
```

### 4.2 核心函数（建议）

- 一次性便捷 API（简单但重复解析）：
  - `fun snapshot(html: String, options: SnapshotOptions = SnapshotOptions()): SnapshotResult`
  - `fun queryRef(html: String, ref: String, kind: RefQueryKind, limitChars: Int = 4_000): RefQueryResult`

- 可复用解析结果（推荐，减少重复解析）：
  - `fun buildDocument(html: String, options: SnapshotOptions = SnapshotOptions()): SnapshotDocument`
  - `fun snapshot(doc: SnapshotDocument): SnapshotResult`
  - `fun query(doc: SnapshotDocument, ref: String, kind: RefQueryKind, limitChars: Int = 4_000): RefQueryResult`

### 4.3 Snapshot 输出格式（给模型看的文本）

建议输出（`PLAIN_TEXT_TREE`）类似：

```
[snapshot] nodes=120 emitted=80 truncated=false
@e1  [button] "登录"
@e2  [link href="https://..."] "价格"
@e3  [input type="text" name="q" placeholder="搜索"]
@e4  [h1] "今日金价"
...
```

约束：

- `snapshotText.length <= options.maxCharsTotal`
- 单行文本/属性必须截断（避免某行爆炸）
- 若达到预算：停止遍历并将 `stats.truncated=true` + `reasons` 说明（如 `maxCharsTotal` / `maxNodes`）

## 5. 解析与抽取策略

### 5.1 HTML 解析（实现建议）

优先使用成熟解析器（纯 Kotlin/JVM）：

- `org.jsoup:jsoup`（JVM/Android 可用，容错好，生态成熟）

### 5.2 Ref 生成与候选节点规则

- 采用 DFS 遍历 DOM
- 遇到“候选节点”分配 `e1,e2,...`，并输出一行 snapshot
- 候选规则（可配置）：
  - interactiveOnly=true：`a, button, input, select, textarea, option, form, [role=button], [onclick]` 等
  - interactiveOnly=false：额外包括 `h1-h6, p, li, article, section` 等“可读结构”
- 文本提取：whitespace normalize；限制 `maxTextPerNode`
- attrs：仅白名单，且每个 value 限长

### 5.3 预算控制（硬约束）

- 全局：`maxCharsTotal`（最重要）
- 结构：`maxNodes`、`maxDepth`
- 字段：`maxTextPerNode`、attrs value 的上限
- 必须返回 `stats`，让上层工具/Agent 可以在 Tool Trace 里可视化预算命中原因

## 6. 与上层工具衔接（重要）

上层 Web 工具（WebView/WebFetch）应改为：

1) 抓到 HTML（或 DOM 片段）后 **不再直接返回整页 outerHTML**；
2) 调用本库 `snapshot(...)`，把 `snapshotText + stats` 作为主要 tool.result 返回给模型；
3) 模型需要更多细节时，再调用工具 `query_ref(ref, kind, limit)` 或 `snapshot(scope=...)`。

必须保证：工具返回永远不会把整页 HTML 原样塞回模型上下文。

## 7. 交付物（Deliverables）

- Kotlin Library：`agent-browser-kotlin`（Gradle 工程，可发布为 maven artifact）
- 核心 API + 实现：
  - `SnapshotOptions / SnapshotResult / SnapshotStats / NodeRef`
  - `buildDocument / snapshot / query`
- 单元测试：
  - 大 HTML（>500KB）能 snapshot 且输出受控
  - interactiveOnly 行为正确
  - 截断标记与 stats 正确
  - ref 稳定性：同一输入同一 options 下 refs 顺序一致
- README：
  - 背景、快速示例、最佳实践（禁止 outerHTML 直喂模型）
  - 推荐默认 options（例如 12k chars）

## 8. 验收标准（Acceptance Criteria）

- 输入 600KB HTML，输出 `snapshotText.length <= maxCharsTotal`，不会 OOM/卡死（合理时间内完成）。  
- 输出包含可交互元素 refs（link/button/input）且包含必要 attrs（href/type/name/placeholder 等）。  
- `query` 在 `limitChars` 下返回稳定且可控的截断结果，且不会泄露整页超长内容。  
- 无 Android 依赖，可在 JVM/Android 工程中引用。

## 9. 推荐默认配置（起点）

- `maxCharsTotal=12000`
- `maxNodes=200`
- `maxDepth=12`
- `maxTextPerNode=200`
- `interactiveOnly=true`
- `includeAttrs={href, name, type, value, placeholder, aria-label, role}`
- 输出：`PLAIN_TEXT_TREE`
