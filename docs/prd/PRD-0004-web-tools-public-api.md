# PRD-0004：WebView WebTools 对外 API 设计（tool 预算 ≤ 25）

- 基准愿景：`docs/prd/archive/PRD-V4.md`
- Agent 接入：`docs/prd/PRD-0003-openagentic-agent-e2e.md`
- OpenAI tool contract：`docs/tools/web-tools.openai.json`
- System prompt 模板（对外交付）：`docs/prompt/webview-webtools-system-prompt.md`
- 日期：2026-02-16

## 1. 背景

真实业务场景里，Agent 往往同时携带：
- WebView 自动化工具（本仓库）
- 文件系统工具（READ/WRITE/EDIT/MKDIR 等）
- 搜索/浏览/技能工具（WEBSEARCH/SKILL 等）

若 WebView 的 tools 过多，会挤占上下文（工具定义本身消耗 token），并降低工具选择稳定性。

因此：本 PRD 明确 **WebView WebTools 对外 API** 的边界、命名与数量上限，并对齐 `agent-browser` 的常用“Core commands”。

## 2. 目标（Goals）

1) **覆盖 agent-browser Core 语义**：open/navigate + click/fill/type/press/hover/select/check/uncheck/scroll/snapshot 等。
2) **补齐导航三件套**：`web_open` + `web_back/web_forward/web_reload`。
3) **wait 统一成 1 个工具**：避免拆成多个 `wait_*` 工具。
4) **tool 数量上限**：WebView 相关 tools **≤ 25**，为通用工具预留至少 10 个空位。
5) **schema 单一来源 + 门禁**：代码实现与 `docs/tools/web-tools.openai.json` 不允许暗漂移（由测试门禁保障）。

## 3. 设计原则（Design Principles）

- 一个 tool 一个职责（但 `web_wait` 例外：用“一个工具 + 多条件参数”收敛）。
- 参数尽量结构化（JSON schema 可校验），避免 “command 字符串协议”。
- 默认不提供高风险能力；若必须提供（如 `web_eval`），需可控开关（默认禁用）。
- 运行证据优先：E2E 需生成“人类可感知”的截图帧/报告/事件链。

## 4. 对外工具清单（Web tools ≤ 25）

本仓库当前对外 WebTools（以 `docs/tools/web-tools.openai.json` 为准）：

### 4.1 Navigation（对齐 agent-browser）

- `web_open(url)`：打开/跳转 URL（允许 `file://`/`http(s)://`；拒绝 `javascript:`/`data:`/`vbscript:`）
- `web_back()` / `web_forward()` / `web_reload()`

### 4.2 Snapshot + Actions（核心闭环）

- `web_snapshot(interactive_only?, cursor_interactive?, scope?)`
- `web_click(ref)` / `web_dblclick(ref)`
- `web_fill(ref, value)` / `web_type(ref, text)`
- `web_select(ref, values[])`
- `web_check(ref)` / `web_uncheck(ref)`
- `web_hover(ref)` / `web_scroll_into_view(ref)`
- `web_scroll(direction, amount?)`
- `web_press_key(key)`

### 4.3 Wait（单工具）

- `web_wait(...)`：仅一个 tool，支持：
  - `ms`：纯等待
  - `selector`：等待 selector 出现
  - `text`：等待页面包含文本
  - `url`：等待当前 URL 包含子串
  - `timeout_ms/poll_ms`：控制等待行为

### 4.4 Query / Debug（有限暴露）

- `web_query(ref, kind, max_length?)`：读取 text/html/value/attrs/computed_styles + 状态类 isvisible/isenabled/ischecked
- `web_screenshot(label?)`：真机证据截图（用于 e2e 报告/人工审阅）
- `web_eval(js, max_length?)`：调试用（默认禁用，需显式开启开关）
- `web_close()`：WebView 场景下等价于跳转 `about:blank`（非“关闭 App”）

## 5. 与 agent-browser Core commands 的映射（对照表）

| agent-browser command | 本仓库 tool | 说明 |
|---|---|---|
| `open <url>` | `web_open(url)` | 必备；并配套 back/forward/reload |
| `click <sel>` | `web_click(ref)` | 以 snapshot 的 `ref` 为主，不走 selector |
| `dblclick <sel>` | `web_dblclick(ref)` | WebView 侧实现为两次 click（满足语义） |
| `fill <sel> <text>` | `web_fill(ref,value)` | clear+fill |
| `type <sel> <text>` | `web_type(ref,text)` | 逐字符键入 |
| `press <key>` | `web_press_key(key)` | 在当前焦点元素上按键 |
| `hover <sel>` | `web_hover(ref)` | hover 事件 |
| `select <sel> <val>` | `web_select(ref,values)` | 支持按 value/text 匹配 |
| `check/uncheck <sel>` | `web_check/web_uncheck(ref)` | checkbox/radio |
| `scroll <dir> [px]` | `web_scroll(direction,amount)` | up/down/left/right |
| `snapshot` | `web_snapshot(...)` | 核心：refs + budget render |
| `wait ...` | `web_wait(...)` | 单工具收敛多条件 |
| `screenshot` | `web_screenshot(...)` | 证据向（非 buffer） |
| `eval <js>` | `web_eval(js)` | 默认禁用；仅调试 |
| `close` | `web_close()` | WebView 跳转 about:blank |

## 6. 规范化要求（DoD）

1) `docs/tools/web-tools.openai.json` 与代码侧 schema 必须一致（门禁测试）。
2) WebTools 数量 ≤ 25（门禁测试）。
3) 必须提供导航：`web_open/back/forward/reload`。
4) wait 只允许一个 tool：`web_wait`。
5) `web_eval` 必须默认禁用且可控开启。
